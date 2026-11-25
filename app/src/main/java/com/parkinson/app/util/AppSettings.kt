package com.parkinson.app.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

object AppSettings {
    private const val PREFS = "parkinson_app_settings"
    private const val KEY_PALETTE = "palette"
    private const val KEY_TEXT_SCALE = "text_scale"
    private const val KEY_AI_API_KEY = "ai_api_key"
    private const val KEY_PATIENT_NAME = "patient_name"

    val colorPalette = mutableStateOf("blue")
    val textScale = mutableStateOf(1f)
    val aiApiKey = mutableStateOf("")
    val patientName = mutableStateOf("")

    val paletteOptions = listOf(
        "blue" to "Azul",
        "green" to "Verde",
        "red" to "Rojo",
        "purple" to "Morado"
    )

    val textScaleOptions = listOf(
        0.85f to "Pequeño",
        1.0f to "Normal",
        1.15f to "Grande",
        1.3f to "Muy grande"
    )

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        colorPalette.value = prefs.getString(KEY_PALETTE, "blue") ?: "blue"
        textScale.value = prefs.getFloat(KEY_TEXT_SCALE, 1f)
        aiApiKey.value = prefs.getString(KEY_AI_API_KEY, "") ?: ""
        patientName.value = prefs.getString(KEY_PATIENT_NAME, "") ?: ""
    }

    fun applyApiKey(context: Context, key: String) {
        aiApiKey.value = key
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_AI_API_KEY, key).apply()
    }

    fun applyPatientName(context: Context, name: String) {
        patientName.value = name
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PATIENT_NAME, name).apply()
    }

    fun applyPalette(context: Context, palette: String) {
        colorPalette.value = palette
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PALETTE, palette).apply()
    }

    fun applyTextScale(context: Context, scale: Float) {
        textScale.value = scale
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_TEXT_SCALE, scale).apply()
    }

    fun colorFor(palette: String): Color {
        return when (palette) {
            "green" -> Color(0xFF2E7D32)
            "red" -> Color(0xFFC62828)
            "purple" -> Color(0xFF6A1B9A)
            else -> Color(0xFF1565C0)
        }
    }
}
