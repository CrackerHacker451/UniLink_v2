package com.example.unilink.utils

import android.app.Activity
import android.content.Context
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.unilink.R

object ThemeUtils {
    private const val PREFS_NAME = "unilink_theme_prefs"
    private const val KEY_THEME = "selected_theme"
    private const val KEY_AUTO_THEME = "auto_theme_enabled"
    private const val KEY_APP_LOCK = "app_lock_enabled"

    private var isSessionBiometricPassed = false

    fun isBiometricSessionPassed(): Boolean = isSessionBiometricPassed

    fun setBiometricSessionPassed(passed: Boolean) {
        isSessionBiometricPassed = passed
    }

    fun canAuthenticateBiometrics(context: Context): Boolean {
        return try {
            val manager = BiometricManager.from(context)
            val result = manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            result == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    fun applyTheme(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val theme = prefs.getString(KEY_THEME, "DarkzMidnight") ?: "DarkzMidnight"

        val themeResId = when (theme) {
            "DarkzMidnight" -> R.style.Theme_UniLink_DarkzMidnight
            "DarkOlive" -> R.style.Theme_UniLink_DarkOlive
            "Light" -> R.style.Theme_UniLink_Light
            "Teal" -> R.style.Theme_UniLink_Teal
            "Indigo" -> R.style.Theme_UniLink_Indigo
            "Rose" -> R.style.Theme_UniLink_Rose
            "Amber" -> R.style.Theme_UniLink_Amber
            else -> R.style.Theme_UniLink_DarkzMidnight
        }
        activity.setTheme(themeResId)

        val isDarkTheme = (theme != "Light")

        if (activity is ComponentActivity) {
            val statusBarStyle = if (isDarkTheme) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
            val navigationBarStyle = if (isDarkTheme) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
            activity.enableEdgeToEdge(statusBarStyle, navigationBarStyle)
        } else {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.statusBarColor = Color.TRANSPARENT
            activity.window.navigationBarColor = Color.TRANSPARENT

            val wic = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            wic.isAppearanceLightStatusBars = !isDarkTheme
            wic.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    fun saveTheme(context: Context, themeName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, themeName)
            .apply()

        try {
            com.example.unilink.repository.UserRepository().updateUserPremiumSettings(appTheme = themeName) {}
        } catch (_: Exception) {}
    }

    fun getSelectedTheme(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, "Dark") ?: "Dark"
    }

    fun checkAndRecreateIfThemeChanged(activity: Activity, lastAppliedTheme: String): Boolean {
        val currentTheme = getSelectedTheme(activity)
        if (lastAppliedTheme.isNotEmpty() && lastAppliedTheme != currentTheme) {
            activity.recreate()
            return true
        }
        return false
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

    fun extractDominantHexColor(bitmap: android.graphics.Bitmap): String {
        return try {
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 40, 40, false)
            var redSum = 0L
            var greenSum = 0L
            var blueSum = 0L
            var count = 0

            for (x in 0 until scaled.width) {
                for (y in 0 until scaled.height) {
                    val pixel = scaled.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    if (r + g + b > 80) {
                        redSum += r
                        greenSum += g
                        blueSum += b
                        count++
                    }
                }
            }

            if (count == 0) return "#A855F7"

            val avgR = (redSum / count).toInt().coerceIn(60, 240)
            val avgG = (greenSum / count).toInt().coerceIn(60, 240)
            val avgB = (blueSum / count).toInt().coerceIn(60, 240)

            String.format("#%02X%02X%02X", avgR, avgG, avgB)
        } catch (_: Exception) {
            "#A855F7"
        }
    }
}
