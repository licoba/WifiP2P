package github.leavesczy.wifip2p.utils

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object MyFileUtils {

    // 从 assets 目录读取文件并转换为字节流
    fun readFileFromAssets(context: Context, fileName: String): ByteArray? {
        val assetManager = context.assets
        var inputStream: InputStream? = null
        var byteArrayOutputStream: ByteArrayOutputStream? = null
        return try {
            inputStream = assetManager.open(fileName)
            byteArrayOutputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, length)
            }
            byteArrayOutputStream.toByteArray()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } finally {
            try {
                inputStream?.close()
                byteArrayOutputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }



    // 从 assets 目录读取文件并转换为 File 对象
    fun getFileFromAssets(context: Context, fileName: String, outputDir: File): File? {
        val byteArray = readFileFromAssets(context, fileName) ?: return null
        val outputFile = File(outputDir, fileName)
        return try {
            FileOutputStream(outputFile).use { fos ->
                fos.write(byteArray)
            }
            outputFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}