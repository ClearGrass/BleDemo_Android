package com.cleargrass.lib.blue.core;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@SuppressLint("MissingPermission")
public class QingpingScanManager {

    QingpingFilter filter;
    ScanCallback callback;
    protected BluetoothAdapter bluetoothAdapter;
    protected AtomicInteger scanSessionId = new AtomicInteger();

    public QingpingScanManager(Context context) {
        android.bluetooth.BluetoothManager manager = (android.bluetooth.BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();
    }

    public void stopScan() {
        // update scanSessionId to prevent stopping next scan by running timeout thread
        scanSessionId.incrementAndGet();
        bluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
        if (QingpingScanManager.this.callback != null) {
            QingpingScanManager.this.callback.onScanStop();
        }
        this.filter = null;
        this.callback = null;
    }

    public void scan(QingpingFilter filter, ScanCallback callback) {
        this.filter = filter;
        this.callback = callback;
        ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
        List<ScanFilter> filters = new ArrayList<>();

        scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            scanSettingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
            scanSettingsBuilder.setNumOfMatches(1);
            scanSettingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
        }

        bluetoothAdapter.getBluetoothLeScanner().startScan(filters, scanSettingsBuilder.build(), mScanCallback);

        if (QingpingScanManager.this.callback != null) {
            QingpingScanManager.this.callback.onScanStart();
        }
    }

    private static final ParcelUuid qp_uuid1 = ParcelUuid.fromString("0000fff9-0000-1000-8000-00805f9b34fb");
    private static final ParcelUuid qp_uuid2 = ParcelUuid.fromString("0000fdcd-0000-1000-8000-00805f9b34fb");

    private android.bluetooth.le.ScanCallback mScanCallback = new android.bluetooth.le.ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, final ScanResult result) {
            if (QingpingScanManager.this.callback == null) {
                return;
            }
            if (QingpingScanManager.this.filter == null) {
                return;
            }
            ScanRecord record = result.getScanRecord();
            if (record == null) {
                return;
            }
            if (!QingpingScanManager.this.filter.bytesMatched(record.getBytes())) {
                return;
            }

            if (record.getServiceData() == null) {
                return;
            }

            Set<ParcelUuid> uuidsSet = record.getServiceData().keySet();
            if (!uuidsSet.contains(qp_uuid1) && !uuidsSet.contains(qp_uuid2)) {
                return;
            }
            String name = result.getDevice().getName();
            String mac = result.getDevice().getAddress();
            if (QingpingScanManager.this.callback != null) {
                QingpingScanManager.this.callback.onAcceptDevice(result);
            }
        }

        @Override
        public void onBatchScanResults(final List<ScanResult> results) {

        }

        @Override
        public void onScanFailed(final int errorCode) {
            if (QingpingScanManager.this.callback != null) {
                QingpingScanManager.this.callback.onScanFailed(errorCode);
            }
            QingpingScanManager.this.callback = null;
            QingpingScanManager.this.filter = null;
        }
    };

}
