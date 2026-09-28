package com.kingzcheung.xime.bitwarden

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

/**
 * 条目图标来源：本机 APK 图标优先，其次按域名拉 Bitwarden 图标服务，否则地球回退。
 * 应用图标仅本地解析，不上传保险库。
 */
sealed class VaultItemIconSource {
    data class AppIcon(val packageName: String) : VaultItemIconSource()
    data class WebIcon(val url: String) : VaultItemIconSource()
    data object Fallback : VaultItemIconSource()
}

object VaultItemIcons {
    /** 官方图标服务：https://icons.bitwarden.net/{hostname}/icon.png */
    private const val BITWARDEN_ICON_HOST = "https://icons.bitwarden.net"

    fun resolve(item: VaultLoginItem, context: Context): VaultItemIconSource {
        val pm = context.packageManager
        for (uri in item.uris) {
            val pkg = androidAppPackage(uri) ?: continue
            if (isPackageInstalled(pm, pkg)) {
                return VaultItemIconSource.AppIcon(pkg)
            }
        }
        for (uri in item.uris) {
            val host = httpHostname(uri) ?: continue
            return VaultItemIconSource.WebIcon("$BITWARDEN_ICON_HOST/$host/icon.png")
        }
        return VaultItemIconSource.Fallback
    }

    fun loadAppDrawable(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun androidAppPackage(uri: String): String? {
        val t = uri.trim()
        if (!t.startsWith("androidapp://", ignoreCase = true)) return null
        val pkg = t.substring("androidapp://".length)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .trim()
        return pkg.takeIf { it.isNotBlank() && it.contains('.') }
    }

    fun httpHostname(uri: String): String? {
        val t = uri.trim()
        if (!t.startsWith("http://", ignoreCase = true) &&
            !t.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        return try {
            Uri.parse(t).host
                ?.trim()
                ?.lowercase()
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() && it.contains('.') }
        } catch (_: Exception) {
            null
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
