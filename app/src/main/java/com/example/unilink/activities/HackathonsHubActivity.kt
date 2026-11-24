package com.example.unilink.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.unilink.databinding.ActivityHackathonsHubBinding
import com.example.unilink.utils.ThemeUtils

class HackathonsHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHackathonsHubBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityHackathonsHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnRsvpEthGlobal.setOnClickListener {
            Toast.makeText(this, "Opening ETHGlobal Mumbai Squad Launcher... 🚀", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, CreateTeamActivity::class.java))
        }

        binding.btnRsvpUniai.setOnClickListener {
            Toast.makeText(this, "RSVP Registered for UniAI Smart Campus Hackathon! 🤖", Toast.LENGTH_SHORT).show()
        }

        binding.btnRsvpChainlink.setOnClickListener {
            Toast.makeText(this, "RSVP Registered for Chainlink CRE Hackathon! 🔗", Toast.LENGTH_SHORT).show()
        }
    }
}
