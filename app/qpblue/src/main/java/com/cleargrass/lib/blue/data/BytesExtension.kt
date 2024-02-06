package com.cleargrass.lib.blue.data


fun String.isGoodToken(): Boolean {
    return matches(Regex("^[0-9a-zA-Z!@#$%^&()_=]{10,16}$"))
}
fun String.isHex(): Boolean {
    return matches(Regex("^[0-9A-Fa-f]*$"))
}

fun ByteArray.number(intRange: IntRange? = null): Int {
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
public fun ByteArray.display(intRange: IntRange? = null, dimter: String = "-", prefix: String = "0x"):String {
    val byteArray = if (intRange == null) this else sliceArray(intRange)
    var result = ""
    byteArray.forEach {
        result += it.display() + dimter
    }
    return "$prefix${result.removeSuffix(dimter)}"
}
public fun Byte.display(prefix: Boolean = false): String {
    return this.toUByte().toString(16).padStart(2, '0').let {
        if (prefix) "0x$it" else it
    }
}
public fun Byte.isFF(): Boolean {
    return this == (-1).toByte()
}
