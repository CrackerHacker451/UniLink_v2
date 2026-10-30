package com.example.unilink.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.unilink.ui.DareDropScreen
import com.example.unilink.utils.ThemeUtils

class DareDropActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try { ThemeUtils.applyTheme(this) } catch (_: Exception) {}
        super.onCreate(savedInstanceState)

        setContent {
            DareDropScreen(
                onNavigateBack = { finish() },
            )
        }
    }
}
