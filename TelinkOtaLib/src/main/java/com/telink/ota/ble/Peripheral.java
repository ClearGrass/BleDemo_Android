/*
 * Copyright (C) 2015 The Telink Bluetooth Light Project
 *
 */
package com.telink.ota.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.telink.ota.util.Arrays;
import com.telink.ota.util.OtaLogger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Peripheral extends BluetoothGattCallback {

    private static final int CONN_STATE_IDLE = 1;
    private static final int CONN_STATE_CONNECTING = 2;
    private static final int CONN_STATE_CONNECTED = 4;
    private static final int CONN_STATE_DISCONNECTING = 8;
//    private static final int CONN_STATE_CLOSED = 16;

    private static final int RSSI_UPDATE_TIME_INTERVAL = 2000;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 20 * 1000;

    private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;

    protected final Queue<CommandContext> mInputCommandQueue = new ConcurrentLinkedQueue<>();
    protected final Queue<CommandContext> mOutputCommandQueue = new ConcurrentLinkedQueue<>();
    protected final Map<String, CommandContext> mNotificationCallbacks = new ConcurrentHashMap<>();

    protected final Handler mTimeoutHandler = new Handler(Looper.getMainLooper());
    protected final Handler mRssiUpdateHandler = new Handler(Looper.getMainLooper());
    protected final Handler mDelayHandler = new Handler(Looper.getMainLooper());
    protected final Runnable mRssiUpdateRunnable = new RssiUpdateRunnable();
    protected final Runnable mConnectionTimeoutRunnable = new ConnectionTimeoutRunnable();

    protected final Runnable mDisconnectionTimeoutRunnable = new DisconnectionTimeoutRunnable();
    protected final Runnable mCommandTimeoutRunnable = new CommandTimeoutRunnable();
    protected final Runnable mCommandDelayRunnable = new CommandDelayRunnable();

    private final Object mStateLock = new Object();
    private final Object mProcessLock = new Object();

    protected BluetoothDevice device;
    protected BluetoothGatt gatt;
    //    protected int rssi;
    protected String name;
    protected String mac;
    protected byte[] macBytes;
    protected int type;
    protected List<BluetoothGattService> mServices;

    protected Boolean processing = false;

    protected boolean monitorRssi;
    protected int updateIntervalMill = 5 * 1000;
    protected int commandTimeoutMill = 10 * 1000;

    protected long lastTime;
    private AtomicInteger mConnState = new AtomicInteger(CONN_STATE_IDLE);
    protected AtomicBoolean isConnectWaiting = new AtomicBoolean(false);

    protected Context mContext;

    private boolean serviceRefreshed = false;

    private int mtu = 23;

    private static final int MTU_SIZE_MAX = 517;

    public Peripheral(Context context) {
        this.mContext = context;
    }

    /********************************************************************************
     * Public API
     *******************************************************************************/

    public BluetoothDevice getDevice() {
        return this.device;
    }

    public String getMacAddress() {
        return this.device == null ? null : this.device.getAddress();
    }

    public List<BluetoothGattService> getServices() {
        return mServices;
    }

    public byte[] getMacBytes() {

        if (this.macBytes == null) {
            String[] strArray = this.getMacAddress().split(":");
            int length = strArray.length;
            this.macBytes = new byte[length];

            for (int i = 0; i < length; i++) {
                this.macBytes[i] = (byte) (Integer.parseInt(strArray[i], 16) & 0xFF);
            }

            Arrays.reverse(this.macBytes, 0, length - 1);
        }

        return this.macBytes;
    }

    public int getType() {
        return this.type;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }


    /*public int getRssi() {
        return this.rssi;
    }*/

    public boolean isConnected() {
        synchronized (this.mStateLock) {
            return this.mConnState.get() == CONN_STATE_CONNECTED;
        }
    }

    public void connect(BluetoothDevice bluetoothDevice) {
        if (isConnected() && bluetoothDevice.equals(device)) {
            this.onConnect();
            // check if discovering services executed, false means discovering is processing
            if (mServices != null) {
                onServicesDiscoveredComplete(mServices);
            }
        } else {
            this.device = bluetoothDevice;
            if (this.disconnect()) {
                // waiting for disconnected callback
                isConnectWaiting.set(true);
            } else {
                // execute connecting action
                this.connect();
            }
        }
    }


    protected void connect() {
        this.lastTime = 0;
        if (this.device == null) return;
        if (this.mConnState.get() == CONN_STATE_IDLE) {
            OtaLogger.w("connect start");
            this.mConnState.set(CONN_STATE_CONNECTING);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                this.gatt = this.device.connectGatt(mContext, false, this, BluetoothDevice.TRANSPORT_LE);
            } else {
                this.gatt = this.device.connectGatt(mContext, false, this);
            }
            if (this.gatt == null) {
                this.disconnect();
                this.mConnState.set(CONN_STATE_IDLE);
                this.onDisconnect(0xFF); // 0xFF for gatt null
            } else {
                mDelayHandler.postDelayed(mConnectionTimeoutRunnable, connectionTimeout);
            }
        }
    }

    public boolean disconnect() {

        OtaLogger.w("disconnect " + " -- " + mConnState.get());
        this.clear();
        int connState = this.mConnState.get();
        if (connState != CONN_STATE_CONNECTING && connState != CONN_STATE_CONNECTED && connState != CONN_STATE_DISCONNECTING)
            return false;
        if (this.gatt != null) {
            if (connState == CONN_STATE_CONNECTED) {
                this.mConnState.set(CONN_STATE_DISCONNECTING);
                this.gatt.disconnect();
//                return true;
            } else if (connState == CONN_STATE_CONNECTING) {
                this.gatt.disconnect();
                this.gatt.close();
                this.mConnState.set(CONN_STATE_IDLE);
                return false;
            } else {
//                return true;
            }
        } else {
            this.mConnState.set(CONN_STATE_IDLE);
            return false;
        }

        mDelayHandler.postDelayed(mDisconnectionTimeoutRunnable, 1500);
        return true;
    }

    protected void forceDisconnect() {
        if (gatt != null) {
            this.gatt.disconnect();
            this.gatt.close();
        }
        this.mConnState.set(CONN_STATE_IDLE);
    }

    protected void clear() {
        this.processing = false;
        this.serviceRefreshed = false;
        this.stopMonitoringRssi();
        this.cancelCommandTimeoutTask();
        this.mInputCommandQueue.clear();
        this.mOutputCommandQueue.clear();
        this.mNotificationCallbacks.clear();
        this.mDelayHandler.removeCallbacksAndMessages(null);
    }

    public boolean sendCommand(Command.Callback callback, Command command) {

        synchronized (this.mStateLock) {
            if (this.mConnState.get() != CONN_STATE_CONNECTED)
                return false;
        }

        CommandContext commandContext = new CommandContext(callback, command);
        this.postCommand(commandContext);

        return true;
    }

    public final void startMonitoringRssi(int interval) {

        this.monitorRssi = true;

        if (interval <= 0)
            this.updateIntervalMill = RSSI_UPDATE_TIME_INTERVAL;
        else
            this.updateIntervalMill = interval;
    }

    public final void stopMonitoringRssi() {
        this.monitorRssi = false;
        this.mRssiUpdateHandler.removeCallbacks(this.mRssiUpdateRunnable);
        this.mRssiUpdateHandler.removeCallbacksAndMessages(null);
    }

    public final boolean requestConnectionPriority(int connectionPriority) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && this.gatt.requestConnectionPriority(connectionPriority);
    }

    /********************************************************************************
     * Protected API
     *******************************************************************************/

    protected void onConnect() {
        this.mDelayHandler.removeCallbacks(mConnectionTimeoutRunnable);
        //this.requestConnectionPriority(CONNECTION_PRIORITY_BALANCED);
        this.enableMonitorRssi(this.monitorRssi);
    }

    protected void onDisconnect(int statusCode) {
        this.mDelayHandler.removeCallbacks(mDisconnectionTimeoutRunnable);
        this.enableMonitorRssi(false);
    }


    private void onServicesDiscoveredComplete(List<BluetoothGattService> services) {
        onServicesDiscovered(services);
        if (gatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                this.gatt.requestMtu(MTU_SIZE_MAX);
            }
        }
    }

    public boolean refreshCache() {
        if (gatt == null) {
            OtaLogger.d("refresh error: gatt null");
            return false;
        } else {
            OtaLogger.d("Device#refreshCache#prepare");
        }
        try {
            BluetoothGatt localBluetoothGatt = gatt;
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                boolean bool = ((Boolean) localMethod.invoke(localBluetoothGatt, new Object[0])).booleanValue();
                if (bool) {
                    mDelayHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            gatt.discoverServices();
                        }
                    }, 0);
                }
                return bool;
            }
        } catch (Exception localException) {
            OtaLogger.e("An exception occurs while refreshing device");
        }
        return false;
    }


    protected void onServicesDiscovered(List<BluetoothGattService> services) {

    }

    protected void onNotify(byte[] data, UUID serviceUUID,
                            UUID characteristicUUID, Object tag) {
    }

    protected void onEnableNotify() {

    }

    protected void onDisableNotify() {

    }

    protected void onRssiChanged() {
    }

    protected void enableMonitorRssi(boolean enable) {

        if (enable) {
            this.mRssiUpdateHandler.removeCallbacks(this.mRssiUpdateRunnable);
            this.mRssiUpdateHandler.postDelayed(this.mRssiUpdateRunnable, this.updateIntervalMill);
        } else {
            this.mRssiUpdateHandler.removeCallbacks(this.mRssiUpdateRunnable);
            this.mRssiUpdateHandler.removeCallbacksAndMessages(null);
        }
    }

    /********************************************************************************
     * Command Handler API
     *******************************************************************************/

    private void postCommand(CommandContext commandContext) {

        OtaLogger.d("postCommand");
        this.mInputCommandQueue.add(commandContext);

        synchronized (this.mProcessLock) {
            if (!this.processing) {
                this.processCommand();
            }
        }
    }

    private void processCommand() {

        OtaLogger.d("processing : " + this.processing);

        CommandContext commandContext;
        Command.CommandType commandType;

        synchronized (mInputCommandQueue) {
            if (this.mInputCommandQueue.isEmpty())
                return;
            commandContext = this.mInputCommandQueue.poll();
        }

        if (commandContext == null)
            return;

        commandType = commandContext.command.type;

        if (commandType != Command.CommandType.ENABLE_NOTIFY && commandType != Command.CommandType.DISABLE_NOTIFY) {
            this.mOutputCommandQueue.add(commandContext);

            synchronized (this.mProcessLock) {
                if (!this.processing)
                    this.processing = true;
            }
        }

        int delay = commandContext.command.delay;
        if (delay > 0) {
            this.mDelayHandler.postDelayed(this.mCommandDelayRunnable, delay);
        } else {
            this.processCommand(commandContext);
        }
    }

    synchronized private void processCommand(CommandContext commandContext) {

        Command command = commandContext.command;
        Command.CommandType commandType = command.type;

        OtaLogger.d("processCommand : " + command.toString());

        switch (commandType) {
            case READ:
                this.postCommandTimeoutTask();
                this.readCharacteristic(commandContext, command.serviceUUID,
                        command.characteristicUUID);
                break;
            case WRITE:
                this.postCommandTimeoutTask();
                this.writeCharacteristic(commandContext, command.serviceUUID,
                        command.characteristicUUID,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                        command.data);
                break;
            case READ_DESCRIPTOR:
                this.postCommandTimeoutTask();
                this.readDescriptor(commandContext, command.serviceUUID, command.characteristicUUID, command.descriptorUUID);
                break;
            case WRITE_NO_RESPONSE:
                this.postCommandTimeoutTask();
                this.writeCharacteristic(commandContext, command.serviceUUID,
                        command.characteristicUUID,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                        command.data);
                break;
            case ENABLE_NOTIFY:
                this.enableNotification(commandContext, command.serviceUUID,
                        command.characteristicUUID);
                break;
            case DISABLE_NOTIFY:
                this.disableNotification(commandContext, command.serviceUUID,
                        command.characteristicUUID);
                break;

            case WRITE_DESCRIPTOR:
                this.postCommandTimeoutTask();
                this.writeDescriptor(commandContext, command.serviceUUID,
                        command.characteristicUUID,
                        command.descriptorUUID,
                        command.data);
                break;


            case REQUEST_MTU:
                this.postCommandTimeoutTask();
                this.requestGattMtu(commandContext);
                break;
        }
    }

    private void commandCompleted() {

        OtaLogger.d("commandCompleted");

        synchronized (this.mProcessLock) {
            if (this.processing)
                this.processing = false;
        }

        this.processCommand();
    }

    private void commandSuccess(CommandContext commandContext, Object data) {

        OtaLogger.d("commandSuccess");

        if (commandContext != null) {

            Command command = commandContext.command;
            Command.Callback callback = commandContext.callback;
            commandContext.clear();

            if (callback != null) {
                callback.success(this, command,
                        data);
            }
        }
    }

    private void commandSuccess(Object data) {

        CommandContext commandContext;
        commandContext = this.mOutputCommandQueue.poll();
        this.commandSuccess(commandContext, data);
    }

    private void commandError(CommandContext commandContext, String errorMsg) {

        OtaLogger.d("commandError");

        if (commandContext != null) {

            Command command = commandContext.command;
            Command.Callback callback = commandContext.callback;
            commandContext.clear();

            if (callback != null) {
                callback.error(this, command,
                        errorMsg);
            }
        }
    }

    private void commandError(String errorMsg) {

        CommandContext commandContext;
        commandContext = this.mOutputCommandQueue.poll();
        this.commandError(commandContext, errorMsg);
    }

    private boolean commandTimeout(CommandContext commandContext) {
        OtaLogger.d("commandTimeout");

        if (commandContext != null) {

            Command command = commandContext.command;
            Command.Callback callback = commandContext.callback;
            commandContext.clear();

            if (callback != null) {
                return callback.timeout(this, command);
            }
        }

        return false;
    }

    private void postCommandTimeoutTask() {

        if (this.commandTimeoutMill <= 0)
            return;

        this.mTimeoutHandler.removeCallbacksAndMessages(null);
        this.mTimeoutHandler.postDelayed(this.mCommandTimeoutRunnable, this.commandTimeoutMill);
    }

    private void cancelCommandTimeoutTask() {
        this.mTimeoutHandler.removeCallbacksAndMessages(null);
    }

    /********************************************************************************
     * Private API
     *******************************************************************************/


    private void readDescriptor(CommandContext commandContext,
                                UUID serviceUUID, UUID characteristicUUID, UUID descriptorUUID) {

        boolean success = true;
        String errorMsg = "";

        BluetoothGattService service = this.gatt.getService(serviceUUID);

        if (service != null) {

            BluetoothGattCharacteristic characteristic = service
                    .getCharacteristic(characteristicUUID);

            if (characteristic != null) {

                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
                if (descriptor != null) {
                    if (!this.gatt.readDescriptor(descriptor)) {
                        success = false;
                        errorMsg = "read descriptor error";
                    }
                } else {
                    success = false;
                    errorMsg = "read descriptor error";
                }

            } else {
                success = false;
                errorMsg = "read characteristic error";
            }
        } else {
            success = false;
            errorMsg = "service is not offered by the remote device";
        }

        if (!success) {
            this.commandError(errorMsg);
            this.commandCompleted();
        }
    }


    private void readCharacteristic(CommandContext commandContext,
                                    UUID serviceUUID, UUID characteristicUUID) {

        boolean success = true;
        String errorMsg = "";

        BluetoothGattService service = this.gatt.getService(serviceUUID);

        if (service != null) {

            BluetoothGattCharacteristic characteristic = service
                    .getCharacteristic(characteristicUUID);

            if (characteristic != null) {

                if (!this.gatt.readCharacteristic(characteristic)) {
                    success = false;
                    errorMsg = "read characteristic error";
                }

            } else {
                success = false;
                errorMsg = "read characteristic error";
            }
        } else {
            success = false;
            errorMsg = "service is not offered by the remote device";
        }

        if (!success) {
            this.commandError(errorMsg);
            this.commandCompleted();
        }
    }

    private void writeCharacteristic(CommandContext commandContext,
                                     UUID serviceUUID, UUID characteristicUUID, int writeType,
                                     byte[] data) {

        boolean success = true;
        String errorMsg = "";

        BluetoothGattService service = this.gatt.getService(serviceUUID);

        if (service != null) {
            BluetoothGattCharacteristic characteristic = this
                    .findWritableCharacteristic(service, characteristicUUID,
                            writeType);
            if (characteristic != null) {

                characteristic.setValue(data);
                characteristic.setWriteType(writeType);

                if (!this.gatt.writeCharacteristic(characteristic)) {
                    success = false;
                    errorMsg = "write characteristic error";
                }

            } else {
                success = false;
                errorMsg = "no characteristic";
            }
        } else {
            success = false;
            errorMsg = "service is not offered by the remote device";
        }

        if (!success) {
            this.commandError(errorMsg);
            this.commandCompleted();
        }
    }

    private void writeDescriptor(CommandContext commandContext,
                                 UUID serviceUUID, UUID characteristicUUID,
                                 UUID descriptorUUID,
                                 byte[] data) {

        boolean success = true;
        String errorMsg = "";

        BluetoothGattService service = this.gatt.getService(serviceUUID);

        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
            if (characteristic != null) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUUID);
                if (descriptor != null) {
                    descriptor.setValue(data);
                    if (!this.gatt.writeDescriptor(descriptor)) {
                        success = false;
                        errorMsg = "write descriptor error";
                    }
                } else {
                    success = false;
                    errorMsg = "no descriptor";
                }
            } else {
                success = false;
                errorMsg = "no characteristic";
            }
        } else {
            success = false;
            errorMsg = "service is not offered by the remote device";
        }

        if (!success) {
            this.commandError(errorMsg);
            this.commandCompleted();
        }
    }

    private void requestGattMtu(CommandContext commandContext) {

        boolean success = true;
        String errorMsg = "";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!this.gatt.requestMtu(commandContext.command.mtu)) {
                errorMsg = "request mtu error";
                success = false;
            }
        } else {
            errorMsg = "request mtu not support";
            success = false;
        }

        if (!success) {
            this.commandError(errorMsg);
            this.commandCompleted();
        }
    }

    private void enableNotification(CommandContext commandContext,
                                    UUID serviceUUID, UUID characteristicUUID) {

        boolean success = true;
        String errorMsg = "";

        BluetoothGattService service = this.gatt.getService(serviceUUID);

        if (service != null) {

            BluetoothGattCharacteristic characteristic = this
                    .findNotifyCharacteristic(service, characteristicUUID);

            if (characteristic != null) {

                if (!this.gatt.setCharacteristicNotification(characteristic,
                        true)) {
                    success = false;
                    errorMsg = "enable notification error";
                } else {
                    String key = this.generateHashKey(serviceUUID,
                            characteristic);
                    this.mNotificationCallbacks.put(key, commandContext);
                }

            } else {
                success = false;
                errorMsg = "no characteristic";
            }

        } else {
            success = false;
            errorMsg = "service is not offered by the remote device";
        }

        if (!success) {
            this.commandError(commandContext, errorMsg);
        } else {
            this.onEnableNotify();
        }

        this.commandCompleted();
    }

    private void disableNotification(CommandContext commandContext,
                                     UUID serviceUUID, UUID characteristicUUID) {

        boolean success = true;
        String errorMsg = "";

        BluetoothGattService service = this.gatt.getService(serviceUUID);

        if (service != null) {

            BluetoothGattCharacteristic characteristic = this
                    .findNotifyCharacteristic(service, characteristicUUID);

            if (characteristic != null) {

                String key = this.generateHashKey(serviceUUID, characteristic);
                this.mNotificationCallbacks.remove(key);

                if (!this.gatt.setCharacteristicNotification(characteristic,
                        false)) {
                    success = false;
                    errorMsg = "disable notification error";
                }

            } else {
                success = false;
                errorMsg = "no characteristic";
            }

        } else {
            success = false;
            errorMsg = "service is not offered by the remote device";
        }

        if (!success) {
            this.commandError(commandContext, errorMsg);
        } else {
            this.onDisableNotify();
        }

        this.commandCompleted();
    }

    private BluetoothGattCharacteristic findWritableCharacteristic(
            BluetoothGattService service, UUID characteristicUUID, int writeType) {

        BluetoothGattCharacteristic characteristic = null;

        int writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE;

        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
        }

        List<BluetoothGattCharacteristic> characteristics = service
                .getCharacteristics();

        for (BluetoothGattCharacteristic c : characteristics) {
            if ((c.getProperties() & writeProperty) != 0
                    && characteristicUUID.equals(c.getUuid())) {
                characteristic = c;
                break;
            }
        }

        return characteristic;
    }

    private BluetoothGattCharacteristic findNotifyCharacteristic(
            BluetoothGattService service, UUID characteristicUUID) {

        BluetoothGattCharacteristic characteristic = null;

        List<BluetoothGattCharacteristic> characteristics = service
                .getCharacteristics();

        for (BluetoothGattCharacteristic c : characteristics) {
            if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    && characteristicUUID.equals(c.getUuid())) {
                characteristic = c;
                break;
            }
        }

        if (characteristic != null)
            return characteristic;

        for (BluetoothGattCharacteristic c : characteristics) {
            if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    && characteristicUUID.equals(c.getUuid())) {
                characteristic = c;
                break;
            }
        }

        return characteristic;
    }

    private String generateHashKey(BluetoothGattCharacteristic characteristic) {
        return this.generateHashKey(characteristic.getService().getUuid(),
                characteristic);
    }

    protected String generateHashKey(UUID serviceUUID,
                                     BluetoothGattCharacteristic characteristic) {
        return String.valueOf(serviceUUID) + "|" + characteristic.getUuid()
                + "|" + characteristic.getInstanceId();
    }

    /********************************************************************************
     * Implements BluetoothGattCallback API
     *******************************************************************************/

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                        int newState) {
        OtaLogger.d("onConnectionStateChange  status :" + status + " state : "
                + newState);

        if (newState == BluetoothGatt.STATE_CONNECTED) {

            synchronized (this.mStateLock) {
                this.mConnState.set(CONN_STATE_CONNECTED);
            }

            if (this.gatt == null || !this.gatt.discoverServices()) {
                OtaLogger.d("remote service discovery has been stopped status = "
                        + newState);
                this.disconnect();
            } else {
//                this.gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                this.onConnect();
            }

        } else {

            synchronized (this.mStateLock) {
                OtaLogger.d("Close");

                if (this.gatt != null) {
                    this.gatt.close();
                }
                this.clear();
                this.mConnState.set(CONN_STATE_IDLE);
                this.onDisconnect(status);
            }
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        String key = this.generateHashKey(characteristic);
        CommandContext commandContext = this.mNotificationCallbacks.get(key);

        if (commandContext != null) {

            this.onNotify(characteristic.getValue(),
                    commandContext.command.serviceUUID,
                    commandContext.command.characteristicUUID,
                    commandContext.command.tag);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);

        this.cancelCommandTimeoutTask();

        if (status == BluetoothGatt.GATT_SUCCESS) {
            byte[] data = characteristic.getValue();
            this.commandSuccess(data);
        } else {
            this.commandError("read characteristic failed");
        }

        this.commandCompleted();
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt,
                                      BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);

        this.cancelCommandTimeoutTask();

        if (status == BluetoothGatt.GATT_SUCCESS) {
            this.commandSuccess(null);
        } else {
            this.commandError("write characteristic fail");
        }

        OtaLogger.d("onCharacteristicWrite newStatus : " + status);

        this.commandCompleted();
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt,
                                 BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorRead(gatt, descriptor, status);

        this.cancelCommandTimeoutTask();

        if (status == BluetoothGatt.GATT_SUCCESS) {
            byte[] data = descriptor.getValue();
            this.commandSuccess(data);
        } else {
            this.commandError("read description failed");
        }

        this.commandCompleted();
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt,
                                  BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);

        this.cancelCommandTimeoutTask();

        if (status == BluetoothGatt.GATT_SUCCESS) {
            this.commandSuccess(null);
        } else {
            this.commandError("write description failed");
        }

        this.commandCompleted();
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            List<BluetoothGattService> services = gatt.getServices();
            this.mServices = services;
            this.onServicesDiscoveredComplete(services);
            OtaLogger.d("Service discovery success:" + services.size());
        } else {
            OtaLogger.d("Service discovery failed");
            this.disconnect();
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {

            /*if (rssi != this.rssi) {
                this.rssi = rssi;
                this.onRssiChanged();
            }*/
        }
    }

    public int getMtu() {
        return mtu;
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
        this.mtu = mtu;
        this.cancelCommandTimeoutTask();

        if (status == BluetoothGatt.GATT_SUCCESS) {
            this.commandSuccess(null);
        } else {
            this.commandError("request mtu callback fail");
        }
        OtaLogger.d("mtu changed : " + mtu);
        this.commandCompleted();
    }

    @Override
    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        super.onReliableWriteCompleted(gatt, status);
    }

    private final class CommandContext {

        public Command command;
        public Command.Callback callback;

        public CommandContext(Command.Callback callback, Command command) {
            this.callback = callback;
            this.command = command;
        }

        public void clear() {
            this.command = null;
            this.callback = null;
        }
    }

    private final class ConnectionTimeoutRunnable implements Runnable {

        @Override
        public void run() {
            if (!disconnect()) {
                onDisconnect(0xFE);// 0xFE for timeout
            }
        }
    }

    private final class DisconnectionTimeoutRunnable implements Runnable {

        @Override
        public void run() {
            OtaLogger.w("disconnection timeout");
            mConnState.set(CONN_STATE_IDLE);
            onDisconnect(0xFD);// 0xFD for disconnect timeout
        }
    }

    private final class RssiUpdateRunnable implements Runnable {

        @Override
        public void run() {

            if (!monitorRssi)
                return;

            if (!isConnected())
                return;

            if (gatt != null)
                gatt.readRemoteRssi();

            mRssiUpdateHandler.postDelayed(mRssiUpdateRunnable, updateIntervalMill);
        }
    }

    private final class CommandTimeoutRunnable implements Runnable {

        @Override
        public void run() {

            synchronized (mOutputCommandQueue) {

                CommandContext commandContext = mOutputCommandQueue.peek();

                if (commandContext != null) {

                    Command command = commandContext.command;
                    Command.Callback callback = commandContext.callback;

                    boolean retry = commandTimeout(commandContext);

                    if (retry) {
                        commandContext.command = command;
                        commandContext.callback = callback;
                        processCommand(commandContext);
                    } else {
                        mOutputCommandQueue.poll();
                        commandCompleted();
                    }
                }
            }
        }
    }

    private final class CommandDelayRunnable implements Runnable {

        @Override
        public void run() {
            synchronized (mOutputCommandQueue) {
                CommandContext commandContext = mOutputCommandQueue.peek();
                processCommand(commandContext);
            }
        }
    }
}
