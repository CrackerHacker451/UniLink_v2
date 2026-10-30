package com.example.unilink.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.unilink.ui.AdminDashboardScreen
import com.example.unilink.utils.ThemeUtils

class AdminDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try { ThemeUtils.applyTheme(this) } catch (_: Exception) {}
        super.onCreate(savedInstanceState)

        setContent {
            AdminDashboardScreen(
                onNavigateBack = { finish() }
            )
        }
    }
}
