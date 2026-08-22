package com.example.unilink.utils

import android.content.Context

object PrefsUtils {
    private const val PREFS_NAME = "unilink_prefs"
    private const val KEY_AURA_ENABLED = "aura_enabled"

    fun isAuraEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AURA_ENABLED, true)
    }

    fun setAuraEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AURA_ENABLED, enabled).apply()
    }
}
