package com.parkinson.app.data.health

import com.parkinson.app.data.local.repository.MedicationRepository
import com.parkinson.app.domain.models.BiometricSample
import com.parkinson.app.domain.models.SymptomEpisode
import com.parkinson.app.domain.models.SymptomType
import com.parkinson.app.domain.models.TimelineBucket
import java.time.Instant
import kotlin.math.roundToInt

class ParkinsonDataAggregator(
    private val health: HealthConnectManager,
    private val medicationRepository: MedicationRepository
) {
    /**
     * Builds a list of time buckets covering (startMillis..endMillis), crossing
     * biometric data (heart rate, steps), symptom episodes (from HC notes)
     * and medication intakes (from Room).
     */
    suspend fun aggregate(
        startMillis: Long,
        endMillis: Long,
        bucketMinutes: Int = 15
    ): List<TimelineBucket> {
        val bucketMillis = bucketMinutes * 60_000L
        val symptoms = health.readSymptoms(
            Instant.ofEpochMilli(startMillis),
            Instant.ofEpochMilli(endMillis)
        )
        val steps = health.readSteps(
            Instant.ofEpochMilli(startMillis),
            Instant.ofEpochMilli(endMillis)
        )
        val hr = health.readHeartRate(
            Instant.ofEpochMilli(startMillis),
            Instant.ofEpochMilli(endMillis)
        )

        // Precompute cumulative step deltas per bucket from StepsRecord
        val stepByBucket = HashMap<Long, Long>()
        for (rec in steps) {
            val s = rec.startTime.toEpochMilli().coerceAtLeast(startMillis)
            val e = rec.endTime.toEpochMilli().coerceAtMost(endMillis)
            // distribute record's count across buckets by start bucket of record
            val b = bucketStart(s, startMillis, bucketMillis)
            stepByBucket[b] = (stepByBucket[b] ?: 0L) + rec.count
        }

        // Heart rate averaging per bucket
        val hrSum = HashMap<Long, Double>()
        val hrCount = HashMap<Long, Int>()
        for (rec in hr) {
            for (sample in rec.samples) {
                val t = sample.time.toEpochMilli()
                if (t < startMillis || t >= endMillis) continue
                val b = bucketStart(t, startMillis, bucketMillis)
                hrSum[b] = (hrSum[b] ?: 0.0) + sample.beatsPerMinute.toDouble()
                hrCount[b] = (hrCount[b] ?: 0) + 1
            }
        }

        val lastDoseCache = HashMap<Long, Long?>()

        val result = mutableListOf<TimelineBucket>()
        var cursor = startMillis
        while (cursor < endMillis) {
            val bEnd = (cursor + bucketMillis).coerceAtMost(endMillis)
            val bStart = cursor

            // symptom overlapping this bucket with highest severity
            val overlapping = symptoms.filter { it.startTime < bEnd && it.endTime > bStart }
            val topSymptom = overlapping.maxByOrNull { it.severity }

            val avgHr = hrCount[bStart]?.let { c -> if (c > 0) hrSum[bStart]!! / c else null }
            val stepsTotal = stepByBucket[bStart] ?: 0L

            // minutes since last dose: compute from Room
            val minutesSince = if (lastDoseCache.containsKey(bStart)) {
                lastDoseCache[bStart]
            } else {
                val last = medicationRepository.lastDoseBefore(bEnd)
                val m = medicationRepository.minutesSinceLastDose(bEnd, last)
                lastDoseCache[bStart] = m
                m
            }

            result.add(
                TimelineBucket(
                    startMillis = bStart,
                    endMillis = bEnd,
                    symptomSeverity = topSymptom?.severity,
                    symptomType = topSymptom?.type,
                    avgHeartRate = avgHr,
                    totalSteps = stepsTotal,
                    minutesSinceLastDose = minutesSince
                )
            )
            cursor = bEnd
        }
        return result
    }

    /**
     * Aggregates ON/OFF percentages for the day dashboard based on symptom episodes.
     * ON  = time without significant tremor/ (1 - avg severity); OFF = tremor/rigidity presence.
     */
    suspend fun dailyOnOffPercent(startMillis: Long, endMillis: Long): Pair<Float, Float> {
        val buckets = aggregate(startMillis, endMillis, 15)
        if (buckets.isEmpty()) return 0.5f to 0.5f
        val symptomBuckets = buckets.count { it.symptomSeverity != null }
        if (symptomBuckets == 0) return 0.5f to 0.5f // sin datos -> neutro
        var offBuckets = 0
        for (b in buckets) {
            val isOff = b.symptomType == SymptomType.OFF || ((b.symptomSeverity ?: 0f) >= 0.4f && b.symptomType == SymptomType.TREMOR)
            if (isOff) offBuckets++
        }
        val off = (offBuckets.toFloat() / buckets.size).coerceIn(0f, 0.95f)
        val on = 1f - off
        return on to off
    }

    private fun bucketStart(t: Long, base: Long, size: Long): Long {
        val offset = t - base
        val idx = (offset / size).toInt()
        return base + idx * size
    }
}
