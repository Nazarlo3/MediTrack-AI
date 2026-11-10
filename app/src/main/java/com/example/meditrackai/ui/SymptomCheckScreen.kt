package com.example.meditrackai.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.meditrackai.ui.theme.*

@Composable
fun SymptomCheckScreen(
    viewModel: SymptomViewModel,
    themeMode: com.example.meditrackai.data.ThemeMode = com.example.meditrackai.data.ThemeMode.SYSTEM,
    onThemeChange: (com.example.meditrackai.data.ThemeMode) -> Unit = {},
    collectionSettings: com.example.meditrackai.data.DataCollectionSettings? = null,
    settingsViewModel: SettingsViewModel? = null
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) {
        viewModel.refreshMedications()
        onPauseOrDispose { }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val canClarify by viewModel.canClarify.collectAsStateWithLifecycle()
    var clarificationDraft by rememberSaveable { mutableStateOf("") }
    var lastCompletedAnalysisId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState) {
        val success = uiState as? SymptomUiState.Success
        if (success != null && success.analysisId != lastCompletedAnalysisId) {
            clarificationDraft = ""
            lastCompletedAnalysisId = success.analysisId
        }
    }
    val history by viewModel.historyState.collectAsStateWithLifecycle()
    val historyLoading by viewModel.historyLoading.collectAsStateWithLifecycle()
    val historyUnavailable by viewModel.historyUnavailable.collectAsStateWithLifecycle()
    val selectedHistory by viewModel.selectedHistory.collectAsStateWithLifecycle()
    val medications by viewModel.medicationsState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(1) }
    val tabStates = rememberSaveableStateHolder()
    val storageError by viewModel.storageError.collectAsStateWithLifecycle()
    var showDebugMenu by remember { mutableStateOf(false) }

    val isDebuggable = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    var headerTapCount by remember { mutableIntStateOf(0) }
    var lastHeaderTapTime by remember { mutableLongStateOf(0L) }

    if (showSettings) {
        AppSettingsScreen(themeMode, onThemeChange, collectionSettings, medications.size, history.size,
            storageError, onBack = { showSettings = false }, viewModel = settingsViewModel)
        return
    }

    if (showDebugMenu && isDebuggable) {
        DebugMenu(
            medications = medications,
            onBack = { showDebugMenu = false }
        )
        return
    }

    androidx.activity.compose.BackHandler(enabled = selectedTab != 1) { selectedTab = 1 }
    com.example.meditrackai.ui.components.MediTrackShell(selectedTab, { selectedTab = it },
        onSettings = { showSettings = true }, onBrandTap = {
            if (isDebuggable) {
                val now = System.currentTimeMillis()
                headerTapCount = if (now - lastHeaderTapTime < 500) headerTapCount + 1 else 1
                lastHeaderTapTime = now
                if (headerTapCount >= 5) { headerTapCount = 0; showDebugMenu = true }
            }
        }) {
    com.example.meditrackai.ui.components.AppPage(spacing = 0.dp) {
        if (storageError != null) {
            Text(storageError!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (selectedTab == 0) {
            tabStates.SaveableStateProvider("symptoms") {
            SymptomInputSection(
                uiState = uiState,
                onCheckSymptom = { text, days, intensity ->
                    clarificationDraft = ""; viewModel.checkSymptom(text, days, intensity)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            when (val state = uiState) {
                is SymptomUiState.Idle -> {}

                is SymptomUiState.Loading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Аналізуємо... Перший запит може тривати 20–30 сек, поки прокидається хмарний сервер.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = viewModel::cancelAnalysis) { Text("Скасувати аналіз") }
                        }
                    }
                }

                is SymptomUiState.Success -> {
                    SuccessCard(
                        category = state.result.category,
                        explanation = state.result.explanation,
                        urgency = state.result.urgency,
                        disclaimer = state.result.disclaimer
                    )
                    ClarificationSection(
                        question = state.result.followUpQuestion,
                        enabled = canClarify, text = clarificationDraft,
                        onTextChange = { clarificationDraft = it },
                        onSend = viewModel::clarifySymptom,
                        onNewCheck = { clarificationDraft = ""; viewModel.reset() }
                    )
                }

                is SymptomUiState.NoResult -> {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Потрібно більше інформації", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(state.result.explanation)
                            Spacer(Modifier.height(8.dp))
                            Text("Цю відповідь не додано до історії завершених перевірок.",
                                style = MaterialTheme.typography.bodySmall)
                            Text(state.result.disclaimer, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    ClarificationSection(
                        question = state.result.followUpQuestion, enabled = canClarify,
                        text = clarificationDraft, onTextChange = { clarificationDraft = it },
                        onSend = viewModel::clarifySymptom,
                        onNewCheck = { clarificationDraft = ""; viewModel.reset() }
                    )
                }

                is SymptomUiState.Error -> {
                    ErrorCard(
                        message = state.message,
                        onRetry = if (state.canRetry) ({ viewModel.retryLastCheck() }) else null
                    )
                }
            }


            }
        } else if (selectedTab == 2) {
            tabStates.SaveableStateProvider("history") {
            Spacer(modifier = Modifier.height(24.dp))
            HistorySection(
                history = history,
                loading = historyLoading,
                unavailable = historyUnavailable,
                selectedItem = selectedHistory,
                onOpen = viewModel::openHistoryItem,
                onDismissDetails = viewModel::closeHistoryItem,
                onClear = viewModel::clearHistory,
                onDelete = viewModel::deleteHistoryItem
            )
            }
        } else {
            tabStates.SaveableStateProvider("medications") {
            MedicationSection(
                medications = medications,
                onAddMedication = { name, dosage, photoUri, days, mealSlot, endDate ->
                    viewModel.addMedication(name, dosage, photoUri, days, mealSlot, endDate)
                },
                onToggleStatus = { id, time -> viewModel.toggleMedicationStatus(id, time) },
                onDeleteMedication = { id -> viewModel.deleteMedication(id) },
                onEditMedication = viewModel::editMedication
            )
            }
        }
    }
    }
}
