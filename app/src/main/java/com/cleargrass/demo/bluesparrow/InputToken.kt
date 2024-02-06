package com.cleargrass.demo.bluesparrow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cleargrass.lib.blue.QpUtils
import com.cleargrass.lib.blue.data.isGoodToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputToken(
    onTokenString: (tokenString: String, bindYesVerifyFalse: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var tokenString by remember { mutableStateOf("ABCDEFGHIJK") }
    var bindYesVerifyFalse by remember { mutableStateOf(false) }
    AlertDialog(
        title = { Text("请输入 Token")},
        text = {
            Column {
                // 第一个输入框
                TextField(
                    value = tokenString,
                    onValueChange = { text ->
                        tokenString = text
                    },
                    singleLine = true,
                    label = { Text("Token") },
                    isError = !tokenString.isGoodToken(),
                    supportingText = { Text(text = "Token是12到18位的字符或数字") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Random Icon",
                            modifier = Modifier.clickable {
                                tokenString = QpUtils.randomToken()
                            }
                        )
                    }
                )
                Row(verticalAlignment= Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    Text(text = "验证Token")
                    Switch(modifier = Modifier.padding(4.dp), checked = bindYesVerifyFalse, onCheckedChange = {
                        bindYesVerifyFalse = it
                    })
                    Text(text = "绑定Token")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                CoroutineScope(Dispatchers.Main).launch {
                    onTokenString(tokenString, bindYesVerifyFalse)
                }
            }) {
                Text("连接")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                onCancel()
            }) {
                Text("取消", color = Color.Black)
            }
        },

        onDismissRequest= {
            onCancel()
        }
    )

}
@Preview(showBackground = true)
@Composable
fun InputTokenPreview() {
    InputToken(
        onTokenString = { token, bind ->

        },
        onCancel = {

        }
    )
}