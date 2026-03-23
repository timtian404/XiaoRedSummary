package com.xiaoredsummary.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subtitles: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)
