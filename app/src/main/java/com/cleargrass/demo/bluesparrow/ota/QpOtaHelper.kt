package com.cleargrass.demo.bluesparrow.ota

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.util.Log
import com.telink.ota.ble.GattConnection
import com.telink.ota.ble.OtaController
import com.telink.ota.ble.OtaController.GattOtaCallback
import com.telink.ota.foundation.OtaSetting
import com.telink.ota.foundation.OtaStatusCode
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class QpOtaHelper {
    lateinit var otaControler: OtaController
    val gattConnection: GattConnection

    init { }
    constructor(context: Context) {
        gattConnection = GattConnection(context)
    }

    fun execOta(device: BluetoothDevice, firmwareFile: String, otaCallback: GattOtaCallback) {
        gattConnection.setConnectionCallback(object: GattConnection.ConnectionCallback {
            override fun onConnectionStateChange(
                state: Int,
                gattConnection: GattConnection?,
                statusCode: Int
            ) {
                val isConnected = state == BluetoothGatt.STATE_CONNECTED
                Log.d("OTA", "onConnectionStateChange: $isConnected")
                if (isConnected) {
                    val otaSetting = OtaSetting();
                    otaSetting.firmwarePath = firmwareFile
                    /**
                     * 这里的 startOta 一定要在主线程运行
                     */
                    MainScope().launch {
                        Log.e("OTA", "otaControler.startOta")
                        otaControler = OtaController(gattConnection)
                        otaControler.setOtaCallback(otaCallback)
                        otaControler.startOta(otaSetting)
                    }
                }
            }

            override fun onNotify(
                data: ByteArray?,
                serviceUUID: UUID?,
                characteristicUUID: UUID?,
                connection: GattConnection?
            ) {
            }

            override fun onMtuChanged(
                mtu: Int,
                connection: GattConnection?
            ) {
            }

        })
        gattConnection.setDevice(device)
//                                gattConnection.connect(device.peripheral.device)
    }
}