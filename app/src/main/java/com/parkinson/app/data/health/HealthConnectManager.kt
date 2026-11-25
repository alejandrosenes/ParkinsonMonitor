package com.parkinson.app.data.health

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.parkinson.app.domain.models.SymptomEpisode
import java.time.Instant

class HealthConnectManager(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    private var client: HealthConnectClient? = null

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    fun getClient(): HealthConnectClient? {
        if (client != null) return client
        if (!isAvailable()) return null
        client = HealthConnectClient.getOrCreate(context)
        return client
    }

    suspend fun hasAllPermissions(): Boolean {
        val c = getClient() ?: return false
        val granted = c.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun readSteps(start: Instant, end: Instant): List<StepsRecord> {
        val c = getClient() ?: return emptyList()
        return try {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            resp.records
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun readHeartRate(start: Instant, end: Instant): List<HeartRateRecord> {
        val c = getClient() ?: return emptyList()
        return try {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            resp.records
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun readExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        val c = getClient() ?: return emptyList()
        return try {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            resp.records
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun readSleepSessions(start: Instant, end: Instant): List<SleepSessionRecord> {
        val c = getClient() ?: return emptyList()
        return try {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            resp.records
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Reads all Parkinson symptom episodes in range by parsing the notes payload.
     */
    suspend fun readSymptoms(start: Instant, end: Instant): List<SymptomEpisode> {
        return readExerciseSessions(start, end)
            .mapNotNull { TremorParser.parseRecord(it) }
    }

    /**
     * Writes a symptom episode into Health Connect as an ExerciseSessionRecord
     * with the serialized payload in `notes`.
     */
    suspend fun writeSymptom(episode: SymptomEpisode): Boolean {
        val c = getClient() ?: return false
        return try {
            val record = ExerciseSessionRecord(
                startTime = java.time.Instant.ofEpochMilli(episode.startTime),
                startZoneOffset = java.time.ZoneOffset.UTC,
                endTime = java.time.Instant.ofEpochMilli(episode.endTime.coerceAtLeast(episode.startTime + 1000)),
                endZoneOffset = java.time.ZoneOffset.UTC,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
                title = "Parkinson ${episode.type.name}",
                notes = TremorParser.encode(episode),
                metadata = Metadata(clientRecordId = episode.id)
            )
            c.insertRecords(listOf(record))
            true
        } catch (e: Exception) {
            false
        }
    }


    suspend fun deleteSymptom(episode: SymptomEpisode): Boolean {
        val c = getClient() ?: return false
        return try {
            c.deleteRecords(
                ExerciseSessionRecord::class,
                emptyList(),
                listOf(episode.id)
            )
            true
        } catch (e: Exception) {
            try {
                c.deleteRecords(
                    ExerciseSessionRecord::class,
                    TimeRangeFilter.between(
                        java.time.Instant.ofEpochMilli(episode.startTime - 1000),
                        java.time.Instant.ofEpochMilli(episode.endTime + 1000)
                    )
                )
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    suspend fun updateSymptom(old: SymptomEpisode, newEpisode: SymptomEpisode): Boolean {
        return try {
            if (deleteSymptom(old)) {
                writeSymptom(newEpisode.copy(id = old.id))
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun permissionsIntent(): Intent? {
        // Delegated to PermissionController in UI; this helper left for fallback.
        return null
    }
}
