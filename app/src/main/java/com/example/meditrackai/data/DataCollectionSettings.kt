package com.example.meditrackai.data

import android.content.Context
import android.content.SharedPreferences
import com.example.meditrackai.network.CollectionApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.security.SecureRandom

data class CollectionChoice(val enabled: Boolean = true) {
    val mayCollect: Boolean get() = enabled
}

/** Capability is private, persisted before use and never shown in UI/logs. */
class DataCollectionSettings(context: Context) : CollectionPreferences {
    private val prefs = context.applicationContext.getSharedPreferences("meditrack_collection", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(CollectionChoice(prefs.getBoolean("enabled", true)))
    val choice: StateFlow<CollectionChoice> = state
    private val statusState = MutableStateFlow("")
    val status: StateFlow<String> = statusState
    private companion object { val lock = Any() }
    // Activity recreation can leave the retained ViewModel with another settings instance.
    // SharedPreferences listeners are weakly held; retain ours for this instance's lifetime.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "enabled" || key == null) {
            state.value = CollectionChoice(prefs.getBoolean("enabled", true))
        }
    }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }

    suspend fun choose(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val ok = prefs.edit().putBoolean("enabled", enabled).putInt("noticeVersion", 2).commit()
            if (ok) state.value = CollectionChoice(enabled)
            else statusState.value = "Не вдалося зберегти вибір."
            ok
        }
    }

    override suspend fun tokenForCollection(): String? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            // Persisted choice is authoritative, even before the UI listener is dispatched.
            if (!prefs.getBoolean("enabled", true)) return@synchronized null
            token()
        }
    }

    private fun token(): String? {
        prefs.getString("token", null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        return if (prefs.edit().putString("token", token).commit()) token else null
    }

    override fun report(status: String?) {
        statusState.value = when (status) {
            "saved" -> "Останнє звернення збережено на сервері."
            "queued" -> "Останнє звернення передано в чергу збереження. Запис у Google ще не підтверджено."
            "disabled" -> "На сервері збір даних поки не налаштований."
            "revoked" -> "Серверне збереження для цього встановлення вимкнено. Зверніться до адміністратора проєкту."
            "consent_required" -> "Сервер потребує новішої версії налаштувань збору даних."
            else -> "Сервер не підтвердив збереження звернення."
        }
    }

    suspend fun deleteRemote(api: CollectionApi) {
        if (!choose(false)) return
        val oldToken = withContext(Dispatchers.IO) { synchronized(lock) { prefs.getString("token", null) } }
        if (oldToken == null) { statusState.value = "З цього встановлення записи не надсилалися."; return }
        try {
            val response = api.deleteMine(oldToken)
            if (!response.isSuccessful || response.body() == null) {
                statusState.value = "Видалення не підтверджено. Спробуйте ще раз із доступом до сервера."
                return
            }
            val cleared = withContext(Dispatchers.IO) { synchronized(lock) { prefs.edit().remove("token").commit() } }
            statusState.value = if (cleared) "Серверні записи видалено. Збір вимкнено; локальна історія збережена."
                else "Серверні записи видалено, але локальний ключ не оновлено. Повторіть видалення."
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { statusState.value = "Не вдалося підтвердити видалення. Перевірте мережу й повторіть." }
    }
}
