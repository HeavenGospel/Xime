package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.config.NoopPluginConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LuaScriptRuntimeTest {

    private fun runtimeFor(script: String, pluginDir: File): LuaScriptRuntime {
        val entry = File(pluginDir, "main.lua")
        pluginDir.mkdirs()
        entry.writeText(script)
        return LuaScriptRuntime("test", pluginDir, "main.lua", NoopPluginConfigStore)
    }

    @Test
    fun `loads main lua and calls exported functions`() {
        // 直接加载 kaomoji-lua 插件项目源码验证（cwd = plugin-core/）
        val dir = File("../plugins/kaomoji")
        val runtime = LuaScriptRuntime(
            "com.kingzcheung.xime.plugin.kaomoji",
            dir,
            "main.lua",
            NoopPluginConfigStore
        )
        assertTrue("main.lua 应能加载", runtime.load())

        val categories = LuaScriptRuntime.tableToList(runtime.call("getCategories"))
            .map { it.tojstring() }
        assertEquals(listOf("颜文字"), categories)

        val emojis = LuaScriptRuntime.tableToList(
            runtime.call(
                "getEmojis",
                org.luaj.vm2.LuaValue.tableOf(
                    arrayOf<org.luaj.vm2.LuaValue>(
                        org.luaj.vm2.LuaValue.valueOf("keyword"),
                        org.luaj.vm2.LuaValue.valueOf(""),
                        org.luaj.vm2.LuaValue.valueOf("topK"),
                        org.luaj.vm2.LuaValue.valueOf(5)
                    )
                )
            )
        )
        assertEquals("应返回 topK=5 个", 5, emojis.size)
        val first = LuaScriptRuntime.tableToMap(emojis[0])
        assertEquals("kaomoji_0", first["id"]?.tojstring())
        assertTrue("text 非空", !first["text"]?.tojstring().isNullOrEmpty())
    }

    @Test
    fun `legacy getEmojis positional args still return items`() {
        // 市场 kaomoji-2.1.0：getEmojis(category, searchText, topK)
        val dir = File("build/lua-kaomoji-legacy")
        val runtime = runtimeFor(
            """
            local plugin = {}
            local kaomojis = { "(ﾟ∀ﾟ)", "(`3´)", "(ゝ∀･)" }
            function plugin.getCategories()
                return { "颜文字" }
            end
            function plugin.getEmojis(category, searchText, topK)
                local list = {}
                for i, k in ipairs(kaomojis) do
                    if searchText == "" or string.find(k, searchText, 1, true) then
                        table.insert(list, { id = "kaomoji_" .. (i - 1), text = k })
                    end
                    if #list >= topK then break end
                end
                return list
            end
            return plugin
            """.trimIndent(),
            dir
        )
        assertTrue(runtime.load())

        // 新宿主传 table 时旧脚本会失败/空；位置参数应成功
        val tableCall = runtime.call(
            "getEmojis",
            org.luaj.vm2.LuaValue.tableOf(
                arrayOf(
                    org.luaj.vm2.LuaValue.valueOf("keyword"),
                    org.luaj.vm2.LuaValue.valueOf(""),
                    org.luaj.vm2.LuaValue.valueOf("topK"),
                    org.luaj.vm2.LuaValue.valueOf(10)
                )
            )
        )
        assertTrue(
            "旧签名收到 table 时通常失败或空",
            tableCall.isnil() || LuaScriptRuntime.tableToList(tableCall).isEmpty()
        )

        val legacy = LuaScriptRuntime.tableToList(
            runtime.call(
                "getEmojis",
                org.luaj.vm2.LuaValue.valueOf("颜文字"),
                org.luaj.vm2.LuaValue.valueOf(""),
                org.luaj.vm2.LuaValue.valueOf(10)
            )
        )
        assertEquals(3, legacy.size)
        assertEquals("kaomoji_0", LuaScriptRuntime.tableToMap(legacy[0])["id"]?.tojstring())
    }

    @Test
    fun `sandbox blocks os io and arbitrary require`() {
        val dir = File("build/lua-kaomoji-test")
        dir.mkdirs()
        val runtime = runtimeFor(
            "return { getTest = function() return os.execute('echo hi') end }",
            dir
        )
        runtime.load()

        // os 库被剥离：os 应为 nil，调用不抛异常但返回 nil
        val result = runtime.call("getTest")
        assertEquals("os.execute 应被沙箱拦截（nil 调用不抛错）", true, result.isnil())
    }

    @Test
    fun `require only loads from libs dir`() {
        val dir = File("build/lua-kaomoji-test2")
        dir.mkdirs()
        File(dir, "libs").mkdirs()
        File(dir, "libs/helper.lua").writeText("return { greeting = 'hello' }")
        val runtime = runtimeFor(
            """
            local helper = require('helper')
            return {
              getGreeting = function()
                return helper.greeting
              end,
              badRequire = function()
                return require('../../etc/passwd')
              end
            }
            """.trimIndent(),
            dir
        )
        runtime.load()

        assertEquals("libs 模块可正常 require", "hello", runtime.call("getGreeting").tojstring())

        // 路径穿越的 require 应抛 LuaError（宿主捕获后返回 NIL）
        val bad = runtime.call("badRequire")
        assertTrue("非法 require 应返回 nil", bad.isnil())
    }

    @Test
    fun `each plugin gets an isolated lua state`() {
        val dirA = File("build/lua-iso-a"); dirA.mkdirs()
        val dirB = File("build/lua-iso-b"); dirB.mkdirs()

        // A 脚本写入全局变量 g
        val runtimeA = runtimeFor(
            "g = 'PLUGIN_A'\nreturn { getGlobal = function() return g end }",
            dirA
        )
        // B 脚本读取同名全局变量 g（应不可见 A 的值，独立 state）
        val runtimeB = runtimeFor(
            "return { getGlobal = function() return g end }",
            dirB
        )
        runtimeA.load()
        runtimeB.load()

        assertEquals("PLUGIN_A", runtimeA.call("getGlobal").tojstring())
        assertTrue("插件 B 的 state 不应看到插件 A 的全局变量", runtimeB.call("getGlobal").isnil())
    }

    @Test
    fun `require cache is isolated per plugin state`() {
        // A、B 各自 libs/helper.lua 内容不同，require 缓存互不干扰
        val dirA = File("build/lua-iso-cache-a"); dirA.mkdirs()
        File(dirA, "libs").mkdirs()
        File(dirA, "libs/helper.lua").writeText("return { tag = 'A' }")
        val dirB = File("build/lua-iso-cache-b"); dirB.mkdirs()
        File(dirB, "libs").mkdirs()
        File(dirB, "libs/helper.lua").writeText("return { tag = 'B' }")

        val script = "local h = require('helper')\nreturn { getTag = function() return h.tag end }"
        val runtimeA = runtimeFor(script, dirA)
        val runtimeB = runtimeFor(script, dirB)
        runtimeA.load()
        runtimeB.load()

        assertEquals("A", runtimeA.call("getTag").tojstring())
        assertEquals("B", runtimeB.call("getTag").tojstring())
    }

    @Test
    fun `infinite loop times out and poisons runtime`() {
        val dir = File("build/lua-timeout"); dir.mkdirs()
        val entry = File(dir, "main.lua")
        entry.writeText("return { spin = function() while true do end end }")
        val runtime = LuaScriptRuntime(
            "test", dir, "main.lua", NoopPluginConfigStore,
            callTimeoutMs = 300, callbackTimeoutMs = 300
        )
        runtime.load()

        val start = System.currentTimeMillis()
        val result = runtime.call("spin")
        val elapsed = System.currentTimeMillis() - start
        assertTrue("死循环应超时返回 nil", result.isnil())
        assertTrue("超时应在限时附近返回", elapsed >= 200 && elapsed < 5000)

        // 中毒后：后续调用立即返回 nil（不再执行 Lua 代码）
        val second = runtime.call("spin")
        assertTrue("中毒后调用应立即返回 nil", second.isnil())
    }

    @Test
    fun `debug library is stripped from sandbox`() {
        val dir = File("build/lua-debug"); dir.mkdirs()
        val runtime = runtimeFor(
            "return { getDebug = function() return debug.getregistry() end }",
            dir
        )
        runtime.load()
        assertTrue("debug 库应被剥离", runtime.call("getDebug").isnil())
    }
}
