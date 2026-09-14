package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.config.NoopPluginConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.LuaValue
import java.io.File

/**
 * 验证 meme-bunny-lua：图片资源插件（resources/ 枚举 + host.resource.path）。
 */
class LuaMemeBunnyTest {

    @Test
    fun `meme bunny lua plugin serves emoji image resources`() {
        val dir = File("../plugins/meme-bunny")
        assertTrue("插件目录应存在: ${dir.absolutePath}", dir.exists())
        val runtime = LuaScriptRuntime(
            "com.kingzcheung.xime.plugin.emoji",
            dir,
            "main.lua",
            NoopPluginConfigStore
        )
        assertTrue("main.lua 应能加载", runtime.load())

        // 分类
        val cats = LuaScriptRuntime.tableToList(runtime.call("getCategories")).map { it.tojstring() }
        assertEquals(listOf("恶搞兔"), cats)

        // 表情：6 张图片，imageUrl 指向 resources/emojis/ 下真实文件
        val query = org.luaj.vm2.LuaTable()
        query.set("category", "")
        query.set("keyword", "")
        query.set("topK", 10)
        val emojis = LuaScriptRuntime.tableToList(
            runtime.call("getEmojis", query)
        )
        assertTrue("应返回全部表情（>=6）", emojis.size >= 6)

        val seenImages = mutableSetOf<String>()
        for (item in emojis) {
            val map = LuaScriptRuntime.tableToMap(item)
            val imageUrl = map["imageUrl"]?.tojstring()
            assertTrue("imageUrl 非空", !imageUrl.isNullOrEmpty())
            assertTrue("imageUrl 指向真实文件: $imageUrl", File(imageUrl).exists())
            seenImages.add(imageUrl!!)
        }
        assertTrue("图片应互不相同", seenImages.size >= 6)

        // insertText 与 displayText 区分（[表情xx] 插入文本）
        val first = LuaScriptRuntime.tableToMap(emojis[0])
        val display = first["text"]?.tojstring() ?: ""
        val insert = first["insertText"]?.tojstring() ?: ""
        assertTrue("insertText 应带[表情]前缀", insert.startsWith("[表情") && insert.endsWith("]"))
        assertTrue("displayText 与 insertText 不同", insert != display)

        // 图标
        val icon = LuaScriptRuntime.tableToMap(runtime.call("getIcon"))
        assertEquals("icon.webp", icon["assetName"]?.tojstring())
    }
}
