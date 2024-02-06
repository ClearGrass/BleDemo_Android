package com.cleargrass.demo.bluesparrow.data

import android.os.Parcel
import android.os.Parcelable
import com.cleargrass.lib.blue.QingpingDevice
import com.cleargrass.lib.blue.data.ScanResultParsed

data class ScanResultDevice(
    val name: String,
    val rssi: Int,
    val data: ScanResultParsed
): Parcelable {
    val isBinding: Boolean
        get() = data.isBinding
    val productId: Byte
        get() = data.productId
    val macAddress: String
        get() = data.mac
    val clientId: String
        get() = macAddress.replace(Regex("[^0-9A-F]"), "").replace(Regex("^0+"), "")

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readInt(),
        ScanResultParsed(parcel.createByteArray() ?: byteArrayOf())
    )

    constructor(qingpingDevice: QingpingDevice) : this(
            qingpingDevice.name, qingpingDevice.rssi, ScanResultParsed(qingpingDevice.scanData)
    )

    override fun equals(other: Any?): Boolean {
        if (other !is ScanResultDevice) {
            return false
        }
        return macAddress == other.macAddress
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(macAddress)
        parcel.writeInt(rssi)
        parcel.writeByteArray(data.rawBytes)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + macAddress.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }

    companion object CREATOR : Parcelable.Creator<ScanResultDevice> {
        override fun createFromParcel(parcel: Parcel): ScanResultDevice {
            return ScanResultDevice(parcel)
        }

        override fun newArray(size: Int): Array<ScanResultDevice?> {
            return arrayOfNulls(size)
        }
    }
}
