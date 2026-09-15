package com.kingzcheung.xime.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 启动器图标跟随键盘主题：
 * - 动态配色：使用 system_accent 画自适应图标（不依赖桌面「主题图标」开关）
 * - 固定主题：按主题强调色色相切换到对应色板图标
 *
 * 注意：切换 alias 时必须先能真正启用目标，再禁用其它；
 * 不可对已禁用组件调用无 MATCH_DISABLED 的 getActivityInfo（会误判失败，
 * 进而把唯一 LAUNCHER 入口关掉，桌面图标点开只进系统设置）。
 */
object LauncherIconHelper {
    private const val TAG = "LauncherIconHelper"

    private const val ALIAS_DYNAMIC = "com.kingzcheung.xime.LauncherDynamic"
    private const val ALIAS_PURPLE = "com.kingzcheung.xime.LauncherPurple"
    private const val ALIAS_BLUE = "com.kingzcheung.xime.LauncherBlue"
    private const val ALIAS_ORANGE = "com.kingzcheung.xime.LauncherOrange"
    private const val ALIAS_PINK = "com.kingzcheung.xime.LauncherPink"
    private const val ALIAS_TEAL = "com.kingzcheung.xime.LauncherTeal"
    private const val ALIAS_GREEN = "com.kingzcheung.xime.LauncherGreen"
    private const val ALIAS_GRAY = "com.kingzcheung.xime.LauncherGray"
    private const val ALIAS_BROWN = "com.kingzcheung.xime.LauncherBrown"

    /** 旧版固定图标 alias，升级后必须关掉，避免桌面出现两个图标。 */
    private const val ALIAS_STATIC_LEGACY = "com.kingzcheung.xime.LauncherStatic"

    /** manifest 里默认 enabled=true 的入口，启用失败时兜底。 */
    private val DEFAULT_ENABLED = setOf(ALIAS_DYNAMIC)

    private val ALL_ALIASES = listOf(
        ALIAS_DYNAMIC,
        ALIAS_PURPLE,
        ALIAS_BLUE,
        ALIAS_ORANGE,
        ALIAS_PINK,
        ALIAS_TEAL,
        ALIAS_GREEN,
        ALIAS_GRAY,
        ALIAS_BROWN,
        ALIAS_STATIC_LEGACY,
    )

    fun syncToCurrentTheme(context: Context) {
        sync(context, SettingsPreferences.getKeyboardTheme(context))
    }

    fun sync(context: Context, themeId: String) {
        val app = context.applicationContext
        val pm = app.packageManager
        var target = resolveAlias(app, themeId)
        try {
            // 先启用目标；失败则退回动态入口，绝不在「无入口」时继续禁用
            if (!ensureEnabled(pm, app, target)) {
                Log.w(TAG, "enable $target failed, fallback to $ALIAS_DYNAMIC")
                target = ALIAS_DYNAMIC
                if (!ensureEnabled(pm, app, target)) {
                    Log.e(TAG, "fallback launcher also failed; abort disable pass")
                    return
                }
            }
            for (alias in ALL_ALIASES) {
                if (alias != target) {
                    setAliasEnabled(pm, app, alias, enabled = false)
                }
            }
            // 再确认至少还有一个有效 LAUNCHER
            if (!isEffectivelyEnabled(pm, app, target)) {
                Log.e(TAG, "target disabled unexpectedly; re-enable $ALIAS_DYNAMIC")
                ensureEnabled(pm, app, ALIAS_DYNAMIC)
            }
            Log.d(TAG, "launcher icon -> $target (theme=$themeId)")
        } catch (e: Exception) {
            Log.w(TAG, "sync launcher icon failed", e)
            try {
                ensureEnabled(pm, app, ALIAS_DYNAMIC)
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveAlias(context: Context, themeId: String): String {
        if (themeId == DynamicThemes.THEME_ID && DynamicThemes.isSupported()) {
            return ALIAS_DYNAMIC
        }
        val accent = KeyboardThemes.getThemeById(themeId).accentLight
        return paletteAliasFor(accent)
    }

    /** 按强调色色相落到预置色板 alias。 */
    internal fun paletteAliasFor(accent: Color): String {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent.toArgb(), hsl)
        val hue = hsl[0]
        val sat = hsl[1]
        if (sat < 0.12f) return ALIAS_GRAY
        return when {
            hue < 20f || hue >= 340f -> ALIAS_PINK
            hue < 55f -> ALIAS_ORANGE
            hue < 80f -> ALIAS_BROWN
            hue < 155f -> ALIAS_GREEN
            hue < 195f -> ALIAS_TEAL
            hue < 255f -> ALIAS_BLUE
            hue < 310f -> ALIAS_PURPLE
            else -> ALIAS_PINK
        }
    }

    private fun ensureEnabled(
        pm: PackageManager,
        context: Context,
        className: String,
    ): Boolean {
        setAliasEnabled(pm, context, className, enabled = true)
        return isEffectivelyEnabled(pm, context, className)
    }

    private fun isEffectivelyEnabled(
        pm: PackageManager,
        context: Context,
        className: String,
    ): Boolean {
        val component = ComponentName(context, className)
        return when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false
            else -> className in DEFAULT_ENABLED // DEFAULT：看 manifest 默认
        }
    }

    private fun setAliasEnabled(
        pm: PackageManager,
        context: Context,
        className: String,
        enabled: Boolean,
    ) {
        val component = ComponentName(context, className)
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        try {
            val cur = pm.getComponentEnabledSetting(component)
            if (cur == newState) return
            pm.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP,
            )
        } catch (e: Exception) {
            Log.w(TAG, "setAliasEnabled($className, $enabled) failed", e)
        }
    }
}
