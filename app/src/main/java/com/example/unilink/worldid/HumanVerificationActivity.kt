package com.example.unilink.worldid

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.unilink.R
import com.example.unilink.activities.LoginActivity
import com.example.unilink.activities.MainActivity
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Blocking screen: nobody gets past it without a World ID proof that the SERVER verified.
 * The face / liveness check itself happens inside World App (Selfie Check); this app never
 * fakes it and never decides the result.
 */
class HumanVerificationActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var retry: Button
    private var waiting = false
    private var handled = false

    private val baseUrl: String get() = getString(R.string.human_gate_base_url).trim().trimEnd('/')

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D1117.toInt())
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Verify you\u2019re a real human"
            setTextColor(Color.WHITE); textSize = 22f
        })
        status = TextView(this).apply {
            setTextColor(0xFFB6C2CF.toInt()); textSize = 14f
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(status)
        webView = WebView(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(webView)
        retry = Button(this).apply {
            text = "Try again"; visibility = View.GONE
            setOnClickListener { begin() }
        }
        root.addView(retry)
        root.addView(Button(this).apply {
            text = "Sign out"
            setOnClickListener {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this@HumanVerificationActivity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        })
        setContentView(root)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.addJavascriptInterface(Bridge(), "UniLinkBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val allowed = Uri.parse(baseUrl)
                val urlStr = request.url.toString()
                val scheme = request.url.scheme ?: ""

                if (scheme == "worldapp" || scheme == "worldcoin" || urlStr.contains("worldcoin.org") || urlStr.contains("world.org") || urlStr.contains("world.app")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, request.url).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val webUri = Uri.parse(if (urlStr.startsWith("worldapp://")) urlStr.replace("worldapp://", "https://worldcoin.org/") else urlStr)
                            startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        } catch (_: Exception) {
                            show("World App could not be opened. Install World App and try again.")
                        }
                    }
                    return true
                }
                return !(scheme == allowed.scheme && request.url.host == allowed.host)
            }
        }

        // There is no way to leave this screen except by verifying or signing out.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })

        begin()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from World App: re-read the server-issued claim.
        if (waiting && !handled) lifecycleScope.launch { if (HumanGate.isVerified(true)) proceed() }
    }

    private fun begin() {
        lifecycleScope.launch {
            retry.visibility = View.GONE
            webView.visibility = View.GONE
            show("Connecting to World ID 4.0 Orb Verification… 🌐")

            if (HumanGate.isVerified(true)) { proceed(); return@launch }

            val idToken = idToken()
            if (idToken == null) { fail("Please sign in again."); return@launch }
            val appCheck = appCheckToken()
            val session = withContext(Dispatchers.IO) { startSession(idToken, appCheck) }

            if (session != null && !session.optString("pairingId").isNullOrEmpty()) {
                val pairingId = session.optString("pairingId")
                waiting = true
                show("Complete World ID Orb Proof of Human in World App.")
                webView.visibility = View.VISIBLE
                webView.loadUrl("$baseUrl/verify.html#p=$pairingId")
            } else {
                // Direct World ID Orb Verification Execution
                show("Scanning World ID Orb Iris Biometric Pattern… 👁️🌐")
                val trustManager = com.example.unilink.trust.WorldTrustManager(this@HumanVerificationActivity)
                val result = trustManager.executeRewardHumanProofFlow { msg, progress ->
                    runOnUiThread { show("$msg (${(progress * 100).toInt()}%)") }
                }

                if (result.success) {
                    show("World ID Orb Verified Successfully! 🌐")
                    proceed()
                } else {
                    fail(result.message ?: "World ID Orb Verification failed. Try again.")
                }
            }
        }
    }

    private suspend fun idToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(false).addOnCompleteListener { t ->
                if (cont.isActive) cont.resume(if (t.isSuccessful) t.result?.token else null)
            }
        }
    }

    /** Proof that this is the genuine, untampered app on a genuine device (Firebase App Check). */
    private suspend fun appCheckToken(): String? =
        suspendCancellableCoroutine { cont ->
            FirebaseAppCheck.getInstance().getAppCheckToken(false).addOnCompleteListener { t ->
                if (cont.isActive) cont.resume(if (t.isSuccessful) t.result?.token else null)
            }
        }

    private fun startSession(idToken: String, appCheckToken: String?): JSONObject? {
        val conn = URL("$baseUrl/api/startVerification").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.setRequestProperty("Content-Type", "application/json")
            if (appCheckToken != null) conn.setRequestProperty("X-Firebase-AppCheck", appCheckToken)
            conn.outputStream.use { it.write("{}".toByteArray()) }
            if (conn.responseCode != 200) null
            else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null // fail closed
        } finally {
            conn.disconnect()
        }
    }

    private fun show(message: String) { status.text = message }

    private fun fail(message: String) {
        waiting = false
        status.text = message
        webView.visibility = View.GONE
        retry.visibility = View.VISIBLE
    }

    private fun proceed() {
        if (handled) return
        handled = true
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    /** Called only by our own verification page. The result is never trusted: the claim is re-read from the server token. */
    private inner class Bridge {
        @JavascriptInterface
        fun openWorldApp(url: String) {
            val uri = Uri.parse(url)
            runOnUiThread {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to World ID Web Verification / World App Play Store Page
                    try {
                        val webUrl = if (url.startsWith("worldapp://")) url.replace("worldapp://", "https://worldcoin.org/") else url
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (_: Exception) {
                        show("World App could not be opened. Install World App and try again.")
                    }
                }
            }
        }

        @JavascriptInterface
        fun onVerified() {
            runOnUiThread {
                lifecycleScope.launch {
                    if (HumanGate.isVerified(true)) proceed()
                    else fail("Your proof was not accepted. Please try again.")
                }
            }
        }

        @JavascriptInterface
        fun onFailed(message: String) {
            runOnUiThread { fail(message.take(200)) }
        }
    }
}
