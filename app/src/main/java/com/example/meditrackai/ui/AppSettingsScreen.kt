package com.example.meditrackai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.meditrackai.data.DataCollectionSettings
import com.example.meditrackai.data.ThemeMode
import com.example.meditrackai.diagnostics.ApiDiagnostics
import com.example.meditrackai.diagnostics.AppDiagnostics
import com.example.meditrackai.network.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit,
                      collectionSettings: DataCollectionSettings?, medications: Int, history: Int,
                      storageError: String?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastApi by ApiDiagnostics.lastRequest.collectAsState()
    var technical by remember { mutableStateOf("Завантаження…") }
    var health by remember { mutableStateOf("Backend ще не перевірено") }
    var checking by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(context, medications, history, refresh) {
        technical = try { AppDiagnostics.describe(context, medications, history) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { "Не вдалося прочитати технічні дані." }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Налаштування", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Готово") }
        }
        Text("Тема застосунку", style = MaterialTheme.typography.titleMedium)
        ThemeMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().clickable { onThemeChange(mode) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = themeMode == mode, onClick = { onThemeChange(mode) })
                Text(mode.label)
            }
        }
        collectionSettings?.let { DataCollectionSettingsSection(it) }
        HorizontalDivider()
        Text("Про застосунок і діагностика", style = MaterialTheme.typography.titleMedium)
        Text(technical, style = MaterialTheme.typography.bodyMedium)
        storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Останній AI-запит: ${lastApi?.describe() ?: "немає в цій сесії"}")
        Text(health)
        OutlinedButton(enabled = !checking, onClick = {
            checking = true
            scope.launch {
                try {
                    val result = ApiClient.diagnosticsApi.health()
                    health = if (result.isSuccessful && result.body()?.status == "ok")
                        "Backend доступний. Це перевірка сервера, без виклику AI та Google."
                    else "Перевірка backend: HTTP ${result.code()}"
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { health = "Backend недоступний. Перевірте інтернет або повторіть пізніше." }
                finally { checking = false }
            }
        }) { Text(if (checking) "Перевіряємо…" else "Перевірити backend") }
        OutlinedButton(onClick = {
            refresh++
        }) { Text("Оновити діагностику") }
        Button(onClick = {
            val text = "$technical\n$health\nОстанній AI-запит: ${lastApi?.describe() ?: "немає"}" +
                (storageError?.let { "\n$it" } ?: "")
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("MediTrack AI — діагностика", text))
            feedback = "Діагностику скопійовано."
        }) { Text("Копіювати діагностику") }
        Text("Містить технічні дані пристрою й застосунку. Тексти звернень, відповіді AI та ключі не копіюються.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Nazarlo3/MediTrack-AI")))
            } catch (_: android.content.ActivityNotFoundException) { feedback = "Не знайдено браузер для відкриття GitHub." }
        }) { Text("Проєкт на GitHub") }
        if (feedback.isNotEmpty()) Text(feedback)
    }
}
