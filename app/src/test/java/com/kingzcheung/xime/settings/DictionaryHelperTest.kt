package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure parts of [DictionaryHelper] — the fix that makes the dictionary
 * viewer follow `import_tables` (so schemes like quick5/cangjie5, whose words live in
 * imported tables, no longer show as empty).
 */
class DictionaryHelperTest {

    @Test
    fun `parseDictEntries reads word-code after data marker`() {
        val text = "# comment\n---\nname: x\n...\n日\ta\n曰\ta\n\n# note\n郎\tivnl\n"
        assertEquals(
            listOf(DictEntry("日", "a"), DictEntry("曰", "a"), DictEntry("郎", "ivnl")),
            DictionaryHelper.parseDictEntries(text),
        )
    }

    @Test
    fun `parseDictEntries ignores header before the marker`() {
        val text = "name: x\nimport_tables:\n  - foo\n...\n词\tcode\n"
        assertEquals(listOf(DictEntry("词", "code")), DictionaryHelper.parseDictEntries(text))
    }

    @Test
    fun `parseDictEntries empty when no entries (import-only dict like quick5)`() {
        val text = "name: quick5\nuse_preset_vocabulary: true\nimport_tables:\n  - cangjie5.base\n...\n"
        assertTrue(DictionaryHelper.parseDictEntries(text).isEmpty())
    }

    @Test
    fun `parseImportTables block form`() {
        val text = "name: quick5\nimport_tables:\n  - cangjie5.base\n  - quick5.supplement\n...\n词\tc\n"
        assertEquals(
            listOf("cangjie5.base", "quick5.supplement"),
            DictionaryHelper.parseImportTables(text),
        )
    }

    @Test
    fun `parseImportTables inline form`() {
        assertEquals(
            listOf("a", "b", "c"),
            DictionaryHelper.parseImportTables("import_tables: [a, b, c]\n...\n"),
        )
    }

    @Test
    fun `parseImportTables none`() {
        assertTrue(DictionaryHelper.parseImportTables("name: x\n...\n词\tc\n").isEmpty())
    }

    @Test
    fun `collectEntries follows import_tables and merges (quick5 case)`() {
        val files = mapOf(
            "quick5" to "import_tables:\n  - cangjie5.base\n  - quick5.supplement\n...\n",
            "cangjie5.base" to "...\n日\ta\n曰\ta\n",
            "quick5.supplement" to "...\n郎\tivnl\n",
        )
        assertEquals(
            listOf(DictEntry("日", "a"), DictEntry("曰", "a"), DictEntry("郎", "ivnl")),
            DictionaryHelper.collectEntries("quick5") { files[it] },
        )
    }

    @Test
    fun `collectEntries dedups table cycles`() {
        val files = mapOf(
            "a" to "import_tables:\n  - b\n...\n词1\tc1\n",
            "b" to "import_tables:\n  - a\n...\n词2\tc2\n",
        )
        assertEquals(
            listOf(DictEntry("词1", "c1"), DictEntry("词2", "c2")),
            DictionaryHelper.collectEntries("a") { files[it] },
        )
    }

    @Test
    fun `collectEntries skips missing tables gracefully`() {
        val files = mapOf("a" to "import_tables:\n  - missing\n...\n词\tc\n")
        assertEquals(
            listOf(DictEntry("词", "c")),
            DictionaryHelper.collectEntries("a") { files[it] },
        )
    }

    @Test
    fun `parseImportTables strips trailing comments`() {
        val text = """
            import_tables:
              - dicts/rime_mint.chars # 单字
              - "dicts/rime_mint.base" # 基础词库
            ...
        """.trimIndent()
        assertEquals(
            listOf("dicts/rime_mint.chars", "dicts/rime_mint.base"),
            DictionaryHelper.parseImportTables(text),
        )
    }

    @Test
    fun `collectEntries respects maxEntries`() {
        val files = mapOf(
            "a" to "...\n甲\ta\n乙\tb\n丙\tc\n",
        )
        assertEquals(
            listOf(DictEntry("甲", "a"), DictEntry("乙", "b")),
            DictionaryHelper.collectEntries("a", maxEntries = 2) { files[it] },
        )
    }
}
