package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.unilink.utils.ThemeUtils

class SignupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        
        // Unified Phone Auth flow is in LoginActivity
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
