package com.kingzcheung.xime.settings

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 个人词库导出：当前方案的 `*.userdb.txt`（打包为 zip 写入 Downloads）。
 * 导入仍走 [UserDictImporter]。
 */
object UserDictManager {
    private const val TAG = "UserDictManager"

    data class ExportResult(
        val fileName: String,
        val savedToDownloads: Boolean,
    )

    fun hasExportableUserDb(context: Context, schemaId: String): Boolean =
        UserDictImporter.findUserDbFile(context, schemaId)?.let { it.exists() && it.length() > 0L } == true

    /**
     * 导出当前方案 `schemaId.userdb.txt` 为 zip，保存到 Downloads。
     * 文件名形如 `pinyin_simp.userdb-2026-09-14.zip`。
     */
    fun exportSchemaUserDb(context: Context, schemaId: String): Result<ExportResult> {
        val source = UserDictImporter.findUserDbFile(context, schemaId)
            ?.takeIf { it.exists() && it.length() > 0L }
            ?: return Result.failure(IllegalStateException("当前方案没有可导出的 *.userdb.txt"))
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val entryName = "$schemaId.userdb.txt"
            val fileName = "$schemaId.userdb-$date.zip"
            val tempZip = File(context.cacheDir, fileName)
            ZipOutputStream(tempZip.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry(entryName))
                source.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
            val saved = saveToDownloads(context, tempZip, fileName)
            tempZip.delete()
            if (!saved) {
                return Result.failure(IllegalStateException("写入 Downloads 失败"))
            }
            Result.success(ExportResult(fileName = fileName, savedToDownloads = true))
        } catch (e: Exception) {
            Log.e(TAG, "exportSchemaUserDb failed", e)
            Result.failure(e)
        }
    }

    private fun saveToDownloads(context: Context, zipFile: File, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    zipFile.inputStream().use { it.copyTo(out) }
                } ?: return false
                true
            } else {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloads.mkdirs()
                zipFile.copyTo(File(downloads, fileName), overwrite = true)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveToDownloads failed", e)
            false
        }
    }
}
