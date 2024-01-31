package com.cleargrass.demo.bluesparrow

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cleargrass.demo.bluesparrow.data.ScanResultDevice
import com.cleargrass.demo.bluesparrow.ui.theme.QpDemoBlueSparrowTheme
import com.cleargrass.lib.blue.BlueManager
import com.cleargrass.lib.blue.BuildConfig
import com.cleargrass.lib.blue.Command
import com.cleargrass.lib.blue.QingpingDevice
import com.cleargrass.lib.blue.QpUtils
import com.cleargrass.lib.blue.core.Peripheral
import com.cleargrass.lib.blue.core.Peripheral.OnConnectionStatusCallback
import com.cleargrass.lib.blue.core.UUIDHelper
import com.cleargrass.lib.blue.core.UUIDs
import com.cleargrass.lib.blue.data.*
import java.lang.Exception
import java.lang.Integer.max
import java.util.regex.Pattern

class DeviceActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra<ScanResultDevice>("device")!!
        setContent {
            QpDemoBlueSparrowTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeviceDetail(device)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetail(d: ScanResultDevice) {
    val context =  LocalContext.current
    val device = remember {
        BlueManager.retrieveOrCreatePeripheral(d.macAddress)?.let { QingpingDevice(it) }
    }
    var toCommonCharacteristic by remember {
        mutableStateOf(true)
    }
    var isLoading by remember {
        mutableStateOf(false)
    }
    var isConnected by remember {
        mutableStateOf(false)
    }
    var showInputToken by remember {
        mutableStateOf<((token: String, bind: Boolean) -> Unit)?>(null)
    }
    var showInputWifi by remember {
        mutableStateOf<((newCommand: String) -> Unit)?>(null)
    }
    var debugCommands by remember {
        mutableStateOf(listOf<Command>())
    }
    LaunchedEffect(key1 = device) {
        device?.debugCommandListener = { command ->
            // 这是 蓝牙命令的每一个回调都会显示到界面
            debugCommands = debugCommands + command
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {

        Column (modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = {
                Column {
                    Text(text = d.macAddress)
                    Text(text = d.name, style = MaterialTheme.typography.titleSmall,)
                }
            }, actions={
                if (!isConnected) {
                    IconButton(
                        enabled = !isLoading,
                        onClick = {
                            showInputToken = { token, bind ->
                                showInputToken = null
                                isLoading = true
                                debugCommands = debugCommands + Command("Connecting and ${if (bind) "Bind" else "Verify"}",
                                    d.macAddress,
                                    QpUtils.wrapProtocol(if (bind) 1 else 2, token.toByteArray())
                                )
                                toCommonCharacteristic = true
                                val connectionStatusCallback = object: OnConnectionStatusCallback {
                                    override fun onPeripheralConnected(peripheral: Peripheral?) {
                                        isConnected = true
                                        toCommonCharacteristic = true
                                        debugCommands = debugCommands + Command("[Connected]", d.macAddress, byteArrayOf())
                                    }

                                    override fun onPeripheralDisconnected(
                                        peripheral: Peripheral?,
                                        error: Exception?
                                    ) {
                                        isLoading = false
                                        isConnected = false
                                        toCommonCharacteristic = true
                                        debugCommands = debugCommands + Command("[Disconnected]", error?.localizedMessage.toString(), byteArrayOf())
                                    }
                                }
                                try {
                                    if (bind) {
                                        device?.connectBind(
                                            context = context,
                                            tokenString = token,
                                            statusChange = connectionStatusCallback
                                        ) { bindResult ->
                                            Log.e("blue", "connectBind: $bindResult")
                                            debugCommands = debugCommands + Command(
                                                "[Bind] Result",
                                                if (bindResult) "SUCCESS" else "FAILED",
                                                byteArrayOf()
                                            )
                                            isLoading = false
                                            toCommonCharacteristic = !bindResult
                                        }
                                    } else {
                                        device?.connectVerify(
                                            context = context,
                                            tokenString = token,
                                            statusChange = connectionStatusCallback
                                        ) { verifyResult ->
                                            Log.e("blue", "connectVerify: $verifyResult")
                                            debugCommands = debugCommands + Command(
                                                "[Verify] Result",
                                                if (verifyResult) "SUCCESS" else "FAILED",
                                                byteArrayOf()
                                            )
                                            isLoading = false
                                            toCommonCharacteristic = !verifyResult
                                        }
                                    }
                                } catch (e: Exception) {
                                    debugCommands = debugCommands + Command(
                                        "[Error]",
                                        e.localizedMessage,
                                        byteArrayOf()
                                    )
                                }
                            }
                            Log.e("blue", "正在连接...")
                        }) {
                        Icon(Icons.Filled.Send, contentDescription = "connect")
                    }
                } else {
                    IconButton(
                        enabled = !isLoading,
                        onClick = {
                            if (isLoading) {
                                return@IconButton
                            }
                            if (device != null) {
                                isLoading = true
                                device.disconnect()
                            } else {
                                isConnected = false
                            }
                        }) {
                        Icon(Icons.Filled.Close, contentDescription = "disconnect")
                    }
                }

            })
            CommandList(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp), debugCommands) {
                Card(modifier = Modifier.padding(8.dp)) {
                    Text(text = d.data.rawBytes.display(), modifier = Modifier.padding(8.dp))
                }
            }

            Inputer(
                enabled = isConnected && !isLoading,
                targetUuid = if (toCommonCharacteristic) "0001" else "0015",
                onSendMessage = {
                    Log.d("blue", "will write $it")
                    if (device == null) {
                        debugCommands += Command( "Not connected", d.macAddress, QpUtils.hexToBytes(it))
                        return@Inputer
                    }
                    if (!toCommonCharacteristic) {
                        device.writeCommand(command = QpUtils.hexToBytes(it)) { it ->
                            Log.d("blue", "ble response  ${it.display()}")
                            //这里把比较特殊的协议回应解析后显示到界面上中方便查看。
                            // WIFI列表
                            if (it[0].isFF() && it[1] == 0x7.toByte()) {
                                // 这是WIFI 列表
                                val data = it.slice(2 until it.size)
                                debugCommands += Command( "parse", "WIFI列表", it)
                            }

                            // 连接WIFI结果
                            if (it[1] == 0x01.toByte()) {
                                debugCommands += Command("parse", if (it[2] == 1.toByte()) "连接WIFI成功" else "连接WIFI失败", it)
                            }
                        }
                    } else {
                        device.writeInternalCommand(command = QpUtils.hexToBytes(it)) {
                            Log.d("blue", "ble response ${it.display()}")

                            if (it[0].isFF() && it[1] == 0x1e.toByte()) {
                                // 这是WIFI 列表
                                debugCommands += Command("parse", "0002; client_id", it)
                            }
                        }
                    }

                },
                menuItems = listOf(Pair("AP LIST(07)", "0107"), Pair("连接WIFI(01)", ""), Pair("client_id(1E)", "011E"))
            ) { idx, string, onCommandCreated ->
                if (idx < 0) {
                    // 切换命令特征
                    toCommonCharacteristic = idx == -1
                    return@Inputer
                }
                Log.d("blue", "will write $string")
                if (idx == 0) {
                    // ap列表
                } else if (idx == 1) {
                    // 连接wifi连接wifi
                    showInputWifi = onCommandCreated
                }
            }
        }
        if (showInputToken != null) {
            InputToken(onTokenString = showInputToken!!, onCancel = {
                showInputToken = null
            })
        }
        if (showInputWifi != null) {
            ConnectWifiDialog(
                onConnect = { wifiName, password ->
                    Log.d("blue", "Connect wifi $wifiName $password")
                    // 这里创建 连接WIFI的命令，数据主体是： `"${wifi}","${password}"`
                    var command = "\"${wifiName}\",\"${password}\""

                    showInputWifi?.invoke( QpUtils.wrapProtocol(1, QpUtils.stringToBytes(command)).display() )
                    showInputWifi = null
                },
                onCancel = { showInputWifi = null },)
        }
        if (isLoading) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray.copy(0.5f))
                .focusable(true)) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            BackHandler {
                if (device != null) {
                    isLoading = true
                    device.disconnect()
                } else {
                    isLoading = false
                    isConnected = false
                }
            }
        }
    }
}
@Composable
fun CommandList(modifier:Modifier = Modifier, commands: List<Command>, content: @Composable () -> Unit) {
    val state: LazyListState = rememberLazyListState()
    LaunchedEffect(key1 = commands) {
        state.scrollToItem(max(commands.size - 1, 0))
    }
    LazyColumn(modifier = modifier, state = state) {

        item("header") {
            content()
        }
        items(commands) { command ->
            CommandText(command)
        }
    }

}
@Composable
fun CommandText(command: Command) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current;
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
        .border(1.dp, if (command.action == "write") Color.Black else Color.Transparent)) {
        Text(
            command.action
                    + "(" + command.uuid + ")\n"
                    + (if (command.bytes.isNotEmpty()) command.bytes.display() else "")
                    + if (command.bytes.size > 5 && !command.bytes[1].isFF())
                            (command.bytes.slice(2 until command.bytes.size).toByteArray().string().trim().let {
                            if (it.isNotEmpty()) {
                                // 替换 \t 是为了方便看
                                return@let "\n[string: ${it.replace("\t", "\n")}]"
                            } else {
                                ""
                            }
                        }) else ""

                .trim(),
            Modifier.padding(4.dp).clickable(enabled = command.bytes.isNotEmpty()) {
                clipboardManager.setText(AnnotatedString(command.bytes.display()))
                Toast.makeText(context, "已复制Bytes", Toast.LENGTH_SHORT).show()
            },
        )

    }
}
@Preview(showBackground = true)
@Composable
fun GreetingPreview2() {
    QpDemoBlueSparrowTheme {
        DeviceDetail(ScanResultDevice(
            "Sparrow",
            "11:22:33:44:55:66",
            1,
            ScanResultParsed(QpUtils.hexToBytes("0x11223344AABBCCDE"))
        ))
    }
}