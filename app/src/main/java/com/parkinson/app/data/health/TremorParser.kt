package com.parkinson.app.data.health

import android.util.Log
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.parkinson.app.domain.models.SymptomEpisode
import com.parkinson.app.domain.models.SymptomType
import org.json.JSONObject

/**
 * Health Connect has no native TremorRecord. We store Parkinson symptoms
 * serialized as JSON inside the `notes` field of an ExerciseSessionRecord,
 * prefixed with (PARKINSON_APP_DATA).
 */
object TremorParser {
    const val SIGNATURE = "[PARKINSON_APP_DATA]:"
    private const val TAG = "TremorParser"

    fun encode(episode: SymptomEpisode): String {
        val obj = JSONObject()
        obj.put("id", episode.id)
        obj.put("type", episode.type.name)
        obj.put("severity", episode.severity.toDouble())
        obj.put("start", episode.startTime)
        obj.put("end", episode.endTime)
        obj.put("source", episode.source)
        episode.notes?.let { obj.put("notes", it) }
        return SIGNATURE + obj.toString()
    }

    fun parseNotes(notes: String?): SymptomEpisode? {
        if (notes.isNullOrBlank() || !notes.contains(SIGNATURE)) return null
        return try {
            val json = notes.substringAfter(SIGNATURE)
            val obj = JSONObject(json)
            val type = SymptomType.valueOf(obj.optString("type", "TREMOR"))
            SymptomEpisode(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                type = type,
                severity = obj.optDouble("severity", 0.0).toFloat(),
                startTime = obj.optLong("start", 0L),
                endTime = obj.optLong("end", 0L),
                source = obj.optString("source", "unknown"),
                notes = obj.optString("notes").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse symptom payload: ${e.message}")
            null
        }
    }

    fun parseRecord(record: ExerciseSessionRecord): SymptomEpisode? {
        return parseNotes(record.notes)
    }

    fun isParkinsonRecord(record: ExerciseSessionRecord): Boolean {
        return record.notes?.contains(SIGNATURE) == true
    }
}
