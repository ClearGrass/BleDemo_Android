package com.cleargrass.demo.bluesparrow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp


val PRD_TYPE_NAMES = listOf("不限制", "网关", "门窗传感器", "门窗传感器2-TI", "人体传感器", "插座-TI")
val PRD_TYPES = listOf(0, 0x0d, 0x04, 0x61, 0x12, 0x59)
val PRD_TYPE_PAIRS = PRD_TYPES.zip(PRD_TYPE_NAMES)
@Composable
fun FilterBar(
    isScanning: Boolean,
    selectPrd: Int,
    checkBinding: Boolean,
    onProductTypeChange: (Int) -> Unit,
    onCheckChange: (Boolean) -> Unit,
) {
    var dropdownMenuBoxScope by remember {
        mutableStateOf(false)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isScanning) 0.4f else 1f)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 产品类型
        Row(modifier = Modifier.weight(1f)) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning,
                onClick = {
                dropdownMenuBoxScope = true
            }) {
                Text(
                    text = "产品: ${PRD_TYPE_NAMES[PRD_TYPES.indexOf(selectPrd)]}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterVertically),
                )
            }
            // 选择控件
            DropdownMenu(
                expanded = !isScanning && dropdownMenuBoxScope,
                onDismissRequest = {
                    dropdownMenuBoxScope = false
                }
            ) {
                PRD_TYPE_PAIRS.forEach { productType ->
                    DropdownMenuItem(
                        text = {
                            Text(text = productType.second)
                        },
                        trailingIcon = {
                              if (selectPrd == productType.first) Icon(
                                  imageVector = Icons.Default.Check,
                                  contentDescription = "Selected"
                              )
                        },
                        onClick = {
                            onProductTypeChange(productType.first)
                            dropdownMenuBoxScope = false
                        }
                    )
                }
            }
        }
        // 是否开关
        Row(modifier = Modifier.weight(1f)) {
            Text(
                text = "仅binding",
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Checkbox(
                checked = checkBinding,
                enabled = !isScanning,
                onCheckedChange = onCheckChange
            )
        }
        if (isScanning) {
            CircularProgressIndicator(

            )
        }
    }
}
