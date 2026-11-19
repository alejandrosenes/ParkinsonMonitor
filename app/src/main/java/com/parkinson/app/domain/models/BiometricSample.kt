package com.parkinson.app.domain.models

data class BiometricSample(
    val timestamp: Long, // epoch millis
    val heartRate: Double?,
    val stepsDelta: Long
)

data class TimelineBucket(
    val startMillis: Long,
    val endMillis: Long,
    val symptomSeverity: Float?,
    val symptomType: SymptomType?,
    val avgHeartRate: Double?,
    val totalSteps: Long,
    val minutesSinceLastDose: Long?
)
