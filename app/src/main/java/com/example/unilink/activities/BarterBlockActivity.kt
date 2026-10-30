package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.unilink.ui.BarterBlockScreen
import com.example.unilink.utils.ThemeUtils

class BarterBlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try { ThemeUtils.applyTheme(this) } catch (_: Exception) {}
        super.onCreate(savedInstanceState)

        val autoOpen = intent.getBooleanExtra("autoOpenCreate", false)

        setContent {
            BarterBlockScreen(
                initialShowCreateDialog = autoOpen,
                onNavigateBack = { finish() },
                onChatTrader = { traderUid, traderName ->
                    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val cid = com.example.unilink.repository.ChatRepository().getChatId(currentUid, traderUid)
                    startActivity(Intent(this@BarterBlockActivity, ChatActivity::class.java).apply {
                        putExtra("chatId", cid)
                        putExtra("receiverId", traderUid)
                        putExtra("receiverName", traderName)
                    })
                }
            )
        }
    }
}
