package com.rkdesign.glasslauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import java.util.Locale

internal object AppCatalog {
    private val iconCache = object : LruCache<String, Drawable>(48) {}

    fun load(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        val unique = LinkedHashMap<String, LaunchableApp>()
        for (item in resolved) {
            val pkg = item.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            try {
                val label = item.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg.substringAfterLast('.') }
                val icon = cachedIcon(pkg) { item.loadIcon(pm) }
                unique[pkg] = LaunchableApp(label, pkg, icon)
            } catch (_: PackageManager.NameNotFoundException) {
                // An app may be removed while the launcher is refreshing its list.
            } catch (_: RuntimeException) {
                // Skip packages whose icon or metadata is unavailable.
            }
        }
        return unique.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun iconFor(context: Context, packageName: String): Drawable? {
        return try {
            cachedIcon(packageName) { context.packageManager.getApplicationIcon(packageName) }
        } catch (_: Exception) { null }
    }

    private fun cachedIcon(key: String, loader: () -> Drawable): Drawable {
        synchronized(iconCache) {
            iconCache.get(key)?.let { return it }
            val icon = loader()
            iconCache.put(key, icon)
            return icon
        }
    }

    fun prioritize(apps: List<LaunchableApp>): List<LaunchableApp> {
        val keywords = listOf(
            listOf("phone", "telepon", "dialer", "panggilan"),
            listOf("camera", "kamera"),
            listOf("message", "pesan", "sms"),
            listOf("chrome", "browser", "peramban"),
            listOf("whatsapp"),
            listOf("photos", "gallery", "galeri", "foto"),
            listOf("setting", "pengaturan"),
            listOf("clock", "jam"),
            listOf("gmail", "mail", "email"),
            listOf("play store"),
            listOf("maps", "peta"),
            listOf("contact", "kontak")
        )
        val ordered = mutableListOf<LaunchableApp>()
        val remaining = apps.toMutableList()
        for (group in keywords) {
            val match = remaining.firstOrNull { app ->
                val name = app.label.lowercase(Locale.ROOT)
                group.any { name.contains(it) }
            }
            if (match != null) {
                ordered.add(match)
                remaining.remove(match)
            }
        }
        ordered.addAll(remaining)
        return ordered
    }
}
