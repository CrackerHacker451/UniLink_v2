package com.example.unilink

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.unilink.utils.NotificationHelper
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp

class UniLinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Enable vector support for older devices (Android 10 and below)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        
        // 1. Initialize Firebase first
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("UniLinkApp", "Firebase init failed: ${e.message}")
        }

        // 2. Initialize AdMob early
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Initialize Notification Channels
        try {
            NotificationHelper.init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
