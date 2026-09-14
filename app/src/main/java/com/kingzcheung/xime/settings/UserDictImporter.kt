package com.kingzcheung.xime.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 个人词库（Rime `*.userdb.txt` / sync 快照）独立导入。
 * 与「输入方案」包分离：方案 zip 不再自动合并 userdb。
 *
 * 亦支持 `user_*.dict.yaml`（如 pinyin_simp 的 user_simp），写入用户目录后需部署。
 */
object UserDictImporter {
    private const val TAG = "UserDictImporter"

    data class Result(
        val success: Boolean,
        val fileCount: Int = 0,
        val synced: Boolean = false,
        val message: String = "",
        /** 已写入的 userdb 对应 schema_id（文件名去掉 .userdb.txt）。 */
        val schemaIds: List<String> = emptyList(),
        /** 已写入的 packs 个人词库名（如 user_simp）。 */
        val packNames: List<String> = emptyList(),
    )

    fun isUserDbFileName(name: String): Boolean =
        name.substringAfterLast('/').endsWith(".userdb.txt", ignoreCase = true)

    fun isUserPackDictFileName(name: String): Boolean {
        val base = name.substringAfterLast('/')
        return base.startsWith("user_", ignoreCase = true) &&
            base.endsWith(".dict.yaml", ignoreCase = true)
    }

    /** 压缩包里是否含 userdb/个人 pack，且不含方案（便于自动路由到个人词库导入）。 */
    fun looksLikeUserDictOnlyArchive(entryNames: List<String>): Boolean {
        val files = entryNames.map { it.substringAfterLast('/') }.filter { it.isNotBlank() }
        if (files.isEmpty()) return false
        val hasUserDb = files.any { isUserDbFileName(it) || isUserPackDictFileName(it) }
        val hasSchema = files.any { it.endsWith(".schema.yaml", ignoreCase = true) }
        return hasUserDb && !hasSchema
    }

    suspend fun importUri(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context, uri)
            ?: return@withContext Result(false, message = "无法识别文件名")
        val name = SchemaManager.sanitizeDisplayName(displayName)
        val stream = when (uri.scheme) {
            "file" -> FileInputStream(uri.path!!)
            else -> context.contentResolver.openInputStream(uri)
        } ?: return@withContext Result(false, message = "无法读取文件")

        stream.use { input ->
            when {
                name.endsWith(".zip", ignoreCase = true) -> {
                    val (schemaIds, packNames) = extractUserDictZip(context, input)
                    if (schemaIds.isEmpty() && packNames.isEmpty()) {
                        Result(false, message = "压缩包中没有 *.userdb.txt 或 user_*.dict.yaml")
                    } else {
                        finalizeImport(context, schemaIds, packNames)
                    }
                }
                isUserDbFileName(name) -> {
                    val base = name.substringAfterLast('/')
                    writeUserDbFile(context, base, input)
                    finalizeImport(context, listOf(schemaIdFromUserDbFileName(base)), emptyList())
                }
                isUserPackDictFileName(name) -> {
                    val base = name.substringAfterLast('/')
                    writeUserPackDict(context, base, input)
                    finalizeImport(context, emptyList(), listOf(packNameFromDictFileName(base)))
                }
                else -> Result(
                    false,
                    message = "请选择 *.userdb.txt、user_*.dict.yaml，或仅含个人词库的 zip"
                )
            }
        }
    }

    fun schemaIdFromUserDbFileName(fileName: String): String =
        fileName.substringAfterLast('/')
            .removeSuffix(".userdb.txt")
            .removeSuffix(".Userdb.Txt")
            .removeSuffix(".USERDB.TXT")

    fun packNameFromDictFileName(fileName: String): String =
        fileName.substringAfterLast('/')
            .removeSuffix(".dict.yaml")
            .removeSuffix(".Dict.Yaml")
            .removeSuffix(".DICT.YAML")

    /** 预览副本目录：sync 合并后原始快照可能被清理，浏览仍读这里。 */
    private fun previewDir(rimeDir: File): File =
        File(rimeDir, "userdb_preview").also { it.mkdirs() }

    private fun mirrorForBrowse(rimeDir: File, fileName: String, source: File) {
        runCatching {
            val dest = File(previewDir(rimeDir), fileName)
            if (source.canonicalPath == dest.canonicalPath) return
            source.copyTo(dest, overwrite = true)
        }.onFailure { Log.w(TAG, "mirrorForBrowse failed: ${it.message}") }
    }

    /** 当前设备上能找到的 *.userdb.txt 对应 schema_id（preview + 任意 sync 快照）。 */
    fun listSyncedUserDbSchemaIds(context: Context): List<String> {
        val rimeDir = SchemaManager.getRimeDir(context)
        val names = linkedSetOf<String>()
        fun collect(dir: File?) {
            if (dir == null || !dir.isDirectory) return
            dir.listFiles()
                ?.filter { it.isFile && isUserDbFileName(it.name) }
                ?.forEach { names += schemaIdFromUserDbFileName(it.name) }
        }
        collect(previewDir(rimeDir))
        val syncRoot = File(rimeDir, "sync")
        if (syncRoot.isDirectory) {
            syncRoot.walkTopDown()
                .maxDepth(2)
                .filter { it.isFile && isUserDbFileName(it.name) }
                .forEach { names += schemaIdFromUserDbFileName(it.name) }
        }
        return names.sorted()
    }

    /** 定位某方案的 userdb 快照：优先预览副本，再本机 sync，再其它 sync 目录。 */
    fun findUserDbFile(context: Context, schemaId: String): File? {
        val rimeDir = SchemaManager.getRimeDir(context)
        val fileName = "$schemaId.userdb.txt"
        val preview = File(previewDir(rimeDir), fileName)
        if (preview.exists() && preview.length() > 0L) return preview
        val installationId = SchemaManager.ensureInstallationId(rimeDir)
        val primary = File(rimeDir, "sync/$installationId/$fileName")
        if (primary.exists() && primary.length() > 0L) return primary
        val syncRoot = File(rimeDir, "sync")
        if (!syncRoot.isDirectory) return null
        return syncRoot.walkTopDown()
            .firstOrNull { it.isFile && it.name.equals(fileName, ignoreCase = true) && it.length() > 0L }
    }

    private fun writeUserDbFile(context: Context, fileName: String, input: InputStream) {
        val rimeDir = SchemaManager.getRimeDir(context)
        val installationId = SchemaManager.ensureInstallationId(rimeDir)
        val destDir = File(rimeDir, "sync/$installationId").also { it.mkdirs() }
        val dest = File(destDir, fileName)
        dest.outputStream().use { out -> input.copyTo(out) }
        mirrorForBrowse(rimeDir, fileName, dest)
        FileLogger.i(TAG, "Imported userdb $fileName -> sync/$installationId/")
    }

    private fun writeUserPackDict(context: Context, fileName: String, input: InputStream) {
        val rimeDir = SchemaManager.getRimeDir(context)
        val dest = File(rimeDir, fileName)
        dest.outputStream().use { out -> input.copyTo(out) }
        FileLogger.i(TAG, "Imported pack dict $fileName -> ${dest.absolutePath}")
    }

    private data class ExtractedUserDict(
        val schemaIds: List<String>,
        val packNames: List<String>,
    )

    private fun extractUserDictZip(context: Context, input: InputStream): ExtractedUserDict {
        val rimeDir = SchemaManager.getRimeDir(context)
        val tmp = File.createTempFile("userdict_", ".zip", context.cacheDir)
        try {
            tmp.outputStream().use { out -> input.copyTo(out) }
            val installationId = SchemaManager.ensureInstallationId(rimeDir)
            val destDir = File(rimeDir, "sync/$installationId").also { it.mkdirs() }
            val schemaIds = mutableListOf<String>()
            val packNames = mutableListOf<String>()
            ZipInputStream(tmp.inputStream().buffered()).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val base = e.name.substringAfterLast('/')
                    if (!e.isDirectory) {
                        when {
                            isUserDbFileName(base) -> {
                                val dest = File(destDir, base)
                                dest.outputStream().use { out -> zis.copyTo(out) }
                                mirrorForBrowse(rimeDir, base, dest)
                                schemaIds += schemaIdFromUserDbFileName(base)
                                FileLogger.i(TAG, "Imported userdb $base -> sync/$installationId/")
                            }
                            isUserPackDictFileName(base) -> {
                                val dest = File(rimeDir, base)
                                dest.outputStream().use { out -> zis.copyTo(out) }
                                packNames += packNameFromDictFileName(base)
                                FileLogger.i(TAG, "Imported pack dict $base")
                            }
                        }
                    }
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }
            return ExtractedUserDict(schemaIds.distinct(), packNames.distinct())
        } finally {
            tmp.delete()
        }
    }

    private fun finalizeImport(
        context: Context,
        schemaIds: List<String>,
        packNames: List<String>,
    ): Result {
        if (schemaIds.isNotEmpty()) {
            SchemaManager.processImportedUserDbFiles(context)
            val rimeDir = SchemaManager.getRimeDir(context)
            for (id in schemaIds) {
                findUserDbFile(context, id)?.let { mirrorForBrowse(rimeDir, "$id.userdb.txt", it) }
            }
        }
        val synced = if (schemaIds.isEmpty()) {
            false
        } else {
            runCatching {
                if (RimeEngine.isInitialized()) RimeEngine.getInstance().syncUserData()
                else false
            }.getOrDefault(false)
        }
        val parts = mutableListOf<String>()
        if (schemaIds.isNotEmpty()) {
            parts += if (synced) {
                "用户词典 ${schemaIds.size} 个已合并（${schemaIds.joinToString("、")}）"
            } else {
                "用户词典 ${schemaIds.size} 个（${schemaIds.joinToString("、")}）已写入，请部署后合并"
            }
        }
        if (packNames.isNotEmpty()) {
            parts += "个人词库 pack ${packNames.joinToString("、")} 已写入，请到输入方案页点「部署」"
        }
        return Result(
            success = true,
            fileCount = schemaIds.size + packNames.size,
            synced = synced,
            schemaIds = schemaIds,
            packNames = packNames,
            message = parts.joinToString("；"),
        )
    }

    /** 解析 userdb.txt 文本行，供词库页预览。 */
    fun parseUserDbEntries(text: String, limit: Int = 50_000): List<DictEntry> {
        val out = ArrayList<DictEntry>(minOf(limit, 1024))
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isEmpty() || line.startsWith('#')) continue
            val parts = line.split('\t')
            if (parts.size < 2) continue
            val code = parts[0].trim()
            val word = parts[1].trim()
            if (code.isEmpty() || word.isEmpty()) continue
            out.add(DictEntry(word, code))
            if (out.size >= limit) break
        }
        return out
    }

    fun loadSyncedUserDbEntries(context: Context, schemaId: String, limit: Int = 50_000): List<DictEntry> {
        val file = findUserDbFile(context, schemaId) ?: return emptyList()
        return try {
            parseUserDbEntries(file.readText(Charsets.UTF_8), limit)
        } catch (e: Exception) {
            Log.w(TAG, "loadSyncedUserDbEntries failed", e)
            emptyList()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment
    }
}
