package com.cleargrass.lib.blue.core;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.MainThread;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;


/**那
 * Peripheral wraps the BluetoothDevice and provides methods to convert to JSON.
 */

@SuppressLint("MissingPermission")
public class Peripheral extends BluetoothGattCallback {

    private static final String CHARACTERISTIC_NOTIFICATION_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private static final String LOG_TAG = "QingpingPeripheral";

    private final BluetoothDevice device;
    protected byte[] advertisingDataBytes = new byte[0];
    protected int advertisingRSSI;
    private boolean connected = false;

    private BluetoothGatt gatt;

    private Callback connectCallback;
    private OnConnectionStatusCallback connectStatusCallback;
    private Callback retrieveServicesCallback;
    private ValueCallback<UuidAndBytes> readCallback;
    private ValueCallback<UuidAndBytes> notifyCallback;
    private ValueCallback<Integer> readRSSICallback;
    private Callback writeCallback;
    private Callback registerNotifyCallback;
    private ValueCallback<Integer> requestMTUCallback;

    //当需要写入很长数据时，按20字节切割 分批发送。
    private List<byte[]> writeQueue = new ArrayList<>();

    public Peripheral(BluetoothDevice device, int advertisingRSSI, byte[] scanRecord) {
        this.device = device;
        this.advertisingRSSI = advertisingRSSI;
        this.advertisingDataBytes = scanRecord;
    }

    public Peripheral(BluetoothDevice device) {
        this.device = device;
    }
    public Peripheral(ScanResult scanResult) {
        this(scanResult.getDevice(), scanResult.getRssi(), scanResult.getScanRecord().getBytes());
    }

    public byte[] getAdvertisingBytes() {
        return advertisingDataBytes;
    }

    public void setOnConnectStatusChange(OnConnectionStatusCallback onConnectStatusChange) {
        connectStatusCallback = onConnectStatusChange;
    }
    @MainThread
    public void connect(Context context, Callback callback, OnConnectionStatusCallback onConnectStatusChange) {
        connectStatusCallback = onConnectStatusChange;
        if (!connected) {
            BluetoothDevice device = getDevice();
            this.connectCallback = callback;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d(Peripheral.LOG_TAG, " Is Or Greater than M $mBluetoothDevice");
                gatt = device.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE);
            } else {
                Log.d(Peripheral.LOG_TAG, " Less than M");
                try {
                    Log.d(Peripheral.LOG_TAG, " Trying TRANPORT LE with reflection");
                    Method m = device.getClass().getDeclaredMethod("connectGatt", Context.class, Boolean.class, BluetoothGattCallback.class, Integer.class);
                    m.setAccessible(true);
                    Integer transport = device.getClass().getDeclaredField("TRANSPORT_LE").getInt(null);
                    gatt = (BluetoothGatt) m.invoke(device, context, false, this, transport);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(Peripheral.LOG_TAG, " Catch to call normal connection");
                    gatt = device.connectGatt(context, false,
                            this);
                }
            }
        } else {
            if (gatt != null) {
                callback.invoke(null, true);
            } else {
                callback.invoke("BluetoothGatt is null", false);
            }
        }
    }
    @MainThread
    public void disconnect() {
        disconnect(false);
    }
    @MainThread
    public void disconnect(boolean force) {
        connectCallback = null;
        connected = false;
        if (gatt != null) {
            try {
                gatt.disconnect();
                if (force) {
                    gatt.close();
                    gatt = null;
                    Log.d(Peripheral.LOG_TAG, "disconnect:");
                    if (connectStatusCallback != null) {
                        connectStatusCallback.onPeripheralDisconnected(this, null);
                    }
                }
                Log.d(Peripheral.LOG_TAG, "Disconnect");
            } catch (Exception e) {
                if (connectStatusCallback != null) {
                    connectStatusCallback.onPeripheralDisconnected(this, e);
                }
                Log.d(Peripheral.LOG_TAG, "Error on disconnect", e);
            }
        } else {
            Log.d(Peripheral.LOG_TAG, "GATT is null");
            connectStatusCallback.onPeripheralDisconnected(this, new NullPointerException("GATT is null"));
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public Boolean hasService(UUID uuid) {
        if (gatt == null) {
            return null;
        }
        return gatt.getService(uuid) != null;
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        if (retrieveServicesCallback != null) {
            retrieveServicesCallback.invoke(null, true);
            retrieveServicesCallback = null;
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatta, int status, int newState) {

        Log.d(Peripheral.LOG_TAG, "onConnectionStateChange to " + newState + " on peripheral: " + device.getAddress() + " with status " + status);

        this.gatt = gatta;

        if (newState == BluetoothProfile.STATE_CONNECTED) {

            connected = true;

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        gatt.discoverServices();
                    } catch (NullPointerException e) {
                        Log.d(Peripheral.LOG_TAG, "onConnectionStateChange connected but gatt of Run method was null");
                    }
                }
            });
            if (connectStatusCallback != null) {
                connectStatusCallback.onPeripheralConnected(this);
            }

            if (connectCallback != null) {
                Log.d(Peripheral.LOG_TAG, "Connected to: " + device.getAddress());
                connectCallback.invoke(null, true);
                connectCallback = null;
            }

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

            if (connected) {
                connected = false;
            }
            if (gatt != null) gatt.disconnect();
            if (status == 133) internalRefreshDeviceCache(gatt);
            if (gatt != null) gatt.close();
            this.gatt = null;

            Log.e(Peripheral.LOG_TAG, "onConnectionStateChange: " + newState + " with state " + status);

            if (connectStatusCallback != null) {
                connectStatusCallback.onPeripheralDisconnected(this, null);
            }
            List<ValueCallback> callbacks = Arrays.asList(writeCallback, retrieveServicesCallback, readRSSICallback, readCallback, registerNotifyCallback, requestMTUCallback);
            for (ValueCallback currentCallback : callbacks) {
                if (currentCallback != null) {
                    currentCallback.invoke("Device disconnected");
                }
            }
            if (connectCallback != null) {
                connectCallback.invoke("Connection error", false);
                connectCallback = null;
            }
            writeCallback = null;
            writeQueue.clear();
            readCallback = null;
            retrieveServicesCallback = null;
            readRSSICallback = null;
            registerNotifyCallback = null;
            requestMTUCallback = null;
        }

    }

    public void updateRssi(int rssi) {
        advertisingRSSI = rssi;
    }

    public void updateData(byte[] data) {
        advertisingDataBytes = data;
    }

    public int unsignedToBytes(byte b) {
        return b & 0xFF;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        byte[] dataValue = characteristic.getValue();
        Log.d(Peripheral.LOG_TAG, "Notify: " + bytesToHex(dataValue) + " from peripheral: " + device.getAddress());
        if (notifyCallback != null) {
            notifyCallback.invoke(null, new UuidAndBytes(characteristic.getUuid(), dataValue));
        } else {
            Log.d(Peripheral.LOG_TAG, "onCharacteristicChanged notifyCallback is null");
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        String string = readCallback == null ? "readCallback is  null" : "readCallback is not null";
        byte[] dataValue = characteristic.getValue();
        Log.d(Peripheral.LOG_TAG, "Read: " + bytesToHex(dataValue) + " from peripheral: " + device.getAddress());
        Log.d(Peripheral.LOG_TAG, "onCharacteristicRead " + string+ ", " + BluetoothGatt.GATT_SUCCESS);
        if (readCallback != null) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readCallback.invoke(null, new UuidAndBytes(characteristic.getUuid(), dataValue));
            } else {
                readCallback.invoke("Error reading " + characteristic.getUuid() + " status=" + status);
            }
            readCallback = null;
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);

        if (writeCallback != null) {

            if (writeQueue.size() > 0) {
                Log.e(LOG_TAG, "onCharacteristicWrite: writeCallback:writeQueue.size:" + writeQueue.size());
                byte[] data = writeQueue.get(0);
                writeQueue.remove(0);
                doWrite(characteristic, data);
            } else {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.e(LOG_TAG, "onCharacteristicWrite: writeCallback.invoke()");
                    writeCallback.invoke(null, true);
                } else {
                    Log.e(Peripheral.LOG_TAG, "Error onCharacteristicWrite:" + status);
                    writeCallback.invoke("Error writing status: " + status, false);
                }
                Log.e(LOG_TAG, "onCharacteristicWrite: writeCallback null release");
                writeCallback = null;
            }
        } else {
            Log.e(Peripheral.LOG_TAG, "No callback on write");
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        if (registerNotifyCallback != null) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                registerNotifyCallback.invoke();
                Log.d(Peripheral.LOG_TAG, "onDescriptorWrite success");
            } else {
                registerNotifyCallback.invoke("Error writing descriptor stats=" + status);
                Log.e(Peripheral.LOG_TAG, "Error writing descriptor stats=" + status);
            }

            registerNotifyCallback = null;
        } else {
            Log.e(Peripheral.LOG_TAG, "onDescriptorWrite with no callback");
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
        if (readRSSICallback != null) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateRssi(rssi);
                readRSSICallback.invoke(rssi);
            } else {
                readRSSICallback.invoke("Error reading RSSI status=" + status);
            }

            readRSSICallback = null;
        }
    }

    private void setNotify(UUID serviceUUID, UUID characteristicUUID, Boolean notify, Callback callback) {
        if (!isConnected()) {
            callback.invoke("Device is not connected (setNotify)");
            return;
        }
        Log.d(Peripheral.LOG_TAG, "setNotify");

        if (gatt == null) {
            callback.invoke("BluetoothGatt is null");
            return;
        }

        BluetoothGattService service = gatt.getService(serviceUUID);
        BluetoothGattCharacteristic characteristic = findNotifyCharacteristic(service, characteristicUUID);

        if (characteristic != null) {
            if (gatt.setCharacteristicNotification(characteristic, notify)) {

                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUIDHelper.uuidFromString(CHARACTERISTIC_NOTIFICATION_CONFIG));
                if (descriptor != null) {

                    // Prefer notify over indicate
                    if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        Log.d(Peripheral.LOG_TAG, "Characteristic " + characteristicUUID + " set NOTIFY");
                        descriptor.setValue(notify ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    } else if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                        Log.d(Peripheral.LOG_TAG, "Characteristic " + characteristicUUID + " set INDICATE");
                        descriptor.setValue(notify ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    } else {
                        Log.d(Peripheral.LOG_TAG, "Characteristic " + characteristicUUID + " does not have NOTIFY or INDICATE property set");
                    }

                    try {
                        registerNotifyCallback = callback;
                        if (gatt.writeDescriptor(descriptor)) {
                            Log.d(Peripheral.LOG_TAG, "setNotify complete");
                        } else {
                            registerNotifyCallback = null;
                            callback.invoke("Failed to set client characteristic notification for " + characteristicUUID);
                        }
                    } catch (Exception e) {
                        Log.d(Peripheral.LOG_TAG, "Error on setNotify", e);
                        callback.invoke("Failed to set client characteristic notification for " + characteristicUUID + ", error: " + e.getMessage());
                    }

                } else {
                    callback.invoke("Set notification failed for " + characteristicUUID);
                }

            } else {
                callback.invoke("Failed to register notification for " + characteristicUUID);
            }

        } else {
            callback.invoke("Characteristic " + characteristicUUID + " not found");
        }

    }
    @MainThread
    public void registerNotify(UUID serviceUUID, UUID characteristicUUID, Callback callback, ValueCallback<UuidAndBytes> notifyCallback) {
        Log.d(Peripheral.LOG_TAG, "registerNotify");
        this.setNotify(serviceUUID, characteristicUUID, true, new Callback() {
            @Override
            public void invoke(String error, Boolean value) {
                if (value) {
                    Peripheral.this.notifyCallback = notifyCallback;
                } else {
                    Peripheral.this.notifyCallback = null;
                }
                callback.invoke(error, value);
            }
        });
    }
    @MainThread
    public void removeNotify(UUID serviceUUID, UUID characteristicUUID, Callback callback) {
        Log.d(Peripheral.LOG_TAG, "removeNotify");
        this.setNotify(serviceUUID, characteristicUUID, false, new Callback() {
            @Override
            public void invoke(String error, Boolean value) {
                callback.invoke(error, value);
                if (value) {
                    Peripheral.this.notifyCallback = null;
                }
            }
        });
    }

    // Some devices reuse UUIDs across characteristics, so we can't use service.getCharacteristic(characteristicUUID)
    // instead check the UUID and properties for each characteristic in the service until we find the best match
    // This function prefers Notify over Indicate
    private BluetoothGattCharacteristic findNotifyCharacteristic(BluetoothGattService service, UUID characteristicUUID) {

        try {
            // Check for Notify first
            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 && characteristicUUID.equals(characteristic.getUuid())) {
                    return characteristic;
                }
            }

            // If there wasn't Notify Characteristic, check for Indicate
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 && characteristicUUID.equals(characteristic.getUuid())) {
                    return characteristic;
                }
            }

            // As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
            return service.getCharacteristic(characteristicUUID);
        } catch (Exception e) {
            Log.e(Peripheral.LOG_TAG, "Error retriving characteristic " + characteristicUUID, e);
            return null;
        }
    }

    public void read(UUID serviceUUID, UUID characteristicUUID, ValueCallback<UuidAndBytes> callback) {

        if (!isConnected()) {
            callback.invoke("Device is not connected (read)", null);
            return;
        }
        if (gatt == null) {
            callback.invoke("BluetoothGatt is null", null);
            return;
        }

        BluetoothGattService service = gatt.getService(serviceUUID);
        BluetoothGattCharacteristic characteristic = findReadableCharacteristic(service, characteristicUUID);

        if (characteristic == null) {
            callback.invoke("Characteristic " + characteristicUUID + " not found.", null);
        } else {
            readCallback = callback;
            if (!gatt.readCharacteristic(characteristic)) {
                readCallback = null;
                callback.invoke("Read failed", null);
            }
        }
    }

    public void readRSSI(ValueCallback<Integer> callback) {
        if (!isConnected()) {
            callback.invoke("Device is not connected (readRSSI)", null);
            return;
        }
        if (gatt == null) {
            callback.invoke("BluetoothGatt is null", null);
            return;
        }

        readRSSICallback = callback;
        if (!gatt.readRemoteRssi()) {
            readCallback = null;
            callback.invoke("Read RSSI failed", null);
        }
    }

    public void refreshCache(Callback callback) {
        try {
            Method localMethod = gatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                boolean res = ((Boolean) localMethod.invoke(gatt, new Object[0])).booleanValue();
                callback.invoke(null, res);
            } else {
                callback.invoke("Could not refresh cache for device.");
            }
        } catch (Exception localException) {
            Log.e(LOG_TAG, "An exception occured while refreshing device");
            callback.invoke(localException.getMessage());
        }
    }

    public void retrieveServices(Callback callback) {
        if (!isConnected()) {
            callback.invoke("Device is not connected (retrieveSerivces)");
            return;
        }
        if (gatt == null) {
            callback.invoke("BluetoothGatt is null");
            return;
        }
        this.retrieveServicesCallback = callback;
        gatt.discoverServices();
    }


    // Some peripherals re-use UUIDs for multiple characteristics so we need to check the properties
    // and UUID of all characteristics instead of using service.getCharacteristic(characteristicUUID)
    private BluetoothGattCharacteristic findReadableCharacteristic(BluetoothGattService service, UUID characteristicUUID) {

        if (service != null) {
            int read = BluetoothGattCharacteristic.PROPERTY_READ;

            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                if ((characteristic.getProperties() & read) != 0 && characteristicUUID.equals(characteristic.getUuid())) {
                    return characteristic;
                }
            }

            // As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
            return service.getCharacteristic(characteristicUUID);
        }

        return null;
    }

    public boolean doWrite(BluetoothGattCharacteristic characteristic, byte[] data) {
        characteristic.setValue(data);
        if (!gatt.writeCharacteristic(characteristic)) {
            Log.d(Peripheral.LOG_TAG, "Error on doWrite");
            return false;
        }
        return true;
    }
    public void writeWithoutResponse(UUID serviceUUID, UUID characteristicUUID, byte[] data) {
        write(serviceUUID, characteristicUUID, data, 20, 0, new Callback() {
            @Override
            public void invoke(String error, Boolean value) {

            }
        }, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
    }
    @MainThread
    public void write(UUID serviceUUID, UUID characteristicUUID, byte[] data, Callback writeCallback) {
        if (writeCallback == null) {
            writeCallback = new Callback() {
                @Override
                public void invoke(String error, Boolean value) {

                }
            };
        }
        write(serviceUUID, characteristicUUID, data, 20, 0, writeCallback, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
    }
    @MainThread
    public void write(UUID serviceUUID, UUID characteristicUUID, byte[] data, int maxByteSize, int queueSleepTime, Callback callback, int writeType) {
        if (!isConnected()) {
            callback.invoke("Device is not connected (write)");
            return;
        }
        if (gatt == null) {
            callback.invoke("BluetoothGatt is null");
        } else {
            BluetoothGattService service = gatt.getService(serviceUUID);
            BluetoothGattCharacteristic characteristic = findWritableCharacteristic(service, characteristicUUID, writeType);

            if (characteristic == null) {
                callback.invoke("Characteristic " + characteristicUUID + " not found.");
            } else {
                characteristic.setWriteType(writeType);

                if (writeQueue.size() > 0) {
                    callback.invoke("You have already an queued message");
                    return;
                } 

                // if (writeCallback != null) {
                //     Log.e(LOG_TAG, "write--writeCallback-You're already writing");
                //     callback.invoke("You're already writing");
                // }

                if (writeQueue.size() == 0 && writeCallback == null) {
                    if (BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE == writeType) {
                        Log.d(LOG_TAG, "write data:: writeType=WRITE_TYPE_NO_RESPONSE without response");
                    }
                    if (BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT == writeType) {
                        Log.d(LOG_TAG, "write data:: writeType=WRITE_TYPE_DEFAULT with response");
                        writeCallback = callback;
                    }
                    Log.i(LOG_TAG, "write data:: :" + bytesToHex(data) );
                    Log.i(LOG_TAG, "write data:: data.length:" + data.length + ", maxByteSize:" + maxByteSize);

                    if (data.length > maxByteSize) {
                        int dataLength = data.length;
                        int count = 0;
                        byte[] firstMessage = null;
                        List<byte[]> splittedMessage = new ArrayList<>();

                        while (count < dataLength && (dataLength - count > maxByteSize)) {
                            if (count == 0) {
                                firstMessage = Arrays.copyOfRange(data, count, count + maxByteSize);
                            } else {
                                byte[] splitMessage = Arrays.copyOfRange(data, count, count + maxByteSize);
                                splittedMessage.add(splitMessage);
                            }
                            count += maxByteSize;
                        }
                        if (count < dataLength) {
                            // Other bytes in queue
                            byte[] splitMessage = Arrays.copyOfRange(data, count, data.length);
                            splittedMessage.add(splitMessage);
                        }

                        if (BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT == writeType) {
                            writeQueue.addAll(splittedMessage);
                            if (!doWrite(characteristic, firstMessage)) {
                                writeQueue.clear();
                                writeCallback = null;
                                callback.invoke("Write failed");
                            }
                        } else {
                            try {
                                boolean writeError = false;
                                if (!doWrite(characteristic, firstMessage)) {
                                    writeError = true;
                                    callback.invoke("Write failed");
                                }
                                if (!writeError) {
                                    Thread.sleep(queueSleepTime);
                                    for (byte[] message : splittedMessage) {
                                        if (!doWrite(characteristic, message)) {
                                            writeError = true;
                                            callback.invoke("Write failed");
                                            break;
                                        }
                                        Thread.sleep(queueSleepTime);
                                    }
                                    if (!writeError) {
                                        callback.invoke();
                                    }
                                }
                            } catch (InterruptedException e) {
                                callback.invoke("Error during writing");
                            }
                        }
                    } else if (doWrite(characteristic, data)) {
                        Log.d(Peripheral.LOG_TAG, "Write completed");
                        if (BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE == writeType) {
                            Log.e(Peripheral.LOG_TAG, "write: writeCallback.invoke()");
                            callback.invoke();
                        }
                    } else {
                        callback.invoke("Write failed");
                        writeCallback = null;
                    }
                }
            }
        }
    }

    public void requestConnectionPriority(int connectionPriority, Callback callback) {
        if (gatt == null) {
            callback.invoke("BluetoothGatt is null");
            return;
        }
        boolean status = gatt.requestConnectionPriority(connectionPriority);
        callback.invoke(null, status);
    }

    public void requestMTU(int mtu, ValueCallback<Integer> callback) {
        if (!isConnected()) {
            callback.invoke("Device is not connected (requestMTU)");
            return;
        }

        if (gatt == null) {
            callback.invoke("BluetoothGatt is null");
            return;
        }
        requestMTUCallback = callback;
        gatt.requestMtu(mtu);
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
        if (requestMTUCallback != null) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                requestMTUCallback.invoke(mtu);
            } else {
                requestMTUCallback.invoke("Error requesting MTU status = " + status);
            }

            requestMTUCallback = null;
        }
    }

    // Some peripherals re-use UUIDs for multiple characteristics so we need to check the properties
    // and UUID of all characteristics instead of using service.getCharacteristic(characteristicUUID)
    private BluetoothGattCharacteristic findWritableCharacteristic(BluetoothGattService service, UUID characteristicUUID, int writeType) {
        try {
            // get write property
            int writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE;
            if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
            }

            List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristics) {
                if ((characteristic.getProperties() & writeProperty) != 0 && characteristicUUID.equals(characteristic.getUuid())) {
                    return characteristic;
                }
            }

            // As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
            return service.getCharacteristic(characteristicUUID);
        } catch (Exception e) {
            Log.e(Peripheral.LOG_TAG, "Error on findWritableCharacteristic", e);
            return null;
        }
    }

    private String generateHashKey(BluetoothGattCharacteristic characteristic) {
        return generateHashKey(characteristic.getService().getUuid(), characteristic);
    }

    private String generateHashKey(UUID serviceUUID, BluetoothGattCharacteristic characteristic) {
        return String.valueOf(serviceUUID) + "|" + characteristic.getUuid() + "|" + characteristic.getInstanceId();
    }

    private boolean internalRefreshDeviceCache(BluetoothGatt gatt) {
        if (gatt == null) // no need to be connected
            return false;

        Log.d(Peripheral.LOG_TAG, "Refreshing device cache...");
        Log.d(Peripheral.LOG_TAG,  "gatt.refresh() (hidden)");
        /*
         * There is a refresh() method in BluetoothGatt class but for now it's hidden.
         * We will call it using reflections.
         */
        try {
            final Method refresh = gatt.getClass().getMethod("refresh");
            //noinspection ConstantConditions
            return (Boolean) refresh.invoke(gatt);
        } catch (final Exception e) {
            Log.w(Peripheral.LOG_TAG, "An exception occurred while refreshing device", e);
            Log.d(Peripheral.LOG_TAG,   "gatt.refresh() method not found");
        }
        return false;
    }

    public interface OnConnectionStatusCallback {
        void onPeripheralConnected(Peripheral peripheral);
        void onPeripheralDisconnected(Peripheral peripheral, Exception error);
    }
    public static abstract class ValueCallback<R> {
        void invoke(R value) {
            this.invoke(null, value);
        }
        void invoke(String error) {
            if (error != null) {
                this.invoke(error, null);
            } else {
                this.invoke(null, null);
            }
        }
        public abstract void invoke(String error, R value);
    }
    public static abstract class Callback extends ValueCallback<Boolean> {
        void invoke() {
            this.invoke(null, true);
        }
        void invoke(String error) {
            if (error != null) {
                this.invoke(error, false);
            } else {
                this.invoke();
            }
        }
    }

    public static class UuidAndBytes extends Pair<UUID, byte[]> {

        /**
         * Constructor for a Pair.
         *
         * @param uuid  the first object in the Pair
         * @param bytes the second object in the pair
         */
        public UuidAndBytes(UUID uuid, byte[] bytes) {
            super(uuid, bytes);
        }

        public UUID getUUID() {
            return first;
        }
        public byte[] getBytes() {
            return second;
        }
    }

         private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
