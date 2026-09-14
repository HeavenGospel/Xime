package com.kingzcheung.xime.data

import android.content.Context
import android.os.SystemClock

/**
 * 表情面板顶栏/子分类记忆，以及从选图/裁剪页返回后恢复表情面板的标记。
 */
object EmojiPanelMemory {
    private const val PREFS = "emoji_panel_memory"
    private const val KEY_TAB = "tab_key"
    private const val KEY_SUB = "sub_index"
    private const val KEY_PENDING_RESTORE = "pending_restore_emoji"
    private const val KEY_RESTORE_UNTIL = "restore_until_elapsed"

    /** 返回后若干毫秒内每次弹出键盘都强制进表情收藏（抗 hide/show 竞态）。 */
    private const val RESTORE_WINDOW_MS = 8_000L

    const val TAB_BUILTIN = "builtin"
    const val TAB_FAVORITES = "favorites"

    fun pluginTabKey(pluginId: String): String = "plugin:$pluginId"

    fun save(context: Context, tabKey: String, subIndex: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TAB, tabKey)
            .putInt(KEY_SUB, subIndex.coerceAtLeast(0))
            .apply()
    }

    fun loadTabKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAB, TAB_BUILTIN) ?: TAB_BUILTIN

    fun loadSubIndex(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SUB, 0).coerceAtLeast(0)

    /** 选图/裁剪结束：记住收藏 Tab，并打开一段恢复窗口。 */
    fun markRestoreFavorites(context: Context) {
        val until = SystemClock.elapsedRealtime() + RESTORE_WINDOW_MS
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TAB, TAB_FAVORITES)
            .putInt(KEY_SUB, 0)
            .putBoolean(KEY_PENDING_RESTORE, true)
            .putLong(KEY_RESTORE_UNTIL, until)
            .commit()
    }

    fun setPendingRestoreEmoji(context: Context, pending: Boolean) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PENDING_RESTORE, pending)
        if (pending) {
            edit.putLong(KEY_RESTORE_UNTIL, SystemClock.elapsedRealtime() + RESTORE_WINDOW_MS)
            edit.putString(KEY_TAB, TAB_FAVORITES)
            edit.putInt(KEY_SUB, 0)
        }
        edit.commit()
    }

    fun hasPendingRestoreEmoji(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PENDING_RESTORE, false)) return true
        return SystemClock.elapsedRealtime() < prefs.getLong(KEY_RESTORE_UNTIL, 0L)
    }

    /**
     * 是否应在本次键盘弹出时打开表情面板。
     * 不立刻清标记，避免 onWindowShown → 短暂 hide → 再 show 时丢恢复。
     */
    fun shouldRestoreEmojiPanel(context: Context): Boolean = hasPendingRestoreEmoji(context)

    fun clearPendingRestoreEmoji(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PENDING_RESTORE, false)
            .putLong(KEY_RESTORE_UNTIL, 0L)
            .commit()
    }

    @Deprecated("用 shouldRestoreEmojiPanel + clearPendingRestoreEmoji")
    fun consumePendingRestoreEmoji(context: Context): Boolean {
        if (!shouldRestoreEmojiPanel(context)) return false
        clearPendingRestoreEmoji(context)
        return true
    }
}
