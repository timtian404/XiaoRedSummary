package com.xiaoredsummary.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.textfield.TextInputEditText
import com.xiaoredsummary.R
import com.xiaoredsummary.data.PrefsManager

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
            tvStatus.text = "API key saved successfully"
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
        }
    }
}
