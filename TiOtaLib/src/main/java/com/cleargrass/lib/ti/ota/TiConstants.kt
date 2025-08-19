package com.cleargrass.lib.ti.ota

import java.io.Serializable
import java.util.UUID

class TI_OtaSetting : Serializable {
    /**
     * firmware data
     */
    var firmwareBytes: ByteArray? = null

    /**
     * firmware file path (alternative to firmwareBytes)
     */
    var firmwarePath: String? = null

    /**
     * selected serviceUUID for OTA
     */
    var serviceUUID: UUID? = TiUuidInfo.OAD_SERVICE_UUID


    /**
     * OTA timeout in milliseconds (default 5 minutes)
     */
    var timeout: Long = 5 * 60 * 1000L
}


object OadStatus {
    const val OAD_PROFILE_SUCCESS: Int = 0            // OAD succeeded.
    const val OAD_PROFILE_VALIDATION_ERR: Int = 1      // Downloaded image header doesn't match.
    const val OAD_PROFILE_FLASH_ERR: Int = 2           // Flash function failure (int, ext).
    const val OAD_PROFILE_BUFFER_OFL: Int = 3          // Block Number doesn't match requested.
    const val OAD_PROFILE_ALREADY_STARTED: Int = 4     // OAD is already in progress.
    const val OAD_PROFILE_NOT_STARTED: Int = 5         // OAD has not yet started.
    const val OAD_PROFILE_DL_NOT_COMPLETE: Int = 6     // An OAD is ongoing.
    const val OAD_PROFILE_NO_RESOURCES: Int = 7        // If memory allocation fails.
    const val OAD_PROFILE_IMAGE_TOO_BIG: Int = 8       // Candidate image is too big.
    const val OAD_PROFILE_INCOMPATIBLE_IMAGE: Int = 9  // Image signing failure, boundary mismatch.
    const val OAD_PROFILE_INVALID_FILE: Int = 10       // If Invalid image ID received.
    const val OAD_PROFILE_INCOMPATIBLE_FILE: Int = 11  // BIM/MCUBOOT or FW mismatch.
    const val OAD_PROFILE_AUTH_FAIL: Int = 12          // Authorization failed.
    const val OAD_PROFILE_EXT_NOT_SUPPORTED: Int = 13  // Ctrl point command not supported.
    const val OAD_PROFILE_DL_COMPLETE: Int = 14        // OAD image payload download complete.
    const val OAD_PROFILE_CCCD_NOT_ENABLED: Int = 15   // CCCD is not enabled, notif can't be sent.
    const val OAD_PROFILE_IMG_ID_TIMEOUT: Int = 16     // Image identify timed out, too many failures.
    const val OAD_PROFILE_APP_STOP_PROCESS: Int = 17   // Target app cancel oad
    const val OAD_PROFILE_ERROR: Int = 18              // General internal error of the module
}
/**
 * OAD Protocol OpCodes
 * Reference: https://software-dl.ti.com/lprf/simplelink_cc2640r2_latest/docs/blestack/ble_user_guide/html/oad-ble-stack-3.x/oad_profile.html#id7
 */
object OadProtocolOpCode {
    const val OAD_REQ_GET_BLK_SZ: Int = 0x01           // Get Block Size
    const val OAD_REQ_DISABLE_BLK_NOTIF: Int = 0x06    // Disable block notification
    const val OAD_REQ_GET_SW_VER: Int = 0x07           // Get software version
    const val OAD_REQ_GET_OAD_STAT: Int = 0x08         // Get OAD public state machine

    // These opcodes are not supported yet
    // const val OAD_REQ_GET_PROF_VER: Int = 0x09      // Get profile version
    // const val OAD_REQ_GET_DEV_TYPE: Int = 0x10      // Get device type
    // const val OAD_REQ_GET_IMG_INFO: Int = 0x11      // Get image info

    const val OAD_RSP_BLK_RSP_NOTIF: Int = 0x12        // Send block request
    const val OAD_REQ_ERASE_BONDS: Int = 0x13          // Erase bonds
    const val OAD_RSP_CMD_NOT_SUPPORTED: Int = 0xFF    // Command not supported error
}

/**
 * OAD Reset Service OpCodes
 */
object OadResetServiceOpCodes {
    const val OAD_RESET_CMD_START_OAD: Int = 0x01      // Start a new OAD operation
}

/**
 * OAD Events
 */
object OadEvent {
    const val OAD_EVT_IMG_IDENTIFY_REQ: Int = 0x00     // Image identify request
    const val OAD_EVT_BLOCK_REQ: Int = 0x01           // Block request
    const val OAD_EVT_TIMEOUT: Int = 0x02              // Timeout
    const val OAD_EVT_START_OAD: Int = 0x03            // Start OAD
    const val OAD_EVT_ENABLE_IMG: Int = 0x04           // Enable image
    const val OAD_EVT_CANCEL_OAD: Int = 0x05           // Cancel OAD
}