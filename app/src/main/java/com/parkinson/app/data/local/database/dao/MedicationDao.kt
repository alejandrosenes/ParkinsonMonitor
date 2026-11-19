package com.parkinson.app.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.parkinson.app.data.local.database.entities.MedicationIntake
import com.parkinson.app.data.local.database.entities.MedicationSchedule
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medication_schedule ORDER BY hour, minute")
    fun observeSchedules(): Flow<List<MedicationSchedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: MedicationSchedule): Long

    @Update
    suspend fun updateSchedule(schedule: MedicationSchedule)

    @Query("DELETE FROM medication_schedule WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    @Query("DELETE FROM medication_schedule")
    suspend fun clearSchedules()

    @Query("DELETE FROM medication_intakes")
    suspend fun clearIntakes()

    @Query("SELECT * FROM medication_intakes ORDER BY intakeTimeMillis DESC")
    fun observeIntakes(): Flow<List<MedicationIntake>>

    @Insert
    suspend fun insertIntake(intake: MedicationIntake): Long

    @Update
    suspend fun updateIntake(intake: MedicationIntake)

    @Delete
    suspend fun deleteIntake(intake: MedicationIntake)

    @Query("SELECT * FROM medication_intakes WHERE intakeTimeMillis <= :now ORDER BY intakeTimeMillis DESC LIMIT 1")
    suspend fun lastIntakeBefore(now: Long): MedicationIntake?

    @Query("SELECT COUNT(*) FROM medication_intakes WHERE intakeTimeMillis >= :start AND intakeTimeMillis < :end")
    suspend fun countIntakesBetween(start: Long, end: Long): Int
}
