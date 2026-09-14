package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDictImporterTest {

    @Test
    fun `parseUserDbEntries reads code and word`() {
        val text = """
            # Rime user dictionary
            #@/db_name	rime_mint
            a 	啊	c=1 d=1 t=1
            ni hao 	你好	c=2 d=1 t=1
        """.trimIndent()
        assertEquals(
            listOf(DictEntry("啊", "a"), DictEntry("你好", "ni hao")),
            UserDictImporter.parseUserDbEntries(text),
        )
    }

    @Test
    fun `looksLikeUserDictOnlyArchive`() {
        assertTrue(
            UserDictImporter.looksLikeUserDictOnlyArchive(
                listOf("rime_mint.userdb.txt", "melt_eng.userdb.txt")
            )
        )
        assertFalse(
            UserDictImporter.looksLikeUserDictOnlyArchive(
                listOf("rime_mint.schema.yaml", "rime_mint.userdb.txt")
            )
        )
        assertFalse(
            UserDictImporter.looksLikeUserDictOnlyArchive(
                listOf("rime_mint.schema.yaml", "dicts/base.dict.yaml")
            )
        )
    }
}
