package com.kingzcheung.xime.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户收藏表情包（仿微信表情收藏）：本地文件 + JSON 索引。
 * 列表顺序即展示顺序，「移到前面」= 挪到索引 0。
 */
object FavoriteStickersStore {
    private const val TAG = "FavoriteStickers"
    private const val DIR_NAME = "favorite_stickers"
    private const val INDEX_NAME = "index.json"
    private const val MAX_COUNT = 200
    /** 非动图转存时最长边上限（收藏表情原始规格）。 */
    private const val MAX_EDGE_PX = 512
    /** 静态图可原样保留的体积上限。 */
    private const val MAX_COPY_BYTES = 2 * 1024 * 1024
    /** 动图（GIF / 动画 WebP）原样保留上限；超过则跳过，避免压成静帧。 */
    private const val MAX_ANIM_BYTES = 12 * 1024 * 1024

    const val STICKER_MAX_EDGE_PX = MAX_EDGE_PX

    /** 表情面板子分类名（与 EmojiCategory.name 对齐）。 */
    const val CATEGORY_NAME = "收藏"
    const val CATEGORY_ICON = "⭐"

    private const val FORMAT_ID = "xime-favorite-stickers"
    private const val FORMAT_VERSION = 1
    private const val BUNDLE_INDEX = "index.json"

    data class Sticker(
        val id: String,
        val fileName: String,
        val mime: String,
    )

    private val _stickers = MutableStateFlow<List<Sticker>>(emptyList())
    val stickersFlow: StateFlow<List<Sticker>> = _stickers.asStateFlow()

    @Volatile
    private var loaded = false

    fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).also { if (!it.exists()) it.mkdirs() }

    fun fileFor(context: Context, sticker: Sticker): File =
        File(dir(context), sticker.fileName)

    fun absolutePath(context: Context, sticker: Sticker): String =
        fileFor(context, sticker).absolutePath

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _stickers.value = readIndex(context)
            loaded = true
        }
    }

    fun refresh(context: Context) {
        _stickers.value = readIndex(context)
        loaded = true
    }

    fun moveToFront(context: Context, id: String) {
        val list = _stickers.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx <= 0) return
        val item = list.removeAt(idx)
        list.add(0, item)
        writeIndex(context, list)
        _stickers.value = list
    }

    /** 拖拽排序：将 [fromIndex] 移到 [toIndex]（均按当前列表下标）。 */
    fun move(context: Context, fromIndex: Int, toIndex: Int) {
        val list = _stickers.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        writeIndex(context, list)
        _stickers.value = list
    }

    /** 用完整有序列表覆盖（设置页拖拽结束时写入）。 */
    fun replaceOrder(context: Context, ordered: List<Sticker>) {
        writeIndex(context, ordered)
        _stickers.value = ordered
    }

    fun delete(context: Context, id: String) {
        val list = _stickers.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val removed = list.removeAt(idx)
        runCatching { fileFor(context, removed).delete() }
        writeIndex(context, list)
        _stickers.value = list
    }

    /**
     * 从内容 URI 批量导入。支持常见位图；HEIC/BMP 等解码后转 PNG；
     * GIF/WebP 在体积允许时原样保留。
     * @return 成功导入数量
     */
    suspend fun importUris(context: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        if (uris.isEmpty()) return@withContext 0
        val current = _stickers.value.toMutableList()
        var added = 0
        for (uri in uris) {
            if (current.size >= MAX_COUNT) break
            val sticker = importOne(context, uri) ?: continue
            current.add(0, sticker) // 新图置顶，贴近「刚收藏」
            added++
        }
        if (added > 0) {
            // 超限裁掉最旧
            while (current.size > MAX_COUNT) {
                val old = current.removeAt(current.lastIndex)
                runCatching { fileFor(context, old).delete() }
            }
            writeIndex(context, current)
            _stickers.value = current
        }
        added
    }

    /** 裁剪/旋转后的位图入库（统一存 PNG）。 */
    suspend fun importBitmap(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        if (_stickers.value.size >= MAX_COUNT) return@withContext false
        val id = UUID.randomUUID().toString().replace("-", "")
        val scaled = scaleToMaxEdge(bitmap, MAX_EDGE_PX)
        val name = "$id.png"
        val out = File(dir(context), name)
        out.outputStream().use { os ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, os)
        }
        if (scaled !== bitmap) scaled.recycle()
        val sticker = Sticker(id, name, "image/png")
        val current = _stickers.value.toMutableList()
        current.add(0, sticker)
        while (current.size > MAX_COUNT) {
            val old = current.removeAt(current.lastIndex)
            runCatching { fileFor(context, old).delete() }
        }
        writeIndex(context, current)
        _stickers.value = current
        true
    }

    data class ImportResult(
        val count: Int,
        val skipped: Int = 0,
    )

    /** 导出全部收藏表情为 zip（含 index.json + 图片文件）。 */
    suspend fun exportBundleZip(context: Context): ByteArray = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val list = _stickers.value
        ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                val indexBytes = buildBundleIndexJson(list).toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry(BUNDLE_INDEX))
                zos.write(indexBytes)
                zos.closeEntry()
                for (sticker in list) {
                    val file = fileFor(context, sticker)
                    if (!file.exists()) continue
                    zos.putNextEntry(ZipEntry(sticker.fileName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            bos.toByteArray()
        }
    }

    /**
     * 从 zip 导入收藏表情。
     * @param replace true 整盘替换；false 合并（导入项置顶，同 id 以导入为准）
     */
    suspend fun importBundleZip(
        context: Context,
        bytes: ByteArray,
        replace: Boolean = true,
    ): ImportResult = withContext(Dispatchers.IO) {
        ensureLoaded(context)
        val entries = readZipEntries(bytes)
        val indexBytes = entries[BUNDLE_INDEX]
            ?: throw IllegalArgumentException("不是曦码收藏表情包（缺少 index.json）")
        val imported = parseBundleIndex(String(indexBytes, Charsets.UTF_8))
        if (imported.isEmpty()) {
            throw IllegalArgumentException("备份包中没有表情")
        }

        var skipped = 0
        val extracted = mutableListOf<Sticker>()
        for (sticker in imported) {
            if (!isSafeEntryName(sticker.fileName)) {
                skipped++
                continue
            }
            val data = entries[sticker.fileName]
            if (data == null) {
                skipped++
                continue
            }
            File(dir(context), sticker.fileName).writeBytes(data)
            extracted.add(sticker)
        }
        if (extracted.isEmpty()) {
            throw IllegalArgumentException("备份包中没有可用的表情文件")
        }

        if (replace) {
            for (old in _stickers.value) {
                runCatching { fileFor(context, old).delete() }
            }
            val trimmed = extracted.take(MAX_COUNT)
            if (trimmed.size < extracted.size) {
                skipped += extracted.size - trimmed.size
                for (drop in extracted.drop(MAX_COUNT)) {
                    runCatching { fileFor(context, drop).delete() }
                }
            }
            writeIndex(context, trimmed)
            _stickers.value = trimmed
            return@withContext ImportResult(count = trimmed.size, skipped = skipped)
        }

        val merged = _stickers.value.toMutableList()
        for (sticker in extracted.asReversed()) {
            merged.removeAll { it.id == sticker.id }
            merged.add(0, sticker)
        }
        while (merged.size > MAX_COUNT) {
            val old = merged.removeAt(merged.lastIndex)
            runCatching { fileFor(context, old).delete() }
            skipped++
        }
        writeIndex(context, merged)
        _stickers.value = merged
        ImportResult(count = extracted.size, skipped = skipped)
    }

    /** 是否为应跳过裁剪的动图（原样导入）。 */
    fun isAnimatedUri(context: Context, uri: Uri): Boolean {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
            val mime = sniffMime(bytes) ?: context.contentResolver.getType(uri)?.lowercase()
            mime == "image/gif" || (mime == "image/webp" && isAnimatedWebP(bytes))
        }.getOrDefault(false)
    }

    private fun importOne(context: Context, uri: Uri): Sticker? {
        return try {
            val resolver = context.contentResolver
            val id = UUID.randomUUID().toString().replace("-", "")
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.isEmpty()) return null

            // 以文件头为准，避免部分 ROM 把 GIF 标成 image/* / octet-stream 后被压成静帧
            val sniffed = sniffMime(bytes)
            val declared = resolver.getType(uri)?.lowercase()
                ?: guessMimeFromName(uri.lastPathSegment)
            val mime = when {
                sniffed != null -> sniffed
                declared == "image/jpg" -> "image/jpeg"
                else -> declared
            }

            when {
                // 动图：必须原样保留，绝不 decode→PNG（会丢掉动画）
                mime == "image/gif" || (mime == "image/webp" && isAnimatedWebP(bytes)) -> {
                    if (bytes.size > MAX_ANIM_BYTES) {
                        Log.w(TAG, "animated sticker too large (${bytes.size}): $uri")
                        return null
                    }
                    val ext = if (mime == "image/gif") "gif" else "webp"
                    val name = "$id.$ext"
                    File(dir(context), name).writeBytes(bytes)
                    Sticker(id, name, mime)
                }
                // 静态 WebP：体积可接受则原样，否则转 PNG
                mime == "image/webp" -> {
                    if (bytes.size <= MAX_COPY_BYTES) {
                        val name = "$id.webp"
                        File(dir(context), name).writeBytes(bytes)
                        Sticker(id, name, mime)
                    } else {
                        decodeAndSavePng(context, uri, bytes, id)
                    }
                }
                mime == "image/png" || mime == "image/jpeg" -> {
                    if (bytes.size <= MAX_COPY_BYTES && !needsDownscale(bytes)) {
                        val ext = if (mime == "image/png") "png" else "jpg"
                        val name = "$id.$ext"
                        File(dir(context), name).writeBytes(bytes)
                        Sticker(id, name, mime)
                    } else {
                        decodeAndSavePng(context, uri, bytes, id)
                    }
                }
                else -> decodeAndSavePng(context, uri, bytes, id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "importOne failed: $uri", e)
            null
        }
    }

    /** 根据魔数识别常见图片类型。 */
    private fun sniffMime(bytes: ByteArray): String? {
        if (bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()
        ) {
            return "image/gif"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        return null
    }

    /** WebP 含 ANIM chunk 则为动图。 */
    private fun isAnimatedWebP(bytes: ByteArray): Boolean {
        // RIFF....WEBP 之后扫描 'ANIM'
        var i = 12
        while (i + 8 <= bytes.size) {
            val tag0 = bytes[i].toInt().toChar()
            val tag1 = bytes[i + 1].toInt().toChar()
            val tag2 = bytes[i + 2].toInt().toChar()
            val tag3 = bytes[i + 3].toInt().toChar()
            val size = (bytes[i + 4].toInt() and 0xff) or
                ((bytes[i + 5].toInt() and 0xff) shl 8) or
                ((bytes[i + 6].toInt() and 0xff) shl 16) or
                ((bytes[i + 7].toInt() and 0xff) shl 24)
            if (tag0 == 'A' && tag1 == 'N' && tag2 == 'I' && tag3 == 'M') return true
            // chunk size 按偶数字节对齐
            val payload = size.coerceAtLeast(0)
            val step = 8 + payload + (payload and 1)
            if (step <= 0) break
            i += step
            if (i > bytes.size) break
        }
        return false
    }

    private fun needsDownscale(bytes: ByteArray): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        return w > MAX_EDGE_PX || h > MAX_EDGE_PX
    }

    private fun decodeAndSavePng(
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        id: String,
    ): Sticker? {
        val bitmap = decodeBitmap(context, uri, bytes) ?: return null
        val scaled = scaleToMaxEdge(bitmap, MAX_EDGE_PX)
        if (scaled !== bitmap) bitmap.recycle()
        val name = "$id.png"
        val out = File(dir(context), name)
        out.outputStream().use { os ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, os)
        }
        scaled.recycle()
        return Sticker(id, name, "image/png")
    }

    private fun decodeBitmap(context: Context, uri: Uri, bytes: ByteArray): Bitmap? {
        // 优先 ImageDecoder（HEIC 等）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.onFailure { Log.d(TAG, "ImageDecoder failed, fallback BitmapFactory", it) }
        }
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        val maxSide = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
        while (maxSide / sample > MAX_EDGE_PX * 2) sample *= 2
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode)
    }

    private fun scaleToMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxEdge) return src
        val scale = maxEdge.toFloat() / longest
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun guessMimeFromName(name: String?): String {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "heic", "heif" -> "image/heic"
                else -> "application/octet-stream"
            }
    }

    private fun indexFile(context: Context): File = File(dir(context), INDEX_NAME)

    private fun readIndex(context: Context): List<Sticker> {
        val file = indexFile(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    val fileName = o.getString("file")
                    val mime = o.optString("mime", "image/png")
                    if (File(dir(context), fileName).exists()) {
                        add(Sticker(id, fileName, mime))
                    }
                }
            }
        }.getOrElse {
            Log.w(TAG, "readIndex failed", it)
            emptyList()
        }
    }

    private fun writeIndex(context: Context, list: List<Sticker>) {
        indexFile(context).writeText(buildBundleIndexJson(list))
    }

    private fun buildBundleIndexJson(list: List<Sticker>): String {
        val root = JSONObject()
        root.put("format", FORMAT_ID)
        root.put("version", FORMAT_VERSION)
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("file", s.fileName)
                    .put("mime", s.mime)
            )
        }
        root.put("stickers", arr)
        return root.toString(2)
    }

    private fun parseBundleIndex(text: String): List<Sticker> {
        val root = JSONObject(text.trim().trimStart('\uFEFF'))
        val format = root.optString("format", "")
        if (format.isNotEmpty() && format != FORMAT_ID) {
            throw IllegalArgumentException("不是曦码收藏表情包（format=$format）")
        }
        val arr = root.optJSONArray("stickers") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val fileName = o.getString("file")
                val mime = o.optString("mime", "image/png")
                if (!isSafeEntryName(fileName)) continue
                add(Sticker(id, fileName, mime))
            }
        }
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    val name = entry.name.substringAfterLast('/')
                    if (isSafeEntryName(name)) {
                        map[name] = zis.readBytes()
                    }
                }
                zis.closeEntry()
            }
        }
        return map
    }

    private fun isSafeEntryName(name: String): Boolean {
        if (name.isEmpty() || name.contains("..")) return false
        if (name.contains('/') || name.contains('\\')) return false
        return true
    }
}
