@file:OptIn(ExperimentalUnsignedTypes::class)

package com.cleargrass.lib.blue

import kotlin.random.Random


object QpUtils {
    private val hexArray = "0123456789ABCDEF".toCharArray()

    fun hexToBytes(randomHex1: String): ByteArray {
        val randomHex = randomHex1.removePrefix("0x").replace(Regex("[^0-9A-Fa-f]"), "") .trim()
        return ByteArray(randomHex.length / 2) {
            randomHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
    fun stringToBytes(string: String): ByteArray {
        return string.toByteArray()
    }

    /**
     * Wrap的作用是把命令数据用协议包裹起来。
     * 青萍协议的格式为：
     *   1字节长度
     *   1字节协议类型
     *   N字节数据
     *   比如 对于 0x11 01 112233445566778899AABBCCDDEEFF00
     *           长度  绑定  数据
     *       对于 0x11 02 112233445566778899AABBCCDDEEFF00
     *           长度  验证  数据
     *       对于 0x01 07
     *           长度  取wifi列表
     * @param protocol 协议类型
     * @param bytes 数据
     */
    fun wrapProtocol(protocol: Byte, bytes: ByteArray): ByteArray {
        return byteArrayOf((bytes.size + 1).toByte(), protocol) +  bytes
    }
    fun parseProtocol(bytes: ByteArray, withPage:Boolean = false): Protocol? {
        if (bytes.size < 2) {
            return null
        }
        if (bytes[1].isFF()) {  //如： 0x04FF010000
            // 第一个字节是0x04，表示数据长度是0x04
            // 第二个字节是0xFF，表示这一包用于表示成功失败
            // 第三个字节是0x01，表示协议类型是 01 （绑定）
            // 第四五个字节是0x00 00，表示成功，在解析时：如果是 0x01 00 ，从后向前取每一个字节成为： 00 01，则==1

            val data = bytes.sliceArray(3 until bytes.size)
            val succ = data.number() == 0

            return Protocol(bytes[2], succ, data)
        } else {
            // 解析协议
            // 其它如：0x06081122334466
            val type = bytes[1]
            // 从第三位开始，都是数据
            val data = bytes.sliceArray(2 until bytes.size )
            val succ = true
            return if (!withPage || bytes.size < 3) {
                Protocol(type, succ, data)
            } else {
                // 对于长数据。如  0x13-07-1e-01-22-51-69-6e-67-70-69-6e-67-20-41-50-22-2c-34-2c
                // 第三位1e表示共几条，每四位的01表示这是第几条。计数从1开始。
                // 第五位22开始，都是数据
                val count = bytes[2].toUByte().toInt()
                val page = bytes[3].toUByte().toInt()
                Protocol(type, succ, data.sliceArray(2 until data.size), count, page)
            }
        }
    }

    fun randomToken(): String {
        Random.nextFloat().let {
            when (it) {
                in 0f..0.3f -> {
                    return "1234567890"
                }

                in 0.33f..0.55f -> {
                    return "1234567890ABCDEF"
                }

                in 0.55f..0.77f -> {
                    return "ABCDEFGHIJKLMN"
                }

                in 0.77f..0.9f -> {
                    return "sefhapiw31csaef32"
                }

                else -> {
                    return "iTalkBB@foo321bar!"
                }
            }
        }
    }
}

fun String.isGoodToken(): Boolean {
    return matches(Regex("^[0-9a-zA-Z!@#$%^&()_=]{12,18}$"))
}
fun String.isHex(): Boolean {
    return matches(Regex("^[0-9A-Fa-f]*$"))
}

private fun ByteArray.number(intRange: IntRange? = null): Int {
    val byteArray = if (intRange == null) this else sliceArray(intRange)
    var shl = 0
    var result = 0
    byteArray.take(4).forEach {
        result = result or (it.toInt() shl (shl++)  * 4)
    }
    return result
}

public fun ByteArray.string(intRange: IntRange? = null): String {
    val byteArray = if (intRange == null) this else sliceArray(intRange)
    return String(byteArray)
}
public fun ByteArray.display(intRange: IntRange? = null, dimter: String = "-"):String {
    val byteArray = if (intRange == null) this else sliceArray(intRange)
    var result = ""
    byteArray.forEach {
        result += it.display() + dimter
    }
    return "0x${result.removeSuffix(dimter)}"
}
public fun Byte.display(prefix: Boolean = false): String {
    return this.toUByte().toString(16).padStart(2, '0').let {
        if (prefix) "0x$it" else it
    }
}
public fun Byte.isFF(): Boolean {
    return this == (-1).toByte()
}


data class Protocol(
    val type: Byte,
    val resultSuccess: Boolean,
    val data: ByteArray?,
    val count: Int = 1,
    val page: Int = 1,
) {
    init {

    }
    companion object {
        fun from(bytes: ByteArray, withPage: Boolean = false): Protocol? {
            return QpUtils.parseProtocol(bytes, withPage)
        }
    }

}
