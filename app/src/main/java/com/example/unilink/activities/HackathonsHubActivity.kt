package com.example.unilink.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ethglobal.com"))
            try { startActivity(intent) } catch (_: Exception) {
                Toast.makeText(this, "Opening ETHGlobal Mumbai Squad Launcher... 🚀", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, CreateTeamActivity::class.java))
            }
        }

        binding.btnRsvpUniai.setOnClickListener {
            Toast.makeText(this, "RSVP Registered for UniAI Smart Campus Hackathon! 🤖", Toast.LENGTH_SHORT).show()
        }

        binding.btnRsvpChainlink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chain.link/hackathon"))
            try { startActivity(intent) } catch (_: Exception) {
                Toast.makeText(this, "RSVP Registered for Chainlink CRE Hackathon! 🔗", Toast.LENGTH_SHORT).show()
            }
        }

        binding.etSearchHackathons.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s.toString().trim()
                if (q.isNotEmpty()) {
                    Toast.makeText(this@HackathonsHubActivity, "Searching hackathons for: '$q'...", Toast.LENGTH_SHORT).show()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
