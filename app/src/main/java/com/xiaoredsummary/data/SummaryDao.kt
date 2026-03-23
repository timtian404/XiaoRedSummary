package com.xiaoredsummary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries ORDER BY timestamp DESC")
    suspend fun getAll(): List<SummaryEntity>

    @Insert
    suspend fun insert(summary: SummaryEntity): Long

    @Delete
    suspend fun delete(summary: SummaryEntity)

    @Query("SELECT * FROM summaries WHERE id = :id")
    suspend fun getById(id: Long): SummaryEntity?
}
