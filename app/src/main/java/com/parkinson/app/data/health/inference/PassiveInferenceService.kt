package com.parkinson.app.data.health.inference

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.HealthConnectClient
import com.parkinson.app.R
import com.parkinson.app.data.health.HealthConnectManager
import com.parkinson.app.data.health.TremorParser
import com.parkinson.app.domain.models.SymptomEpisode
import com.parkinson.app.domain.models.SymptomType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

class PassiveInferenceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var health: HealthConnectManager

    override fun onCreate() {
        super.onCreate()
        health = HealthConnectManager(this)
        startForeground(NOTIF_ID, buildNotification("Monitoreando síntomas…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            while (true) {
                runInference()
                delay(15 * 60 * 1000L)
            }
        }
        return START_STICKY
    }

    private suspend fun runInference() {
        if (!health.isAvailable()) return
        if (!health.hasAllPermissions()) return
        val end = Instant.now()
        val start = end.minusSeconds(15 * 60)
        val steps = health.readSteps(start, end)
        val hr = health.readHeartRate(start, end)
        val totalSteps = steps.sumOf { it.count }
        val avgHr = hr.flatMap { it.samples }.map { it.beatsPerMinute.toDouble() }.average().takeIf { it.isFinite() }

        // Heuristic: high step count (micro-movements) + resting heart rate => rest tremor
        val restingHr = avgHr != null && avgHr < 75.0
        val microMovements = totalSteps > 200
        if (restingHr && microMovements) {
            val severity = ((totalSteps / 2000.0).coerceIn(0.2, 0.9)).toFloat()
            val episode = SymptomEpisode(
                type = SymptomType.TREMOR,
                severity = severity,
                startTime = start.toEpochMilli(),
                endTime = end.toEpochMilli(),
                source = "inferred"
            )
            health.writeSymptom(episode)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        val channelId = "parkinson_monitor"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Parkinson Monitor", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Parkinson Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 42
    }
}
