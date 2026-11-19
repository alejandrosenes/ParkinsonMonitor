package com.parkinson.app.util

import android.content.Context

object FavoriteMedStore {
    private const val PREFS = "parkinson_meds"
    private const val KEY = "favorites"

    fun getAll(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()
    }

    fun toggle(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toMutableSet()
        if (cur.contains(name)) cur.remove(name) else cur.add(name)
        prefs.edit().putStringSet(KEY, cur).apply()
    }

    fun isFavorite(context: Context, name: String): Boolean {
        return getAll(context).contains(name)
    }

    fun setAll(context: Context, values: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, values).apply()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
