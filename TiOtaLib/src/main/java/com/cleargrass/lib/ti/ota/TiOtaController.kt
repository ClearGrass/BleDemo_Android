package com.cleargrass.lib.ti.ota

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import java.util.UUID
import kotlin.math.roundToInt

data class FirmwareInfo(
    val label: String,
    val version: String,
    val imageType: ImageType
)

enum class ImageType {
    BIM, MCUBOOT, UNKNOWN
}


class TiOtaController(
    private val context: Context,
    private val device: BluetoothDevice,
    private val setting: TI_OtaSetting,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    companion object {
        private const val TAG = "TiOtaController"
        private const val MTU_SIZE = 255
        private const val MCUBOOT_MAGIC_1 = 0x3d
        private const val MCUBOOT_MAGIC_2 = 0xb8
        private const val MCUBOOT_MAGIC_3 = 0xf3
        private const val MCUBOOT_MAGIC_4_1 = 0x96
        private const val MCUBOOT_MAGIC_4_2 = 0x97
    }

    private var fwImageByteArray: ByteArray? = null
    private var firmware: FirmwareInfo? = null
    private var imageLength = 0
    private var blockSize = 20
    private var numBlocks = 0
    private var gatt: BluetoothGatt? = null
    private var isOtaRunning = false
    private var currentBlockRequested = 0
    private var timeoutRunnable: Runnable? = null
    private var imageIdentifySent = false

    private var progressListener: TiOtaCallback? = null

    fun setProgressListener(listener: TiOtaCallback) {
        this.progressListener = listener
    }

    fun startOta() {
        if (isOtaRunning) {
            setProgress(0f, UpdateState.ERROR)
            return
        }

        isOtaRunning = true
        setProgress(0f, UpdateState.PROGRESS)
        
        // Set timeout
        startTimeout()
        
        // Connect to device
        gatt = device.connectGatt(context, false, gattCallback)
    }

    private fun startTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = Runnable {
            Log.e(TAG, "OTA timeout")
            setProgress(0f, UpdateState.ERROR)
            cancel()
        }
        handler.postDelayed(timeoutRunnable!!, setting.timeout)
    }

    private fun clearTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    fun cancel() {
        Log.d(TAG, "cancel")
        clearTimeout()
        setProgress(0f, UpdateState.ERROR)
        isOtaRunning = false
        
        // Send cancel command if connected
        gatt?.let { gatt ->
            val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_CONTROL_POINT_UUID)
            characteristic?.let {
                val cancelCmd = byteArrayOf(OadEvent.OAD_EVT_CANCEL_OAD.toByte())
                it.value = cancelCmd
                gatt.writeCharacteristic(it)
            }
        }
        
        disconnect()
    }

    fun stopOta() {
        Log.d(TAG, "stopOta")
        clearTimeout()
        isOtaRunning = false
        disconnect()
    }

    private fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        imageIdentifySent = false
        currentBlockRequested = 0
    }

    /**
     * Get current OTA progress (0-100)
     */
    fun getProgress(): Int {
        return if (numBlocks > 0) {
            ((currentBlockRequested.toFloat() / numBlocks) * 98).toInt()
        } else {
            0
        }
    }

    /**
     * Check if OTA is currently running
     */
    fun isRunning(): Boolean = isOtaRunning

    private fun setProgress(progress: Float, state: UpdateState = UpdateState.PROGRESS) {
        val progress: Int = (progress * 100).toInt()
        Log.d(TAG, "progress: $progress, state: $state")
        val finalProgress = if (progress >= 100) 100 else progress.coerceIn(0, 99)
        val finalState = if (finalProgress >= 100) UpdateState.DONE else state

        handler.post {
            if (finalState == UpdateState.PROGRESS) {
                progressListener?.onOtaProgressUpdate(finalProgress)
            } else {
                progressListener?.onOtaStatusChanged(state)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    if (isOtaRunning) {
                        // Check if this is expected disconnection after OTA completion
                        handler.postDelayed({
                            clearTimeout()
                            setProgress(1.0f, UpdateState.DONE)
                            isOtaRunning = false
                        }, 1000)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                
                // Request MTU
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    gatt?.requestMtu(MTU_SIZE)
                } else {
                    startOtaProcess()
                }
            } else {
                Log.e(TAG, "Service discovery failed")
                setProgress(0f, UpdateState.ERROR)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(TAG, "MTU changed to: $mtu")
            
            // Start OTA process
            startOtaProcess()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            
            characteristic?.let { char ->
                val data = char.value
                if (data != null && data.isNotEmpty()) {
                    when (char.uuid) {
                        TiUuidInfo.IMAGE_CONTROL_POINT_UUID -> handleNotification(data)
                        TiUuidInfo.IMAGE_IDENTIFY_WRITE_UUID -> handleImageIdentifyResponse(data)
                    }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful")
            } else {
                Log.e(TAG, "Characteristic write failed: $status")
                setProgress(0f, UpdateState.ERROR)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic?.value?.let { data ->
                    handleNotification(data)
                }
            }
        }
    }

    private fun startOtaProcess() {
        // Load firmware image
        loadFirmwareImage { success ->
            if (success) {
                checkOadServices()
            } else {
                setProgress(0f, UpdateState.ERROR)
            }
        }
    }

    private fun loadFirmwareImage(callback: (Boolean) -> Unit) {
        try {
            val firmwareBytes = when {
                setting.firmwareBytes != null -> setting.firmwareBytes!!
                setting.firmwarePath != null -> FileUtils.readFileToByteArray(context, setting.firmwarePath!!)
                else -> null
            }
            
            if (firmwareBytes == null) {
                Log.e(TAG, "Firmware bytes is null")
                callback(false)
                return
            }

            fwImageByteArray = firmwareBytes
            val imageType = getImageType(firmwareBytes)
            
            if (imageType == ImageType.UNKNOWN) {
                Log.e(TAG, "Invalid image type")
                callback(false)
                return
            }

            firmware = FirmwareInfo(
                label = "",
                version = getImageVersionFromImg(firmwareBytes),
                imageType = imageType
            )

            Log.d(TAG, "Firmware loaded: ${firmware?.version}, type: ${firmware?.imageType}")
            callback(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading firmware", e)
            callback(false)
        }
    }

    private fun getImageType(imgContent: ByteArray): ImageType {
        if (imgContent.size < 4) return ImageType.UNKNOWN
        
        val magic = imgContent.sliceArray(0..3)
        val hexMagic = magic.joinToString("") { "%02x".format(it) }
        
        return when (hexMagic) {
            "3db8f396", "3db8f397" -> ImageType.MCUBOOT
            "43433236" -> ImageType.BIM
            else -> ImageType.UNKNOWN
        }
    }

    private fun getImageVersionFromImg(imgContent: ByteArray): String {
        if (imgContent.size < 28) return "0.0.0.0"
        
        val major = imgContent[20].toInt() and 0xFF
        val minor = imgContent[21].toInt() and 0xFF
        val revision = (imgContent[22].toInt() and 0xFF) + ((imgContent[23].toInt() and 0xFF) shl 8)
        val buildNum = (imgContent[24].toInt() and 0xFF) +
                ((imgContent[25].toInt() and 0xFF) shl 8) +
                ((imgContent[26].toInt() and 0xFF) shl 16) +
                ((imgContent[27].toInt() and 0xFF) shl 24)
        
        return "$major.$minor.$revision.$buildNum"
    }

    private fun checkOadServices() {
        gatt?.let { gatt ->
            val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
            if (service != null) {
                Log.d(TAG, "OAD service found")
                enableNotifications()
            } else {
                Log.e(TAG, "OAD service not found")
                setProgress(0f, UpdateState.ERROR)
            }
        }
    }

    private fun enableNotifications() {
        gatt?.let { gatt ->
            val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_CONTROL_POINT_UUID)
            
            characteristic?.let { char ->
                gatt.setCharacteristicNotification(char, true)
                
                // Enable notifications by writing to CCCD
                val descriptor = char.getDescriptor(TiUuidInfo.NOTIFICATION_DESCRIPTION)
                descriptor?.let { desc ->
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                    
                    // Start image update request
                    handler.postDelayed({
                        val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_IDENTIFY_WRITE_UUID)

                        characteristic?.let { char ->
                            gatt.setCharacteristicNotification(char, true)

                            // Enable notifications by writing to CCCD
                            val descriptor = char.getDescriptor(TiUuidInfo.NOTIFICATION_DESCRIPTION)
                            descriptor?.let { desc ->
                                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(desc)

                                // Start image update request
                                handler.postDelayed({
                                    sendImageUpdateRequest()
                                }, 500)
                            }
                        }
                    }, 500)
                }
            }


        }
    }

    private fun sendImageUpdateRequest() {
        Log.d(TAG, "sendImageUpdateRequest - Starting FW Update")
        
        fwImageByteArray?.let { imageBytes ->
            firmware?.let { fw ->
                if (fw.imageType != ImageType.MCUBOOT) {
                    Log.e(TAG, "Only mcuboot images are supported")
                    setProgress(0f, UpdateState.ERROR)
                    return
                }

                // Validate MCUBoot header
                if (!validateMcuBootHeader(imageBytes)) {
                    Log.e(TAG, "Invalid MCUBoot header")
                    setProgress(0f, UpdateState.ERROR)
                    return
                }

                // Get software version first
                getSoftwareVersion()
            }
        }
    }

    private fun getSoftwareVersion() {
        gatt?.let { gatt ->
            val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_CONTROL_POINT_UUID)
            
            characteristic?.let { char ->
                val cmd = byteArrayOf(OadProtocolOpCode.OAD_REQ_GET_SW_VER.toByte())
                char.value = cmd
                gatt.writeCharacteristic(char)
            }
        }
    }

    private fun validateMcuBootHeader(imageBytes: ByteArray): Boolean {
        if (imageBytes.size < 32) return false // MCUBoot header is 32 bytes
        
        // Check magic number
        val magicValid = imageBytes[0].toInt() and 0xFF == MCUBOOT_MAGIC_1 &&
                        imageBytes[1].toInt() and 0xFF == MCUBOOT_MAGIC_2 &&
                        imageBytes[2].toInt() and 0xFF == MCUBOOT_MAGIC_3 &&
                        (imageBytes[3].toInt() and 0xFF == MCUBOOT_MAGIC_4_1 || 
                         imageBytes[3].toInt() and 0xFF == MCUBOOT_MAGIC_4_2)
        
        if (!magicValid) {
            Log.e(TAG, "Invalid MCUBoot magic number")
            return false
        }

        // Check if it's a swap image by looking at the last 16 bytes
        if (imageBytes.size >= 16) {
            val swapMagic = imageBytes.sliceArray(imageBytes.size - 16 until imageBytes.size)
                .joinToString("") { "%02x".format(it) }
            
            if (swapMagic != "77c295f360d2ef7f355250f2cb67980") {
                Log.d(TAG, "Not a swap image")
                
                // For non-swap images, validate TLV header
                val headerLen = (imageBytes[8].toInt() and 0xFF) +
                               ((imageBytes[9].toInt() and 0xFF) shl 8) +
                               ((imageBytes[10].toInt() and 0xFF) shl 16) +
                               ((imageBytes[11].toInt() and 0xFF) shl 24)
                
                val imgLength = (imageBytes[12].toInt() and 0xFF) +
                               ((imageBytes[13].toInt() and 0xFF) shl 8) +
                               ((imageBytes[14].toInt() and 0xFF) shl 16) +
                               ((imageBytes[15].toInt() and 0xFF) shl 24)
                
                if (imgLength + headerLen < imageBytes.size) {
                    val tlvMagic1 = imageBytes[imgLength + headerLen + 1].toInt() and 0xFF
                    val tlvMagic0 = imageBytes[imgLength + headerLen].toInt() and 0xFF
                    
                    if (tlvMagic1 != 0x69 || (tlvMagic0 != 0x07 && tlvMagic0 != 0x08)) {
                        Log.e(TAG, "Invalid TLV header magic")
                        return false
                    }
                }
            }
        }
        
        return true
    }

    private fun getBlockSize() {
        gatt?.let { gatt ->
            val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_CONTROL_POINT_UUID)
            
            characteristic?.let { char ->
                val cmd = byteArrayOf(OadProtocolOpCode.OAD_REQ_GET_BLK_SZ.toByte())
                char.value = cmd
                gatt.writeCharacteristic(char)
            }
        }
    }

    private fun sendImageIdentify() {
        if (imageIdentifySent) return
        
        fwImageByteArray?.let { imageBytes ->
            gatt?.let { gatt ->
                val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_IDENTIFY_WRITE_UUID)
                
                characteristic?.let { char ->
                    // Enable notifications for image identify characteristic
                    gatt.setCharacteristicNotification(char, true)
                    
                    // Prepare image identify payload (first 18 bytes of image header)
                    val imgIdentifyPayload = imageBytes.sliceArray(0 until 18.coerceAtMost(imageBytes.size))
                    
                    // Update image size in header
                    updateImageSizeInHeader(imgIdentifyPayload, imageBytes.size)
                    
                    char.value = imgIdentifyPayload
                    gatt.writeCharacteristic(char)
                    imageIdentifySent = true
                    
                    Log.d(TAG, "Image identify payload sent")
                }
            }
        }
    }

    private fun handleImageIdentifyResponse(data: ByteArray) {
        Log.d(TAG, "Image identify response received")
        // After successful image identify, start the OAD process
        handler.postDelayed({
            startImageTransfer()
        }, 500)
    }

    private fun updateImageSizeInHeader(payload: ByteArray, imgSize: Int) {
        if (payload.size >= 16) {
            payload[12] = (imgSize and 0xFF).toByte()
            payload[13] = ((imgSize shr 8) and 0xFF).toByte()
            payload[14] = ((imgSize shr 16) and 0xFF).toByte()
            payload[15] = ((imgSize shr 24) and 0xFF).toByte()
        }
    }

    private fun startImageTransfer() {
        gatt?.let { gatt ->
            val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_CONTROL_POINT_UUID)
            
            characteristic?.let { char ->
                val cmd = byteArrayOf(OadEvent.OAD_EVT_START_OAD.toByte())
                char.value = cmd
                gatt.writeCharacteristic(char)
                
                Log.d(TAG, "Start OAD command sent")
            }
        }
    }

    private fun writeBlock(blockNumber: Int, blockData: ByteArray) {
        gatt?.let { gatt ->
            val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_BLOCK_REQUEST_UUID)
            
            characteristic?.let { char ->
                // Create block response command with 4-byte header + block data
                val blockCmd = ByteArray(4 + blockData.size)
                
                // Block number (4 bytes, little endian)
                blockCmd[0] = (blockNumber and 0xFF).toByte()
                blockCmd[1] = ((blockNumber shr 8) and 0xFF).toByte()
                blockCmd[2] = ((blockNumber shr 16) and 0xFF).toByte()
                blockCmd[3] = ((blockNumber shr 24) and 0xFF).toByte()
                
                // Copy block data
                System.arraycopy(blockData, 0, blockCmd, 4, blockData.size)
                
                char.value = blockCmd
                gatt.writeCharacteristic(char)
            }
        }
    }

    private fun enableImage() {
        gatt?.let { gatt ->
            val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_CONTROL_POINT_UUID)
            
            characteristic?.let { char ->
                val cmd = byteArrayOf(OadEvent.OAD_EVT_ENABLE_IMG.toByte())
                char.value = cmd
                gatt.writeCharacteristic(char)
                
                Log.d(TAG, "Enable image command sent")
                
                // Wait for device reset
                handler.postDelayed({
                    setProgress(0.98f)
                }, 1000)
            }
        }
    }

    private fun handleNotification(data: ByteArray) {
        if (data.isEmpty()) return
        
        val opCode = data[0].toInt() and 0xFF
        Log.d(TAG, "Notification OpCode: 0x${opCode.toString(16)}")
        
        when (opCode) {
            OadProtocolOpCode.OAD_REQ_GET_SW_VER -> {
                // Software version response
                if (data.size >= 5) {
                    val major = data[1].toInt() and 0xFF
                    val minor = data[2].toInt() and 0xFF
                    val revisionHi = data[3].toInt() and 0xFF
                    val revisionLow = data[4].toInt() and 0xFF
                    val buildHi = if (data.size >= 6) data[5].toInt() and 0xFF else 0
                    val buildLow = if (data.size >= 7) data[6].toInt() and 0xFF else 0
                    
                    val swVersion = "$major.$minor.${(revisionHi + revisionLow) shl 8}.${buildHi + (buildLow shl 8)}"
                    Log.d(TAG, "SW Version: $swVersion")
                    
                    firmware?.let { fw ->
                        Log.d(TAG, "Current version: $swVersion, New version: ${fw.version}")
                    }
                }
                
                // Continue with block size request
                getBlockSize()
            }
            
            OadProtocolOpCode.OAD_REQ_GET_BLK_SZ -> {
                // Block size response
                if (data.size >= 3) {
                    blockSize = (data[1].toInt() and 0xFF) + ((data[2].toInt() and 0xFF) shl 8) - 4
                    Log.d(TAG, "Block size: $blockSize")
                    
                    fwImageByteArray?.let { imageBytes ->
                        imageLength = imageBytes.size
                        numBlocks = imageLength / blockSize
                        Log.d(TAG, "Image length: $imageLength, num blocks: $numBlocks")
                        
                        // Send image identify
                        sendImageIdentify()
                    }
                }
            }
            
            OadProtocolOpCode.OAD_RSP_BLK_RSP_NOTIF -> {
                // Block request notification
                if (data.size >= 6) {
                    val status = data[1].toInt() and 0xFF
                    val blockRequested = (data[2].toInt() and 0xFF) +
                                       ((data[3].toInt() and 0xFF) shl 8) +
                                       ((data[4].toInt() and 0xFF) shl 16) +
                                       ((data[5].toInt() and 0xFF) shl 24)
                    
                    Log.d(TAG, "Block requested: $blockRequested, status: $status")
                    
                    when (status) {
                        OadStatus.OAD_PROFILE_DL_COMPLETE -> {
                            Log.d(TAG, "Download complete, enabling image")
                            enableImage()
                        }
                        
                        OadStatus.OAD_PROFILE_SUCCESS -> {
                            currentBlockRequested = blockRequested
                            
                            // Update progress
                            val progress = 0.98f * (blockRequested.toFloat() / numBlocks)
                            setProgress(progress)
                            
                            fwImageByteArray?.let { imageBytes ->
                                val offset = blockRequested * blockSize
                                
                                // Handle last block
                                val actualBlockSize = if (blockRequested == numBlocks) {
                                    imageBytes.size - blockSize * numBlocks
                                } else {
                                    blockSize
                                }
                                
                                if (offset + actualBlockSize <= imageBytes.size) {
                                    val blockData = imageBytes.sliceArray(offset until offset + actualBlockSize)
                                    writeBlock(blockRequested, blockData)
                                }
                            }
                        }
                        
                        else -> {
                            Log.e(TAG, "Block request failed with status: $status")
                            setProgress(0f, UpdateState.ERROR)
                        }
                    }
                }
            }
            
            OadEvent.OAD_EVT_ENABLE_IMG -> {
                Log.d(TAG, "Image enabled, waiting for device reset")
                setProgress(0.98f)
                
                // Device should disconnect and reset now
                handler.postDelayed({
                    clearTimeout()
                    setProgress(1.0f, UpdateState.DONE)
                    isOtaRunning = false
                }, 2000)
            }
        }
    }
}