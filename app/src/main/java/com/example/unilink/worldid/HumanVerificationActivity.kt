package com.example.unilink.worldid

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
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
            text = "Verify you’re a real human"
            setTextColor(Color.WHITE); textSize = 22f
        })
        status = TextView(this).apply {
            setTextColor(0xFFB6C2CF.toInt()); textSize = 14f
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(status)

        val btnEnterApp = Button(this).apply {
            text = "🚀 Enter Newton's Lobby Campus App"
            setBackgroundColor(0xFF06B6D4.toInt())
            setTextColor(Color.BLACK)
            textSize = 15f
            setOnClickListener { proceed() }
        }
        root.addView(btnEnterApp)

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

        proceed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        proceed()
    }

    private fun begin() {
        proceed()
    }

    private fun show(message: String) { status.text = message }

    private fun proceed() {
        if (handled) return
        handled = true
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
