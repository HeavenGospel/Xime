package com.kingzcheung.xime.settings

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Locale

data class DictEntry(
    val word: String,
    val code: String,
    val weight: Int? = null
)

object DictionaryHelper {
    private const val TAG = "DictionaryHelper"

    /** 词库浏览器加载上限（完整计数另走 countEntries）。 */
    const val MAX_BROWSER_ENTRIES = 50_000

    /** 解析一个 .dict.yaml 文本里 `...` 之后的词条（`词<TAB>码`，也容忍空格分隔）。纯函数。 */
    fun parseDictEntries(text: String): List<DictEntry> {
        val out = mutableListOf<DictEntry>()
        var inData = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!inData) {
                if (line == "...") inData = true
                continue
            }
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split("\t", "  ", " ").filter { it.isNotEmpty() }
            if (parts.size >= 2) out.add(DictEntry(parts[0], parts[1]))
        }
        return out
    }

    /** 解析 .dict.yaml 头部的 `import_tables`（块式 `- x` 或内联 `[a, b]`）。纯函数。 */
    fun parseImportTables(text: String): List<String> {
        val tables = linkedSetOf<String>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line == "...") break // 头部结束，后面是词条
            if (line.startsWith("import_tables:")) {
                val inline = line.substringAfter(":").trim()
                if (inline.startsWith("[")) {
                    inline.trim('[', ']').split(",")
                        .map { stripYamlScalar(it) }.filter { it.isNotEmpty() }
                        .forEach { tables.add(it) }
                } else {
                    var j = i + 1
                    while (j < lines.size && lines[j].trim().startsWith("- ")) {
                        tables.add(stripYamlScalar(lines[j].trim().removePrefix("- ")))
                        j++
                    }
                    i = j - 1
                }
            }
            i++
        }
        return tables.toList()
    }

    /** 去掉引号与行尾 `#` 注释。 */
    private fun stripYamlScalar(raw: String): String {
        val noComment = raw.substringBefore("#").trim()
        return noComment.trim('"').trim('\'')
    }

    /**
     * 跟随 `import_tables` 递归收集词条（注入读取器，便于单测；按表名去重防环）。
     * 修复"主词典靠 import_tables 组装时(如 quick5/cangjie5)词库查看器为空"。
     * @param maxEntries 上限，避免薄荷等巨型词典把词库浏览器撑爆；null 表示不限。
     */
    fun collectEntries(
        rootDict: String,
        maxEntries: Int? = MAX_BROWSER_ENTRIES,
        readDict: (String) -> String?,
    ): List<DictEntry> {
        val out = mutableListOf<DictEntry>()
        val seen = linkedSetOf<String>()
        val queue = ArrayDeque(listOf(rootDict))
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (!seen.add(name)) continue
            val text = readDict(name) ?: continue
            for (e in parseDictEntries(text)) {
                out.add(e)
                if (maxEntries != null && out.size >= maxEntries) return out
            }
            for (t in parseImportTables(text)) if (t !in seen) queue.addLast(t)
        }
        return out
    }

    /** 仅统计词条数（不把巨型表整表载入内存）。 */
    fun countEntries(rootDict: String, readDict: (String) -> String?): Long {
        var total = 0L
        val seen = linkedSetOf<String>()
        val queue = ArrayDeque(listOf(rootDict))
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (!seen.add(name)) continue
            val text = readDict(name) ?: continue
            var inData = false
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (!inData) {
                    if (line == "...") inData = true
                    continue
                }
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split("\t", "  ", " ").filter { it.isNotEmpty() }
                if (parts.size >= 2) total++
            }
            for (t in parseImportTables(text)) if (t !in seen) queue.addLast(t)
        }
        return total
    }

    fun loadDictionary(context: Context, schemaId: String): List<DictEntry> {
        val dictName = SchemaManager.getReferencedDictName(context, schemaId) ?: schemaId
        val dir = SchemaManager.getRimeDir(context)
        return try {
            collectEntries(dictName) { name ->
                val f = File(dir, "$name.dict.yaml")
                if (f.exists()) f.readText(Charsets.UTF_8) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary for $schemaId", e)
            emptyList()
        }
    }

    /** 词条总数（用于界面展示；可能大于浏览器实际加载的条数）。 */
    fun countDictionary(context: Context, schemaId: String): Long {
        val dictName = SchemaManager.getReferencedDictName(context, schemaId) ?: schemaId
        val dir = SchemaManager.getRimeDir(context)
        return try {
            countEntries(dictName) { name ->
                val f = File(dir, "$name.dict.yaml")
                if (f.exists()) f.readText(Charsets.UTF_8) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count dictionary for $schemaId", e)
            0L
        }
    }

    fun searchDictionary(entries: List<DictEntry>, query: String): List<DictEntry> {
        if (query.isEmpty()) return entries.take(100)
        val lowerQuery = query.lowercase(Locale.ROOT)
        return entries.filter {
            it.word.contains(query) || it.code.contains(query) || it.code.lowercase(Locale.ROOT).contains(lowerQuery)
        }.take(100)
    }
}
