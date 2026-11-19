package com.parkinson.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.parkinson.app.data.local.database.entities.TremorResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TremorDao {
    @Insert
    suspend fun insert(result: TremorResultEntity): Long

    @Query("SELECT * FROM tremor_result ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TremorResultEntity>>

    @Query("DELETE FROM tremor_result")
    suspend fun clearAll()
}