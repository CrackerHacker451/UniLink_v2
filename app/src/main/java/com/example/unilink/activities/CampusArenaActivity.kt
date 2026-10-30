package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.unilink.ui.CampusArenaScreen
import com.example.unilink.utils.ThemeUtils

class CampusArenaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try { ThemeUtils.applyTheme(this) } catch (_: Exception) {}
        super.onCreate(savedInstanceState)

        setContent {
            CampusArenaScreen(
                onNavigateBack = { finish() },
                onOpenAudioRoom = {
                    val intent = Intent(this, LiveStreamActivity::class.java)
                    startActivity(intent)
                },
                onOpenDareDrop = {
                    val intent = Intent(this, DareDropActivity::class.java)
                    startActivity(intent)
                },
                onOpenBarterBlock = {
                    val intent = Intent(this, BarterBlockActivity::class.java)
                    startActivity(intent)
                }
            )
        }
    }
}
