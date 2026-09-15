package com.kingzcheung.xime.settings

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.keyboard.GestureAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * 按键自定义覆盖层（Prefs JSON）。
 *
 * 优先级：本覆盖 > 用户目录 xime.custom.yaml > assets 内置。
 * 与 Rime 输入方案无关，只影响键盘 UI（键面字根 / 角标 / 长按气泡）。
 * 上屏编码仍走 tap.value（一般为字母）。
 */
object KeyCustomizeStore {
    private const val TAG = "KeyCustomizeStore"
    private const val KEY_OVERRIDES_ZH = "key_customize_overrides_zh"
    private const val KEY_OVERRIDES_EN = "key_customize_overrides_en"
    private const val FORMAT_ID = "xime-key-customize"
    private const val FORMAT_VERSION = 1

    /** 单键覆盖；字段为 null 表示不改该项。空字符串 / 空列表表示清除。 */
    data class KeyOverride(
        /** 键面主文字 / 字根（只改显示，不改 tap.value） */
        val tapLabel: String? = null,
        val swipeUp: String? = null,
        val swipeDown: String? = null,
        val longPress: List<String>? = null,
    ) {
        fun isEmpty(): Boolean =
            tapLabel == null && swipeUp == null && swipeDown == null && longPress == null
    }

    /**
     * 键面字根/符号预设（只改 tap.label；上屏仍为字母）。
     */
    data class KeyFacePreset(
        val id: String,
        val title: String,
        val subtitle: String,
        val labels: Map<String, String>,
    )

    /** 仓颉字根。 */
    val CANGJIE_RADICALS: Map<String, String> = mapOf(
        "a" to "日", "b" to "月", "c" to "金", "d" to "木", "e" to "水",
        "f" to "火", "g" to "土", "h" to "竹", "i" to "戈", "j" to "十",
        "k" to "大", "l" to "中", "m" to "一", "n" to "弓", "o" to "人",
        "p" to "心", "q" to "手", "r" to "口", "s" to "尸", "t" to "廿",
        "u" to "山", "v" to "女", "w" to "田", "x" to "難", "y" to "卜",
        "z" to "重",
    )

    /** 五笔 86 一级字根（键面常用简写）。 */
    val WUBI86_RADICALS: Map<String, String> = mapOf(
        "q" to "金", "w" to "人", "e" to "月", "r" to "白", "t" to "禾",
        "y" to "言", "u" to "立", "i" to "水", "o" to "火", "p" to "之",
        "a" to "工", "s" to "木", "d" to "大", "f" to "土", "g" to "王",
        "h" to "目", "j" to "日", "k" to "口", "l" to "田",
        "z" to "Ｚ", "x" to "纟", "c" to "又", "v" to "女", "b" to "子",
        "n" to "已", "m" to "山",
    )

    /**
     * 注音符号（台湾常见 QWERTY 字母行映射）。
     * 数字行/标点行的注音不在全键字母网格内，此处仅覆盖 a–z。
     */
    val ZHUYIN_SYMBOLS: Map<String, String> = mapOf(
        "q" to "ㄆ", "w" to "ㄊ", "e" to "ㄍ", "r" to "ㄐ", "t" to "ㄔ",
        "y" to "ㄗ", "u" to "ㄧ", "i" to "ㄛ", "o" to "ㄟ", "p" to "ㄣ",
        "a" to "ㄇ", "s" to "ㄋ", "d" to "ㄎ", "f" to "ㄑ", "g" to "ㄕ",
        "h" to "ㄘ", "j" to "ㄨ", "k" to "ㄜ", "l" to "ㄠ",
        "z" to "ㄈ", "x" to "ㄌ", "c" to "ㄏ", "v" to "ㄒ", "b" to "ㄖ",
        "n" to "ㄙ", "m" to "ㄩ",
    )

    /** 英文字母（大写显示）。 */
    val LETTERS_UPPER: Map<String, String> =
        ('a'..'z').associate { it.toString() to it.uppercaseChar().toString() }

    /** 英文字母（小写显示）。 */
    val LETTERS_LOWER: Map<String, String> =
        ('a'..'z').associate { it.toString() to it.toString() }

    val KEY_FACE_PRESETS: List<KeyFacePreset> = listOf(
        KeyFacePreset(
            id = "cangjie",
            title = "仓颉字根",
            subtitle = "日/月/金/木…，适合仓颉、速成",
            labels = CANGJIE_RADICALS,
        ),
        KeyFacePreset(
            id = "wubi86",
            title = "五笔字根",
            subtitle = "王/土/大/木…（86 一级字根）",
            labels = WUBI86_RADICALS,
        ),
        KeyFacePreset(
            id = "zhuyin",
            title = "注音符号",
            subtitle = "ㄅㄆㄇ风格字母行（ㄆ/ㄊ/ㄍ…）",
            labels = ZHUYIN_SYMBOLS,
        ),
        KeyFacePreset(
            id = "letters_upper",
            title = "英文字母（大写）",
            subtitle = "键面恢复为 A B C…",
            labels = LETTERS_UPPER,
        ),
        KeyFacePreset(
            id = "letters_lower",
            title = "英文字母（小写）",
            subtitle = "键面恢复为 a b c…",
            labels = LETTERS_LOWER,
        ),
    )

    fun loadOverrides(context: Context, isAsciiMode: Boolean): Map<String, KeyOverride> {
        val raw = SettingsPreferences.getPrefsPublic(context)
            .getString(prefsKey(isAsciiMode), null)
            ?: return emptyMap()
        return try {
            parseOverrides(raw)
        } catch (e: Exception) {
            Log.w(TAG, "loadOverrides failed", e)
            emptyMap()
        }
    }

    fun getOverride(context: Context, isAsciiMode: Boolean, key: String): KeyOverride? =
        loadOverrides(context, isAsciiMode)[key.lowercase()]

    fun saveOverride(context: Context, isAsciiMode: Boolean, key: String, override: KeyOverride) {
        val map = loadOverrides(context, isAsciiMode).toMutableMap()
        val id = key.lowercase()
        if (override.isEmpty()) {
            map.remove(id)
        } else {
            map[id] = override
        }
        persist(context, isAsciiMode, map)
    }

    fun clearOverride(context: Context, isAsciiMode: Boolean, key: String) {
        val map = loadOverrides(context, isAsciiMode).toMutableMap()
        map.remove(key.lowercase())
        persist(context, isAsciiMode, map)
    }

    fun clearAll(context: Context, isAsciiMode: Boolean) {
        persist(context, isAsciiMode, emptyMap())
    }

    /**
     * 套用键面预设（保留已有上滑/下滑/长按覆盖，只改/写入 tapLabel）。
     */
    fun applyTapLabelPreset(
        context: Context,
        isAsciiMode: Boolean,
        labels: Map<String, String>,
    ) {
        val map = loadOverrides(context, isAsciiMode).toMutableMap()
        for ((letter, label) in labels) {
            val id = letter.lowercase()
            val prev = map[id]
            map[id] = (prev ?: KeyOverride()).copy(tapLabel = label)
        }
        persist(context, isAsciiMode, map)
    }

    /** @deprecated 使用 [applyTapLabelPreset] / [KEY_FACE_PRESETS] */
    fun applyCangjieRadicals(context: Context, isAsciiMode: Boolean) {
        applyTapLabelPreset(context, isAsciiMode, CANGJIE_RADICALS)
    }

    fun applyOverrides(
        base: Map<String, KeyGestureConfig>,
        overrides: Map<String, KeyOverride>,
    ): Map<String, KeyGestureConfig> {
        if (overrides.isEmpty()) return base
        val result = base.toMutableMap()
        for ((key, override) in overrides) {
            val existing = result[key] ?: KeyGestureConfig(
                tap = GestureDef(
                    label = key,
                    action = GestureAction.COMMIT,
                    value = key,
                    display = DisplayMode.BOTH,
                )
            )
            result[key] = mergeOverride(key, existing, override)
        }
        return result
    }

    private fun mergeOverride(
        key: String,
        base: KeyGestureConfig,
        override: KeyOverride,
    ): KeyGestureConfig {
        val tap = when {
            override.tapLabel == null -> base.tap
            override.tapLabel.isEmpty() -> {
                val commit = base.tap?.value?.takeIf { it.isNotEmpty() } ?: key
                (base.tap ?: GestureDef(action = GestureAction.COMMIT, value = commit))
                    .copy(label = commit, value = commit, action = GestureAction.COMMIT)
            }
            else -> {
                val commit = base.tap?.value?.takeIf { it.isNotEmpty() } ?: key
                (base.tap ?: GestureDef(action = GestureAction.COMMIT, value = commit))
                    .copy(
                        label = override.tapLabel,
                        value = commit,
                        action = base.tap?.action ?: GestureAction.COMMIT,
                    )
            }
        }
        val swipeUp = when {
            override.swipeUp == null -> base.swipeUp
            override.swipeUp.isEmpty() -> null
            else -> commitGesture(override.swipeUp)
        }
        val swipeDown = when {
            override.swipeDown == null -> base.swipeDown
            override.swipeDown.isEmpty() -> null
            else -> commitGesture(override.swipeDown)
        }
        val longPress = when {
            override.longPress == null -> base.longPress
            override.longPress.isEmpty() -> null
            else -> LongPressConfig(
                display = "bubble",
                values = override.longPress.map { commitGesture(it) }.take(10),
            )
        }
        return base.copy(tap = tap, swipeUp = swipeUp, swipeDown = swipeDown, longPress = longPress)
    }

    private fun commitGesture(text: String): GestureDef =
        GestureDef(
            label = text,
            action = GestureAction.COMMIT,
            value = text,
            display = DisplayMode.BOTH,
        )

    private fun prefsKey(isAsciiMode: Boolean): String =
        if (isAsciiMode) KEY_OVERRIDES_EN else KEY_OVERRIDES_ZH

    data class ImportResult(
        val zhCount: Int,
        val enCount: Int,
    )

    /** 导出中英文全键覆盖为可分享的 JSON 文本。 */
    fun exportBundleJson(context: Context): String {
        val root = JSONObject()
        root.put("format", FORMAT_ID)
        root.put("version", FORMAT_VERSION)
        root.put("qwerty", overridesToJsonObject(loadOverrides(context, isAsciiMode = false)))
        root.put("qwerty_en", overridesToJsonObject(loadOverrides(context, isAsciiMode = true)))
        return root.toString(2)
    }

    /**
     * 从 JSON 导入。缺省的盘（qwerty / qwerty_en）保持不变；
     * [replace] 为 true 时整盘替换，false 时与现有覆盖按键合并（同键以导入为准）。
     */
    fun importBundleJson(context: Context, text: String, replace: Boolean = true): ImportResult {
        val root = JSONObject(text.trim().trimStart('\uFEFF'))
        val format = root.optString("format", "")
        if (format.isNotEmpty() && format != FORMAT_ID) {
            throw IllegalArgumentException("不是曦码按键自定义文件（format=$format）")
        }
        var zhCount = -1
        var enCount = -1
        if (root.has("qwerty")) {
            val imported = parseOverridesObject(root.getJSONObject("qwerty"))
            val merged = if (replace) imported
            else loadOverrides(context, false).toMutableMap().apply { putAll(imported) }
            persist(context, isAsciiMode = false, merged)
            zhCount = merged.size
        }
        if (root.has("qwerty_en")) {
            val imported = parseOverridesObject(root.getJSONObject("qwerty_en"))
            val merged = if (replace) imported
            else loadOverrides(context, true).toMutableMap().apply { putAll(imported) }
            persist(context, isAsciiMode = true, merged)
            enCount = merged.size
        }
        if (zhCount < 0 && enCount < 0) {
            throw IllegalArgumentException("文件中没有 qwerty / qwerty_en 数据")
        }
        return ImportResult(
            zhCount = zhCount.coerceAtLeast(0),
            enCount = enCount.coerceAtLeast(0),
        )
    }

    private fun overridesToJsonObject(map: Map<String, KeyOverride>): JSONObject {
        val json = JSONObject()
        for ((key, o) in map) {
            val obj = JSONObject()
            if (o.tapLabel != null) obj.put("tap_label", o.tapLabel)
            if (o.swipeUp != null) obj.put("swipe_up", o.swipeUp)
            if (o.swipeDown != null) obj.put("swipe_down", o.swipeDown)
            if (o.longPress != null) {
                val arr = JSONArray()
                o.longPress.forEach { arr.put(it) }
                obj.put("long_press", arr)
            }
            json.put(key, obj)
        }
        return json
    }

    private fun persist(context: Context, isAsciiMode: Boolean, map: Map<String, KeyOverride>) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString(prefsKey(isAsciiMode), overridesToJsonObject(map).toString())
            .apply()
    }

    private fun parseOverrides(raw: String): Map<String, KeyOverride> {
        return try {
            parseOverridesObject(JSONObject(raw))
        } catch (e: Exception) {
            Log.w(TAG, "parseOverrides failed", e)
            emptyMap()
        }
    }

    private fun parseOverridesObject(root: JSONObject): Map<String, KeyOverride> {
        val result = mutableMapOf<String, KeyOverride>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = root.optJSONObject(key) ?: continue
            val tapLabel = if (obj.has("tap_label")) obj.optString("tap_label", "") else null
            val swipeUp = if (obj.has("swipe_up")) obj.optString("swipe_up", "") else null
            val swipeDown = if (obj.has("swipe_down")) obj.optString("swipe_down", "") else null
            val longPress = if (obj.has("long_press")) {
                val arr = obj.optJSONArray("long_press")
                if (arr == null) emptyList()
                else (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i, null)?.takeIf { it.isNotEmpty() }
                }
            } else null
            val override = KeyOverride(
                tapLabel = tapLabel,
                swipeUp = swipeUp,
                swipeDown = swipeDown,
                longPress = longPress,
            )
            if (!override.isEmpty()) result[key.lowercase()] = override
        }
        return result
    }
}
