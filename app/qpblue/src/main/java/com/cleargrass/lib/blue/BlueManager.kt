package com.cleargrass.lib.blue

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.app.ActivityCompat
import com.cleargrass.lib.blue.core.Peripheral
import com.cleargrass.lib.blue.core.Peripheral.Callback
import com.cleargrass.lib.blue.core.Peripheral.OnConnectionStatusCallback
import com.cleargrass.lib.blue.core.QingpingScanManager
import com.cleargrass.lib.blue.core.QingpingFilter
import com.cleargrass.lib.blue.core.ScanCallback


@SuppressLint("MissingPermission")
public object BlueManager {
    val LOG_TAG = "BlueManager"
    private val peripherals: MutableMap<String, Peripheral> = mutableMapOf()
    private var bluetoothManager: BluetoothManager? = null
    private var qingpingScan: QingpingScanManager? = null
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    fun initBleManager(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        qingpingScan = QingpingScanManager(context)
        return bluetoothAdapter?.isEnabled == true
    }
    @MainThread@SuppressLint("MissingPermission")
    fun scan(filter: QingpingFilter, scanCallback: DeviceScanCallback): Boolean {
        if (bluetoothAdapter?.isEnabled != true) {
            scanCallback.onScanFailed(-99999);
            return false
        }
        stopScan();
        qingpingScan?.scan(filter, object :
            ScanCallback {
            override fun onScanStart() {
                scanCallback.onScanStart()
            }

            override fun onScanStop() {
                scanCallback.onScanStop()
            }

            override fun onAcceptDevice(scanResult: ScanResult?) {
                val peripheral = Peripheral(scanResult)
                synchronized(peripherals) {
                    peripherals[peripheral.device.address]= peripheral
                }
                scanCallback.onDeviceInRange(QingpingDevice(peripheral))
            }

            override fun onScanFailed(errorCode: Int) {
                // 处理扫描失败
                scanCallback.onScanFailed(errorCode)
            }

        })
        return true
    }
    @MainThread
    fun stopScan() {
        qingpingScan?.stopScan()
    }
    @MainThread
    fun connect(context: Context, address: String, onConnectStatusCallback: OnConnectionStatusCallback): Peripheral? {
        if (bluetoothAdapter?.isEnabled != true) {
            throw  IllegalStateException("Bluetooth not enabled!")
        }

        val peripheral = retrieveOrCreatePeripheral(address);
        // 检查设备是否已连接
        if (peripheral?.isConnected == true) {
            Log.d(LOG_TAG, "Peripheral (${address}) was connected. callback!")
            peripheral.setOnConnectStatusChange(onConnectStatusCallback)
            onConnectStatusCallback.onPeripheralConnected(peripheral)
            return peripheral
        }
        Log.d(LOG_TAG, "Peripheral (${address}) connecting... ")
        peripheral?.connect(context, object: Callback() {
            override fun invoke(error: String?, value: Boolean?) {
                if (value == true) {
                    // Connected
                }
            }
        }, onConnectStatusCallback)
        return peripheral
    }

    public fun retrieveOrCreatePeripheral(address: String): Peripheral? {
        // peripherals 保存了本次app启动扫描到过的广播实例
        var peripheral: Peripheral? = peripherals[address]
        if (peripheral == null) {
            synchronized(peripherals) {
                var peripheralUUID = address!!.uppercase()
                if (BluetoothAdapter.checkBluetoothAddress(peripheralUUID)) {
                    // 如果没有在缓存过的设备中找到，则通过getRemoteDevice接口构造一个，并添加到peripherals中
                    bluetoothAdapter?.getRemoteDevice(peripheralUUID)?.let { device: BluetoothDevice ->
                        peripheral = Peripheral(device);
                        peripherals.put(address, peripheral!!)
                    }
                }
            }
        }
        return peripheral
    }


    private fun internalRefreshDeviceCache(gatt: BluetoothGatt?): Boolean {
        if (gatt == null) // no need to be connected
            return false
        Log.d(LOG_TAG, "Refreshing device cache...")
        Log.d(LOG_TAG, "gatt.refresh() (hidden)")
        /*
         * There is a refresh() method in BluetoothGatt class but for now it's hidden.
         * We will call it using reflections.
         */try {
            val refresh = gatt.javaClass.getMethod("refresh")
            return refresh.invoke(gatt) as Boolean
        } catch (e: Exception) {
            Log.w(LOG_TAG, "An exception occurred while refreshing device", e)
            Log.d(LOG_TAG, "gatt.refresh() method not found")
        }
        return false
    }


}
