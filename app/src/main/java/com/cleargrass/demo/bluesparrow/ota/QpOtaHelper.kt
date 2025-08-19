package com.cleargrass.demo.bluesparrow.ota

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cleargrass.lib.ti.ota.TI_OtaSetting
import com.cleargrass.lib.ti.ota.TiOtaCallback
import com.cleargrass.lib.ti.ota.TiOtaController
import com.cleargrass.lib.ti.ota.UpdateState
import com.telink.ota.ble.GattConnection
import com.telink.ota.ble.OtaController
import com.telink.ota.ble.OtaController.GattOtaCallback
import com.telink.ota.foundation.OtaSetting
import com.telink.ota.util.OtaLogger
import java.util.UUID


class QpOtaHelper {
    lateinit var telinkOtaControler: OtaController
    lateinit var tiOtaControler: TiOtaController
    private var context: Context
    private var handler: Handler
    var gattConnection: GattConnection? = null

    init { }
    constructor(context: Context) {
        this.context = context
        this.handler = Handler(Looper.getMainLooper())
    }

    fun execTelinkOTA(device: BluetoothDevice, firmwareFile: String, otaCallback: GattOtaCallback) {
        if (gattConnection == null) {
            gattConnection = GattConnection(context)
        }
        gattConnection!!.setConnectionCallback(object: GattConnection.ConnectionCallback {
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
                    handler.postDelayed({
                        Log.e("OTA", "otaControler.startOta")
                        telinkOtaControler = OtaController(gattConnection)
                        telinkOtaControler.setOtaCallback(otaCallback)
                        telinkOtaControler.startOta(otaSetting)
                    }, 500)
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
        gattConnection!!.connect(device)
    }

    @SuppressLint("MissingPermission")
    fun execTiOTA(device: BluetoothDevice, firmwareFile: String, listener: TiOtaCallback) {
        val setting = TI_OtaSetting()
        setting.firmwarePath = firmwareFile
        tiOtaControler = TiOtaController(context, device, setting, handler);
        tiOtaControler.setProgressListener(listener)
        tiOtaControler.startOta();
    }
}