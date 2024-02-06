package com.cleargrass.lib.blue.data

import kotlin.experimental.and

data class ScanResultParsed(
    val frameControl: FrameControl,
    val productId: Byte,
    val mac: String,
    val rawBytes: ByteArray
)  {
    val isBinding: Boolean
        get() = frameControl.binding
    val hasBind: Boolean
        get() = frameControl.hasBind

    constructor(rawBytes: ByteArray): this(
        frameControl = FrameControl(rawBytes[7]),
        productId = rawBytes[8],
        rawBytes.slice(9 ..14).reversed().toByteArray().display(dimter = ":", prefix = "").uppercase(),
        rawBytes = rawBytes
    )
}

data class FrameControl(
    val aes: Boolean,
    val binding: Boolean,
    val isBooting: Boolean,
    val version: Int,
    val isEvent: Boolean,
    val hasBind: Boolean
) {
    constructor(byte0: Byte): this(
        aes = byte0 and 0x1 > 0,
        binding = byte0 and 0x2 > 0,
        isBooting = byte0 and 0x4 > 0,
        version = (byte0.toInt() shr 3) and 0x07,
        isEvent = byte0 and 0x40 > 0,
        hasBind = byte0 and 0x80.toByte() > 0
    )
}