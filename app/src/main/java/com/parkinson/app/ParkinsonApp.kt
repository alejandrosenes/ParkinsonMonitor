package com.parkinson.app

import android.app.Application
import androidx.room.Room
import com.parkinson.app.data.local.database.AppDatabase
import com.parkinson.app.data.health.HealthConnectManager
import com.parkinson.app.data.local.repository.MedicationRepository
import com.parkinson.app.util.MedicationAlarmScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ParkinsonApp : Application() {
    val healthConnect: HealthConnectManager by lazy { HealthConnectManager(this) }
    lateinit var database: AppDatabase
        private set
    lateinit var medicationRepository: MedicationRepository
        private set
    lateinit var tremorDao: com.parkinson.app.data.local.database.dao.TremorDao
        private set
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "parkinson.db"
        ).fallbackToDestructiveMigration().build()
        medicationRepository = MedicationRepository(database.medicationDao())
        tremorDao = database.tremorDao()
        appScope.launch {
            try {
                val schedules = database.medicationDao().observeSchedules().first()
                MedicationAlarmScheduler.scheduleAll(applicationContext, schedules)
            } catch (_: Exception) { }
        }
    }
}
