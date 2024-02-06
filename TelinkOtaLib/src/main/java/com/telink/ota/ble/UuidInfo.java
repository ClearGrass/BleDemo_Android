package com.telink.ota.ble;

import java.util.UUID;

public class UuidInfo {

    public static final UUID OTA_SERVICE_UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1912");

    public static final UUID OTA_CHARACTERISTIC_UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b12");

    public static final UUID VERSION_SERVICE_UUID = UUID.fromString("0000d0ff-3c17-d293-8e48-14fe2e4da212");

    public static final UUID VERSION_CHARACTERISTIC_UUID = UUID.fromString("0000ffd4-0000-1000-8000-00805f9b34fb");

    public static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");

    public static final UUID BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

    public static final UUID CFG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
}
