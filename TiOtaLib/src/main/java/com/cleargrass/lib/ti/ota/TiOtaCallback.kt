package com.cleargrass.lib.ti.ota

interface TiOtaCallback {
    fun onOtaStatusChanged(status: UpdateState)
    fun onOtaProgressUpdate(progress: Int)
}

enum class UpdateState {
    PROGRESS, DONE, ERROR
}