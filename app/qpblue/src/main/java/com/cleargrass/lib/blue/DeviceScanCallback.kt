package com.cleargrass.lib.blue

import android.bluetooth.le.ScanResult
import com.cleargrass.lib.blue.core.ScanCallback

abstract class DeviceScanCallback : ScanCallback {
    override fun onAcceptDevice(scanResult: ScanResult) {}
    abstract fun onDeviceInRange(qingpingDevice: QingpingDevice)
}