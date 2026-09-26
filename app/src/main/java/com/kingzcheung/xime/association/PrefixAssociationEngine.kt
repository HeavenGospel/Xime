package com.kingzcheung.xime.association

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.settings.DictionaryHelper
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 清风式词语联想：以上文为前缀，在方案词库中查更长的词。
 *
 * - 只读词库，不写 userdb / 不碰 ONNX
 * - 候选展示整词；上屏只补 [prefix] 之后的后缀（由 [commitSuffix] 给出）
 */
object PrefixAssociationEngine {
    private const val TAG = "PrefixAssociationEngine"
    /** 单方案最多纳入的唯一词条数，防止极端巨型方案 OOM。 */
    private const val MAX_UNIQUE_WORDS = 400_000
    private const val MAX_PREFIX_CHARS = 8

    data class Hit(
        /** 候选栏展示用整词，如「中国」 */
        val display: String,
        /** 真正上屏的后缀，如「国」（上文已是「中」） */
        val commitSuffix: String,
        val weight: Int,
    )

    private data class WordEntry(val text: String, val weight: Int)

    private val mutex = Mutex()

    @Volatile
    private var loadedSchemaId: String? = null

    /** 按 text 字典序升序，同前缀连续，便于二分下界。 */
    @Volatile
    private var words: Array<WordEntry> = emptyArray()

    fun isReady(): Boolean = words.isNotEmpty()

    suspend fun ensureLoaded(context: Context, schemaId: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val target = schemaId
                ?.takeIf { it.isNotBlank() }
                ?: SettingsPreferences.getCurrentSchema(context).ifBlank { "pinyin_simp" }
            if (loadedSchemaId == target && words.isNotEmpty()) return@withContext true
            mutex.withLock {
                if (loadedSchemaId == target && words.isNotEmpty()) return@withContext true
                loadLocked(context, target)
            }
        }

    fun invalidate() {
        loadedSchemaId = null
        words = emptyArray()
    }

    /**
     * @param contextText 上屏累积上下文；会从最长后缀试到 1 字，取第一批非空命中。
     * @return 按权重降序的命中；[Hit.display] 供展示，[Hit.commitSuffix] 供上屏。
     */
    fun lookup(contextText: String, topK: Int = 20): List<Hit> {
        if (topK <= 0 || contextText.isEmpty() || words.isEmpty()) return emptyList()

        val maxLen = minOf(contextText.length, MAX_PREFIX_CHARS)
        for (len in maxLen downTo 1) {
            val prefix = contextText.takeLast(len)
            if (prefix.isBlank()) continue
            val hits = lookupExactPrefix(prefix, topK)
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    private fun lookupExactPrefix(prefix: String, topK: Int): List<Hit> {
        val arr = words
        val start = lowerBound(arr, prefix)
        if (start >= arr.size || !arr[start].text.startsWith(prefix)) return emptyList()

        // 必须扫完该前缀区间再按权重取 topK，不能提前截断（否则退化成字典序前 N）。
        val bucket = ArrayList<WordEntry>(64)
        var i = start
        while (i < arr.size) {
            val w = arr[i]
            if (!w.text.startsWith(prefix)) break
            // 排除等于前缀本身：选中等于上屏空串
            if (w.text.length > prefix.length) bucket.add(w)
            i++
        }
        if (bucket.isEmpty()) return emptyList()

        bucket.sortByDescending { it.weight }
        return bucket.take(topK).map { e ->
            Hit(
                display = e.text,
                commitSuffix = e.text.substring(prefix.length),
                weight = e.weight,
            )
        }
    }

    private fun lowerBound(arr: Array<WordEntry>, prefix: String): Int {
        var lo = 0
        var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid].text < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun loadLocked(context: Context, schemaId: String): Boolean {
        return try {
            val start = System.currentTimeMillis()
            val dictName = SchemaManager.getReferencedDictName(context, schemaId) ?: schemaId
            val dir = SchemaManager.getRimeDir(context)
            val byText = HashMap<String, Int>(65_536)

            collectWords(dictName, dir, byText)

            installWordMap(byText, schemaId)
            Log.i(
                TAG,
                "Loaded prefix index schema=$schemaId words=${words.size} in ${System.currentTimeMillis() - start}ms"
            )
            words.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load prefix index for $schemaId", e)
            words = emptyArray()
            loadedSchemaId = null
            false
        }
    }

    /** 单测 / 调试：直接注入词表。 */
    internal fun installWordMapForTest(map: Map<String, Int>, schemaId: String = "test") {
        installWordMap(HashMap(map), schemaId)
    }

    private fun installWordMap(byText: HashMap<String, Int>, schemaId: String) {
        val sorted = byText.entries
            .map { WordEntry(it.key, it.value) }
            .sortedBy { it.text }
            .toTypedArray()
        words = sorted
        loadedSchemaId = schemaId
    }

    private fun collectWords(rootDict: String, dir: File, byText: HashMap<String, Int>) {
        val seen = linkedSetOf<String>()
        val queue = ArrayDeque(listOf(rootDict))
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (!seen.add(name)) continue
            val file = File(dir, "$name.dict.yaml")
            if (!file.exists()) continue
            val text = file.readText(Charsets.UTF_8)
            parseWordsInto(text, byText)
            if (byText.size >= MAX_UNIQUE_WORDS) return
            for (t in DictionaryHelper.parseImportTables(text)) {
                if (t !in seen) queue.addLast(t)
            }
        }
    }

    /** 解析 `词\t码[\t权重]`；同词多码取最大权重。 */
    internal fun parseWordsInto(text: String, byText: HashMap<String, Int>) {
        var inData = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!inData) {
                if (line == "...") inData = true
                continue
            }
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split('\t')
            if (parts.isEmpty()) continue
            val word = parts[0].trim()
            if (word.isEmpty() || word.length > 32) continue
            // 至少两列才是标准词条；无权重时记 1
            if (parts.size < 2) continue
            val weight = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 1
            val prev = byText[word]
            if (prev == null || weight > prev) {
                byText[word] = weight
                if (byText.size >= MAX_UNIQUE_WORDS) return
            }
        }
    }
}
