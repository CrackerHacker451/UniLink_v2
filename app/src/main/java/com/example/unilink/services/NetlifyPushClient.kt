package com.example.unilink.services

import android.util.Log
import com.example.unilink.R
import com.example.unilink.UniLinkApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Best-effort delivery request after the inbox document has committed. Never sends secrets or FCM tokens. */
object NetlifyPushClient {
    private val executor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(128), ThreadPoolExecutor.AbortPolicy())

    fun request(receiverUid: String, notificationId: String, senderUid: String) {
        val base = UniLinkApp.instance.getString(R.string.push_backend_base_url).trim().trimEnd('/')
        if (!base.startsWith("https://")) {
            Log.w("NetlifyPush", "Configure push_backend_base_url before release")
            return
        }
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return
        if (user.uid != senderUid) return
        user.getIdToken(false).addOnSuccessListener { result ->
            val token = result.token ?: return@addOnSuccessListener
            FirebaseAppCheck.getInstance().getAppCheckToken(false).addOnCompleteListener { check ->
                if (auth.currentUser?.uid != senderUid) return@addOnCompleteListener
                val appCheckToken = if (check.isSuccessful) check.result?.token else null
                try {
                    executor.execute {
                        if (auth.currentUser?.uid != senderUid) return@execute
                        var connection: HttpURLConnection? = null
                        try {
                            val conn = URL("$base/api/sendPush").openConnection() as HttpURLConnection
                            connection = conn
                            conn.instanceFollowRedirects = false
                            conn.requestMethod = "POST"
                            conn.connectTimeout = 8000
                            conn.readTimeout = 10000
                            conn.doOutput = true
                            conn.setRequestProperty("Content-Type", "application/json")
                            conn.setRequestProperty("Authorization", "Bearer $token")
                            if (!appCheckToken.isNullOrBlank()) conn.setRequestProperty("X-Firebase-AppCheck", appCheckToken)
                            val payload = JSONObject().put("receiverUid", receiverUid)
                                .put("notificationId", notificationId).toString().toByteArray(Charsets.UTF_8)
                            conn.outputStream.use { it.write(payload) }
                            val status = conn.responseCode
                            if (status !in 200..299) Log.w("NetlifyPush", "Push request returned HTTP $status")
                        } catch (_: Exception) {
                            Log.w("NetlifyPush", "Push request failed")
                        } finally {
                            connection?.disconnect()
                        }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    Log.w("NetlifyPush", "Push request queue full; inbox record retained")
                }
            }
        }.addOnFailureListener { Log.w("NetlifyPush", "Firebase authorization unavailable") }
    }
}
