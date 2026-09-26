package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FuzzyPinyinHelperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun stripManagedBlock_removesOnlyMarkedSection() {
        val text = """
            patch:
              "menu/page_size": 20
            # XIME_FUZZY_BEGIN
              "speller/algebra/+":
                - derive/^zh/z/
            # XIME_FUZZY_END
              "other": 1
        """.trimIndent()
        val stripped = FuzzyPinyinHelper.stripManagedBlock(text)
        assertFalse(stripped.contains("XIME_FUZZY"))
        assertFalse(stripped.contains("derive/^zh/z/"))
        assertTrue(stripped.contains("menu/page_size"))
        assertTrue(stripped.contains("\"other\": 1"))
    }

    @Test
    fun rewriteManagedBlock_insertsUnderPatch_andIsIdempotent() {
        val file = tmp.newFile("pinyin_simp.custom.yaml")
        file.writeText("patch:\n  \"menu/page_size\": 20\n")
        val rules = listOf("derive/^zh/z/", "derive/^z([^h])/zh\$1/")
        assertTrue(FuzzyPinyinHelper.rewriteManagedBlock(file, rules))
        val once = file.readText()
        assertTrue(once.contains("# XIME_FUZZY_BEGIN"))
        assertTrue(once.contains("derive/^zh/z/"))
        assertTrue(once.contains("menu/page_size"))
        assertFalse(FuzzyPinyinHelper.rewriteManagedBlock(file, rules))
        assertEquals(once, file.readText())
    }

    @Test
    fun rewriteManagedBlock_emptyRules_clearsBlock() {
        val file = tmp.newFile("t9_pinyin.custom.yaml")
        FuzzyPinyinHelper.rewriteManagedBlock(file, listOf("derive/^zh/z/"))
        assertTrue(file.readText().contains("XIME_FUZZY_BEGIN"))
        assertTrue(FuzzyPinyinHelper.rewriteManagedBlock(file, emptyList()))
        assertFalse(file.readText().contains("XIME_FUZZY"))
    }

    @Test
    fun isPinyinLikeSchema_detectsBuiltinAndSkipsPureWubi() {
        val dir = tmp.newFolder("rime")
        File(dir, "pinyin_simp.schema.yaml").writeText(
            """
            engine:
              translators:
                - script_translator
            speller:
              alphabet: zyxwvutsrqponmlkjihgfedcba
            """.trimIndent()
        )
        File(dir, "wubi86.schema.yaml").writeText(
            """
            schema:
              schema_id: wubi86
            engine:
              translators:
                - table_translator
            """.trimIndent()
        )
        assertTrue(FuzzyPinyinHelper.isPinyinLikeSchema(dir, "pinyin_simp"))
        assertFalse(FuzzyPinyinHelper.isPinyinLikeSchema(dir, "wubi86"))
    }
}
