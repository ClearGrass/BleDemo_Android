package com.cleargrass.demo.bluesparrow

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cleargrass.demo.bluesparrow.data.ScanResultDevice
import com.cleargrass.demo.bluesparrow.ui.theme.QpDemoBlueSparrowTheme
import com.cleargrass.lib.blue.BlueManager
import com.cleargrass.lib.blue.DeviceScanCallback
import com.cleargrass.lib.blue.QingpingDevice
import com.cleargrass.lib.blue.core.QingpingFilter

class MainActivity : ComponentActivity() {
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

            QpDemoBlueSparrowTheme {
                // A surface container using the 'background' color from the theme
                MainPage()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // check bluetooth permission

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) !=
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT),
                    1
                )

            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.BLUETOOTH_ADMIN),
                    1
                )

            }
        }
    }

    override fun onPause() {
        super.onPause()
        BlueManager.stopScan()
    }
}
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPage() {
    val context = LocalContext.current;
    val scanDevice  = remember {
        mutableStateListOf<ScanResultDevice>(
            ScanResultDevice("Name1", "Address", 123, byteArrayOf()),
            ScanResultDevice("Name2", "Address", 123, byteArrayOf()),
            ScanResultDevice("Name3", "Address", 123, byteArrayOf()),
        )
    }
    var onlyPairing by remember {
        mutableStateOf(false)
    }
    var selectedPrd by remember {
        mutableStateOf(0)
    }
    var isScanning by remember {
        mutableStateOf(false)
    }
    var filterText by remember {
        mutableStateOf("")
    }
    var content by remember {
        mutableStateOf("")
    }
    Scaffold(
        modifier = Modifier,
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier,
                onClick = {
                    if (isScanning) {
                        BlueManager.stopScan()
                        return@FloatingActionButton;
                    }
                    scanDevice.clear()
                    if (BlueManager.initBleManager(context)) {
                        BlueManager.scan(QingpingFilter.build(onlyPairing, false, if (selectedPrd > 0) byteArrayOf(
                            selectedPrd.toByte()
                        ) else null, null), object :DeviceScanCallback() {
                            override fun onDeviceInRange(qingpingDevice: QingpingDevice) {
                                Log.d("blue", "onDeviceInRange: $qingpingDevice")
                                ScanResultDevice(qingpingDevice.name, qingpingDevice.address, 1, qingpingDevice.scanData).let {
                                    if (scanDevice.contains(it)) {
                                        scanDevice.remove(it)
                                    }
                                    scanDevice.add(0, it)
                                }
                            }

                            override fun onScanStart() {
                                Log.d("blue", "onScanStart")
                                isScanning = true;
                            }

                            override fun onScanStop() {
                                Log.d("blue", "onScanStop")
                                isScanning = false;
                            }

                            override fun onScanFailed(errorCode: Int) {
                                Log.d("blue", "onScanFailed $errorCode")
                                isScanning = false;
                                content = "Scan Failed:$errorCode"
                            }

                        })
                    } else {
                        Log.d("blue", "onScanFailed permission denied")
                        content = "Scan Failed: permission denied"
                    }

                })
            { Icon(imageVector = if (isScanning) Icons.Default.Close else Icons.Default.Refresh, contentDescription = "Add") }
        }
    ) {
        Column {
            Greeting("Qingping")
            FilterBar(
                isScanning = isScanning,
                selectPrd = selectedPrd,
                checkBinding = onlyPairing,
                onCheckChange = {
                    onlyPairing = it
                },
                onProductTypeChange = {
                    selectedPrd = it
                },
            )
            Row {
                TextField(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    value = filterText,
                    singleLine = true,
                    placeholder = {Text("过滤名称或MAC地址")},
                    onValueChange = {
                        filterText = it
                    })
            }
            DeviceList(if (filterText.isEmpty()) scanDevice else scanDevice.filter {
                it.name.contains(filterText, true)
                        || it.macAddress.replace(":", "").contains(filterText.replace(":", ""), true)
            }) {
                val intent = Intent(context, DeviceActivity::class.java)
                intent.putExtra("device", it)
                context.startActivity(intent)
            }
        }

        if (content.isNotEmpty()) {
            AlertDialog(
                text={
                    Text(text = content)
                },
                onDismissRequest = {
                    content = ""
                },
                confirmButton = {
                    Button(onClick = {
                        content = ""
                    }) {
                        Text(text = "OK")
                    }
                }
            )
        }
    }
}
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    QpDemoBlueSparrowTheme {
        MainPage()
    }
}