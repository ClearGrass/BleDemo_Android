package com.cleargrass.lib.ti.ota

import android.content.Context
import java.io.File
import java.io.IOException

object FileUtils {

    /**
     * 从文件路径读取字节数组
     * @param context 上下文
     * @param filePath 文件路径
     * @return 字节数组，如果读取失败返回null
     */
    fun readFileToByteArray(context: Context, filePath: String): ByteArray? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return null
            }
            file.readBytes()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: SecurityException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从assets目录读取文件
     * @param context 上下文
     * @param assetFilePath assets中的文件路径
     * @return 字节数组，如果读取失败返回null
     */
    fun readAssetFileToByteArray(context: Context, assetFilePath: String): ByteArray? {
        return try {
            context.assets.open(assetFilePath).use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}