package com.example.meditrackai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.meditrackai.R
import com.example.meditrackai.data.DataCollectionSettings
import com.example.meditrackai.data.ThemeMode
import com.example.meditrackai.diagnostics.ApiDiagnostics
import com.example.meditrackai.ui.components.AppPage
import com.example.meditrackai.ui.components.SectionCard

/** Route owns lifecycle/platform UI actions; content has no network or disk access. */
@Composable
fun AppSettingsScreen(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit,
    collectionSettings: DataCollectionSettings?, medications: Int, history: Int,
    storageError: String?, onBack: () -> Unit, viewModel: SettingsViewModel? = null) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.diagnostics_copied)
    val browserMessage = stringResource(R.string.browser_unavailable)
    val state = viewModel?.uiState?.collectAsStateWithLifecycle()?.value ?: SettingsUiState(technical = "Діагностика недоступна")
    val lastApi by ApiDiagnostics.lastRequest.collectAsStateWithLifecycle()
    var feedback by remember { mutableStateOf("") }
    LifecycleResumeEffect(viewModel, medications, history) {
        viewModel?.refresh(medications, history)
        onPauseOrDispose { }
    }
    AppSettingsContent(themeMode, onThemeChange, collectionSettings, state, storageError,
        lastApi?.describe() ?: stringResource(R.string.no_session_request), feedback,
        canCheck = viewModel != null, onBack = onBack,
        onCheck = { viewModel?.checkBackend() }, onRefresh = { viewModel?.refresh(medications, history) },
        onCopy = {
            val text = "${state.technical}\n${state.health}\nОстанній AI-запит: ${lastApi?.describe() ?: "немає"}" +
                (storageError?.let { "\n$it" } ?: "")
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("MediTrack AI — діагностика", text))
            feedback = copiedMessage
        }, onGithub = {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Nazarlo3/MediTrack-AI"))) }
            catch (_: android.content.ActivityNotFoundException) { feedback = browserMessage }
        })
}

@Composable
private fun AppSettingsContent(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit,
    collectionSettings: DataCollectionSettings?, state: SettingsUiState, storageError: String?,
    lastApi: String, feedback: String, canCheck: Boolean, onBack: () -> Unit,
    onCheck: () -> Unit, onRefresh: () -> Unit, onCopy: () -> Unit, onGithub: () -> Unit) {
    var panel by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var chooseTheme by remember { mutableStateOf(false) }
    BackHandler(enabled = panel != 0) { panel = 0 }
    AppPage {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val title = when (panel) { 1 -> R.string.about_app; 2 -> R.string.diagnostics_title; else -> R.string.settings_title }
            Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { if (panel == 0) onBack() else panel = 0 }) {
                Text(stringResource(if (panel == 0) R.string.done else R.string.back))
            }
        }
        when (panel) {
            0 -> {
                Text("Зробіть MediTrack зручним для себе", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    ListItem(headlineContent = { Text(stringResource(R.string.theme_title)) },
                        supportingContent = { Text(themeMode.label) }, modifier = Modifier.clickable { chooseTheme = true })
                    DropdownMenu(expanded = chooseTheme, onDismissRequest = { chooseTheme = false }) {
                        ThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text(mode.label) }, onClick = { onThemeChange(mode); chooseTheme = false })
                        }
                    }
                }
                HorizontalDivider()
                collectionSettings?.let { DataCollectionSettingsSection(it) }
                HorizontalDivider()
                ListItem(headlineContent = { Text(stringResource(R.string.about_app)) },
                    supportingContent = { Text("Версія та GitHub") }, modifier = Modifier.clickable { panel = 1 })
                ListItem(headlineContent = { Text(stringResource(R.string.diagnostics_title)) },
                    supportingContent = { Text("З’єднання, дозволи та технічні дані") }, modifier = Modifier.clickable { panel = 2 })
            }
            1 -> {
                Text("MediTrack AI", style = MaterialTheme.typography.headlineMedium)
                Text(state.technical.lineSequence().firstOrNull().orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ваші ліки за графіком та попередня оцінка симптомів. AI не встановлює діагноз і не замінює лікаря.")
                TextButton(onClick = onGithub) { Text(stringResource(R.string.github_project)) }
            }
            2 -> {
                Text(state.technical, style = MaterialTheme.typography.bodyMedium)
                storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                HorizontalDivider()
                Text(stringResource(R.string.last_api_request, lastApi))
                Text(state.health)
                Button(enabled = canCheck && !state.checking, onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.checking) R.string.checking else R.string.check_backend))
                }
                OutlinedButton(enabled = canCheck, onClick = onRefresh) { Text(stringResource(R.string.refresh_diagnostics)) }
                TextButton(onClick = onCopy) { Text(stringResource(R.string.copy_diagnostics)) }
                Text(stringResource(R.string.diagnostics_notice), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (feedback.isNotEmpty()) Text(feedback)
    }
}
