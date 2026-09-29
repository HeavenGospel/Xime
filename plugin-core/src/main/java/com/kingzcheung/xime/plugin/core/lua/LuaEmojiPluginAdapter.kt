package com.kingzcheung.xime.plugin.core.lua

import com.kingzcheung.xime.plugin.core.api.EmojiPlugin
import com.kingzcheung.xime.plugin.core.api.EmojiQuery
import com.kingzcheung.xime.plugin.core.api.PluginResultItem
import com.kingzcheung.xime.plugin.core.lua.sdk.LuaPluginContract
import com.kingzcheung.xime.plugin.core.model.PluginContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/** emoji 类型 Lua 插件的宿主侧适配器：实现 EmojiPlugin 接口。 */
class LuaEmojiPluginAdapter(
    runtime: LuaScriptRuntime,
    pluginContext: PluginContext
) : LuaPluginAdapter(runtime, pluginContext), EmojiPlugin {

    /**
     * 统一的候选项解析（emoji 与 tool 的 items 共用同一协议 schema `{id, text, insertText?, imageUrl?}`）。
     *
     * 兼容两套 Lua 签名：
     * - 新：`getEmojis({ category, keyword, topK })`
     * - 旧（市场 kaomoji-2.1.0 等）：`getEmojis(category, searchText, topK)`
     * 旧包收到 table 时会因 `searchText/topK` 为 nil 而报错返回空，再回退位置参数。
     */
    override suspend fun getEmojis(query: EmojiQuery): List<PluginResultItem> =
        withContext(Dispatchers.IO) {
            val category = query.category ?: ""
            val keyword = query.keyword ?: ""
            val topK = query.topK.coerceAtLeast(1)

            val tableArgs = LuaTable()
            tableArgs.set("category", LuaValue.valueOf(category))
            tableArgs.set("keyword", LuaValue.valueOf(keyword))
            tableArgs.set("topK", LuaValue.valueOf(topK))
            val tableResult = runtime.call(LuaPluginContract.FN_GET_EMOJIS, tableArgs)
            val fromTable = parseResultItems(tableResult, "getEmojis 返回")
            if (fromTable.isNotEmpty()) return@withContext fromTable

            // 旧版位置参数：getEmojis(category, searchText, topK)
            val legacyResult = runtime.call(
                LuaPluginContract.FN_GET_EMOJIS,
                LuaValue.valueOf(category),
                LuaValue.valueOf(keyword),
                LuaValue.valueOf(topK),
            )
            parseResultItems(legacyResult, "getEmojis(legacy) 返回")
        }

    override suspend fun getCategories(): List<String> = withContext(Dispatchers.IO) {
        LuaScriptRuntime.tableToList(runtime.call(LuaPluginContract.FN_GET_CATEGORIES))
            .mapNotNull { it.tojstring() }
    }
}
