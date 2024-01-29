package com.cleargrass.lib.blue.core;

import android.bluetooth.le.ScanResult;

public interface ScanCallback {
    void onScanStart();
    void onScanStop();
    void onAcceptDevice(ScanResult scanResult);
    void onScanFailed(int errorCode);

}
