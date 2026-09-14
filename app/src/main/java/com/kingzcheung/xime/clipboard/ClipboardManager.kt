package com.kingzcheung.xime.clipboard

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.kingzcheung.xime.clipboard.db.ClipboardDatabase
import com.kingzcheung.xime.clipboard.db.ClipboardEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import android.content.ClipboardManager as AndroidClipboardManager

data class ClipboardItem(
    val id: Long = 0,
    val text: String,
    /** 快捷发送触发编码（如 dh），空 = 仅内容命中不参与编码匹配。 */
    val code: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isQuickSend: Boolean = false,
    val consumed: Boolean = false
)

class ClipboardManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ClipboardManager"
        private const val MAX_ITEMS = 1000
        private const val MAX_QUICK_SEND_ITEMS = 20
        private const val PREFS_NAME = "clipboard_prefs"
        private const val KEY_CLIPBOARD_ITEMS = "clipboard_items"
        private const val KEY_QUICK_SEND_ITEMS = "quick_send_items"

        @Volatile
        private var instance: ClipboardManager? = null

        fun getInstance(context: Context): ClipboardManager {
            return instance ?: synchronized(this) {
                instance ?: ClipboardManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val androidClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager

    private val clipboardListener = AndroidClipboardManager.OnPrimaryClipChangedListener {
        readClipboard()
    }

    private fun readClipboard(retries: Int = 3) {
        try {
            val clipData = androidClipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val text = when {
                    item.text != null -> item.text.toString()
                    item.uri != null -> item.uri.toString()
                    item.intent != null -> item.intent.toUri(0)
                    else -> null
                }
                if (!text.isNullOrEmpty()) {
                    addItem(text)
                    return
                }
            }
            if (retries > 0) {
                Handler(Looper.getMainLooper()).postDelayed({ readClipboard(retries - 1) }, 100L)
            } else {
                Log.w(TAG, "Failed to read clipboard after all retries")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read clipboard: missing permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error reading clipboard", e)
        }
    }

    private val database = ClipboardDatabase.getInstance(context)
    private val dao = database.clipboardDao()
    private val scope = ClipboardDatabase.scope()

    private val _clipboardItems = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val clipboardItems: StateFlow<List<ClipboardItem>> = _clipboardItems.asStateFlow()

    private val _quickSendItems = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val quickSendItems: StateFlow<List<ClipboardItem>> = _quickSendItems.asStateFlow()

    private val _recentItems = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val recentItems: StateFlow<List<ClipboardItem>> = _recentItems.asStateFlow()

    /** 本地剪贴板变更事件流（新增/更新条目时发射，供剪贴板同步等外部消费）。 */
    private val _clipboardChanged = MutableSharedFlow<ClipboardItem>(extraBufferCapacity = 16)
    val clipboardChanged: SharedFlow<ClipboardItem> = _clipboardChanged.asSharedFlow()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyData()
        scope.launch {
            dao.observeAll().collect { entries ->
                _clipboardItems.value = entries.map { it.toClipboardItem() }
                updateRecentItems()
            }
        }
        scope.launch {
            dao.observeQuickSend().collect { entries ->
                _quickSendItems.value = entries.map { it.toClipboardItem() }
            }
        }
        startListening()
    }

    /**
     * 将旧版 SharedPreferences 中的剪贴板/快捷发送数据一次性迁移到 Room。
     * 幂等：prefs 无数据时直接返回；迁移成功后删除 prefs 键，避免重复迁移。
     */
    fun migrateLegacyData() {
        val legacyClipboard = prefs.getString(KEY_CLIPBOARD_ITEMS, null)
        val legacyQuickSend = prefs.getString(KEY_QUICK_SEND_ITEMS, null)
        if (legacyClipboard == null && legacyQuickSend == null) return
        scope.launch {
            try {
                val entries = mutableListOf<ClipboardEntry>()
                legacyClipboard?.let { str ->
                    deserializeItems(str).forEach { item ->
                        entries.add(item.toEntry())
                    }
                }
                legacyQuickSend?.let { str ->
                    deserializeItems(str).forEach { item ->
                        entries.add(item.toEntry())
                    }
                }
                if (entries.isNotEmpty()) {
                    dao.insertAll(entries)
                }
                prefs.edit()
                    .remove(KEY_CLIPBOARD_ITEMS)
                    .remove(KEY_QUICK_SEND_ITEMS)
                    .apply()
                Log.i(TAG, "Migrated ${entries.size} legacy clipboard items to Room")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to migrate legacy clipboard items", e)
            }
        }
    }

    private fun updateRecentItems() {
        val now = System.currentTimeMillis()
        val cutoff = now - 10 * 1000L
        _recentItems.value = _clipboardItems.value.filter { it.timestamp >= cutoff }
    }

    private fun deserializeItems(json: String): List<ClipboardItem> {
        if (json.isEmpty()) return emptyList()
        return json.split("|||").mapNotNull { itemStr ->
            val parts = itemStr.split(":::")
            if (parts.size == 5) {
                try {
                    ClipboardItem(
                        id = parts[0].toLong(),
                        text = parts[1].unescape(),
                        timestamp = parts[2].toLong(),
                        isPinned = parts[3].toBoolean(),
                        isQuickSend = parts[4].toBoolean()
                    )
                } catch (e: Exception) {
                    null
                }
            } else if (parts.size == 4) {
                try {
                    ClipboardItem(
                        id = parts[0].toLong(),
                        text = parts[1].unescape(),
                        timestamp = parts[2].toLong(),
                        isPinned = parts[3].toBoolean(),
                        isQuickSend = false
                    )
                } catch (e: Exception) {
                    null
                }
            } else null
        }
    }

    private fun String.unescape(): String {
        return this.replace("〈PIPE〉", "|||").replace("〈COLON〉", ":::")
    }

    private fun ClipboardItem.toEntry(): ClipboardEntry {
        return ClipboardEntry(
            id = 0,
            text = text,
            timestamp = timestamp,
            isPinned = isPinned,
            isQuickSend = isQuickSend,
            consumed = consumed
        )
    }

    private fun ClipboardEntry.toClipboardItem(): ClipboardItem {
        return ClipboardItem(
            id = id,
            text = text,
            code = code,
            timestamp = timestamp,
            isPinned = isPinned,
            isQuickSend = isQuickSend,
            consumed = consumed
        )
    }

    private fun startListening() {
        androidClipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    fun release() {
        // Singleton — no cleanup needed.
    }

    fun addItem(text: String) {
        if (text.isBlank()) return
        scope.launch {
            dao.upsertAndTrim(text, System.currentTimeMillis(), MAX_ITEMS)
            _clipboardChanged.emit(
                ClipboardItem(
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeItem(id: Long) {
        scope.launch {
            dao.deleteClipboardById(id)
        }
    }

    /** 批量删除剪贴板条目（仅 isQuickSend = 0，不影响快捷发送）。 */
    fun removeItems(ids: List<Long>) {
        if (ids.isEmpty()) return
        scope.launch {
            dao.deleteClipboardByIds(ids)
        }
    }

    /** 清空剪贴板（仅 isQuickSend = 0，不影响快捷发送）。 */
    fun clearClipboard() {
        scope.launch {
            dao.clearAllClipboard()
        }
    }

    fun splitItem(id: Long) {
        scope.launch {
            val item = _clipboardItems.value.find { it.id == id } ?: return@launch
            dao.deleteClipboardById(id)
            val now = System.currentTimeMillis()
            item.text.forEachIndexed { index, char ->
                dao.insert(
                    ClipboardEntry(
                        text = char.toString(),
                        timestamp = now + index
                    )
                )
            }
        }
    }

    fun clearAll() {
        scope.launch {
            dao.clearUnpinned()
        }
    }

    fun addToQuickSend(id: Long) {
        scope.launch {
            dao.addQuickSend(id, System.currentTimeMillis(), MAX_QUICK_SEND_ITEMS)
        }
    }

    fun removeFromQuickSend(id: Long) {
        scope.launch {
            dao.deleteQuickSendById(id)
        }
    }

    fun togglePinQuickSend(id: Long) {
        scope.launch {
            dao.updateTimestamp(id, System.currentTimeMillis())
        }
    }

    fun updateQuickSendItem(id: Long, newText: String, newCode: String = ""): Boolean {
        if (newText.isBlank()) return false
        val index = _quickSendItems.value.indexOfFirst { it.id == id }
        if (index < 0) return false
        scope.launch {
            dao.updateQuickSendItem(id, newText, newCode.trim(), System.currentTimeMillis())
        }
        return true
    }

    fun addQuickSendItem(text: String, code: String = "") {
        if (text.isBlank()) return
        scope.launch {
            dao.insertQuickSend(text, code.trim(), System.currentTimeMillis(), MAX_QUICK_SEND_ITEMS)
        }
    }

    fun copyToSystemClipboard(text: String) {
        val clip = ClipData.newPlainText("kime_clipboard", text)
        androidClipboardManager.setPrimaryClip(clip)
    }

    fun getCurrentClipboardText(): String? {
        val clipData = androidClipboardManager.primaryClip
        return if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0).text?.toString()
        } else null
    }

    fun getRecentItems(seconds: Int = 30): List<ClipboardItem> {
        val now = System.currentTimeMillis()
        val cutoff = now - seconds * 1000L
        // 候选栏只展示未消费的最近剪贴板项（用户点选上屏后标记 consumed 不再显示）
        return _clipboardItems.value.filter { it.timestamp >= cutoff && !it.consumed }
    }

    /**
     * 标记指定文本的剪贴板条目为"已消费"（候选栏不再显示）。
     * 匹配最近一条未消费的相同文本，避免影响历史重复条目。
     */
    fun markConsumed(text: String) {
        scope.launch {
            val item = _clipboardItems.value
                .filter { it.text == text && !it.consumed }
                .maxByOrNull { it.timestamp } ?: return@launch
            dao.markConsumed(item.id)
        }
    }

    fun copyImageToSystemClipboard(imagePath: String, label: String = "emoji_image"): Boolean {
        return try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file not found: $imagePath")
                return false
            }

            val mime = when (imageFile.extension.lowercase()) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "image/png"
            }

            val cacheDir = File(context.cacheDir, "emoji_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val cacheFile = File(cacheDir, imageFile.name)
            FileInputStream(imageFile).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val uri = getContentUriForImage(cacheFile, mime) ?: return false

            val clip = ClipData(
                android.content.ClipDescription(label, arrayOf(mime)),
                ClipData.Item(uri)
            )
            androidClipboardManager.setPrimaryClip(clip)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to clipboard", e)
            false
        }
    }

    /**
     * 生成图片 content URI。
     *
     * 优先使用 FileProvider；Android 12+ 部分厂商 ROM 上
     * FileProvider.getUriForFile 内部 resolveContentProvider 以 USER_ALL(-10000)
     * 校验跨用户权限时抛 "Invalid userId -10000"，此时降级为 MediaStore
     * 插入图片获取系统 content URI（API 29+ 无需权限）。
     */
    private fun getContentUriForImage(imageFile: File, mimeType: String): Uri? {
        try {
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            Log.w(TAG, "FileProvider getUriForFile failed, falling back to MediaStore", e)
        }
        return insertImageToMediaStore(imageFile, mimeType)
    }

    /** 把图片插入 MediaStore（Downloads/Xime），返回系统 content URI。 */
    private fun insertImageToMediaStore(imageFile: File, mimeType: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.e(TAG, "MediaStore fallback requires API 29+, clipboard image copy failed")
            return null
        }
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, imageFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Xime")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            // Files 通道保留原始 MIME，避免 Images 集合把 GIF 当静图
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(imageFile).use { input -> input.copyTo(output) }
                } ?: return null
                val update = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, update, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore insert failed", e)
            null
        }
    }
}
