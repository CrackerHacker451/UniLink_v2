package com.example.unilink.repository

import com.example.unilink.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class UserRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun observeUser(uid: String, onResult: (User?) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            if (e != null) {
                onResult(null)
                return@addSnapshotListener
            }
            onResult(snapshot?.toObject(User::class.java))
        }
    }

    fun observeCurrentUser(onResult: (User?) -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: return null
        return observeUser(uid, onResult)
    }

    fun getCurrentUser(onResult: (User?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(null)
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                if (user != null) {
                    checkAndUpdateStreak(user) { updatedUser ->
                        onResult(updatedUser)
                    }
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    private fun checkAndUpdateStreak(user: User, onComplete: (User) -> Unit) {
        val now = java.util.Calendar.getInstance()
        val lastLogin = user.lastLoginDate?.let {
            val cal = java.util.Calendar.getInstance()
            cal.time = it
            cal
        }

        if (lastLogin == null) {
            // First time login or data missing
            val baseBonus = getBaseDailyReward(user)
            updateUserStreak(user.uid, 1, baseBonus, onComplete, user)
        } else {
            val diff = now.timeInMillis - lastLogin.timeInMillis
            val daysDiff = (diff / (1000 * 60 * 60 * 24)).toInt()

            if (daysDiff == 1) {
                // Consecutive day
                val baseBonus = getBaseDailyReward(user)
                updateUserStreak(user.uid, user.streakDays + 1, baseBonus, onComplete, user)
            } else if (daysDiff > 1) {
                // Streak broken
                val baseBonus = getBaseDailyReward(user)
                updateUserStreak(user.uid, 1, baseBonus, onComplete, user)
            } else {
                // Same day, no update needed
                onComplete(user)
            }
        }
    }

    private fun getBaseDailyReward(user: User): Int {
        var reward = 50 // Base XP
        if (user.isPremium) reward += 50
        if (user.isVip) {
            reward += when(user.vipLevel) {
                1 -> 100
                2 -> 250
                3 -> 500
                else -> 50
            }
        }
        return reward
    }

    private fun updateUserStreak(uid: String, streak: Int, xpGain: Int, onComplete: (User) -> Unit, oldUser: User) {
        val uniCoinGain = if (oldUser.isVip) (xpGain / 5) else (xpGain / 10)
        val newXp = oldUser.totalXp + xpGain
        val newImpact = newXp + (oldUser.vouchCount * 15)
        
        val updates = mapOf(
            "streakDays" to streak,
            "totalXp" to newXp,
            "impactScore" to newImpact,
            "uniCoins" to oldUser.uniCoins + uniCoinGain,
            "lastLoginDate" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                // Refetch user to get the actual server timestamp and updated data
                db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                    onComplete(doc.toObject(User::class.java) ?: oldUser)
                }
            }
            .addOnFailureListener {
                onComplete(oldUser)
            }
    }

    fun getAllUsers(onResult: (List<User>) -> Unit) {
        val currentUid = auth.currentUser?.uid
        db.collection("users").get()
            .addOnSuccessListener { documents ->
                val users = documents.mapNotNull { it.toObject(User::class.java) }
                    .filter { it.uid != currentUid }
                    .sortedWith(compareByDescending<User> { 
                        it.boostUntil?.after(java.util.Date()) == true 
                    }.thenByDescending { it.isVip }
                     .thenByDescending { it.totalXp })
                onResult(users)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun getNearbyUsers(onResult: (List<Pair<User, Double>>) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onResult(emptyList())
        getUserById(currentUid) { me ->
            if (me?.latitude == null || me.longitude == null) {
                onResult(emptyList())
                return@getUserById
            }

            db.collection("users").get().addOnSuccessListener { snapshots ->
                val nearby = snapshots.mapNotNull { it.toObject(User::class.java) }
                    .filter { it.uid != currentUid && it.latitude != null && it.longitude != null && !it.isIncognito }
                    .map { user ->
                        val distance = calculateDistance(
                            me.latitude!!, me.longitude!!,
                            user.latitude!!, user.longitude!!
                        )
                        user to distance
                    }
                    .filter { it.second <= 10.0 } // 10km radius
                    .sortedBy { it.second }
                
                onResult(nearby)
            }.addOnFailureListener {
                onResult(emptyList())
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    fun saveUser(user: User, onComplete: (Boolean) -> Unit) {
        // Use set with merge to preserve fields like linkers/linked if they exist
        db.collection("users").document(user.uid).set(user, SetOptions.merge())
            .addOnCompleteListener { task ->
                onComplete(task.isSuccessful)
            }
    }

    fun isUsernameUnique(username: String, onResult: (Boolean) -> Unit) {
        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { documents ->
                onResult(documents.isEmpty)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun getUserById(uid: String, onResult: (User?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                onResult(document.toObject(User::class.java))
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    fun blockUser(targetUid: String, onComplete: (Boolean) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onComplete(false)
        db.collection("users").document(currentUid)
            .update("blockedUsers", com.google.firebase.firestore.FieldValue.arrayUnion(targetUid))
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun unblockUser(targetUid: String, onComplete: (Boolean) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onComplete(false)
        db.collection("users").document(currentUid)
            .update("blockedUsers", com.google.firebase.firestore.FieldValue.arrayRemove(targetUid))
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun linkUser(targetUid: String, onComplete: (Boolean) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onComplete(false)
        val batch = db.batch()
        
        // Add to current user's linked list
        val currentUserRef = db.collection("users").document(currentUid)
        batch.set(currentUserRef, mapOf("linked" to com.google.firebase.firestore.FieldValue.arrayUnion(targetUid)), SetOptions.merge())
        
        // Add to target user's linkers list
        val targetUserRef = db.collection("users").document(targetUid)
        batch.set(targetUserRef, mapOf("linkers" to com.google.firebase.firestore.FieldValue.arrayUnion(currentUid)), SetOptions.merge())
        
        batch.commit().addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun unlinkUser(targetUid: String, onComplete: (Boolean) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onComplete(false)
        val batch = db.batch()
        
        // Remove from current user's linked list
        val currentUserRef = db.collection("users").document(currentUid)
        batch.set(currentUserRef, mapOf("linked" to com.google.firebase.firestore.FieldValue.arrayRemove(targetUid)), SetOptions.merge())
        
        // Remove from target user's linkers list
        val targetUserRef = db.collection("users").document(targetUid)
        batch.set(targetUserRef, mapOf("linkers" to com.google.firebase.firestore.FieldValue.arrayRemove(currentUid)), SetOptions.merge())
        
        batch.commit().addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun redeemPremiumCode(code: String, onComplete: (Boolean, String?) -> Unit) {
        val validCodes = listOf("PREMIUM100", "UNILINK_PRO", "STUDENT_ELITE", "PAPABOL1917", "NDSIR", "1234")
        if (code.uppercase() == "PAPABOL1917" || code.uppercase() == "NDSIR") {
            val uid = auth.currentUser?.uid ?: return onComplete(false, "User not logged in")
            val updates = mapOf(
                "isPremium" to true,
                "premium" to true,
                "isVip" to true,
                "vip" to true,
                "premiumCode" to code.uppercase(),
                "uniCoins" to com.google.firebase.firestore.FieldValue.increment(500) // Bonus coins for this special code
            )
            db.collection("users").document(uid).update(updates)
                .addOnSuccessListener { onComplete(true, "VIP Premium activated! +500 UniCoins awarded.") }
                .addOnFailureListener { onComplete(false, it.message) }
        } else if (code.uppercase() in validCodes) {
            val uid = auth.currentUser?.uid ?: return onComplete(false, "User not logged in")
            val updates = mapOf(
                "isPremium" to true,
                "premium" to true,
                "premiumCode" to code.uppercase()
            )
            db.collection("users").document(uid).update(updates)
                .addOnSuccessListener { onComplete(true, "Premium activated!") }
                .addOnFailureListener { onComplete(false, it.message) }
        } else {
            onComplete(false, "Invalid premium code")
        }
    }

    fun activatePremium(onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val updates = mapOf(
            "isPremium" to true,
            "premium" to true,
            "premiumCode" to "DIRECT_SUBSCRIPTION"
        )
        // Using set with merge is safer than update if the document might not exist
        db.collection("users").document(uid).set(updates, SetOptions.merge())
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun activateVip(level: Int, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val vipBadge = when(level) {
            1 -> "Gold VIP"
            2 -> "Platinum VIP"
            3 -> "Diamond VIP"
            else -> "VIP"
        }
        val updates = mapOf(
            "isPremium" to true,
            "premium" to true,
            "isVip" to true,
            "vip" to true,
            "vipLevel" to level,
            "premiumCode" to "VIP_UPGRADE_LVL_$level",
            "badges" to com.google.firebase.firestore.FieldValue.arrayUnion(vipBadge)
        )
        db.collection("users").document(uid).set(updates, SetOptions.merge())
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun updateUserPremiumSettings(bannerResId: Int? = null, bannerName: String? = null, wallpaperResId: Int? = null, profilePicUrl: String? = null, appTheme: String? = null, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val updates = mutableMapOf<String, Any>()
        
        bannerResId?.let { updates["profileBannerUrl"] = it.toString() }
        bannerName?.let { updates["profileBannerUrl"] = it }
        wallpaperResId?.let { updates["chatWallpaperUrl"] = it.toString() }
        profilePicUrl?.let { updates["profileImageUrl"] = it }
        appTheme?.let { updates["appTheme"] = it }
        
        if (updates.isEmpty()) return onComplete(true)
        
        db.collection("users").document(uid).update(updates)
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun vouchForSkill(targetUid: String, skillName: String, onComplete: (Boolean) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onComplete(false)
        if (currentUid == targetUid) return onComplete(false)

        getUserById(currentUid) { currentUser ->
            val vouchWeight = when {
                currentUser?.isVip == true && currentUser.vipLevel >= 3 -> 5
                currentUser?.isVip == true -> 3
                currentUser?.isPremium == true -> 2
                else -> 1
            }

            val ref = db.collection("users").document(targetUid)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(ref)
                val vouches = snapshot.get("skillVouches") as? Map<String, List<String>> ?: emptyMap()
                val skillVouches = vouches[skillName]?.toMutableList() ?: mutableListOf()

                if (!skillVouches.contains(currentUid)) {
                    skillVouches.add(currentUid)
                    val newVouches = vouches.toMutableMap()
                    newVouches[skillName] = skillVouches
                    transaction.update(ref, "skillVouches", newVouches)
                    
                    val currentVouchCount = snapshot.getLong("vouchCount") ?: 0
                    val newVouchCount = currentVouchCount + vouchWeight
                    transaction.update(ref, "vouchCount", newVouchCount)
                    
                    // Update Impact Score
                    val currentXp = snapshot.getLong("totalXp") ?: 0
                    transaction.update(ref, "impactScore", currentXp + (newVouchCount * 15))
                    
                    // Trigger internal notification
                    NotificationRepository().sendNotification(
                        receiverUid = targetUid,
                        type = "vouch",
                        title = "Powerful Vouch! ⭐",
                        message = "${currentUser?.name ?: "Someone"} gave you a +$vouchWeight vouch for $skillName!"
                    )
                }
            }.addOnCompleteListener { onComplete(it.isSuccessful) }
        }
    }

    fun logProfileView(targetUid: String) {
        val currentUid = auth.currentUser?.uid ?: return
        if (currentUid == targetUid) return

        db.collection("users").document(targetUid)
            .update("profileViews", com.google.firebase.firestore.FieldValue.arrayUnion(currentUid))
    }

    fun boostProfile(onComplete: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false, "Not logged in")
        getUserById(uid) { user ->
            if (user?.isPremium != true) {
                return@getUserById onComplete(false, "Only Premium members can boost their profile")
            }
            
            val boostCost = if (user.isVip) 100 else 200
            if (user.uniCoins < boostCost) {
                return@getUserById onComplete(false, "Insufficient UniCoins ($boostCost required)")
            }

            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.HOUR, 24)
            
            val updates = mapOf(
                "boostUntil" to calendar.time,
                "uniCoins" to com.google.firebase.firestore.FieldValue.increment(-boostCost.toLong())
            )
            
            db.collection("users").document(uid).update(updates)
                .addOnSuccessListener { onComplete(true, "Profile boosted for 24 hours!") }
                .addOnFailureListener { onComplete(false, it.message) }
        }
    }

    fun setIncognitoMode(enabled: Boolean, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        db.collection("users").document(uid).update("isIncognito", enabled)
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun deleteAccount(onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return onComplete(false)
        val uid = user.uid
        
        // 1. Delete Firestore data
        db.collection("users").document(uid).delete()
            .addOnSuccessListener {
                // 2. Delete Auth user
                user.delete().addOnCompleteListener { onComplete(it.isSuccessful) }
            }
            .addOnFailureListener { onComplete(false) }
    }
}
