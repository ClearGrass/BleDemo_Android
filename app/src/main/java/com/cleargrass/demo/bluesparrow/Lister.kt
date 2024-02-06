package com.cleargrass.demo.bluesparrow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cleargrass.demo.bluesparrow.data.ScanResultDevice
import com.cleargrass.lib.blue.data.*

@Composable
fun DeviceList(devices: List<ScanResultDevice>, onItemClicked: (ScanResultDevice) -> Unit) {
    LazyColumn(modifier = Modifier.padding(8.dp)) {
        items(devices) { device ->
            DeviceItem(device) {
                onItemClicked(device)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceItem(device: ScanResultDevice, onClicked: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        onClick = onClicked
    ) {
        Column {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RSSI: ${device.rssi}",
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.End
                )
            }
            Row {

                Text(
                    text = "MAC: ${device.macAddress}",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = "PID: 0x${device.productId.toString(16)} (${device.productId})",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.Black
                )
                if (device.isBinding) {
                    Text(
                        text = "  [binding]",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Red
                    )
                }
            }
            if (device.macAddress.startsWith("06:66")) {
                Text(
                    text = "clientid: ${device.clientId}",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Start
                )
            }
            Text(text = device.data.rawBytes.display(), modifier = Modifier.padding(8.dp))
        }
    }
}

