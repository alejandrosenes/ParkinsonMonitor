package com.parkinson.app.data.local.repository

import com.parkinson.app.data.local.database.dao.MedicationDao
import com.parkinson.app.data.local.database.entities.MedicationIntake
import com.parkinson.app.data.local.database.entities.MedicationSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar

class MedicationRepository(private val dao: MedicationDao) {

    fun observeSchedules(): Flow<List<MedicationSchedule>> = dao.observeSchedules()
    fun observeIntakes(): Flow<List<MedicationIntake>> = dao.observeIntakes()

    suspend fun getAllSchedules(): List<MedicationSchedule> = dao.observeSchedules().first()
    suspend fun getAllIntakes(): List<MedicationIntake> = dao.observeIntakes().first()

    suspend fun addSchedule(schedule: MedicationSchedule): Long = dao.insertSchedule(schedule)
    suspend fun updateSchedule(schedule: MedicationSchedule) = dao.updateSchedule(schedule)
    suspend fun removeSchedule(id: Long) = dao.deleteSchedule(id)

    suspend fun addIntake(intake: MedicationIntake): Long = dao.insertIntake(intake)
    suspend fun updateIntake(intake: MedicationIntake) = dao.updateIntake(intake)
    suspend fun deleteIntake(intake: MedicationIntake) = dao.deleteIntake(intake)

    suspend fun clearAll() {
        dao.clearSchedules()
        dao.clearIntakes()
    }

    suspend fun markTaken(schedule: MedicationSchedule?, name: String, doseMg: Float, atMillis: Long = System.currentTimeMillis()): Long {
        return dao.insertIntake(
            MedicationIntake(
                scheduleId = schedule?.id,
                medicationName = name,
                doseMg = doseMg,
                intakeTimeMillis = atMillis
            )
        )
    }

    suspend fun lastDoseBefore(now: Long): MedicationIntake? = dao.lastIntakeBefore(now)

    fun minutesSinceLastDose(now: Long, last: MedicationIntake?): Long? {
        return last?.let { (now - it.intakeTimeMillis) / 60000L }
    }

    /**
     * Computes the next scheduled dose time-of-day after (now).
     */
    fun nextScheduledDose(now: Long, schedules: List<MedicationSchedule>): Pair<MedicationSchedule, Long>? {
        if (schedules.isEmpty()) return null
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val todayMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        var best: Pair<MedicationSchedule, Long>? = null
        for (s in schedules) {
            val schedMinutes = s.hour * 60 + s.minute
            val targetMillis = if (schedMinutes > todayMinutes) {
                // later today
                val c = cal.clone() as Calendar
                c.set(Calendar.HOUR_OF_DAY, s.hour)
                c.set(Calendar.MINUTE, s.minute)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                c.timeInMillis
            } else {
                // tomorrow
                val c = cal.clone() as Calendar
                c.add(Calendar.DAY_OF_MONTH, 1)
                c.set(Calendar.HOUR_OF_DAY, s.hour)
                c.set(Calendar.MINUTE, s.minute)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                c.timeInMillis
            }
            if (best == null || targetMillis < best!!.second) {
                best = s to targetMillis
            }
        }
        return best
    }
}
