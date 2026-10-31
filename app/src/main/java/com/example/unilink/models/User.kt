package com.example.unilink.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    var uid: String = "",
    var name: String = "",
    var username: String = "",
    var email: String = "",
    var userType: String = "Student",
    var collegeName: String? = null,
    var schoolName: String? = null,
    var branch: String? = null,
    var yearOrSemester: String? = null,
    var city: String = "",
    var state: String = "",
    var bio: String = "",
    var profileImageUrl: String? = null,
    var profileBannerUrl: String? = null,
    var chatWallpaperUrl: String? = null,
    var phoneNumber: String? = null,
    var goals: String = "",
    var fcmToken: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,

    // Social
    var linked: List<String> = emptyList(), // Following
    var linkers: List<String> = emptyList(), // Followers
    var blockedUsers: List<String> = emptyList(),
    var profileViews: List<String> = emptyList(),

    // Gamification
    var skills: List<String> = emptyList(),
    var skillVouches: Map<String, List<String>> = emptyMap(),
    var vouchCount: Int = 0,
    var uniCoins: Int = 100,
    var totalXp: Int = 0,
    var impactScore: Int = 0,
    var streakDays: Int = 0,
    var badges: List<String> = emptyList(),

    var status: String = "Open to Pair",

    // Status & Matching with 3-Minute Heartbeat Expiry Check
    @get:PropertyName("isOnline") @set:PropertyName("isOnline")
    var isOnline: Boolean = false,

    @get:PropertyName("isLookingForRandomMatch") @set:PropertyName("isLookingForRandomMatch")
    var isLookingForRandomMatch: Boolean = false,

    // Premium & VIP
    @get:PropertyName("isPremium") @set:PropertyName("isPremium")
    var isPremiumField: Boolean = false,

    @get:PropertyName("isVip") @set:PropertyName("isVip")
    var isVipField: Boolean = false,

    @get:PropertyName("isAdmin") @set:PropertyName("isAdmin")
    var isAdminField: Boolean = false,

    var vipLevel: Int = 0,

    @get:PropertyName("isIncognito") @set:PropertyName("isIncognito")
    var isIncognito: Boolean = false,

    var appTheme: String = "Dark",
    var boostUntil: Date? = null,

    // Active Calling Signaling Fields
    var activeCallerUid: String? = null,
    var activeCallerName: String? = null,
    var activeIncomingCallId: String? = null,
    @ServerTimestamp var activeCallTimestamp: Date? = null,

    // Mappings for older or alternative field names in DB
    @get:PropertyName("premium") @set:PropertyName("premium")
    var hasLegacyPremiumField: Boolean = false,
    @get:PropertyName("vip") @set:PropertyName("vip")
    var hasLegacyVipField: Boolean = false,

    @get:PropertyName("incognito") @set:PropertyName("incognito")
    var hasIncognitoField: Boolean = false,
    @get:PropertyName("online") @set:PropertyName("online")
    var hasOnlineField: Boolean = false,
    @get:PropertyName("lookingForRandomMatch") @set:PropertyName("lookingForRandomMatch")
    var hasLookingForRandomMatchField: Boolean = false,

    var currentMatchId: String? = null,
    var currentMatchName: String? = null,
    var followers: List<String> = emptyList(),
    var following: List<String> = emptyList(),
    var interests: List<String> = emptyList(),
    var skillLevels: Map<String, Int> = emptyMap(),
    var autoTheme: Boolean = false,
    var premiumCode: String? = null,

    // Web3, World ID & Decentralized Identity Fields
    var hederaAccountId: String? = null,
    var walletAddress: String? = null,
    var encryptedSwarmAvatarRef: String? = null,
    var worldIdVerified: Boolean = false,
    var worldIdNullifier: String? = null,
    var ensSubname: String? = null,
    var sybilScore: Int = 0,
    var worldIdCredentialType: String? = null,

    @ServerTimestamp var lastSeenTimestamp: Date? = null,
    @ServerTimestamp var lastLoginDate: Date? = null,
    @ServerTimestamp var createdAt: Date? = null
) {
    @get:Exclude @set:Exclude
    var distanceKm: Double? = null

    @get:Exclude
    val isPremium: Boolean
        get() = isPremiumField || hasLegacyPremiumField

    @get:Exclude
    val isVip: Boolean
        get() = isVipField || hasLegacyVipField

    @get:Exclude
    val isAdmin: Boolean
        get() = isAdminField || userType.equals("Admin", ignoreCase = true) || email.contains("admin", ignoreCase = true)
}
