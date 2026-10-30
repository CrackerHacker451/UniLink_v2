package com.example.unilink.utils

import android.content.Context
import android.content.Intent
import com.example.unilink.activities.BarterBlockActivity
import com.example.unilink.activities.CampusArenaActivity
import com.example.unilink.activities.CreatePostActivity
import com.example.unilink.activities.CreateTeamActivity
import com.example.unilink.activities.DareDropActivity
import com.example.unilink.activities.DoubtBoardActivity
import com.example.unilink.activities.FindTeamActivity
import com.example.unilink.activities.FocusRoomActivity
import com.example.unilink.activities.LeaderboardActivity
import com.example.unilink.activities.LiveStreamActivity
import com.example.unilink.activities.NearbyStudentsActivity
import com.example.unilink.activities.NotificationsActivity
import com.example.unilink.activities.RandomMatchActivity
import com.example.unilink.activities.SettingsActivity
import com.example.unilink.activities.VaultActivity

sealed class AgentExecutionResult {
    data class ExecutedAction(val message: String, val intent: Intent) : AgentExecutionResult()
    object HandledByAi : AgentExecutionResult()
}

object NewtonAiAgent {

    fun parseAndExecuteCommand(context: Context, prompt: String): AgentExecutionResult {
        val lower = prompt.lowercase().trim()

        return when {
            // Live Stream Intent
            lower.contains("stream") || lower.contains("live") || lower.contains("broadcast") -> {
                val extractedTitle = prompt.substringAfter("about", "").substringAfter("live", "").trim()
                val title = if (extractedTitle.length > 3) extractedTitle else "Campus Live Stream 🎥"
                val intent = Intent(context, LiveStreamActivity::class.java).apply {
                    putExtra("streamTitle", title)
                    putExtra("autoOpenCreate", true)
                }
                AgentExecutionResult.ExecutedAction(
                    "🎥 Right away! Launching Newton's Lobby Live Broadcast stage for '$title'...",
                    intent
                )
            }

            // Barter Intent
            lower.contains("barter") || lower.contains("trade") || lower.contains("sell") || lower.contains("bartein") || lower.contains("exchange") -> {
                val intent = Intent(context, BarterBlockActivity::class.java).apply {
                    putExtra("autoOpenCreate", true)
                }
                AgentExecutionResult.ExecutedAction(
                    "📦 Absolutely! Opening Barter Block so you can list your item for trade...",
                    intent
                )
            }

            // Create Team Intent
            lower.contains("create team") || lower.contains("start team") || lower.contains("build team") -> {
                val intent = Intent(context, CreateTeamActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "👥 Opening Team Creator to assemble your hackathon squad...",
                    intent
                )
            }

            // Find Team Intent
            lower.contains("find team") || lower.contains("team finder") || lower.contains("look for team") || lower.contains("join team") || lower.contains("teammate") -> {
                val intent = Intent(context, FindTeamActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "👥 Opening Team Finder to connect with campus collaborators...",
                    intent
                )
            }

            // Post Doubt Intent
            lower.contains("doubt") || lower.contains("ask question") || lower.contains("homework") || lower.contains("question") -> {
                val intent = Intent(context, DoubtBoardActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "🧠 Opening Campus Doubt Board to help solve your academic questions...",
                    intent
                )
            }

            // Start Dare Intent
            lower.contains("dare") -> {
                val intent = Intent(context, DareDropActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "🔥 Action time! Opening Campus Dare Drop...",
                    intent
                )
            }

            // Focus Room Intent
            lower.contains("focus") || lower.contains("pomodoro") || lower.contains("study mode") -> {
                val intent = Intent(context, FocusRoomActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "⏳ Setting up your Focus Room session for deep work...",
                    intent
                )
            }

            // Scan Nearby Intent
            lower.contains("nearby") || lower.contains("radar") || lower.contains("scan campus") || lower.contains("peer") -> {
                val intent = Intent(context, NearbyStudentsActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "📡 Scanning campus radar for nearby students...",
                    intent
                )
            }

            // Leaderboard Intent
            lower.contains("leaderboard") || lower.contains("top student") || lower.contains("xp") || lower.contains("rank") -> {
                val intent = Intent(context, LeaderboardActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "🏆 Opening Campus Leaderboard to view top innovators...",
                    intent
                )
            }

            // Settings / Royal Pass Intent
            lower.contains("settings") || lower.contains("royal pass") || lower.contains("pro pass") || lower.contains("theme") -> {
                val intent = Intent(context, SettingsActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "⚙️ Opening Settings & Royal VIP Portal...",
                    intent
                )
            }

            // Vault Notes Intent
            lower.contains("vault") || lower.contains("notes") || lower.contains("replay") || lower.contains("pdf") || lower.contains("document") -> {
                val intent = Intent(context, VaultActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "📚 Accessing Newton's Lobby Notes Vault...",
                    intent
                )
            }

            // Random Match Intent
            lower.contains("random") || lower.contains("speed link") -> {
                val intent = Intent(context, RandomMatchActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "⚡ Connecting you to Campus Random Peer Match...",
                    intent
                )
            }

            // Create Post Intent
            lower.contains("post") || lower.contains("publish") || lower.contains("pulse") -> {
                val intent = Intent(context, CreatePostActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "✍️ Opening Campus Feed Post Creator...",
                    intent
                )
            }

            // Notifications Intent
            lower.contains("notification") || lower.contains("alert") || lower.contains("inbox") -> {
                val intent = Intent(context, NotificationsActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "🔔 Opening Campus Notifications...",
                    intent
                )
            }

            // Arena Intent
            lower.contains("arena") || lower.contains("hub") -> {
                val intent = Intent(context, CampusArenaActivity::class.java)
                AgentExecutionResult.ExecutedAction(
                    "🎪 Entering Campus Arena...",
                    intent
                )
            }

            else -> AgentExecutionResult.HandledByAi
        }
    }
}
