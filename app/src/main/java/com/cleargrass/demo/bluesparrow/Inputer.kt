package com.cleargrass.demo.bluesparrow

import android.util.Log
import android.view.Menu
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

typealias MenuClicked = (Int, onCommandCreated: ((String) -> Unit)?) -> Unit

typealias MenuItem = Triple<String, String, MenuClicked?>


@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun Inputer(
    enabled: Boolean = true,
    targetUuid: String = "0001",
    onChangeTargetUuid: (String) -> Unit,
    menuItems: List<MenuItem>,
    onSendMessage: (String) -> Unit,
) {
    // 输入框
    var inputText by remember { mutableStateOf( TextFieldValue()) }
    var menuShow by remember { mutableStateOf(false) }
    var abcdefKeyShow by remember { mutableStateOf(false) }
    // 组合
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 菜单
            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                IconButton(
                    enabled=enabled && menuItems.isNotEmpty(),
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
                    },

                ) {
                    DropdownMenuItem(
                        text = { Text("写到0001", Modifier) },
                        onClick = {
                            onChangeTargetUuid("0001")
                            menuShow = false
                        },
                        trailingIcon = {
                            if (targetUuid == "0001") Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = ""
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("写到0015", Modifier) },
                        onClick = {
                            onChangeTargetUuid("0015")
                            menuShow = false
                        },
                        trailingIcon = {
                            if (targetUuid == "0015") Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = ""
                            )
                        }
                    )
                    Divider()
                    menuItems.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = { Text(item.first, Modifier) },
                            onClick = {
                                if (item.second.isNotEmpty()) {
                                    onSendMessage(item.second)
                                    inputText = inputText.set()
                                }
                                item.third?.let {
                                    it(index) { newCommand ->
                                        if (newCommand.isNotEmpty()) {
                                            onSendMessage(newCommand)
                                            inputText = inputText.set()
                                        }
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
        if (enabled && WindowInsets.isImeVisible) {
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
        onChangeTargetUuid = {},
        onSendMessage = {},  menuItems = listOf(
            MenuItem("WIFI", "0107", null),
            MenuItem("WIFI_旧", "0104", null),
        )
    )
}