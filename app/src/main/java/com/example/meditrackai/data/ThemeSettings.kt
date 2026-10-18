package com.example.meditrackai.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

enum class ThemeMode(val label: String) {
    SYSTEM("Як у системі"), LIGHT("Світла"), DARK("Темна")
}

class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("meditrack_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(runCatching {
        ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name)!!)
    }.getOrDefault(ThemeMode.SYSTEM))
    val mode: StateFlow<ThemeMode> = state

    suspend fun save(mode: ThemeMode): Boolean = withContext(Dispatchers.IO) {
        if (prefs.edit().putString("theme", mode.name).commit()) { state.value = mode; true } else false
    }
}
