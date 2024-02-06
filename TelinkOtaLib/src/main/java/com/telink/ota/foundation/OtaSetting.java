package com.telink.ota.foundation;

import android.content.Context;

import com.telink.ota.util.FileSystem;

import java.io.Serializable;
import java.util.UUID;

public class OtaSetting implements Serializable {


    /**
     * selected protocol
     */
    public OtaProtocol protocol = OtaProtocol.Legacy;

    /**
     * selected firmware data
     */
    public String firmwarePath;


    /**
     * need to check firmware crc
     */
    public boolean checkFirmwareCrc = false;

    /**
     * selected serviceUUID for OTA
     */
    public UUID serviceUUID;

    /**
     * selected characteristicUUID for OTA
     */
    public UUID characteristicUUID;

    /**
     * read interval: read every [x] write packets
     * if value <= 0, no read check will be sent
     */
    public int readInterval = 0;

    /**
     * PDU length used in extend protocol, should be 16 * n (1~15)
     */
    public int pduLength = 16;

    /**
     * version compare used in extend protocol
     */
    public boolean versionCompare = false;

    /**
     * selected firmware bin version
     */
    public byte[] firmwareVersion;


    /**
     * OTA flow timeout
     * default 5 minutes
     */
    public int timeout = 5 * 60 * 1000;


}
