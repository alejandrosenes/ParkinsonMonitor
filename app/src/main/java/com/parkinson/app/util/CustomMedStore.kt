package com.parkinson.app.util

import android.content.Context

data class CustomMed(val name: String, val doseMg: Float)

object CustomMedStore {
    private const val PREFS = "parkinson_custom_meds"
    private const val KEY = "meds"

    private fun encode(name: String, dose: Float): String = "$name|$dose"

    fun getAll(context: Context): List<CustomMed> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())!!.mapNotNull { e ->
                val parts = e.split("|")
                val name = parts.getOrElse(0) { return@mapNotNull null }
                val dose = parts.getOrElse(1) { "100" }.toFloatOrNull() ?: 100f
                if (name.isBlank()) null else CustomMed(name, dose)
            }
    }

    fun add(context: Context, name: String, dose: Float) {
        if (name.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        cur.add(encode(name, dose))
        prefs.edit().putStringSet(KEY, cur).apply()
    }

    fun remove(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        cur.removeIf { it.split("|").firstOrNull() == name }
        prefs.edit().putStringSet(KEY, cur).apply()
    }

    fun update(context: Context, oldName: String, newName: String, newDose: Float) {
        remove(context, oldName)
        add(context, newName, newDose)
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
