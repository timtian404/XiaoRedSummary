package com.xiaoredsummary.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.xiaoredsummary.R
import com.xiaoredsummary.api.ClaudeApiClient
import com.xiaoredsummary.data.PrefsManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = PrefsManager(this)
        val editApiKey = findViewById<TextInputEditText>(R.id.editApiKey)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        if (prefs.hasApiKey) {
            editApiKey.setText(prefs.apiKey)
            tvStatus.text = "API key is saved"
        }

        btnSave.setOnClickListener {
            val key = editApiKey.text?.toString()?.trim() ?: ""
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.apiKey = key
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnTest.setOnClickListener {
            val key = editApiKey.text?.toString()?.trim() ?: ""
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter an API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnTest.isEnabled = false
            tvStatus.text = "Testing API key..."
            tvStatus.setTextColor(0xFF757575.toInt())

            lifecycleScope.launch {
                val client = ClaudeApiClient(key)
                val result = client.summarize("Hello, this is a test.")
                result.onSuccess {
                    tvStatus.text = "API key is valid!"
                    tvStatus.setTextColor(0xFF4CAF50.toInt())
                }.onFailure { e ->
                    tvStatus.text = "API key test failed: ${e.message}"
                    tvStatus.setTextColor(0xFFF44336.toInt())
                }
                btnTest.isEnabled = true
            }
        }
    }
}
