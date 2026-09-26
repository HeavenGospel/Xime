package com.kingzcheung.xime.association

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrefixAssociationEngineTest {

    @Before
    fun setUp() {
        PrefixAssociationEngine.invalidate()
    }

    @Test
    fun parseWordsInto_keepsMaxWeightPerWord() {
        val yaml = """
            ---
            name: t
            ...
            中国	zhong guo	10
            中国	zhongguo	99
            中间	zhong jian	50
            中	zhong	1000
        """.trimIndent()
        val map = HashMap<String, Int>()
        PrefixAssociationEngine.parseWordsInto(yaml, map)
        assertEquals(99, map["中国"])
        assertEquals(50, map["中间"])
        assertEquals(1000, map["中"])
    }

    @Test
    fun lookup_excludesExactPrefix_ordersByWeight_commitIsSuffix() {
        PrefixAssociationEngine.installWordMapForTest(
            mapOf(
                "中" to 999,
                "中国" to 80,
                "中国人" to 40,
                "中间" to 90,
                "中心" to 70,
            )
        )
        val hits = PrefixAssociationEngine.lookup("中", 10)
        assertEquals(listOf("中间", "中国", "中心", "中国人"), hits.map { it.display })
        assertEquals(listOf("间", "国", "心", "国人"), hits.map { it.commitSuffix })
        assertTrue(hits.none { it.display == "中" })
    }

    @Test
    fun lookup_triesLongerContextSuffixFirst() {
        PrefixAssociationEngine.installWordMapForTest(
            mapOf(
                "京" to 1,
                "北京" to 50,
                "北京大学" to 100,
                "北" to 9,
            )
        )
        val hits = PrefixAssociationEngine.lookup("去北京", 5)
        assertEquals("北京大学", hits.first().display)
        assertEquals("大学", hits.first().commitSuffix)
    }
}
