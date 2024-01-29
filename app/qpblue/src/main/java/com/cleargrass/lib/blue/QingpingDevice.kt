package com.cleargrass.lib.blue

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.cleargrass.lib.blue.core.Peripheral
import com.cleargrass.lib.blue.core.Peripheral.Callback
import com.cleargrass.lib.blue.core.Peripheral.OnConnectionStatusCallback
import com.cleargrass.lib.blue.core.Peripheral.ValueCallback
import com.cleargrass.lib.blue.core.UUIDs
import java.lang.IllegalStateException

data class Command(val action: String, val uuid: String, val bytes: ByteArray, val succ: Boolean? = null)
typealias DebugCommandListener = (Command) -> Unit
typealias CommandResponder = (ByteArray) -> Unit
typealias ActionResult = (Boolean) -> Unit
@SuppressLint("MissingPermission")
class QingpingDevice constructor(var peripheral: Peripheral) {
    var productType = 0
    var deviceId: String? = null
    val name: String
        get() = peripheral.device.name ?: ""
    val address: String
        get() = peripheral.device.address ?: "??:??:??:??:??:??"
    val scanData: ByteArray
        get() = peripheral.advertisingBytes ?: byteArrayOf()
    private val notifyCallback: ValueCallback<ByteArray>
    private val reponseCollector = ResponseCollector();
    public var debugCommandListener: DebugCommandListener?= null

    init {
        val bytes = peripheral.advertisingBytes;
        if (bytes != null && bytes.size >= 13) {
            deviceId = peripheral.device.address.replace(":", "")
        }
        notifyCallback = object: ValueCallback<ByteArray>() {
            override fun invoke(error: String?, value: ByteArray?) {
                value?.let {value ->
                    Log.d("blue", "Response: ${QpUtils.parseProtocol(value)}")
                    debugCommandListener?.invoke(Command("notify", "", value, null))
                    reponseCollector.collect(value)
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
        writeInternalCommand(context, QpUtils.wrapProtocol(0x01, randomBytes)) { bindResponse ->
            if (QpUtils.parseProtocol(bindResponse)?.resultSuccess != true) {
                responder.invoke(false)
            } else {
                verify(context, randomBytes, responder)
            }
        }
    }
    private fun verify(context: Context, randomBytes: ByteArray, responder: ActionResult) {
        writeInternalCommand(context, QpUtils.wrapProtocol(0x02, randomBytes)) { verifyResponse ->
            if (QpUtils.parseProtocol(verifyResponse)?.resultSuccess != true) {
                responder.invoke(false)
            } else {
                peripheral.registerNotify(UUIDs.SERVICE, UUIDs.MY_READ, object: Callback() {
                    override fun invoke(error: String?, value: Boolean?) {
                        responder.invoke(true)
                        if (value != true) {
                            peripheral.disconnect()
                        }
                    }
                }, this@QingpingDevice.notifyCallback);

            }
        }
    }

    fun connectBind(context: Context, tokenString: String, statusChange: OnConnectionStatusCallback, responder: ActionResult) {
        if (!QpUtils.isGoodToken(tokenString)) {
            throw IllegalArgumentException("Invalid token")
        }
        connect(context, object :
            OnConnectionStatusCallback {
            override fun onPeripheralConnected(peripheral: Peripheral?) {
                statusChange.onPeripheralConnected(peripheral)
                bind(context, QpUtils.stringToBytes(tokenString), responder)
            }
            override fun onPeripheralDisconnected(peripheral: Peripheral?, error: Exception?) {
                statusChange.onPeripheralDisconnected(peripheral, error)
                responder.invoke(false)
            }
        })
    }
    fun connectVerify(context: Context, tokenString: String, statusChange: OnConnectionStatusCallback, responder: ActionResult) {
        if (!QpUtils.isGoodToken(tokenString)) {
            throw IllegalArgumentException("Invalid token")
        }
        connect(context, object :
            OnConnectionStatusCallback {
            override fun onPeripheralConnected(peripheral: Peripheral?) {
                statusChange.onPeripheralConnected(peripheral)
                verify(context, QpUtils.stringToBytes(tokenString), responder)
            }
            override fun onPeripheralDisconnected(peripheral: Peripheral?, error: Exception?) {
                statusChange.onPeripheralDisconnected(peripheral, error)
                responder.invoke(false)
            }
        })
    }
    fun writeInternalCommand(context: Context, command: ByteArray, responder: CommandResponder) {
        if (command == null) {
            return;
        }
        peripheral.write(UUIDs.SERVICE, UUIDs.COMMON_WRITE, command, if (debugCommandListener != null ) object : Callback() {
            override fun invoke(error: String?, value: Boolean?) {
                if (value == false) {
                    debugCommandListener?.invoke(Command("write Error", "0001", command, false))
                }
            }
        } else null)
        debugCommandListener?.invoke(Command("write", "0001", command))
        reponseCollector.setResponder(command[1], responder)
    }
    fun writeCommand(context: Context, command: ByteArray, responder: CommandResponder) {
        if (command == null) {
            return;
        }
        peripheral.write(UUIDs.SERVICE, UUIDs.MY_WRITE, command, if (debugCommandListener != null ) object : Callback() {
            override fun invoke(error: String?, value: Boolean?) {
                if (value == false) {
                    debugCommandListener?.invoke(Command("write Error", "0015", command, false))
                }
            }
        } else null)
        debugCommandListener?.invoke(Command("write", "0015", command))
        reponseCollector.setResponder(command[1], responder)
    }

    fun disconnect(focus: Boolean = false) {
        peripheral.disconnect(focus)
    }

}

/**
 * 用于接收蓝牙指令，
 * 并调用相应的回调函数
 * 这里的作用是收集“长”命令，收到所有数据后，再回调。
 */
internal class ResponseCollector() {
    var waitingType: Byte = 0
    var isCollecting = false
    private var nextResponder: CommandResponder?= null
    private var respMap = mutableMapOf<Int, ByteArray>()
    public fun setResponder(type: Byte, responder: CommandResponder) {
        if (isCollecting) {
            throw IllegalStateException("ResponseCollector is collecting")
        }
        if (waitingType > 0) {
            throw IllegalStateException("ResponseCollector is waiting for 0x${waitingType.toString(16)}, not 0x${type.toString(16)}")
        }
        nextResponder = responder
        waitingType = type
        respMap.clear()
    }
    public fun collect(command: ByteArray) {
        if (waitingType == 0.toByte()) {
            throw IllegalStateException("ResponseCollector is not waiting")
        }
        if (nextResponder == null) {
            throw IllegalStateException("ResponseCollector nextResponder == null")
        }
        isCollecting = true;
        Protocol.from(command, true)?.let { protocol ->
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
        isCollecting = false
        respMap.clear()
    }
}