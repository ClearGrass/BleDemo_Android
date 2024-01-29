package com.cleargrass.demo.bluesparrow

import android.app.Instrumentation
import android.util.Log
import android.view.KeyEvent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Inputer(
    enabled: Boolean = true,
    onSendMessage: (String) -> Unit,
    menuItems: List<Pair<String, String>>,
    onMenuClicked: ((index: Int, Pair<String, String>, onCommandCreated: (newCommand: String) -> Unit)-> Unit)?
) {
    // 输入框
    var inputText by remember { mutableStateOf( TextFieldValue()) }
    var menuShow by remember { mutableStateOf(false) }
    var keyShow by remember { mutableStateOf(false) }
    // 组合
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 菜单
            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                IconButton(
                    enabled=enabled,
                    onClick = {
                    menuShow = !menuShow
                }) {
                    Icon(imageVector = Icons.Default.Build, contentDescription = "")
                }
                DropdownMenu(
                    modifier = Modifier.width(200.dp),
                    expanded = menuShow,
                    onDismissRequest = {
                        menuShow = false
                    }
                ) {
                    menuItems.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = { Text(item.first, Modifier) },
                            onClick = {
                                if (item.second.isNotEmpty()) {
                                    onSendMessage(item.second)
                                    inputText = inputText.set()
                                }
                                onMenuClicked?.invoke(index, item) {newCommand ->
                                    if (newCommand.isNotEmpty()) {
                                        onSendMessage(newCommand)
                                        inputText = inputText.set()
                                    }
                                }
                                menuShow = false
                            }
                        )
                    }
                }
            }

            // 输入框
            Box(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .weight(1f)
                    .clickable {
                        keyShow = !keyShow
                        menuShow = false
                    }
            ) {

                TextField(
                    enabled=enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp)
                        .background(Color.White),
//                    enabled = false,
                    value = inputText,
                    onValueChange = { inputText = it },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Send,
                        autoCorrect = false
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onSendMessage(inputText.text)
                            inputText = inputText.set()
                        }
                    ),
                    colors = TextFieldDefaults.textFieldColors(
                        disabledTextColor = Color.Black,
                        containerColor = Color.White,
                    ),

                    placeholder = { Text("请输入16进制文字", Modifier) },

                )
            }

            // 发送按钮
            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                Button(
                    enabled=enabled,
                    modifier = Modifier.height(50.dp),
                    onClick = {
                        // 发送消息
                        onSendMessage(inputText.text)
                        inputText = inputText.set()
                    }
                ) {
                    Text("发送")
                }
            }
        }
        if (enabled) {
            Row {
                ABCDEF(modifier = Modifier.weight(1f), "A") {
                    inputText = inputText.insert("A")
                }
                ABCDEF(modifier = Modifier.weight(1f), "B") {
                    inputText = inputText.insert("B")
                }
                ABCDEF(modifier = Modifier.weight(1f), "C") {
                    inputText = inputText.insert("C")
                }
                ABCDEF(modifier = Modifier.weight(1f), "D") {
                    inputText = inputText.insert("D")
                }
                ABCDEF(modifier = Modifier.weight(1f), "E") {
                    inputText = inputText.insert("E")
                }
                ABCDEF(modifier = Modifier.weight(1f), "F") {
                    inputText = inputText.insert("F")
                }
            }
        }
    }
}
fun TextFieldValue.set(string: String = ""): TextFieldValue {
    return copy(text = string)
}
fun TextFieldValue.insert(string: String): TextFieldValue {
    Log.d("blueinput", string + ">" + selection.start +"|"+ selection.end)
    return copy(
        text = text.insert(selection.start, string),
        selection= TextRange(selection.start+ string.length)
    )
}
inline fun String.insert(index: Int, text: String): String {
    Log.d("bluesert", "$this + $text > $index")
    val builder = StringBuilder(this)
    builder.insert(index, text)
    Log.d("bluesert", builder.toString())
    return builder.toString()
}
@Composable
fun ABCDEF(modifier:Modifier, text: String, onClickText: (String) -> Unit) {
    Button(onClick = {
        onClickText(text)
    }, modifier = modifier.border(1.dp, Color.Gray),
        shape = RoundedCornerShape(5),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        )
    ) {
        Text(text = text, Modifier.padding(0.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun InputComponentPreview() {
    Inputer(
        enabled = false,
        onSendMessage = {},  menuItems = listOf(
            Pair("WIFI", "0107"),
            Pair("WIFI_旧", "0104"),
        ), onMenuClicked = null
    )
}