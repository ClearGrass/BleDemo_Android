package com.cleargrass.lib.blue

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.cleargrass.lib.blue.core.Peripheral
import com.cleargrass.lib.blue.core.Peripheral.Callback
import com.cleargrass.lib.blue.core.Peripheral.OnConnectionStatusCallback
import com.cleargrass.lib.blue.core.Peripheral.UuidAndBytes
import com.cleargrass.lib.blue.core.Peripheral.ValueCallback
import com.cleargrass.lib.blue.core.UUIDHelper
import com.cleargrass.lib.blue.core.UUIDs
import com.cleargrass.lib.blue.data.Protocol
import com.cleargrass.lib.blue.data.*
import java.lang.IllegalStateException
import java.util.UUID

data class DebugCommand(val action: String, val uuid: String, val bytes: ByteArray, val succ: Boolean? = null)
typealias DebugCommandListener = (DebugCommand) -> Unit
typealias CommandResponder = (ByteArray) -> Unit
typealias ActionResult = (Boolean) -> Unit
@SuppressLint("MissingPermission")
class QingpingDevice constructor(var peripheral: Peripheral) {
    var deviceId: String? = null
    val name: String
        get() = peripheral.device.name ?: ""
    val address: String
        get() = peripheral.device.address ?: "??:??:??:??:??:??"
    val scanData: ByteArray
        get() = peripheral.advertisingBytes ?: byteArrayOf()
    val rssi: Int
        get() = peripheral.advertisingRSSI
    val productType: Byte
        get() = scanData[8]

    private val notifyCallback: ValueCallback<UuidAndBytes>
    private val reponseCollector = ResponseCollector();
    public var debugCommandListener: DebugCommandListener?= null

    init {
        val bytes = peripheral.advertisingBytes;
        if (bytes != null && bytes.size >= 13) {
            deviceId = peripheral.device.address.replace(":", "")
        }
        notifyCallback = object: ValueCallback<UuidAndBytes>() {
            override fun invoke(error: String?, uuidBytes: UuidAndBytes?) {
                uuidBytes?.let {uuidBytes ->
                    Log.d("blue", "Response: ${QpUtils.parseProtocol(uuidBytes.bytes)} Uuid: ${uuidBytes.uuid}")
                    debugCommandListener?.invoke(DebugCommand("notify", UUIDHelper.simpler(uuidBytes.uuid), uuidBytes.bytes, null))
                    reponseCollector.collect(uuidBytes)
                }
            }
        }
    }

    private fun connect(context: Context, statusChange: OnConnectionStatusCallback) {
        BlueManager.connect(context, address, object:
            OnConnectionStatusCallback {
            override fun onPeripheralConnected(peripheral: Peripheral?) {
                peripheral?.retrieveServices(object: Callback() {
                    override fun invoke(error: String?, value: Boolean?) {
                        if (value == true) {
                            peripheral.registerNotify(UUIDs.SERVICE, UUIDs.COMMON_READ, object: Callback() {
                                override fun invoke(error: String?, value: Boolean?) {
                                    if (value == true) {
                                        statusChange.onPeripheralConnected(peripheral)
                                    } else {
                                        peripheral.disconnect()
                                    }
                                }
                            }, this@QingpingDevice.notifyCallback);
                        } else {
                            peripheral.disconnect()
                        }
                    }
                })
            }
            override fun onPeripheralDisconnected(peripheral: Peripheral?, error: Exception?) {
                statusChange.onPeripheralDisconnected(peripheral, error)
            }
        })
    }
    private fun bind(context: Context, randomBytes: ByteArray, responder: ActionResult) {
        var callOnce = false
        writeInternalCommand(QpUtils.wrapProtocol(0x01, randomBytes)) { bindResponse ->
            if (QpUtils.parseProtocol(bindResponse)?.resultSuccess != true) {
                if (!callOnce) {
                    responder.invoke(false)
                    callOnce = true;
                }
            } else {
                verify(context, randomBytes, responder)
            }
        }
    }
    private fun verify(context: Context, randomBytes: ByteArray, responder: ActionResult) {
        var callOnce = false
        writeInternalCommand(QpUtils.wrapProtocol(0x02, randomBytes)) { verifyResponse ->
            if (QpUtils.parseProtocol(verifyResponse)?.resultSuccess != true) {
                if (!callOnce) {
                    responder.invoke(false)
                    callOnce = true;
                }
            } else {
                if (!callOnce) {
                    responder.invoke(true)
                    callOnce = true;
                }
                writeInternalCommand(QpUtils.wrapProtocol(0x0D)) {
                    peripheral.registerNotify(UUIDs.SERVICE, UUIDs.MY_READ, object: Callback() {
                        override fun invoke(error: String?, value: Boolean?) {
                            Log.e("blue", "registerNotify(0016):" + (error ?: "") + "result: $value")
                        }
                    }, this@QingpingDevice.notifyCallback);
                }
            }
        }
    }

    fun connectBind(context: Context, tokenString: String, statusChange: OnConnectionStatusCallback, responder: ActionResult) {
        if (!tokenString.isGoodToken()) {
            throw IllegalArgumentException("Invalid token: $tokenString")
        }
        connect(context, object :
            OnConnectionStatusCallback {
            override fun onPeripheralConnected(peripheral: Peripheral?) {
                statusChange.onPeripheralConnected(peripheral)
                bind(context, QpUtils.stringToBytes(tokenString), object: ActionResult{
                    override fun invoke(value: Boolean) {
                        responder.invoke(value)
                        peripheral?.setOnConnectStatusChange(statusChange)
                    }
                })
            }
            override fun onPeripheralDisconnected(peripheral: Peripheral?, error: Exception?) {
                statusChange.onPeripheralDisconnected(peripheral, error)
                responder.invoke(false)
                peripheral?.setOnConnectStatusChange(statusChange)
            }
        })
    }
    fun connectVerify(context: Context, tokenString: String, statusChange: OnConnectionStatusCallback, responder: ActionResult) {
        if (!tokenString.isGoodToken()) {
            throw IllegalArgumentException("Invalid token: $tokenString")
        }
        connect(context, object :
            OnConnectionStatusCallback {
            override fun onPeripheralConnected(peripheral: Peripheral?) {
                statusChange.onPeripheralConnected(peripheral)
                verify(context, QpUtils.stringToBytes(tokenString), object: ActionResult{
                    override fun invoke(value: Boolean) {
                        responder.invoke(value)
                        peripheral?.setOnConnectStatusChange(statusChange)
                    }
                })
            }
            override fun onPeripheralDisconnected(peripheral: Peripheral?, error: Exception?) {
                statusChange.onPeripheralDisconnected(peripheral, error)
                responder.invoke(false)
                peripheral?.setOnConnectStatusChange(statusChange)
            }
        })
    }
    fun writeInternalCommand(command: ByteArray, responder: CommandResponder) {
        if (command == null || command.size < 2) {
            return;
        }
        reponseCollector.off()
        reponseCollector.setResponder(command[1], UUIDs.COMMON_READ, responder)
        peripheral.write(UUIDs.SERVICE, UUIDs.COMMON_WRITE, command, if (debugCommandListener != null ) object : Callback() {
            override fun invoke(error: String?, value: Boolean?) {
                if (value == false) {
                    reponseCollector.off()
                    debugCommandListener?.invoke(DebugCommand("write Error", "0001", command, false))
                }
            }
        } else null)
        debugCommandListener?.invoke(DebugCommand("write", "0001", command))
    }
    fun writeCommand(command: ByteArray, responder: CommandResponder) {
        if (command == null || command.size < 2) {
            return;
        }
        reponseCollector.off()
        reponseCollector.setResponder(command[1], UUIDs.MY_READ, responder)
        peripheral.write(UUIDs.SERVICE, UUIDs.MY_WRITE, command, if (debugCommandListener != null ) object : Callback() {
            override fun invoke(error: String?, value: Boolean?) {
                if (value == false) {
                    reponseCollector.off()
                    debugCommandListener?.invoke(DebugCommand("write Error", "0015", command, false))
                }
            }
        } else null)
        debugCommandListener?.invoke(DebugCommand("write", "0015", command))
    }

    fun readDeviceInfoValue(characteristic: UUID, responder: CommandResponder) {
        return readValue(UUIDs.SERVICE, characteristic, responder)
    }
    fun readValue(service: UUID, characteristic: UUID, responder: CommandResponder) {
        peripheral.read(service, characteristic, object: ValueCallback<UuidAndBytes>() {
            override fun invoke(error: String?, value: UuidAndBytes?) {
                value?.let {
                    debugCommandListener?.invoke(DebugCommand("read", UUIDHelper.simpler(value.uuid), it.bytes))
                    responder(it.bytes)
                }
                if (error != null) {
                    throw Exception(error)
                }
            }
        })
    }

    fun disconnect(focus: Boolean = false) {
        peripheral.disconnect(focus)
    }

}

/**
 * 用于接收蓝牙指令，
 * 并调用相应的回调函数
 * 这里的作用是收集“长”命令（wifi列表），收到所有数据后，再回调。
 */
internal class ResponseCollector() {
    var waitingType: Byte = 0
    var waitingCharacteristic: UUID? = null
    var isCollecting = false
    private var nextResponder: CommandResponder?= null
    private var respMap = mutableMapOf<Int, ByteArray>()
    public fun setResponder(type: Byte, fromCharacteristic: UUID, responder: CommandResponder) {
        if (isCollecting) {
            throw IllegalStateException("ResponseCollector is collecting")
        }
        if (waitingType > 0) {
            throw IllegalStateException("ResponseCollector is waiting for 0x${waitingType.toString(16)}, not 0x${type.toString(16)}")
        }
        waitingCharacteristic = fromCharacteristic
        nextResponder = responder
        waitingType = type
        respMap.clear()
    }
    public fun collect(uuidAndBytes: UuidAndBytes) {
        return collect(uuidAndBytes.uuid, uuidAndBytes.bytes)
    }
    public fun collect(fromUUID: UUID, bytes: ByteArray) {
        if (waitingCharacteristic != fromUUID) {
            // 如果不是目标特征的响应 则忽略
            return
        }
        if (waitingType == 0.toByte() || nextResponder == null) {
            // 不是一马事儿，忽略
            return
        }
        /**
         *
         * 目前 0x1E 命令 是多页的。是取clientid的
         * 目前 0x07 和 0x04 命令 是多页的。都是获取wifi列表命令，0x04已废弃。
         */
        val reponseHasMultiPage =
                (waitingCharacteristic == UUIDs.MY_READ
                        && (waitingType == 0x7.toByte() || waitingType == 0x4.toByte()))
                || (waitingCharacteristic == UUIDs.COMMON_READ && waitingType == 0x1e.toByte())
        if (bytes[1].isFF() || !reponseHasMultiPage) {
            // 是 04FF010000 格式数据，或 非分页，直接回调
            nextResponder?.let { responder ->
                /**
                 * 先设置reponder为空，再回调。
                 * 防止在回调中再次调用
                 * 防止invoke里的responder无法被设置
                 */
                off();
                responder.invoke(bytes)
            }
            return
        }

        isCollecting = true;
        Protocol.from(bytes, reponseHasMultiPage)?.let { protocol ->
            if (protocol.type == waitingType) {
                respMap[protocol.page] = protocol.data!!
            }
            if (respMap.count() == protocol.count) {
                // 已收集到所有
                // 组合成一个符合协议格式的数据,不写长度，因为可能长度已经超出byte了。
                var data = byteArrayOf(-1, waitingType)
                for (i in 1..protocol.count) {
                    data += respMap[i]!!
                }
                if (!reponseHasMultiPage) {
                    data[0] = (data.size - 1).toByte()
                }
                nextResponder?.let { responder ->
                    /**
                     * 先设置reponder为空，再回调。
                     * 防止在回调中再次调用
                     * 防止invoke里的responder无法被设置
                     */
                    off();
                    responder.invoke(data)
                }
            }
        }
    }

    fun off() {
        nextResponder = null
        waitingType = 0
        waitingCharacteristic = null
        isCollecting = false
        respMap.clear()
    }
}