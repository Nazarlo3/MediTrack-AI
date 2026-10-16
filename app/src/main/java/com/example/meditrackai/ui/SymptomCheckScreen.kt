package com.example.meditrackai.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meditrackai.ui.theme.*

@Composable
fun SymptomCheckScreen(viewModel: SymptomViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.historyState.collectAsState()
    val historyLoading by viewModel.historyLoading.collectAsState()
    val historyUnavailable by viewModel.historyUnavailable.collectAsState()
    val selectedHistory by viewModel.selectedHistory.collectAsState()
    val medications by viewModel.medicationsState.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(1) }
    val tabStates = rememberSaveableStateHolder()
    val storageError by viewModel.storageError.collectAsState()
    var showDebugMenu by remember { mutableStateOf(false) }

    val isDebuggable = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    var headerTapCount by remember { mutableIntStateOf(0) }
    var lastHeaderTapTime by remember { mutableLongStateOf(0L) }

    if (showDebugMenu && isDebuggable) {
        DebugMenu(
            medications = medications,
            onBack = { showDebugMenu = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isDebuggable) {
                        val now = System.currentTimeMillis()
                        if (now - lastHeaderTapTime < 500L) {
                            headerTapCount++
                        } else {
                            headerTapCount = 1
                        }
                        lastHeaderTapTime = now
                        if (headerTapCount >= 5) {
                            headerTapCount = 0
                            showDebugMenu = true
                        }
                    }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "MediTrack AI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Штучний інтелект для вашого здоров'я",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Short Tab Names for Single Line Fit
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "Аналіз AI",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Мої ліки (${medications.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (storageError != null) {
            Text(storageError!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (selectedTab == 0) {
            tabStates.SaveableStateProvider("symptoms") {
            SymptomInputSection(
                uiState = uiState,
                onCheckSymptom = { viewModel.checkSymptom(it) }
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
                }

                is SymptomUiState.Error -> {
                    ErrorCard(
                        message = state.message,
                        onRetry = if (state.canRetry) ({ viewModel.retryLastCheck() }) else null
                    )
                }
            }

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
                onToggleStatus = { id -> viewModel.toggleMedicationStatus(id) },
                onDeleteMedication = { id -> viewModel.deleteMedication(id) }
            )
            }
        }
    }
}

@Composable
private fun SymptomInputSection(
    uiState: SymptomUiState,
    onCheckSymptom: (String) -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Опишіть своє самопочуття, щоб отримати загальну категорію звернення",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Опис симптому") },
                placeholder = { Text("Наприклад: болить голова і трохи нудить") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = uiState !is SymptomUiState.Loading,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onCheckSymptom(inputText) },
                enabled = uiState !is SymptomUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (uiState is SymptomUiState.Loading) "Перевіряємо..." else "Перевірити",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun SuccessCard(
    category: String,
    explanation: String,
    urgency: String?,
    disclaimer: String
) {
    val isDark = isSystemInDarkTheme()
    val safeUrgency = (urgency ?: "звичайне").lowercase()

    val (badgeText, badgeBgColor, badgeTextColor) = remember(safeUrgency, isDark) {
        when {
            safeUrgency.contains("терміново") -> Triple(
                "Терміново: Потрібна допомога",
                if (isDark) UrgencyUrgentBgDark else UrgencyUrgentBgLight,
                if (isDark) UrgencyUrgentTextDark else UrgencyUrgentTextLight
            )
            safeUrgency.contains("увага") -> Triple(
                "Увага: Консультація ближчим часом",
                if (isDark) UrgencyWarningBgDark else UrgencyWarningBgLight,
                if (isDark) UrgencyWarningTextDark else UrgencyWarningTextLight
            )
            else -> Triple(
                "Стан: Плановий / Звичайний",
                if (isDark) UrgencyNormalBgDark else UrgencyNormalBgLight,
                if (isDark) UrgencyNormalTextDark else UrgencyNormalTextLight
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Категорія: $category",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .background(badgeBgColor, shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeTextColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = disclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Спробувати ще раз", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
