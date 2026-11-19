package com.parkinson.app.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tremor_result")
data class TremorResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String = "rest",
    val timestamp: Long = System.currentTimeMillis(),
    val score: Double = 0.0,
    val peakFreqHz: Double = 0.0,
    val tremorPower: Double = 0.0,
    val rmsDisplacementMm: Double = 0.0,
    val samplesCsv: String? = null
)