package com.kingzcheung.xime.service

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文本上屏与剪贴板提交。
 *
 * 承载 commitImage、剪贴板候选提交与语音撤销/搜索动作。
 * 共享状态通过 service 引用访问。
 */
internal class ImeTextCommit(private val service: XimeInputMethodService) {

    internal fun performUndo() {
        val currentTextBeforeCursor = service.currentInputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        val currentLength = currentTextBeforeCursor.length

        val charsToDelete = currentLength - service.voiceRecognitionHandler.textLengthBeforeVoiceInput

        if (charsToDelete > 0) {
            for (i in 0 until charsToDelete) {
                service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }

        service.voiceRecognitionHandler.textBeforeVoiceInput = ""
        service.voiceRecognitionHandler.textLengthBeforeVoiceInput = 0
    }

    internal fun performSearch() {
        service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        service.currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    /** 微信系包名：commitContent 收动图常变静帧，改走分享。 */
    internal fun isWeChatHost(packageName: String? = service.currentInputEditorInfo?.packageName): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName == "com.tencent.mm" ||
            packageName.startsWith("com.tencent.mm.")
    }

    /** 动图在微信中应优先分享发送（不走 commitContent）。 */
    internal fun shouldShareAnimatedInstead(imagePath: String): Boolean {
        val file = File(imagePath)
        if (!file.exists()) return false
        if (!isWeChatHost()) return false
        val mime = mimeTypeForImage(file, "image/jpeg")
        return isAnimatedMime(mime, file)
    }

    internal fun commitImage(imagePath: String, mimeType: String = "image/jpeg"): Boolean {
        return try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.e(XimeInputMethodService.TAG, "Image file not found: $imagePath")
                return false
            }

            val actualMimeType = mimeTypeForImage(imageFile, mimeType)
            val animated = isAnimatedMime(actualMimeType, imageFile)
            val editorInfo = service.currentInputEditorInfo ?: return false

            // 微信 commitContent 动图基本只会留首帧，这里直接失败交给上层走分享
            if (animated && isWeChatHost(editorInfo.packageName)) {
                Log.i(XimeInputMethodService.TAG, "WeChat animated: skip commitContent, use share")
                return false
            }

            val supportedMimeTypes = editorInfo.contentMimeTypes
            val declaredOk = supportsMimeType(supportedMimeTypes, actualMimeType)
            if (!declaredOk && !animated) {
                Log.i(
                    XimeInputMethodService.TAG,
                    "Host does not support image commit (contentMimeTypes=${supportedMimeTypes?.contentToString()})"
                )
                return false
            }
            if (!declaredOk && animated) {
                Log.i(
                    XimeInputMethodService.TAG,
                    "Host did not declare $actualMimeType, still trying commitContent to keep animation"
                )
            }

            val cacheFile = copyToEmojiCache(imageFile) ?: return false
            val sendMime = actualMimeType
            val uri = getContentUriForImage(cacheFile, sendMime) ?: return false

            val targetPackage = editorInfo.packageName
            if (!targetPackage.isNullOrBlank()) {
                runCatching {
                    service.grantUriPermission(
                        targetPackage,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            val ic = service.currentInputConnection ?: return false
            runCatching { ic.finishComposingText() }

            val descriptionMimes = buildMimeList(sendMime, supportedMimeTypes, animated)
            val inputContentInfo = InputContentInfoCompat(
                uri,
                android.content.ClipDescription("emoji_image", descriptionMimes),
                null
            )

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            } else {
                0
            }

            val ok = InputConnectionCompat.commitContent(ic, editorInfo, inputContentInfo, flags, null)
            Log.i(
                XimeInputMethodService.TAG,
                "commitImage ok=$ok mime=$sendMime animated=$animated pkg=$targetPackage " +
                    "declared=${supportedMimeTypes?.contentToString()} file=${cacheFile.name} bytes=${cacheFile.length()}"
            )
            ok
        } catch (e: Exception) {
            Log.e(XimeInputMethodService.TAG, "Failed to commit image", e)
            false
        }
    }

    /**
     * 将图片以 ACTION_SEND 分享给当前输入焦点所在应用（EditorInfo.packageName）。
     * 对方需提供接收 image 的分享入口；不是所有 IM 都会接。
     */
    internal fun shareImageToCurrentApp(imagePath: String, mimeType: String = "image/jpeg"): Boolean {
        return try {
            val targetPackage = service.currentInputEditorInfo?.packageName
            if (targetPackage.isNullOrBlank()) {
                Log.w(XimeInputMethodService.TAG, "shareImage: no target package")
                return false
            }
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.e(XimeInputMethodService.TAG, "Image file not found: $imagePath")
                return false
            }
            val actualMimeType = mimeTypeForImage(imageFile, mimeType)
            val cacheFile = copyToEmojiCache(imageFile) ?: return false
            val sendMime = actualMimeType
            val uri = getContentUriForImage(cacheFile, sendMime) ?: return false

            service.grantUriPermission(
                targetPackage,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            val send = Intent(Intent.ACTION_SEND).apply {
                type = sendMime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(service.contentResolver, "emoji_image", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(targetPackage)
            }

            try {
                service.startActivity(send)
                true
            } catch (e: ActivityNotFoundException) {
                Log.w(XimeInputMethodService.TAG, "No SEND handler in $targetPackage, using chooser", e)
                val openSend = Intent(send).apply { setPackage(null) }
                val chooser = Intent.createChooser(openSend, "分享图片")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(chooser)
                true
            }
        } catch (e: Exception) {
            Log.e(XimeInputMethodService.TAG, "Failed to share image", e)
            false
        }
    }

    private fun buildMimeList(
        primary: String,
        declared: Array<String>?,
        animated: Boolean,
    ): Array<String> {
        val list = linkedSetOf(primary)
        // 附带宿主已声明的 image/* 具体类型，避免对方只认声明表里的项
        declared?.forEach { mime ->
            if (mime.startsWith("image/", ignoreCase = true) && !mime.endsWith("/*")) {
                list.add(mime)
            }
        }
        if (animated && primary.equals("image/gif", ignoreCase = true)) {
            // 少数宿主把动图标成 webp 通道
            list.add("image/webp")
        }
        return list.toTypedArray()
    }

    private fun isAnimatedMime(mime: String, file: File? = null): Boolean {
        if (mime.equals("image/gif", ignoreCase = true)) return true
        if (!mime.equals("image/webp", ignoreCase = true)) return false
        if (file == null || !file.exists()) return true // 保守：按动图原样发，避免误压静帧
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(minOf(64 * 1024, file.length().toInt().coerceAtLeast(12)))
                val n = input.read(header)
                if (n < 12) return@use false
                // 扫描 ANIM chunk
                var i = 12
                while (i + 8 <= n) {
                    val tag = String(header, i, 4, Charsets.US_ASCII)
                    val size = (header[i + 4].toInt() and 0xff) or
                        ((header[i + 5].toInt() and 0xff) shl 8) or
                        ((header[i + 6].toInt() and 0xff) shl 16) or
                        ((header[i + 7].toInt() and 0xff) shl 24)
                    if (tag == "ANIM") return@use true
                    val payload = size.coerceAtLeast(0)
                    val step = 8 + payload + (payload and 1)
                    if (step <= 0) break
                    i += step
                }
                false
            }
        }.getOrDefault(false)
    }

    private fun mimeTypeForImage(imageFile: File, fallback: String): String =
        when (imageFile.extension.lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            else -> fallback
        }

    private fun copyToEmojiCache(imageFile: File): File? {
        return try {
            val cacheDir = File(service.cacheDir, "emoji_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            // 带上扩展名，避免 FileProvider / 宿主按名猜成 image/jpeg
            val cacheFile = File(cacheDir, imageFile.name)
            FileInputStream(imageFile).use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            cacheFile
        } catch (e: Exception) {
            Log.e(XimeInputMethodService.TAG, "Failed to cache emoji image", e)
            null
        }
    }

    // 支持精确匹配、image 通配（如 image/*）以及 */*
    private fun supportsMimeType(declaredMimeTypes: Array<String>?, mimeType: String): Boolean {
        if (declaredMimeTypes.isNullOrEmpty()) return false
        val starSlashStar = "*${'/'}" + "*"
        val slashStar = "${'/'}" + "*"
        return declaredMimeTypes.any { declared ->
            when {
                declared == starSlashStar -> true
                declared.equals(mimeType, ignoreCase = true) -> true
                declared.endsWith(slashStar) ->
                    mimeType.startsWith(declared.dropLast(2), ignoreCase = true)
                else -> false
            }
        }
    }

    internal fun selectClipboardItem(text: String) {
        if (service.candidateState.value.isComposing) {
            service.keyRouter.postRimeJob {
                service.rimeEngine.clearComposition()
                withContext(Dispatchers.Main) {
                    service.updateUI()
                }
            }
        }
        service.clipboardManager.markConsumed(text)
        service.commitPastedText(text)
        service.clipboardManager.copyToSystemClipboard(text)
    }

    internal fun commitClipboardText(text: String) {
        // 上滑/长按符号直达上屏：同步清掉编码，避免松手竞态里补发的字母又挂上候选
        if (service.candidateState.value.isComposing) {
            service.rimeEngine.clearComposition()
            service.updateUI()
        }
        // 中文模式引号：走 Rime punctuator pair，否则每次都是左引号 “/‘
        if (!service.uiState.value.isAsciiMode) {
            val pairKey = pairedPunctAsciiCode(text)
            if (pairKey != null) {
                val result = service.rimeEngine.processKeyAndGetResult(pairKey, 0)
                if (result.processed && result.committedText.isNotEmpty()) {
                    service.commitPastedText(result.committedText)
                    return
                }
            }
        }
        service.commitPastedText(text)
    }

    /** “”‘’ 与 ASCII 引号 → Rime 键码，供成对标点切换。 */
    private fun pairedPunctAsciiCode(text: String): Int? {
        if (text.length != 1) return null
        return when (text[0]) {
            '"', '“', '”' -> '"'.code
            '\'', '‘', '’' -> '\''.code
            else -> null
        }
    }

    internal fun deleteClipboardChars(count: Int) {
        service.currentInputConnection?.deleteSurroundingText(count, 0)
    }

    /**
     * 生成图片 content URI。
     * 优先 FileProvider；部分 ROM 失败时降级 MediaStore（Files，避免 Images 集合把动图当静图处理）。
     */
    private fun getContentUriForImage(imageFile: File, mimeType: String): Uri? {
        try {
            return FileProvider.getUriForFile(
                service,
                "${service.packageName}.fileprovider",
                imageFile
            )
        } catch (e: IllegalArgumentException) {
            Log.w(XimeInputMethodService.TAG, "FileProvider unavailable, falling back to MediaStore", e)
        } catch (e: Exception) {
            Log.w(XimeInputMethodService.TAG, "FileProvider getUriForFile failed, falling back to MediaStore", e)
        }

        return insertImageToMediaStore(imageFile, mimeType)
    }

    private fun insertImageToMediaStore(imageFile: File, mimeType: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.e(XimeInputMethodService.TAG, "MediaStore fallback requires API 29+, image commit failed")
            return null
        }
        return try {
            val resolver = service.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, imageFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Xime")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            // 用 Files 而不是 Images：Images 通道上部分宿主会按位图解码，动图变首帧
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
            Log.e(XimeInputMethodService.TAG, "MediaStore insert failed", e)
            null
        }
    }
}
