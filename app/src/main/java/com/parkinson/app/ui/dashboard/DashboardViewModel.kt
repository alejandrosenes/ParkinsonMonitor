package com.parkinson.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parkinson.app.ParkinsonApp
import com.parkinson.app.data.health.ParkinsonDataAggregator
import com.parkinson.app.data.local.database.entities.MedicationSchedule
import com.parkinson.app.data.local.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

data class DashboardState(
    val onPercent: Float = 0.5f,
    val offPercent: Float = 0.5f,
    val nextDoseMillis: Long? = null,
    val nextDoseName: String? = null,
    val nextDoseSchedule: MedicationSchedule? = null,
    val schedules: List<MedicationSchedule> = emptyList(),
    val healthSummary: String = ""
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val parkinsonApp = app as ParkinsonApp
    private val repo: MedicationRepository = parkinsonApp.medicationRepository
    private val aggregator = ParkinsonDataAggregator(parkinsonApp.healthConnect, repo)

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeSchedules().collect { list ->
                refresh(list)
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10000)
                refreshNow()
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            val list = try { repo.observeSchedules().first() } catch (_: Exception) { _state.value.schedules }
            refresh(list)
        }
    }

    private suspend fun refresh(schedules: List<MedicationSchedule>) {
        val now = System.currentTimeMillis()
        val (sched, nextMillis) = try { repo.nextScheduledDose(now, schedules) ?: (null to null) } catch (_: Exception) { null to null }
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L
        val onOff = try { aggregator.dailyOnOffPercent(dayStart, dayEnd) } catch (_: Exception) { 0.5f to 0.5f }

        // Synced Google Health items for today
        val health = parkinsonApp.healthConnect
        val startI = java.time.Instant.ofEpochMilli(dayStart)
        val endI = java.time.Instant.ofEpochMilli(dayEnd)
        val steps = try { health.readSteps(startI, endI) } catch (_: Exception) { emptyList() }
        val hr = try { health.readHeartRate(startI, endI) } catch (_: Exception) { emptyList() }
        val sleep = try { health.readSleepSessions(startI, endI) } catch (_: Exception) { emptyList() }
        val exercise = try { health.readExerciseSessions(startI, endI) } catch (_: Exception) { emptyList() }
        val stepsTotal = steps.sumOf { it.count }
        val hrValues = hr.flatMap { r -> r.samples.map { it.beatsPerMinute.toInt() } }
        val hrText = if (hrValues.isNotEmpty()) {
            val avg = hrValues.average().toInt()
            val min = hrValues.minOrNull() ?: 0
            val max = hrValues.maxOrNull() ?: 0
            "FC ${avg} bpm (min ${min}, max ${max})"
        } else "FC sin datos"
        val sleepMinutes = sleep.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
        val sleepText = if (sleep.isNotEmpty()) "Sueño ${sleepMinutes / 60}h ${sleepMinutes % 60}m" else "Sueño sin datos"
        val sports = exercise.filter { com.parkinson.app.data.health.TremorParser.parseRecord(it) == null }
        val sportsText = if (sports.isNotEmpty()) {
            "Actividades: " + sports.joinToString(", ") { (it.title?.takeIf { t -> t.isNotBlank() } ?: "Deporte") + " " + (java.time.Duration.between(it.startTime, it.endTime).toMinutes()) + "m" }
        } else "Actividades sin datos"
        val healthSummary = "Items sincronizados con Google Health:\n" +
            "Pasos: $stepsTotal\n" +
            "$hrText\n" +
            "$sleepText\n" +
            "$sportsText"

        _state.value = DashboardState(
            onPercent = onOff.first,
            offPercent = onOff.second,
            nextDoseMillis = nextMillis,
            nextDoseName = sched?.name,
            nextDoseSchedule = sched,
            schedules = schedules,
            healthSummary = healthSummary
        )
    }

    fun markDoseTaken() {
        viewModelScope.launch {
            val s = _state.value.nextDoseSchedule
            val name = s?.name ?: "Levodopa"
            val dose = s?.doseMg ?: 100f
            repo.markTaken(s, name, dose)
            val list = try { repo.observeSchedules().first() } catch (_: Exception) { _state.value.schedules }
            refresh(list)
        }
    }

    fun recordMotorState(on: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().getSharedPreferences("parkinson_motor", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("last_on", on).apply()
            val now = System.currentTimeMillis()
            val type = if (on) com.parkinson.app.domain.models.SymptomType.ON else com.parkinson.app.domain.models.SymptomType.OFF
            parkinsonApp.healthConnect.writeSymptom(
                com.parkinson.app.domain.models.SymptomEpisode(
                    type = type,
                    severity = 1f,
                    startTime = now,
                    endTime = now + 15 * 60 * 1000L,
                    source = "dashboard_toggle"
                )
            )
            val list = try { repo.observeSchedules().first() } catch (_: Exception) { _state.value.schedules }
            refresh(list)
        }
    }

    fun recordMood(score: Int, observation: String = "", atMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val now = atMillis
            parkinsonApp.healthConnect.writeSymptom(
                com.parkinson.app.domain.models.SymptomEpisode(
                    type = com.parkinson.app.domain.models.SymptomType.MOOD,
                    severity = score.toFloat(),
                    startTime = now,
                    endTime = now + 60 * 1000L,
                    source = "dashboard_mood",
                    notes = if (observation.isBlank()) "mood=$score" else "mood=$score; obs=$observation"
                )
            )
        }
    }
}
