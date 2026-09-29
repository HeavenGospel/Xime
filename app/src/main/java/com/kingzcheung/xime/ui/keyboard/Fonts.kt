package com.kingzcheung.xime.ui.keyboard

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.os.Build
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.kingzcheung.xime.settings.KeyboardFontConfig
import java.io.File

/**
 * 字体管理器，管理键盘相关字体的加载和缓存。
 *
 * 字体路径查找规则：
 * 1. 绝对路径（以 / 开头）：从根目录找起，如 /fonts/myfont.ttf
 * 2. 相对路径（包含 / 但不以 / 开头）：相对于 rime/ 目录，如 fonts/myfont.ttf
 * 3. 仅文件名（不包含 /）：相对于 rime/ 目录查找，如 myfont.ttf
 *
 * 候选/注释默认用内置「遍黑体 P1」（覆盖 CJK 扩展 B 等），缺字时不再空白。
 * 若配置了自定义候选字体，API 29+ 会把遍黑体挂为缺字回退。
 */
object AppFonts {
    private const val TAG = "AppFonts"
    private const val CHAI_PUA_FONT = "ChaiPUA-0.2.7-snow.ttf"
    private const val CJK_FALLBACK_ASSET = "fonts/PlangothicP1-Regular.ttf"

    private var initialized = false
    private lateinit var assetManager: AssetManager
    private lateinit var filesDir: File
    private lateinit var rimeDir: File

    // 初值勿用 0：全空 KeyboardFontConfig.hashCode() 也为 0
    private var loadedConfigHash: Int = Int.MIN_VALUE

    val chaiPuaTypeface: Typeface by lazy {
        Typeface.createFromAsset(assetManager, CHAI_PUA_FONT)
    }

    val chaiPuaFontFamily: FontFamily by lazy {
        FontFamily(Font(CHAI_PUA_FONT, assetManager))
    }

    private var _cjkFallbackTypeface: Typeface? = null

    private var _keyTypeface: Typeface? = null
    private var _keyLabelTypeface: Typeface? = null
    private var _candidateTypeface: Typeface? = null
    private var _commentTypeface: Typeface? = null

    private var _keyFontFamily: FontFamily? = null
    private var _keyLabelFontFamily: FontFamily? = null
    private var _candidateFontFamily: FontFamily? = null
    private var _commentFontFamily: FontFamily? = null

    private var _keyFontTypeface: Typeface? = null

    val keyTypeface: Typeface get() = _keyTypeface ?: Typeface.DEFAULT
    val keyFontTypeface: Typeface get() {
        val cached = _keyFontTypeface
        if (cached != null) return cached
        val created = Typeface.create(keyTypeface, Typeface.BOLD)
        _keyFontTypeface = created
        return created
    }
    val keyLabelTypeface: Typeface get() = _keyLabelTypeface ?: chaiPuaTypeface
    val candidateTypeface: Typeface get() = _candidateTypeface ?: Typeface.DEFAULT
    val commentTypeface: Typeface get() = _commentTypeface ?: Typeface.DEFAULT

    val keyFontFamily: FontFamily get() = _keyFontFamily ?: FontFamily.Default
    val keyLabelFontFamily: FontFamily get() = _keyLabelFontFamily ?: chaiPuaFontFamily
    val candidateFontFamily: FontFamily get() = _candidateFontFamily ?: FontFamily.Default
    val commentFontFamily: FontFamily get() = _commentFontFamily ?: FontFamily.Default

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        assetManager = context.assets
        filesDir = context.filesDir
        rimeDir = File(filesDir, "rime")
    }

    fun loadCustomFonts(config: KeyboardFontConfig) {
        if (!initialized) return
        val hash = config.hashCode()
        if (hash == loadedConfigHash) return
        loadedConfigHash = hash

        _keyTypeface = loadTypeface(config.keyFont)
        _keyLabelTypeface = loadTypeface(config.keyLabelFont)
        _candidateTypeface = resolveTextTypeface(config.candidateFont)
        _commentTypeface = resolveTextTypeface(config.commentFont)

        _keyFontFamily = loadFontFamily(config.keyFont, _keyTypeface)
        _keyLabelFontFamily = loadFontFamily(config.keyLabelFont, _keyLabelTypeface)
        _candidateFontFamily = FontFamily(_candidateTypeface!!)
        _commentFontFamily = FontFamily(_commentTypeface!!)

        _keyFontTypeface = null

        Log.d(
            TAG,
            "Custom fonts loaded: key=${config.keyFont}, keyLabel=${config.keyLabelFont}, " +
                "candidate=${config.candidateFont}, comment=${config.commentFont}, " +
                "cjkFallback=${_cjkFallbackTypeface != null}",
        )
    }

    /**
     * 未配置自定义字体 → 直接用遍黑体（覆盖扩展区）。
     * 已配置 → 主字体 + 遍黑体缺字回退（API 29+）。
     */
    private fun resolveTextTypeface(fontPath: String): Typeface {
        val fallback = cjkFallbackTypeface()
        if (fontPath.isBlank()) {
            return fallback ?: Typeface.SANS_SERIF ?: Typeface.DEFAULT
        }
        val primaryFile = resolveFontPath(fontPath)
        val primary = loadTypeface(fontPath)
            ?: return fallback ?: Typeface.SANS_SERIF ?: Typeface.DEFAULT
        if (fallback == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return primary
        }
        return try {
            val primaryFamily = PlatformFontFamily.Builder(
                PlatformFont.Builder(primaryFile).build(),
            ).build()
            val fallbackFamily = platformFamilyFromCjkFallback()
                ?: return primary
            Typeface.CustomFallbackBuilder(primaryFamily)
                .addCustomFallback(fallbackFamily)
                .setSystemFallback("sans-serif")
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "CustomFallbackBuilder failed, using primary only", e)
            primary
        }
    }

    private fun cjkFallbackTypeface(): Typeface? {
        _cjkFallbackTypeface?.let { return it }
        if (!initialized) return null
        val userFile = File(rimeDir, "fonts/PlangothicP1-Regular.ttf")
        if (userFile.exists()) {
            try {
                return Typeface.createFromFile(userFile).also { _cjkFallbackTypeface = it }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load user CJK fallback: ${userFile.absolutePath}", e)
            }
        }
        return try {
            Typeface.createFromAsset(assetManager, CJK_FALLBACK_ASSET).also {
                _cjkFallbackTypeface = it
            }
        } catch (e: Exception) {
            Log.w(TAG, "CJK fallback asset missing: $CJK_FALLBACK_ASSET", e)
            null
        }
    }

    private fun platformFamilyFromCjkFallback(): PlatformFontFamily? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val userFile = File(rimeDir, "fonts/PlangothicP1-Regular.ttf")
        return try {
            val font = if (userFile.exists()) {
                PlatformFont.Builder(userFile).build()
            } else {
                PlatformFont.Builder(assetManager, CJK_FALLBACK_ASSET).build()
            }
            PlatformFontFamily.Builder(font).build()
        } catch (e: Exception) {
            Log.w(TAG, "CJK fallback FontFamily missing", e)
            null
        }
    }

    private fun resolveFontPath(fontPath: String): File {
        return when {
            fontPath.startsWith("/") -> File(filesDir, fontPath.removePrefix("/"))
            fontPath.startsWith("rime/") -> File(filesDir, fontPath)
            fontPath.contains("/") -> File(rimeDir, fontPath)
            else -> File(rimeDir, fontPath)
        }
    }

    private fun loadTypeface(fontPath: String): Typeface? {
        if (fontPath.isBlank()) return null
        val fontFile = resolveFontPath(fontPath)
        if (!fontFile.exists()) {
            Log.w(TAG, "Font file not found: ${fontFile.absolutePath}")
            return null
        }
        return try {
            Typeface.createFromFile(fontFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load typeface from ${fontFile.absolutePath}", e)
            null
        }
    }

    private fun loadFontFamily(fontPath: String, typeface: Typeface?): FontFamily? {
        if (fontPath.isBlank()) return null
        if (typeface != null) return FontFamily(typeface)
        val fontFile = resolveFontPath(fontPath)
        if (!fontFile.exists()) return null
        return try {
            FontFamily(Typeface.createFromFile(fontFile))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load font family from ${fontFile.absolutePath}", e)
            null
        }
    }
}
