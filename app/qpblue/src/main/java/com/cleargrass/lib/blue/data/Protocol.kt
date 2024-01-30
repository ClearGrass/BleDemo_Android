package com.cleargrass.lib.blue.data

import com.cleargrass.lib.blue.QpUtils


data class Protocol(
    val type: Byte,
    val resultSuccess: Boolean,
    val data: ByteArray?,
    val count: Int = 1,
    val page: Int = 1,
) {
    companion object {
        fun from(bytes: ByteArray, withPage: Boolean = false): Protocol? {
            return QpUtils.parseProtocol(bytes, withPage)
        }
    }
}