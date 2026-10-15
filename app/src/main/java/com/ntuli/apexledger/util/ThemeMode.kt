package com.ntuli.apexledger.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Remembers the person's light/dark choice and applies it. */
object ThemeMode {
    private const val PREFS = "app_prefs"
    private const val KEY = "dark_mode"   // 0 = follow system, 1 = light, 2 = dark

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun isDark(context: Context): Boolean {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)
        if (saved != 0) return saved == 2
        val night = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, if (dark) 2 else 1).apply()
        apply(context)
    }
}
