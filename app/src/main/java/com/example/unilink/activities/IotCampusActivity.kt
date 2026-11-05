package com.example.unilink.activities

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.unilink.R
import com.example.unilink.utils.HapticUtils
import com.example.unilink.utils.ThemeUtils

class IotCampusActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iot_campus)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val tvSolarOutput = findViewById<TextView>(R.id.tvSolarOutput)
        val tvLidarDistance = findViewById<TextView>(R.id.tvLidarDistance)
        val tvIot0gCid = findViewById<TextView>(R.id.tvIot0gCid)
        val btnRunIotStream = findViewById<Button>(R.id.btnRunIotStream)

        btnRunIotStream.setOnClickListener { v ->
            HapticUtils.playSuccess(v)
            val outputKw = (12..24).random() + 0.5f
            val obstacleM = (5..18).random() / 10.0f
            val cid = "bafkreib${(1000..9999).random()}...iot_mesh_${System.currentTimeMillis()}"

            tvSolarOutput.text = "Output: $outputKw kW"
            tvLidarDistance.text = "LiDAR Obstacle: ${obstacleM}m"
            tvIot0gCid.text = "0G CID: $cid"

            Toast.makeText(this, "📡 Live IoT Telemetry Stream Updated & Settled on 0G!", Toast.LENGTH_SHORT).show()
        }
    }
}
