package com.kingzcheung.xime.bitwarden

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

object InstalledApps {
    fun loadLaunchable(pm: PackageManager): List<InstalledAppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolves = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val seen = HashSet<String>()
        val out = ArrayList<InstalledAppInfo>(resolves.size)
        for (ri in resolves) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (!seen.add(pkg)) continue
            val label = try {
                ri.loadLabel(pm)?.toString()?.trim().orEmpty()
            } catch (_: Exception) {
                ""
            }.ifBlank { pkg }
            val icon = try {
                ri.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            out += InstalledAppInfo(packageName = pkg, label = label, icon = icon)
        }
        return out.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.label },
        )
    }

    fun filter(apps: List<InstalledAppInfo>, query: String): List<InstalledAppInfo> {
        val q = query.trim()
        if (q.isEmpty()) return apps
        return apps.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
    }

    fun packageFromAndroidAppUri(uri: String): String {
        val t = uri.trim()
        if (!t.startsWith("androidapp://", ignoreCase = true)) return ""
        return t.substring("androidapp://".length)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .trim()
    }

    fun firstWebUri(uris: List<String>): String =
        uris.firstOrNull {
            it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true)
        }.orEmpty()

    fun firstAppPackage(uris: List<String>): String =
        uris.firstNotNullOfOrNull { uri ->
            packageFromAndroidAppUri(uri).takeIf { it.isNotBlank() }
        }.orEmpty()
}
