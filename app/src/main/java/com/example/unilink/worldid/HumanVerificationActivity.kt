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
            // Only our own verification page may load inside this WebView; anything else is blocked.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val allowed = Uri.parse(baseUrl)
                return !(request.url.scheme == allowed.scheme && request.url.host == allowed.host)
            }
        }

        // There is no way to leave this screen except by verifying or signing out.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })

        if (intent?.data?.host == "human-verified") onReturnedFromWorld() else begin()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data?.host == "human-verified") onReturnedFromWorld()
    }

    private fun onReturnedFromWorld() {
        lifecycleScope.launch {
            if (HumanGate.isVerified(true)) proceed()
            else fail("Verification was not completed. Please try again.")
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from World App: re-read the server-issued claim.
        if (waiting && !handled) lifecycleScope.launch { if (HumanGate.isVerified(true)) proceed() }
    }

    private data class StartSessionResult(
        val httpCode: Int,
        val json: JSONObject? = null,
        val errorMsg: String? = null
    )

    private fun begin() {
        lifecycleScope.launch {
            retry.visibility = View.GONE
            webView.visibility = View.GONE
            if (baseUrl.isEmpty() || baseUrl.contains("REPLACE_WITH")) {
                fail("Human verification is not configured yet (human_gate_base_url).")
                return@launch
            }
            show("Checking your account\u2026")
            if (HumanGate.isVerified(true)) { proceed(); return@launch }

            val idToken = idToken()
            if (idToken == null) { fail("Please sign in again."); return@launch }
            val appCheck = appCheckToken()
            val sessionResult = withContext(Dispatchers.IO) { startSession(idToken, appCheck) }

            val sessionJson = sessionResult.json
            val error = sessionResult.errorMsg

            when {
                sessionResult.httpCode == 200 && sessionJson?.optBoolean("alreadyVerified", false) == true -> {
                    if (HumanGate.isVerified(true)) proceed() else fail("Verification could not be confirmed. Try again.")
                }
                sessionResult.httpCode == 200 && sessionJson?.has("pairingId") == true -> {
                    val pairingId = sessionJson.optString("pairingId")
                    if (pairingId.isEmpty()) { fail("Could not start verification."); return@launch }
                    waiting = true
                    show("Complete the selfie check in World App. You will come back here automatically.")
                    webView.visibility = View.VISIBLE
                    webView.loadUrl("$baseUrl/verify.html#p=$pairingId")
                }
                error == "app_check_failed" || (sessionResult.httpCode == 401 && sessionJson?.optString("error") == "app_check_failed") -> {
                    fail("App verification failed (App Check). Make sure you are using an official, untampered build of UniLink.")
                }
                error == "unauthenticated" || sessionResult.httpCode == 401 -> {
                    fail("Please sign in again.")
                }
                error == "human_already_linked" || sessionResult.httpCode == 409 -> {
                    fail("This person has already verified another UniLink account. Each person can only verify one account.")
                }
                error == "invalid_or_expired_session" -> {
                    fail("Verification session expired. Tap 'Try again' to restart.")
                }
                error == "network_error" || sessionResult.httpCode == 0 -> {
                    fail("Could not connect to verification server. Please check your internet connection.")
                }
                else -> {
                    val friendlyMsg = when (error) {
                        "verification_unavailable" -> "World ID service is currently unavailable. Please try again later."
                        "method_not_allowed" -> "Invalid request method."
                        else -> error ?: "Could not start verification. Check your connection."
                    }
                    fail(friendlyMsg)
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
                if (cont.isActive) {
                    val token = if (t.isSuccessful) t.result?.token else null
                    android.util.Log.d("HumanVerification", "App Check token: ${token?.take(15)}...")
                    cont.resume(token)
                }
            }
        }

    private fun startSession(idToken: String, appCheckToken: String?): StartSessionResult {
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

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = try { JSONObject(responseText) } catch (_: Exception) { null }
            val error = json?.optString("error")

            StartSessionResult(code, json, error)
        } catch (_: Exception) {
            StartSessionResult(0, errorMsg = "network_error")
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
                    show("Complete verification in World ID Simulator (https://simulator.worldcoin.org) or World App.")
                } catch (e: Exception) {
                    show("Could not open link: ${e.message}")
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
