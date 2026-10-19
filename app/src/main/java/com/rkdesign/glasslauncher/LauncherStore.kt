package com.rkdesign.glasslauncher

import android.content.Context

internal object LauncherStore {
    private const val PREFS = "rk_glass_prefs"
    fun get(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun readPackages(context: Context, key: String): MutableList<String> {
        val raw = get(context).getString(key, "").orEmpty()
        return raw.split('|').filter { it.isNotBlank() }.distinct().toMutableList()
    }

    fun savePackages(context: Context, key: String, values: List<String>) {
        get(context).edit().putString(key, values.distinct().joinToString("|")).apply()
    }
}
