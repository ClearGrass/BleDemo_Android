package com.cleargrass.lib.blue.core;

import java.util.UUID;

public class UUIDs {
    public static final UUID SERVICE = UUIDHelper.uuidFromString("22210000-554a-4546-5542-46534450464d");
    public static final UUID COMMON_WRITE = UUIDHelper.uuidFromString("0001");
    public static final UUID COMMON_READ = UUIDHelper.uuidFromString("0002");
    public static final UUID MY_WRITE = UUIDHelper.uuidFromString("0015");
    public static final UUID MY_READ = UUIDHelper.uuidFromString("0016");

    public static final UUID DFU_SERVICE = UUIDHelper.uuidFromString("fe59");
    public static final UUID DFU_POINT = UUIDHelper.uuidFromString("8ec90001-f315-4f60-9fb8-838830daea50");
    public static final UUID DFU_PACKET = UUIDHelper.uuidFromString("8ec90002-f315-4f60-9fb8-838830daea50");
    public static final UUID DFU_INTO = UUIDHelper.uuidFromString("8ec90003-f315-4f60-9fb8-838830daea50");

    public static final UUID INFO_SERVICE = UUIDHelper.uuidFromString("180a");
    public static final UUID INFO_CHAR = UUIDHelper.uuidFromString("0010");
    public static final UUID INFO_VERSION_CHAR = UUIDHelper.uuidFromString("2a26");
}
