package com.parkinson.app.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_schedule")
data class MedicationSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "Levodopa",
    val doseMg: Float,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
)

@Entity(tableName = "medication_intakes")
data class MedicationIntake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long? = null,
    val medicationName: String,
    val doseMg: Float,
    val intakeTimeMillis: Long,
    val markedByCaregiver: Boolean = false
)
