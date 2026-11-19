package com.parkinson.app.data.health.inference

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import com.parkinson.app.MainActivity
import com.parkinson.app.R
import com.parkinson.app.data.local.database.entities.MedicationSchedule
import com.parkinson.app.util.MedicationAlarmScheduler

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: "Medicacion"
        val dose = intent.getFloatExtra("dose", 0f)
        val hour = intent.getIntExtra("hour", 8)
        val minute = intent.getIntExtra("minute", 0)
        val id = intent.getLongExtra("id", 0L).toInt().coerceAtLeast(1)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "Medicacion", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 1) // Medicacion
        }
        val contentPi = PendingIntent.getActivity(context, 1000 + id, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hora de tomar: $name")
            .setContentText("Dosis ${dose.toInt()} mg")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()
        nm.notify(id, notification)

        // Reschedule next occurrence
        MedicationAlarmScheduler.scheduleOne(
            context,
            MedicationSchedule(id = id.toLong(), name = name, doseMg = dose, hour = hour, minute = minute)
        )
    }

    companion object {
        const val CHANNEL = "medication_alarms"
    }
}
