package com.xiaoredsummary.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.xiaoredsummary.R
import com.xiaoredsummary.data.AppDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SummaryDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary_detail)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val summaryId = intent.getLongExtra("summary_id", -1)
        if (summaryId == -1L) {
            finish()
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(this@SummaryDetailActivity)
            val summary = db.summaryDao().getById(summaryId)

            summary?.let {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                findViewById<TextView>(R.id.tvTitle).text = it.title
                findViewById<TextView>(R.id.tvTimestamp).text = dateFormat.format(Date(it.timestamp))
                findViewById<TextView>(R.id.tvSubtitles).text = it.subtitles
                findViewById<TextView>(R.id.tvSummary).text = it.summary
            }
        }
    }
}
