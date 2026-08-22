package com.example.unilink.utils

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.unilink.R

object ThemeUtils {
    private const val PREFS_NAME = "unilink_theme_prefs"
    private const val KEY_THEME = "selected_theme"
    private const val KEY_AUTO_THEME = "auto_theme_enabled"

    fun applyTheme(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val theme = prefs.getString(KEY_THEME, "Ars White")
        
        val themeResId = when (theme) {
            "Indigo Liquid" -> R.style.Theme_UniLink_Indigo
            "Ocean Glass" -> R.style.Theme_UniLink_Teal
            "Rose Crystal" -> R.style.Theme_UniLink_Rose
            "Amber Glow" -> R.style.Theme_UniLink_Amber
            "Midnight Gold" -> R.style.Theme_UniLink_Gold
            "Classic theme" -> R.style.Theme_UniLink_Light
            "Ars Dark" -> R.style.Theme_UniLink_ArsDark
            "Aura Glass" -> R.style.Theme_UniLink_AuraGlass
            "Cyber" -> R.style.Theme_UniLink
            else -> R.style.Theme_UniLink_White // Default Ars White
        }
        activity.setTheme(themeResId)

        // Enable Full Screen / Edge-to-Edge
        if (activity is ComponentActivity) {
            activity.enableEdgeToEdge()
        } else {
            // Fallback for non-ComponentActivity (though mostly everything is ComponentActivity now)
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.statusBarColor = Color.TRANSPARENT
            activity.window.navigationBarColor = Color.TRANSPARENT
        }

        // Manage Status Bar Icon Colors
        val isDarkTheme = when (theme) {
            "Ars White", "Classic theme", "Ocean Glass", "Rose Crystal" -> false
            else -> true
        }
        
        val window = activity.window
        val decorView = window.decorView
        val wic = WindowInsetsControllerCompat(window, decorView)
        wic.isAppearanceLightStatusBars = !isDarkTheme
        wic.isAppearanceLightNavigationBars = !isDarkTheme
    }

    fun saveTheme(context: Context, themeName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, themeName)
            .apply()
    }
    
    fun getSelectedTheme(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, "Ars White") ?: "Ars White"
    }

    fun setAutoTheme(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_THEME, enabled)
            .apply()
    }

    fun isAutoThemeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_THEME, false)
    }

    private const val KEY_APP_LOCK = "app_lock_enabled"

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_LOCK, enabled)
            .apply()
    }

    fun isAppLockEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_LOCK, false)
    }

    fun getDrawableIdByName(context: Context, name: String?): Int {
        if (name.isNullOrEmpty()) return 0
        return context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    fun getTextPrimaryColor(context: Context): Int {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(R.attr.textPrimaryColor, typedValue, true)
        return typedValue.data
    }
}
