package com.parkinson.app.domain.models

enum class SymptomType { TREMOR, DYSKINESIA, ON, OFF, MOOD, OBSERVATION }

data class SymptomEpisode(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: SymptomType,
    val severity: Float, // 0.0 .. 1.0
    val startTime: Long, // epoch millis
    val endTime: Long,
    val source: String = "manual", // manual | inferred | active_test
    val notes: String? = null
)
