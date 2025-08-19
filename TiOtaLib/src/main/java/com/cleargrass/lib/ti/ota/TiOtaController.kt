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

enum class OtaState {
    IDLE,
    CONNECTING,
    DISCOVERING_SERVICES,
    LOADING_FIRMWARE,
    CHECKING_SERVICES,
    ENABLING_NOTIFICATIONS,
    GETTING_SW_VERSION,
    GETTING_BLOCK_SIZE,
    SENDING_IMAGE_IDENTIFY,
    TRANSFERRING_BLOCKS,
    ENABLING_IMAGE,
    COMPLETED,
    ERROR
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
    private var currentState = OtaState.IDLE

    private var progressListener: TiOtaCallback? = null

    fun setProgressListener(listener: TiOtaCallback) {
        this.progressListener = listener
    }

    fun startOta() {
        if (isOtaRunning) {
            setProgress(0f, UpdateState.ERROR)
            return
        }

        Log.d(TAG, "Starting OTA process")
        isOtaRunning = true
        setProgress(0f, UpdateState.PROGRESS)
        startTimeout()
        
        // 开始状态机
        changeState(OtaState.CONNECTING)
    }

    /**
     * 状态机核心方法 - 根据当前状态执行相应操作
     * 
     * OTA 流程状态转换：
     * IDLE -> CONNECTING -> DISCOVERING_SERVICES -> LOADING_FIRMWARE -> 
     * CHECKING_SERVICES -> ENABLING_NOTIFICATIONS -> GETTING_SW_VERSION -> 
     * GETTING_BLOCK_SIZE -> SENDING_IMAGE_IDENTIFY -> TRANSFERRING_BLOCKS -> 
     * ENABLING_IMAGE -> COMPLETED
     * 
     * 任何步骤出错都会转到 ERROR 状态
     */
    private fun changeState(newState: OtaState) {
        Log.d(TAG, "State change: ${currentState} -> $newState")
        currentState = newState
        
        when (newState) {
            OtaState.CONNECTING -> {
                gatt = device.connectGatt(context, false, gattCallback)
            }
            
            OtaState.DISCOVERING_SERVICES -> {
                gatt?.discoverServices()
            }
            
            OtaState.LOADING_FIRMWARE -> {
                loadFirmwareImage()
            }
            
            OtaState.CHECKING_SERVICES -> {
                checkOadServices()
            }
            
            OtaState.ENABLING_NOTIFICATIONS -> {
                enableNotifications()
            }
            
            OtaState.GETTING_SW_VERSION -> {
                getSoftwareVersion()
            }
            
            OtaState.GETTING_BLOCK_SIZE -> {
                getBlockSize()
            }
            
            OtaState.SENDING_IMAGE_IDENTIFY -> {
                sendImageIdentify()
            }
            
            OtaState.TRANSFERRING_BLOCKS -> {
                startImageTransfer()
            }
            
            OtaState.ENABLING_IMAGE -> {
                enableImage()
            }
            
            OtaState.COMPLETED -> {
                Log.d(TAG, "OTA completed successfully")
                clearTimeout()
                setProgress(1.0f, UpdateState.DONE)
                isOtaRunning = false
            }
            
            OtaState.ERROR -> {
                Log.e(TAG, "OTA failed")
                clearTimeout()
                setProgress(0f, UpdateState.ERROR)
                isOtaRunning = false
                disconnect()
            }
            
            OtaState.IDLE -> {
                // 初始状态，无需操作
            }
        }
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
        Log.d(TAG, "Cancelling OTA")
        
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
        
        changeState(OtaState.ERROR)
    }

    fun stopOta() {
        Log.d(TAG, "Stopping OTA")
        clearTimeout()
        isOtaRunning = false
        currentState = OtaState.IDLE
        disconnect()
    }

    private fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        resetState()
    }

    private fun resetState() {
        imageIdentifySent = false
        currentBlockRequested = 0
        currentState = OtaState.IDLE
        fwImageByteArray = null
        firmware = null
        imageLength = 0
        blockSize = 20
        numBlocks = 0
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
                    if (currentState == OtaState.CONNECTING) {
                        changeState(OtaState.DISCOVERING_SERVICES)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    if (currentState == OtaState.ENABLING_IMAGE) {
                        // 这是预期的断开连接（设备重启）
                        handler.postDelayed({
                            changeState(OtaState.COMPLETED)
                        }, 1000)
                    } else if (isOtaRunning) {
                        // 意外断开连接
                        changeState(OtaState.ERROR)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                
                // Request MTU first, then continue
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    gatt?.requestMtu(MTU_SIZE)
                } else {
                    // 直接进入下一步
                    if (currentState == OtaState.DISCOVERING_SERVICES) {
                        changeState(OtaState.LOADING_FIRMWARE)
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed")
                changeState(OtaState.ERROR)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(TAG, "MTU changed to: $mtu")
            
            if (currentState == OtaState.DISCOVERING_SERVICES) {
                changeState(OtaState.LOADING_FIRMWARE)
            }
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

    private fun loadFirmwareImage() {
        try {
            val firmwareBytes = when {
                setting.firmwareBytes != null -> setting.firmwareBytes!!
                setting.firmwarePath != null -> FileUtils.readFileToByteArray(context, setting.firmwarePath!!)
                else -> null
            }
            
            if (firmwareBytes == null) {
                Log.e(TAG, "Firmware bytes is null")
                changeState(OtaState.ERROR)
                return
            }

            fwImageByteArray = firmwareBytes
            val imageType = getImageType(firmwareBytes)
            
            if (imageType == ImageType.UNKNOWN) {
                Log.e(TAG, "Invalid image type")
                changeState(OtaState.ERROR)
                return
            }

            firmware = FirmwareInfo(
                label = "",
                version = getImageVersionFromImg(firmwareBytes),
                imageType = imageType
            )

            Log.d(TAG, "Firmware loaded: ${firmware?.version}, type: ${firmware?.imageType}")
            
            // 验证固件格式
            if (!validateFirmware(firmwareBytes)) {
                changeState(OtaState.ERROR)
                return
            }
            
            // 继续下一步
            changeState(OtaState.CHECKING_SERVICES)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading firmware", e)
            changeState(OtaState.ERROR)
        }
    }

    private fun validateFirmware(firmwareBytes: ByteArray): Boolean {
        firmware?.let { fw ->
            if (fw.imageType != ImageType.MCUBOOT) {
                Log.e(TAG, "Only mcuboot images are supported")
                return false
            }

            if (!validateMcuBootHeader(firmwareBytes)) {
                Log.e(TAG, "Invalid MCUBoot header")
                return false
            }
        }
        return true
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
                changeState(OtaState.ENABLING_NOTIFICATIONS)
            } else {
                Log.e(TAG, "OAD service not found")
                changeState(OtaState.ERROR)
            }
        } ?: run {
            Log.e(TAG, "GATT is null")
            changeState(OtaState.ERROR)
        }
    }

    private fun enableNotifications() {
        gatt?.let { gatt ->
            val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
            
            // 启用控制点通知
            val controlChar = service?.getCharacteristic(TiUuidInfo.IMAGE_CONTROL_POINT_UUID)
            controlChar?.let { char ->
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(TiUuidInfo.NOTIFICATION_DESCRIPTION)
                descriptor?.let { desc ->
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)

                    // Start image update request
                    handler.postDelayed({
                        val imageChar = service?.getCharacteristic(TiUuidInfo.IMAGE_IDENTIFY_WRITE_UUID)

                        imageChar?.let { char ->
                            gatt.setCharacteristicNotification(char, true)

                            // Enable notifications by writing to CCCD
                            val descriptor = char.getDescriptor(TiUuidInfo.NOTIFICATION_DESCRIPTION)
                            descriptor?.let { desc ->
                                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(desc)

                                // Start image update request
                                handler.postDelayed({
                                    changeState(OtaState.GETTING_SW_VERSION)
                                }, 500)
                            }
                        }
                    }, 500)
                }
            }
        }
    }



    private fun getSoftwareVersion() {
        Log.d(TAG, "Getting software version")
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
        Log.d(TAG, "Getting block size")
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
        
        Log.d(TAG, "Sending image identify")
        fwImageByteArray?.let { imageBytes ->
            gatt?.let { gatt ->
                val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_IDENTIFY_WRITE_UUID)
                
                characteristic?.let { char ->
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
        if (currentState == OtaState.SENDING_IMAGE_IDENTIFY) {
            changeState(OtaState.TRANSFERRING_BLOCKS)
        }
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
        Log.d(TAG, "Starting image transfer")
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
        Log.d(TAG, "Enabling image")
        gatt?.let { gatt ->
            val service = gatt.getService(TiUuidInfo.OAD_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(TiUuidInfo.IMAGE_CONTROL_POINT_UUID)
            
            characteristic?.let { char ->
                val cmd = byteArrayOf(OadEvent.OAD_EVT_ENABLE_IMG.toByte())
                char.value = cmd
                gatt.writeCharacteristic(char)
                
                Log.d(TAG, "Enable image command sent, waiting for device reset")
                setProgress(0.98f)
            }
        }
    }

    private fun handleNotification(data: ByteArray) {
        if (data.isEmpty()) return
        
        val opCode = data[0].toInt() and 0xFF
        Log.d(TAG, "Notification OpCode: 0x${opCode.toString(16)}, Current state: $currentState")
        
        when (opCode) {
            OadProtocolOpCode.OAD_REQ_GET_SW_VER -> {
                handleSoftwareVersionResponse(data)
            }
            
            OadProtocolOpCode.OAD_REQ_GET_BLK_SZ -> {
                handleBlockSizeResponse(data)
            }
            
            OadProtocolOpCode.OAD_RSP_BLK_RSP_NOTIF -> {
                handleBlockResponse(data)
            }
            
            OadEvent.OAD_EVT_ENABLE_IMG -> {
                Log.d(TAG, "Image enabled, device will reset")
                if (currentState == OtaState.ENABLING_IMAGE) {
                    setProgress(0.98f)
                    // 设备将会断开连接并重启，在 onConnectionStateChange 中处理完成状态
                }
            }
        }
    }

    private fun handleSoftwareVersionResponse(data: ByteArray) {
        if (currentState != OtaState.GETTING_SW_VERSION) return
        
        if (data.size >= 5) {
            val major = data[1].toInt() and 0xFF
            val minor = data[2].toInt() and 0xFF
            val revisionHi = data[3].toInt() and 0xFF
            val revisionLow = data[4].toInt() and 0xFF
            val buildHi = if (data.size >= 6) data[5].toInt() and 0xFF else 0
            val buildLow = if (data.size >= 7) data[6].toInt() and 0xFF else 0
            
            val swVersion = "$major.$minor.${(revisionHi + revisionLow) shl 8}.${buildHi + (buildLow shl 8)}"
            Log.d(TAG, "Current SW Version: $swVersion")
            
            firmware?.let { fw ->
                Log.d(TAG, "New firmware version: ${fw.version}")
            }
        }
        
        // 继续获取块大小
        changeState(OtaState.GETTING_BLOCK_SIZE)
    }

    private fun handleBlockSizeResponse(data: ByteArray) {
        if (currentState != OtaState.GETTING_BLOCK_SIZE) return
        
        if (data.size >= 3) {
            blockSize = (data[1].toInt() and 0xFF) + ((data[2].toInt() and 0xFF) shl 8) - 4
            Log.d(TAG, "Block size: $blockSize")
            
            fwImageByteArray?.let { imageBytes ->
                imageLength = imageBytes.size
                numBlocks = imageLength / blockSize
                Log.d(TAG, "Image length: $imageLength, num blocks: $numBlocks")
            }
            
            // 继续发送图像识别
            changeState(OtaState.SENDING_IMAGE_IDENTIFY)
        }
    }

    private fun handleBlockResponse(data: ByteArray) {
        if (currentState != OtaState.TRANSFERRING_BLOCKS) return
        
        if (data.size >= 6) {
            val status = data[1].toInt() and 0xFF
            val blockRequested = (data[2].toInt() and 0xFF) +
                               ((data[3].toInt() and 0xFF) shl 8) +
                               ((data[4].toInt() and 0xFF) shl 16) +
                               ((data[5].toInt() and 0xFF) shl 24)
            
            Log.d(TAG, "Block requested: $blockRequested/$numBlocks, status: $status")
            
            when (status) {
                OadStatus.OAD_PROFILE_DL_COMPLETE -> {
                    Log.d(TAG, "Download complete")
                    changeState(OtaState.ENABLING_IMAGE)
                }
                
                OadStatus.OAD_PROFILE_SUCCESS -> {
                    currentBlockRequested = blockRequested
                    
                    // Update progress
                    val progress = 0.98f * (blockRequested.toFloat() / numBlocks)
                    setProgress(progress)
                    
                    // Send next block
                    sendNextBlock(blockRequested)
                }
                
                else -> {
                    Log.e(TAG, "Block request failed with status: $status")
                    changeState(OtaState.ERROR)
                }
            }
        }
    }

    private fun sendNextBlock(blockNumber: Int) {
        fwImageByteArray?.let { imageBytes ->
            val offset = blockNumber * blockSize
            
            // Handle last block
            val actualBlockSize = if (blockNumber == numBlocks) {
                imageBytes.size - blockSize * numBlocks
            } else {
                blockSize
            }
            
            if (offset + actualBlockSize <= imageBytes.size) {
                val blockData = imageBytes.sliceArray(offset until offset + actualBlockSize)
                writeBlock(blockNumber, blockData)
            }
        }
    }
}