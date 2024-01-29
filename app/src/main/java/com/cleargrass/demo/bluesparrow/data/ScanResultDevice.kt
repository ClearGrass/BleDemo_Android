package com.cleargrass.demo.bluesparrow.data

import android.os.Parcel
import android.os.Parcelable

data class ScanResultDevice(
    val name: String,
    val macAddress: String,
    val signal: Int,
    val data: ByteArray
): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt(),
        parcel.createByteArray()!!
    ) {
    }

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
        parcel.writeInt(signal)
        parcel.writeByteArray(data)
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
