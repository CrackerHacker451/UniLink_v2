package com.example.unilink.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date

data class SystemStats(
    val totalUsers: Int = 0,
    val totalPosts: Int = 0,
    val totalDoubts: Int = 0,
    val totalBarterItems: Int = 0,
    val totalLiveStreams: Int = 0,
    val totalDares: Int = 0,
    val onlineUsers: Int = 0
)

data class AppConfig(
    val isMaintenanceMode: Boolean = false,
    val maintenanceMessage: String = "UniLink is under scheduled maintenance by Admin. Please check back shortly!",
    val isLiveStreamingEnabled: Boolean = true,
    val isBarterTradingEnabled: Boolean = true,
    val isRandomMatchEnabled: Boolean = true
)

data class AdminAuditLog(
    val logId: String = "",
    val adminUid: String = "",
    val adminName: String = "",
    val actionType: String = "",
    val targetId: String = "",
    val details: String = "",
    val timestamp: Date? = null
)

class AdminRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun logAdminAction(actionType: String, targetId: String, details: String) {
        val currentUid = auth.currentUser?.uid ?: "System"
        val logRef = db.collection("admin_audit_logs").document()

        db.collection("users").document(currentUid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: auth.currentUser?.displayName ?: "Royal Admin"
            val logData = hashMapOf(
                "logId" to logRef.id,
                "adminUid" to currentUid,
                "adminName" to name,
                "actionType" to actionType,
                "targetId" to targetId,
                "details" to details,
                "timestamp" to FieldValue.serverTimestamp()
            )
            logRef.set(logData)
        }.addOnFailureListener {
            val logData = hashMapOf(
                "logId" to logRef.id,
                "adminUid" to currentUid,
                "adminName" to "Royal Admin",
                "actionType" to actionType,
                "targetId" to targetId,
                "details" to details,
                "timestamp" to FieldValue.serverTimestamp()
            )
            logRef.set(logData)
        }
    }

    fun observeSystemStats(onUpdate: (SystemStats) -> Unit): ListenerRegistration {
        return db.collection("users").addSnapshotListener { userSnap, _ ->
            val userCount = userSnap?.size() ?: 0
            val onlineCount = userSnap?.documents?.count { it.getBoolean("isOnline") == true } ?: 0

            db.collection("posts").get().addOnSuccessListener { postSnap ->
                val postCount = postSnap?.size() ?: 0

                db.collection("doubts").get().addOnSuccessListener { doubtSnap ->
                    val doubtCount = doubtSnap?.size() ?: 0

                    db.collection("barter_trades").get().addOnSuccessListener { barterSnap ->
                        val barterCount = barterSnap?.size() ?: 0

                        db.collection("live_streams").get().addOnSuccessListener { streamSnap ->
                            val streamCount = streamSnap?.size() ?: 0

                            db.collection("dares").get().addOnSuccessListener { dareSnap ->
                                val dareCount = dareSnap?.size() ?: 0

                                onUpdate(
                                    SystemStats(
                                        totalUsers = userCount,
                                        totalPosts = postCount,
                                        totalDoubts = doubtCount,
                                        totalBarterItems = barterCount,
                                        totalLiveStreams = streamCount,
                                        totalDares = dareCount,
                                        onlineUsers = onlineCount
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun grantCoinsToUser(targetUid: String, coinsAmount: Int, onComplete: (Boolean) -> Unit) {
        if (targetUid.isEmpty()) return onComplete(false)
        db.collection("users").document(targetUid)
            .update(
                mapOf(
                    "uniCoins" to FieldValue.increment(coinsAmount.toLong()),
                    "vouchCount" to FieldValue.increment(10L),
                    "totalXp" to FieldValue.increment(500L)
                )
            )
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    logAdminAction("GRANT_COINS", targetUid, "Granted $coinsAmount UniCoins to user $targetUid")
                    NotificationRepository().sendNotification(
                        receiverUid = targetUid,
                        type = "reward",
                        title = "👑 Royal Admin Reward Granted!",
                        message = "You have received $coinsAmount UniCoins from Newton's Lobby Admin! 💰✨"
                    )
                }
                onComplete(task.isSuccessful)
            }
    }

    fun grantCoinsToAllUsers(coinsAmount: Int, onComplete: (Boolean) -> Unit) {
        db.collection("users").get().addOnSuccessListener { snapshot ->
            if (snapshot == null || snapshot.isEmpty) return@addOnSuccessListener onComplete(false)

            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "uniCoins" to FieldValue.increment(coinsAmount.toLong()),
                        "vouchCount" to FieldValue.increment(5L),
                        "totalXp" to FieldValue.increment(200L)
                    )
                )
            }
            batch.commit().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    logAdminAction("MASS_AIRDROP", "ALL_USERS", "Airdropped $coinsAmount UniCoins to ${snapshot.size()} users")
                    snapshot.documents.forEach { doc ->
                        NotificationRepository().sendNotification(
                            receiverUid = doc.id,
                            type = "reward",
                            title = "🎉 Campus Mega Airdrop!",
                            message = "The Admin dropped $coinsAmount UniCoins into your wallet! 💰 Enjoy!"
                        )
                    }
                }
                onComplete(task.isSuccessful)
            }
        }.addOnFailureListener {
            onComplete(false)
        }
    }

    fun grantVipAndBadges(targetUid: String, vipLevel: Int, badgeName: String, onComplete: (Boolean) -> Unit) {
        val updates = hashMapOf<String, Any>(
            "isVip" to true,
            "isPremium" to true,
            "vipLevel" to vipLevel,
            "badges" to FieldValue.arrayUnion(badgeName, "👑 Royal VIP", "⚡ Verified Student")
        )
        db.collection("users").document(targetUid).update(updates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    logAdminAction("GRANT_VIP", targetUid, "Granted VIP Level $vipLevel & Badge '$badgeName'")
                }
                onComplete(task.isSuccessful)
            }
    }

    fun resetUserBalance(targetUid: String, newCoins: Int, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(targetUid).update("uniCoins", newCoins)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    logAdminAction("RESET_BALANCE", targetUid, "Set UniCoins balance to $newCoins")
                }
                onComplete(task.isSuccessful)
            }
    }

    fun toggleAdminStatus(targetUid: String, makeAdmin: Boolean, onComplete: (Boolean) -> Unit) {
        val userType = if (makeAdmin) "Admin" else "Student"
        db.collection("users").document(targetUid)
            .update(
                mapOf(
                    "isAdmin" to makeAdmin,
                    "userType" to userType,
                    "isVip" to makeAdmin
                )
            )
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    logAdminAction("TOGGLE_ADMIN", targetUid, "Admin status set to $makeAdmin")
                }
                onComplete(task.isSuccessful)
            }
    }

    fun toggleBanUser(targetUid: String, isBanned: Boolean, onComplete: (Boolean) -> Unit) {
        db.collection("users").document(targetUid)
            .update("isBanned", isBanned)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    logAdminAction("TOGGLE_BAN", targetUid, "Ban status set to $isBanned")
                }
                onComplete(task.isSuccessful)
            }
    }

    fun toggleMaintenanceMode(isEnabled: Boolean, message: String, onComplete: (Boolean) -> Unit) {
        val configData = hashMapOf(
            "isMaintenanceMode" to isEnabled,
            "maintenanceMessage" to message,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("app_config").document("system_settings").set(configData, com.google.firebase.firestore.SetOptions.merge())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    logAdminAction("TOGGLE_MAINTENANCE", "SYSTEM", "Maintenance mode set to $isEnabled")
                }
                onComplete(task.isSuccessful)
            }
    }

    fun observeAppConfig(onUpdate: (AppConfig) -> Unit): ListenerRegistration {
        return db.collection("app_config").document("system_settings")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val isMaint = snapshot.getBoolean("isMaintenanceMode") ?: false
                    val msg = snapshot.getString("maintenanceMessage") ?: "UniLink is under scheduled maintenance."
                    val isLive = snapshot.getBoolean("isLiveStreamingEnabled") ?: true
                    val isBarter = snapshot.getBoolean("isBarterTradingEnabled") ?: true
                    val isRandom = snapshot.getBoolean("isRandomMatchEnabled") ?: true
                    onUpdate(AppConfig(isMaint, msg, isLive, isBarter, isRandom))
                } else {
                    onUpdate(AppConfig())
                }
            }
    }

    fun observeAuditLogs(onUpdate: (List<AdminAuditLog>) -> Unit): ListenerRegistration {
        return db.collection("admin_audit_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                val logs = snapshot?.mapNotNull { doc ->
                    try {
                        val id = doc.getString("logId") ?: doc.id
                        val adminUid = doc.getString("adminUid") ?: ""
                        val adminName = doc.getString("adminName") ?: "Admin"
                        val action = doc.getString("actionType") ?: ""
                        val target = doc.getString("targetId") ?: ""
                        val details = doc.getString("details") ?: ""
                        val ts = doc.getTimestamp("timestamp")?.toDate()
                        AdminAuditLog(id, adminUid, adminName, action, target, details, ts)
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
                onUpdate(logs)
            }
    }

    fun deletePost(postId: String, onComplete: (Boolean) -> Unit) {
        db.collection("posts").document(postId).delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) logAdminAction("DELETE_POST", postId, "Deleted post $postId")
                onComplete(task.isSuccessful)
            }
    }

    fun deleteDoubt(doubtId: String, onComplete: (Boolean) -> Unit) {
        db.collection("doubts").document(doubtId).delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) logAdminAction("DELETE_DOUBT", doubtId, "Deleted doubt $doubtId")
                onComplete(task.isSuccessful)
            }
    }

    fun deleteBarterTrade(tradeId: String, onComplete: (Boolean) -> Unit) {
        db.collection("barter_trades").document(tradeId).delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) logAdminAction("DELETE_BARTER", tradeId, "Deleted barter trade $tradeId")
                onComplete(task.isSuccessful)
            }
    }

    fun deleteDare(dareId: String, onComplete: (Boolean) -> Unit) {
        db.collection("dares").document(dareId).delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) logAdminAction("DELETE_DARE", dareId, "Deleted dare $dareId")
                onComplete(task.isSuccessful)
            }
    }

    fun purgeEndedStreams(onComplete: (Int) -> Unit) {
        db.collection("live_streams").whereEqualTo("isLive", false).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot == null || snapshot.isEmpty) return@addOnSuccessListener onComplete(0)
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnCompleteListener { task ->
                    val count = if (task.isSuccessful) snapshot.size() else 0
                    if (task.isSuccessful) logAdminAction("PURGE_STREAMS", "ALL", "Purged $count ended streams")
                    onComplete(count)
                }
            }
    }
}
