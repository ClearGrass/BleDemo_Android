package com.cleargrass.demo.bluesparrow

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cleargrass.demo.bluesparrow.data.ScanResultDevice
import com.cleargrass.demo.bluesparrow.ui.theme.QpDemoBlueSparrowTheme
import com.cleargrass.lib.blue.BlueManager
import com.cleargrass.lib.blue.Command
import com.cleargrass.lib.blue.QingpingDevice
import com.cleargrass.lib.blue.QpUtils
import com.cleargrass.lib.blue.core.Peripheral
import com.cleargrass.lib.blue.core.Peripheral.OnConnectionStatusCallback
import com.cleargrass.lib.blue.display
import com.cleargrass.lib.blue.isFF
import com.cleargrass.lib.blue.string
import java.lang.Exception
import java.lang.Integer.max

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
    var isLoading by remember {
        mutableStateOf(false)
    }
    var isConnected by remember {
        mutableStateOf(false)
    }
    var showInputWifi by remember {
        mutableStateOf<((newCommand: String) -> Unit)?>(null)
    }
    var debugCommands by remember {
        mutableStateOf(listOf<Command>())
    }
    LaunchedEffect(key1 = device) {
        device?.debugCommandListener = { command ->
            debugCommands = debugCommands + command
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {

        Column (modifier = Modifier.fillMaxSize()) {
            TopAppBar(title = {
                Column {
                    Text(text = d.name)
                    Text(text = "Device Detail", style = MaterialTheme.typography.titleSmall,)
                }
            }, actions={
                if (!isConnected) {
                    IconButton(
                        enabled = !isLoading,
                        onClick = {

                        }) {
                        Icon(Icons.Filled.Edit, contentDescription = "verify")
                    }
                    IconButton(
                        enabled = !isLoading,
                        onClick = {
                            isLoading = true
                            device?.connectBind(
                                    context = context,
                                    tokenString = "AABBCCDDEEFF",
                                    statusChange = object: OnConnectionStatusCallback {
                                        override fun onPeripheralConnected(peripheral: Peripheral?) {
                                            isConnected = true
                                        }

                                        override fun onPeripheralDisconnected(
                                            peripheral: Peripheral?,
                                            error: Exception?
                                        ) {
                                            isConnected = false
                                        }

                                    }
                                ) { bindR ->
                                    Log.e("blue", "connectBind: $bindR")
                                    isLoading = false
                                }
                                Log.e("blue", "正在连接...")
                        }) {
                        Icon(Icons.Filled.Send, contentDescription = "connect")
                    }
                } else {
                    IconButton(
                        enabled = !isLoading,
                        onClick = {
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
                    Text(text = d.data.display(), modifier = Modifier.padding(8.dp))
                }
            }

            Inputer(
                enabled = true || isConnected && !isLoading,
                onSendMessage = {
                    Log.d("blue", "will write $it")
                    device?.writeCommand(context = context, command = QpUtils.hexToBytes(it)) { it ->
                        Log.d("blue", "did get ${it.display()}")
                        if (it[0].isFF() && it[1] == 0x7.toByte()) {
                            // 这是WIFI 列表
                            val data = it.slice(2 until it.size)
                            debugCommands += Command( "${
                                data.toByteArray().string()
                                    .replace("\t", "\n")}\n",
                                "WIFI列表", byteArrayOf())
                        }
                    }
                }, menuItems = listOf(Pair("AP LIST(07)", "0107"), Pair("连接WIFI(01)", "")), onMenuClicked = { idx, string, onCommandCreated ->
                    Log.d("blue", "will write $string")
                    if (idx == 0) {
                        // ap列表
                    } else if (idx == 1) {
                        // 连接wifi连接wifi
                        showInputWifi = onCommandCreated
//                        onCommandCreated("")
                    }
                }
            )

        }
        if (showInputWifi != null) {
            ConnectWifiDialog(
                onConnect = { wifiName, password ->
                    Log.d("blue", "Connect wifi $wifiName $password")
                    // `"${wifi}","${password}"`
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
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
        .border(1.dp, if (command.action == "write") Color.Black else Color.Transparent)) {
        Text(
            command.action + "(" + command.uuid + ")\n" + command.bytes.display(),
            Modifier.padding(4.dp),
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
            QpUtils.hexToBytes("0x11223344AABBCCDE")))
    }
}