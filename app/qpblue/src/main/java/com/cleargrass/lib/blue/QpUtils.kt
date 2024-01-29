@file:OptIn(ExperimentalUnsignedTypes::class)

package com.cleargrass.lib.blue


object QpUtils {
    private val hexArray = "0123456789ABCDEF".toCharArray()

    fun hexToBytes(randomHex: String): ByteArray {
        val randomHex = randomHex.removePrefix("0x").trim();
        return ByteArray(randomHex.length / 2) {
            randomHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
    fun stringToBytes(string: String): ByteArray {
        return string.toByteArray()
    }
    fun wrapProtocol(protocol: Byte, bytes: ByteArray): ByteArray {
        return byteArrayOf((bytes.size + 1).toByte(), protocol) +  bytes
    }
    fun parseProtocol(bytes: ByteArray, withPage:Boolean = false): Protocol? {
        if (bytes.size < 2) {
            return null;
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
            val type = bytes[1];
            // 从第三位开始，都是数据
            val data = bytes.sliceArray(2 until bytes.size )
            val succ = true
            return if (!withPage) {
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

    fun isGoodToken(tokenString: String): Boolean {
        return tokenString.matches(Regex("^[0-9a-zA-Z]{12,16}$"))
    }
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
public fun ByteArray.display(intRange: IntRange? = null):String {
    val byteArray = if (intRange == null) this else sliceArray(intRange)
    var result = ""
    byteArray.forEach {
        result += it.display() + "-"
    }
    return "0x${result.trimEnd('-')}"
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
