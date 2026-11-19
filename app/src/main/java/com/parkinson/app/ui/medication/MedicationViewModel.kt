package com.parkinson.app.ui.medication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parkinson.app.ParkinsonApp
import com.parkinson.app.util.MedicationAlarmScheduler
import com.parkinson.app.data.local.database.entities.MedicationIntake
import com.parkinson.app.data.local.database.entities.MedicationSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationViewModel(app: Application) : AndroidViewModel(app) {
    private val parkinsonApp = app as ParkinsonApp
    private val repo = parkinsonApp.medicationRepository

    val schedules: StateFlow<List<MedicationSchedule>> =
        repo.observeSchedules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val intakes: StateFlow<List<MedicationIntake>> =
        repo.observeIntakes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repo.observeSchedules().collect { list ->
                MedicationAlarmScheduler.scheduleAll(parkinsonApp.applicationContext, list)
            }
        }
    }

    fun addSchedule(name: String, doseMg: Float, hour: Int, minute: Int) {
        viewModelScope.launch {
            repo.addSchedule(MedicationSchedule(name = name, doseMg = doseMg, hour = hour, minute = minute))
        }
    }

    fun updateSchedule(schedule: com.parkinson.app.data.local.database.entities.MedicationSchedule) {
        viewModelScope.launch { repo.updateSchedule(schedule) }
    }

    fun removeSchedule(id: Long) {
        viewModelScope.launch { repo.removeSchedule(id) }
    }

    fun markTaken(name: String, doseMg: Float) {
        viewModelScope.launch { repo.markTaken(null, name, doseMg) }
    }

    fun deleteIntake(intake: com.parkinson.app.data.local.database.entities.MedicationIntake) {
        viewModelScope.launch { repo.deleteIntake(intake) }
    }

    fun updateIntake(intake: com.parkinson.app.data.local.database.entities.MedicationIntake) {
        viewModelScope.launch { repo.updateIntake(intake) }
    }
}
