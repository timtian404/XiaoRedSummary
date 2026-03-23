package com.xiaoredsummary.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xiaoredsummary.R
import com.xiaoredsummary.data.PrefsManager
import com.xiaoredsummary.service.OverlayService

class MainActivity : AppCompatActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_CODE = 1000
    }

    private lateinit var prefs: PrefsManager
    private var serviceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)

        val btnToggle = findViewById<Button>(R.id.btnStartOverlay)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        val btnHistory = findViewById<Button>(R.id.btnHistory)

        btnToggle.setOnClickListener {
            if (serviceRunning) {
                stopOverlayService()
                btnToggle.text = getString(R.string.start_service)
                serviceRunning = false
            } else {
                startOverlay()
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun startOverlay() {
        if (!prefs.hasApiKey) {
            Toast.makeText(this, "Please set your API key in Settings first", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
            return
        }

        requestScreenCaptureAndStart()
    }

    private fun requestScreenCaptureAndStart() {
        val intent = Intent(this, MediaProjectionRequestActivity::class.java)
        startActivity(intent)
        serviceRunning = true
        findViewById<Button>(R.id.btnStartOverlay).text = getString(R.string.stop_service)
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (Settings.canDrawOverlays(this)) {
                requestScreenCaptureAndStart()
            } else {
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }
}
