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
        val lastLogin = user.lastLoginDate?.let {
            val cal = java.util.Calendar.getInstance()
            cal.time = it
            cal
        }

        if (lastLogin == null) {
            val baseBonus = getBaseDailyReward(user)
            updateUserStreak(user.uid, 1, baseBonus, onComplete, user)
        } else {
            // Normalize calendars to midnight for date comparison
            val today = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val last = (lastLogin.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val diffMillis = today.timeInMillis - last.timeInMillis
            val daysDiff = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

            if (daysDiff == 1) {
                // Consecutive day
                val baseBonus = getBaseDailyReward(user)
                updateUserStreak(user.uid, user.streakDays + 1, baseBonus, onComplete, user)
            } else if (daysDiff > 1) {
                // Streak broken
                val baseBonus = getBaseDailyReward(user)
                updateUserStreak(user.uid, 1, baseBonus, onComplete, user)
            } else {
                // Same day or some anomaly, just proceed
                onComplete(user)
            }
        }
    }

    private fun getBaseDailyReward(user: User): Int {
        var reward = 50 
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
        val allFetchedUsers = mutableListOf<User>()
        val collectionsToFetch = listOf("users", "web_users", "students", "nl_users", "newton_users", "newtons_lobby", "nl_students", "hackathon_users")
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)

        collectionsToFetch.forEach { colName ->
            db.collection(colName).get()
                .addOnSuccessListener { documents ->
                    kotlin.concurrent.thread {
                        val users = documents.mapNotNull { doc ->
                            parseUserFromDoc(doc, colName)
                        }
                        synchronized(allFetchedUsers) {
                            allFetchedUsers.addAll(users)
                        }
                        
                        if (completedCount.incrementAndGet() == collectionsToFetch.size) {
                            val distinctUsers = allFetchedUsers
                                .filter { it.uid.isNotEmpty() && it.uid != currentUid }
                                .distinctBy { user -> if (user.email.isNotEmpty()) user.email.lowercase() else user.uid }
                                .sortedWith(compareByDescending<User> { 
                                    it.boostUntil?.after(java.util.Date()) == true 
                                }.thenByDescending { it.isVip }
                                 .thenByDescending { it.totalXp })

                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onResult(distinctUsers)
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    if (completedCount.incrementAndGet() == collectionsToFetch.size) {
                        // Still return results even if some fail
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onResult(allFetchedUsers) 
                        }
                    }
                }
        }
    }

    private fun parseUserFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot, colName: String): User {
        var user: User? = null
        try {
            user = doc.toObject(User::class.java)
        } catch (_: Exception) { }

        val u = user ?: User()

        val uid = doc.getString("uid") ?: doc.getString("id") ?: doc.id
        if (u.uid.isEmpty()) u.uid = uid

        val email = doc.getString("email") ?: doc.getString("userEmail") ?: doc.getString("mail") ?: doc.getString("user_email") ?: u.email
        u.email = email

        val name = doc.getString("name") 
            ?: doc.getString("displayName") 
            ?: doc.getString("fullName") 
            ?: doc.getString("first_name") 
            ?: doc.getString("user_name")
            ?: u.name

        u.name = if (name.isNotEmpty()) name else if (email.contains("@")) email.substringBefore("@") else "Student Member"

        val username = doc.getString("username") ?: doc.getString("user_handle") ?: u.username
        u.username = if (username.isNotEmpty()) username else u.name.lowercase().replace(" ", "")

        val college = doc.getString("collegeName") ?: doc.getString("college") ?: doc.getString("schoolName") ?: doc.getString("school") ?: u.collegeName
        u.collegeName = college

        val branch = doc.getString("branch") 
            ?: doc.getString("department") 
            ?: doc.getString("major") 
            ?: u.branch

        u.branch = if (!branch.isNullOrEmpty()) branch else if (email.contains("nl-lobby") || colName.contains("nl") || colName.contains("newton")) "Newton's Lobby" else "Campus Member"

        val profilePic = doc.getString("profileImageUrl") 
            ?: doc.getString("avatar") 
            ?: doc.getString("photoUrl") 
            ?: doc.getString("image") 
            ?: doc.getString("profile_pic")
            ?: u.profileImageUrl

        u.profileImageUrl = profilePic
        
        // Ensure student specific data is prioritized if coming from 'students' collection
        if (colName == "students" || colName == "nl_students") {
            u.userType = "Student"
        }

        return u
    }

    fun updateUserLocation(lat: Double?, lng: Double?, city: String? = null, state: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>()
        if (lat != null) updates["latitude"] = lat
        if (lng != null) updates["longitude"] = lng

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val currentCity = doc.getString("city")
            val currentState = doc.getString("state")

            if (currentCity.isNullOrEmpty() && !city.isNullOrEmpty()) {
                updates["city"] = city
            }
            if (currentState.isNullOrEmpty() && !state.isNullOrEmpty()) {
                updates["state"] = state
            }

            if (updates.isNotEmpty()) {
                db.collection("users").document(uid).update(updates)
            }
        }
    }

    fun getNearbyUsers(onResult: (List<Pair<User, Double>>) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onResult(emptyList())

        db.collection("users").document(currentUid).get().addOnSuccessListener { myDoc ->
            val myLat = myDoc.getDouble("latitude") ?: 12.8231
            val myLng = myDoc.getDouble("longitude") ?: 80.0442

            val collectionsToFetch = listOf("users", "web_users", "students", "nl_users", "newton_users")
            val allNearby = mutableListOf<Pair<User, Double>>()
            val completedCount = java.util.concurrent.atomic.AtomicInteger(0)

            collectionsToFetch.forEach { colName ->
                db.collection(colName).get().addOnSuccessListener { documents ->
                    val users = documents.mapNotNull { doc ->
                        parseUserFromDoc(doc, colName)
                    }.filter { user ->
                        user.uid.isNotEmpty() && user.uid != currentUid && !user.isIncognito
                    }.map { user ->
                        val targetLat = user.latitude ?: (myLat + ((-30..30).random() / 1000.0))
                        val targetLng = user.longitude ?: (myLng + ((-30..30).random() / 1000.0))
                        val distance = calculateDistance(myLat, myLng, targetLat, targetLng)
                        val roundedDistance = Math.round(distance * 10.0) / 10.0
                        user to roundedDistance
                    }

                    synchronized(allNearby) {
                        allNearby.addAll(users)
                    }

                    if (completedCount.incrementAndGet() == collectionsToFetch.size) {
                        val distinct = allNearby
                            .distinctBy { if (it.first.email.isNotEmpty()) it.first.email.lowercase() else it.first.uid }
                            .sortedBy { it.second }

                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onResult(distinct)
                        }
                    }
                }.addOnFailureListener {
                    if (completedCount.incrementAndGet() == collectionsToFetch.size) {
                        val distinct = allNearby
                            .distinctBy { if (it.first.email.isNotEmpty()) it.first.email.lowercase() else it.first.uid }
                            .sortedBy { it.second }

                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onResult(distinct)
                        }
                    }
                }
            }
        }.addOnFailureListener {
            onResult(emptyList())
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    fun saveUser(user: User, onComplete: (Boolean) -> Unit) {
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
                try {
                    onResult(document.toObject(User::class.java))
                } catch (e: Exception) {
                    android.util.Log.e("UserRepository", "Error deserializing User $uid: ${e.message}")
                    onResult(null)
                }
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
        val currentUserRef = db.collection("users").document(currentUid)
        batch.set(currentUserRef, mapOf("linked" to com.google.firebase.firestore.FieldValue.arrayUnion(targetUid)), SetOptions.merge())
        val targetUserRef = db.collection("users").document(targetUid)
        batch.set(targetUserRef, mapOf("linkers" to com.google.firebase.firestore.FieldValue.arrayUnion(currentUid)), SetOptions.merge())
        batch.commit().addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun unlinkUser(targetUid: String, onComplete: (Boolean) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onComplete(false)
        val batch = db.batch()
        val currentUserRef = db.collection("users").document(currentUid)
        batch.set(currentUserRef, mapOf("linked" to com.google.firebase.firestore.FieldValue.arrayRemove(targetUid)), SetOptions.merge())
        val targetUserRef = db.collection("users").document(targetUid)
        batch.set(targetUserRef, mapOf("linkers" to com.google.firebase.firestore.FieldValue.arrayRemove(currentUid)), SetOptions.merge())
        batch.commit().addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun redeemPremiumCode(inputCode: String, onComplete: (Boolean, String?) -> Unit) {
        val code = inputCode.trim().uppercase().ifEmpty { "UNILINKPRO" }
        val uid = auth.currentUser?.uid ?: return onComplete(false, "User not logged in")

        val vipCodes = listOf("CAMPUS_VIP", "PROPASS2025", "PAPABOL1917", "NDSIR")
        val proCodes = listOf("UNILINKPRO", "UNILINK_PRO", "CYBER_PRO", "PREMIUM100", "STUDENT_ELITE", "1234", "PRO")

        if (code in vipCodes) {
            val updates = mapOf(
                "isPremium" to true,
                "premium" to true,
                "isVip" to true,
                "vip" to true,
                "vipLevel" to 3,
                "premiumCode" to code,
                "uniCoins" to com.google.firebase.firestore.FieldValue.increment(500),
                "totalXp" to com.google.firebase.firestore.FieldValue.increment(200)
            )
            db.collection("users").document(uid).set(updates, SetOptions.merge())
                .addOnSuccessListener { onComplete(true, "👑 VIP Pro Pass Activated! +500 Bonus UniCoins Awarded!") }
                .addOnFailureListener { onComplete(false, it.message) }
        } else if (code in proCodes) {
            val updates = mapOf(
                "isPremium" to true,
                "premium" to true,
                "premiumCode" to code,
                "uniCoins" to com.google.firebase.firestore.FieldValue.increment(200)
            )
            db.collection("users").document(uid).set(updates, SetOptions.merge())
                .addOnSuccessListener { onComplete(true, "✨ Pro Pass Unlocked! +200 Bonus UniCoins!") }
                .addOnFailureListener { onComplete(false, it.message) }
        } else {
            onComplete(false, "Invalid Pro Pass code. Try UNILINKPRO or CAMPUS_VIP!")
        }
    }

    fun addUniCoins(amount: Int, onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        db.collection("users").document(uid).update("uniCoins", com.google.firebase.firestore.FieldValue.increment(amount.toLong()))
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun activatePremium(onComplete: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onComplete(false)
        val updates = mapOf(
            "isPremium" to true,
            "premium" to true,
            "premiumCode" to "DIRECT_SUBSCRIPTION"
        )
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
                currentUser?.isVip == true && (currentUser.vipLevel ?: 0) >= 3 -> 5
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
                    
                    val currentXp = snapshot.getLong("totalXp") ?: 0
                    transaction.update(ref, "impactScore", currentXp + (newVouchCount * 15))
                    
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

    fun grantLoyaltyStar(targetUid: String, onComplete: (Boolean, String?) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onComplete(false, "Please log in first.")
        if (currentUid == targetUid) return onComplete(false, "You cannot grant a loyalty star to yourself!")

        getUserById(currentUid) { senderUser ->
            val senderName = senderUser?.name ?: "Campus Peer"
            val targetRef = db.collection("users").document(targetUid)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(targetRef)
                if (!snapshot.exists()) throw Exception("User not found")

                val linkers = (snapshot.get("linkers") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val currentStars = snapshot.getLong("vouchCount") ?: 0L
                val currentXp = snapshot.getLong("totalXp") ?: 0L
                val currentCoins = snapshot.getLong("uniCoins") ?: 0L

                if (linkers.contains("star_$currentUid")) {
                    throw Exception("You have already granted a Loyalty Star to this user!")
                }

                val updatedStars = currentStars + 1
                val updatedXp = currentXp + 15
                val updatedCoins = currentCoins + 10

                transaction.update(
                    targetRef,
                    "vouchCount", updatedStars,
                    "totalXp", updatedXp,
                    "impactScore", updatedXp,
                    "uniCoins", updatedCoins,
                    "linkers", com.google.firebase.firestore.FieldValue.arrayUnion("star_$currentUid")
                )

                val senderRef = db.collection("users").document(currentUid)
                transaction.update(senderRef, "uniCoins", com.google.firebase.firestore.FieldValue.increment(5))

                NotificationRepository().sendNotification(
                    receiverUid = targetUid,
                    type = "vouch",
                    title = "⭐ Loyalty Star Received!",
                    message = "$senderName granted you a Loyalty Star! +15 Impact XP & +10 UniCoins awarded! 🌟",
                    targetId = currentUid
                )
            }.addOnSuccessListener {
                onComplete(true, "Granted Loyalty Star ⭐! +10 UniCoins awarded!")
            }.addOnFailureListener { e ->
                onComplete(false, e.message ?: "Failed to grant star")
            }
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

    fun setUserOnline(uid: String, online: Boolean) {
        if (uid.isEmpty()) return
        val updates = mapOf<String, Any>(
            "isOnline" to online,
            "online" to online,
            "lastSeenTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).update(updates)
    }

    fun deleteAccount(onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return onComplete(false)
        val uid = user.uid
        db.collection("users").document(uid).delete()
            .addOnSuccessListener {
                user.delete().addOnCompleteListener { onComplete(it.isSuccessful) }
            }
            .addOnFailureListener { onComplete(false) }
    }
}
