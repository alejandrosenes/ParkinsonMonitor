package com.parkinson.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.parkinson.app.data.local.database.dao.MedicationDao
import com.parkinson.app.data.local.database.dao.TremorDao
import com.parkinson.app.data.local.database.entities.MedicationIntake
import com.parkinson.app.data.local.database.entities.MedicationSchedule
import com.parkinson.app.data.local.database.entities.TremorResultEntity

@Database(
    entities = [MedicationSchedule::class, MedicationIntake::class, TremorResultEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun tremorDao(): TremorDao
}
