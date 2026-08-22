package com.example.unilink.models

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
    
    // Status & Matching (with fallback names for stability)
    @get:PropertyName("isOnline") @set:PropertyName("isOnline")
    var isOnline: Boolean = false,
    
    @get:PropertyName("isLookingForRandomMatch") @set:PropertyName("isLookingForRandomMatch")
    var isLookingForRandomMatch: Boolean = false,
    
    // Premium & VIP
    @get:PropertyName("isPremium") @set:PropertyName("isPremium")
    var isPremium: Boolean = false,
    
    @get:PropertyName("isVip") @set:PropertyName("isVip")
    var isVip: Boolean = false,
    
    var vipLevel: Int = 0,
    
    @get:PropertyName("isIncognito") @set:PropertyName("isIncognito")
    var isIncognito: Boolean = false,
    
    var appTheme: String = "Ars White",
    var boostUntil: Date? = null,

    // Mappings for older or alternative field names in DB to avoid warnings
    @get:PropertyName("premium") @set:PropertyName("premium")
    var hasPremiumField: Boolean = false,
    @get:PropertyName("vip") @set:PropertyName("vip")
    var hasVipField: Boolean = false,
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
    
    @ServerTimestamp var lastLoginDate: Date? = null,
    @ServerTimestamp var createdAt: Date? = null
)
