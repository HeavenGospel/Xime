package com.kingzcheung.xime.data

import android.content.Context

/** 符号面板底部分类记忆（按 category.id，如 punctuationSymbols / englishSymbols）。 */
object SymbolPanelMemory {
    private const val PREFS = "symbol_panel_memory"
    private const val KEY_CATEGORY_ID = "category_id"

    fun saveCategoryId(context: Context, categoryId: String) {
        if (categoryId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CATEGORY_ID, categoryId)
            .apply()
    }

    fun loadCategoryId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CATEGORY_ID, null)
            ?.takeIf { it.isNotBlank() }
}
