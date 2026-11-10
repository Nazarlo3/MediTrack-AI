package com.example.meditrackai.data.settings

import android.content.Context
import com.example.meditrackai.diagnostics.AppDiagnostics
import com.example.meditrackai.network.DiagnosticsApi

interface SettingsRepository {
    suspend fun diagnostics(medications: Int, history: Int): String
    suspend fun health(): String
}

class AndroidSettingsRepository(context: Context, private val api: DiagnosticsApi) : SettingsRepository {
    private val context = context.applicationContext
    override suspend fun diagnostics(medications: Int, history: Int) = AppDiagnostics.describe(context, medications, history)
    override suspend fun health(): String {
        val result = api.health()
        return if (result.isSuccessful && result.body()?.status == "ok")
            "Backend доступний. Це перевірка сервера, без виклику AI та Google."
        else "Перевірка backend: HTTP ${result.code()}"
    }
}
