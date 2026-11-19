package com.parkinson.app.util

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parkinson.app.ParkinsonApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class MedicationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ParkinsonApp
                val schedules = app.database.medicationDao().observeSchedules().first()
                MedicationAlarmScheduler.scheduleAll(context, schedules)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
