package com.cleargrass.demo.bluesparrow

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectWifiDialog(
    onConnect: (wifiName: String, password: String) -> Unit,
    onCancel: () -> Unit
) {
    var wifiName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        title = { Text("连接 WiFi")},
        text = {
            Column {
                // 第一个输入框
                TextField(
                    value = wifiName,
                    onValueChange = { text ->
                        wifiName = text
                    },
                    singleLine = true,
                    label = { Text("WiFi 名称") }
                )

                // 第二个输入框
                TextField(
                    value = password,
                    onValueChange = { text ->
                        password = text
                    },
                    singleLine = true,
                    label = { Text("密码") },
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                // 连接 WiFi
                onConnect(wifiName, password)
            }) {
                Text("连接")
            }
        },
        dismissButton = {
            Button(onClick = {
                onCancel()
            }) {
                Text("取消")
            }
        },
        onDismissRequest= {
            onCancel()
        }
    )

}
