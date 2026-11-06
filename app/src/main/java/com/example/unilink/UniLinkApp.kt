package com.example.unilink

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.NotificationHelper
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class UniLinkApp : Application(), Application.ActivityLifecycleCallbacks, ImageLoaderFactory {

    companion object {
        lateinit var instance: UniLinkApp
            private set
    }

    private val userRepository by lazy { UserRepository() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private var activeActivityCount = 0

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of available RAM for instant 0ms image rendering
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100MB persistent disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // App Check: lets Firebase and our server reject scripts, agents calling the API directly,
        // emulators and tampered copies of the app. Debug builds use the debug provider.
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (debuggable) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )
        
        // Enable vector support for older devices
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        
        // 1. Initialize Firebase first
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("UniLinkApp", "Firebase init failed: ${e.message}")
        }

        // 2. Enable Firestore Persistent Disk Caching for Instant 0ms Screen Loading
        try {
            val db = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            db.firestoreSettings = settings
        } catch (e: Exception) {
            android.util.Log.e("UniLinkApp", "Firestore settings init error: ${e.message}")
        }

        // 3. Initialize AdMob early
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Initialize Notification Channels
        try {
            NotificationHelper.init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 5. Register Activity Lifecycle Callbacks for Accurate Online/Offline Presence
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (activeActivityCount == 0) {
            startOnlinePresence()
        }
        activeActivityCount++
    }

    override fun onActivityStopped(activity: Activity) {
        activeActivityCount--
        if (activeActivityCount <= 0) {
            activeActivityCount = 0
            stopOnlinePresence()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun startOnlinePresence() {
        val uid = auth.currentUser?.uid ?: return
        userRepository.setUserOnline(uid, true)

        // Heartbeat every 60 seconds while active
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        heartbeatRunnable = object : Runnable {
            override fun run() {
                val currentUid = auth.currentUser?.uid
                if (currentUid != null && activeActivityCount > 0) {
                    userRepository.setUserOnline(currentUid, true)
                    heartbeatHandler.postDelayed(this, 60 * 1000)
                }
            }
        }
        heartbeatHandler.postDelayed(heartbeatRunnable!!, 60 * 1000)
    }

    private fun stopOnlinePresence() {
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        val uid = auth.currentUser?.uid ?: return
        userRepository.setUserOnline(uid, false)
    }
}
