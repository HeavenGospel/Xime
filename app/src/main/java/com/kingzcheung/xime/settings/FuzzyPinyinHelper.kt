package com.kingzcheung.xime.settings

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 模糊拼音：把开关写成各拼音方案 `{schema}.custom.yaml` 的 `speller/algebra/+` 补丁。
 * 规则风格对齐雾凇 / 清风常见分组；改完需部署才能进 prism。
 */
object FuzzyPinyinHelper {
    private const val TAG = "FuzzyPinyinHelper"
    private const val BEGIN = "# XIME_FUZZY_BEGIN"
    private const val END = "# XIME_FUZZY_END"

    /** 始终尝试写入的内置拼音方案（文件不存在则跳过）。 */
    private val BUILTIN_PINYIN_SCHEMAS = listOf("pinyin_simp", "t9_pinyin")

    enum class Group(
        val id: String,
        val title: String,
        val subtitle: String,
        /** 常用预设（一键开启） */
        val recommended: Boolean,
        val rules: List<String>,
    ) {
        ZH_Z(
            "zh_z", "zh ↔ z", "知≈资（zhi≈zi）", true,
            listOf("derive/^zh/z/", "derive/^z([^h])/zh\$1/")
        ),
        CH_C(
            "ch_c", "ch ↔ c", "吃≈呲（chi≈ci）", true,
            listOf("derive/^ch/c/", "derive/^c([^h])/ch\$1/")
        ),
        SH_S(
            "sh_s", "sh ↔ s", "诗≈丝（shi≈si）", true,
            listOf("derive/^sh/s/", "derive/^s([^h])/sh\$1/")
        ),
        N_L(
            "n_l", "n ↔ l", "你≈里（ni≈li）", false,
            listOf("derive/^n/l/", "derive/^l/n/")
        ),
        F_H(
            "f_h", "f ↔ h", "飞≈黑（fei≈hei）", false,
            listOf("derive/^f/h/", "derive/^h/f/")
        ),
        R_L(
            "r_l", "r ↔ l", "人≈冷（ren≈leng）", false,
            listOf("derive/^r/l/", "derive/^l/r/")
        ),
        AN_ANG(
            "an_ang", "an ↔ ang", "安≈昂", true,
            listOf("derive/([aeiou])n\$/\$1ng/", "derive/([aeiou])ng\$/\$1n/")
        ),
        EN_ENG(
            "en_eng", "en ↔ eng", "分≈风", true,
            listOf("derive/en\$/eng/", "derive/eng\$/en/")
        ),
        IN_ING(
            "in_ing", "in ↔ ing", "音≈英", true,
            listOf("derive/in\$/ing/", "derive/ing\$/in/")
        ),
        IAN_IANG(
            "ian_iang", "ian ↔ iang", "天≈娘（tian≈niang 韵腹）", false,
            listOf("derive/ian\$/iang/", "derive/iang\$/ian/")
        ),
        UAN_UANG(
            "uan_uang", "uan ↔ uang", "欢≈荒", false,
            listOf("derive/uan\$/uang/", "derive/uang\$/uan/")
        );

        companion object {
            fun fromId(id: String): Group? = entries.find { it.id == id }
        }
    }

    fun isEnabled(context: Context, group: Group): Boolean =
        SettingsPreferences.isFuzzyPinyinGroupEnabled(context, group.id)

    fun setEnabled(context: Context, group: Group, enabled: Boolean) {
        SettingsPreferences.setFuzzyPinyinGroupEnabled(context, group.id, enabled)
    }

    fun enabledGroups(context: Context): List<Group> =
        Group.entries.filter { isEnabled(context, it) }

    fun enableRecommended(context: Context) {
        Group.entries.forEach { setEnabled(context, it, it.recommended) }
    }

    fun disableAll(context: Context) {
        Group.entries.forEach { setEnabled(context, it, false) }
    }

    /** 收集已开启规则（去重保序）。 */
    fun buildRules(context: Context): List<String> {
        val out = linkedSetOf<String>()
        enabledGroups(context).forEach { g -> out.addAll(g.rules) }
        return out.toList()
    }

    /**
     * 把当前偏好写入目标方案的 custom.yaml。
     * @return 实际写入/清理过的 schemaId 列表
     */
    fun applyToSchemas(context: Context): List<String> {
        val rules = buildRules(context)
        val rimeDir = SchemaManager.getRimeDir(context)
        if (!rimeDir.exists()) rimeDir.mkdirs()
        val targets = targetSchemaIds(context, rimeDir)
        val touched = mutableListOf<String>()
        for (schemaId in targets) {
            val file = File(rimeDir, "$schemaId.custom.yaml")
            if (rewriteManagedBlock(file, rules)) {
                touched.add(schemaId)
                Log.i(TAG, "Fuzzy patch updated for $schemaId rules=${rules.size}")
            }
        }
        return touched
    }

    /** 拼音类方案：内置 + 已启用且含 script_translator / 拼音痕迹的方案。 */
    fun targetSchemaIds(context: Context, rimeDir: File = SchemaManager.getRimeDir(context)): List<String> {
        val ids = linkedSetOf<String>()
        BUILTIN_PINYIN_SCHEMAS.forEach { ids.add(it) }
        SchemaManager.getEnabledSchemas(context).forEach { id ->
            if (isPinyinLikeSchema(rimeDir, id)) ids.add(id)
        }
        // 只保留磁盘上确有 schema 文件的
        return ids.filter { File(rimeDir, "$it.schema.yaml").exists() }
    }

    internal fun isPinyinLikeSchema(rimeDir: File, schemaId: String): Boolean {
        if (schemaId.contains("wubi", ignoreCase = true) &&
            !schemaId.contains("pinyin", ignoreCase = true)
        ) {
            return false
        }
        if (schemaId == "handwriting" || schemaId == "numbers" || schemaId == "stroke") {
            return false
        }
        val file = File(rimeDir, "$schemaId.schema.yaml")
        if (!file.exists()) return false
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return false
        }
        val hasScript = text.contains("script_translator")
        val looksPinyin = text.contains("pinyin", ignoreCase = true) ||
            text.contains("speller:") && text.contains("alphabet:") &&
            Regex("""alphabet:\s*["']?zyxwvutsrqponmlkjihgfedcba""", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)
        return hasScript || looksPinyin
    }

    /**
     * 重写 custom.yaml 中托管块。rules 为空则删除托管块。
     * @return 文件是否发生变化
     */
    internal fun rewriteManagedBlock(file: File, rules: List<String>): Boolean {
        val old = if (file.exists()) file.readText(Charsets.UTF_8) else ""
        val stripped = stripManagedBlock(old)
        val newText = if (rules.isEmpty()) {
            ensureTrailingNewline(stripped)
        } else {
            insertManagedBlock(stripped, rules)
        }
        if (newText == ensureTrailingNewline(old)) return false
        file.parentFile?.mkdirs()
        file.writeText(newText, Charsets.UTF_8)
        return true
    }

    internal fun stripManagedBlock(text: String): String {
        if (text.isEmpty()) return text
        val begin = text.indexOf(BEGIN)
        if (begin < 0) return text
        val end = text.indexOf(END, begin)
        if (end < 0) return text
        val afterEnd = end + END.length
        var endIdx = afterEnd
        if (endIdx < text.length && text[endIdx] == '\r') endIdx++
        if (endIdx < text.length && text[endIdx] == '\n') endIdx++
        var startIdx = begin
        // 吃掉 BEGIN 前多余空行（最多一个）
        if (startIdx > 0 && text[startIdx - 1] == '\n') {
            startIdx--
            if (startIdx > 0 && text[startIdx - 1] == '\r') startIdx--
        }
        return text.removeRange(startIdx, endIdx)
    }

    private fun insertManagedBlock(text: String, rules: List<String>): String {
        val block = buildString {
            append(BEGIN)
            append('\n')
            append("  \"speller/algebra/+\":\n")
            for (r in rules) {
                append("    - ")
                append(r)
                append('\n')
            }
            append(END)
            append('\n')
        }
        val cleaned = text.trimEnd('\n', '\r', ' ').removeSuffix("...").trimEnd()
        val patchMatch = Regex("^patch:\\s*$", RegexOption.MULTILINE).find(cleaned)
        return if (patchMatch != null) {
            val at = patchMatch.range.last + 1
            ensureTrailingNewline(
                cleaned.substring(0, at) + "\n" + block + cleaned.substring(at).trimStart('\n', '\r')
            )
        } else if (cleaned.isBlank()) {
            "patch:\n$block"
        } else {
            ensureTrailingNewline("$cleaned\n\npatch:\n$block")
        }
    }

    private fun ensureTrailingNewline(s: String): String =
        if (s.isEmpty() || s.endsWith("\n")) s else "$s\n"
}
