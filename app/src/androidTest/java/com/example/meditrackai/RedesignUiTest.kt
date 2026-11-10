package com.example.meditrackai

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meditrackai.data.*
import com.example.meditrackai.data.settings.SettingsRepository
import com.example.meditrackai.data.symptoms.*
import com.example.meditrackai.domain.medications.MedicationRepository
import com.example.meditrackai.domain.symptoms.AnalyzeSymptomUseCase
import com.example.meditrackai.network.*
import com.example.meditrackai.ui.*
import com.example.meditrackai.ui.theme.MediTrackAITheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

class RedesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun navigationDraftsSettingsAndReadableThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val prefix = "redesign_${UUID.randomUUID()}_"
        val names = mutableSetOf<String>()
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                names += prefix+name
                return target.getSharedPreferences(prefix+name,mode)
            }
        }
        val collection = DataCollectionSettings(context)
        val today = LocalDate.now().toString()
        var items = listOf(
            MedicationItem(name="Вітамін C",dosage="1 таблетка",times=listOf("08:00"),endDate="31.12.2030",takenDoseDates=mapOf("08:00" to today)),
            MedicationItem(name="Омега-3",dosage="1 капсула",times=listOf("18:00"),endDate="31.12.2030",takenDoseDates=emptyMap()))
        val medications = object : MedicationRepository {
            override suspend fun load() = items
            override suspend fun update(change: (List<MedicationItem>)->List<MedicationItem>): List<MedicationItem> { items=change(items); return items }
            override suspend fun synchronize(): String? = null
            override suspend fun deletePhoto(path: String?) { }
        }
        val api = object : MediTrackApi {
            override suspend fun analyzeSymptom(request: SymptomRequest): retrofit2.Response<SymptomResponse> = error("No network in design tests")
        }
        val repository = DefaultSymptomRepository(api,SharedPreferencesSymptomHistorySource(LocalDataStore(context)))
        val models = ViewModelStore()
        lateinit var vm: SymptomViewModel
        lateinit var settings: SettingsViewModel
        val dark = mutableStateOf(false)
        val font = mutableFloatStateOf(1f)
        fun capture(name: String) {
            compose.waitForIdle()
            val directory=File(target.cacheDir,"redesign-ui").apply { mkdirs() }
            File(directory,"$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
        fun tab(text: String) { compose.onNode(hasText(text) and isSelectable()).performClick() }
        try {
            instrumentation.runOnMainSync {
                vm=SymptomViewModel(medications,repository,AnalyzeSymptomUseCase(repository));models.put("symptoms",vm)
                settings=SettingsViewModel(object : SettingsRepository {
                    override suspend fun diagnostics(medications: Int,history: Int)="MediTrack AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nAndroid 15 • синтетичні дані\nЛокально: $medications препаратів • $history аналізів"
                    override suspend fun health()="Синтетична перевірка: backend доступний"
                });models.put("settings",settings)
            }
            compose.setContent {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,font.floatValue)) {
                    MediTrackAITheme(darkTheme=dark.value) {
                        Scaffold { padding ->
                            Box(Modifier.padding(padding)) {
                                SymptomCheckScreen(vm,collectionSettings=collection,settingsViewModel=settings)
                            }
                        }
                    }
                }
            }
            compose.waitUntil(5000) { vm.medicationsState.value.size==2 && !vm.historyLoading.value }
            compose.onNodeWithText("1 із 2 прийомів виконано").assertExists()
            compose.onNodeWithText("Прийнято ✓").assertExists()
            capture("01-medications-light")
            compose.onNodeWithText("Додати препарат").performScrollTo().performClick()
            compose.onNodeWithText("Додати новий препарат").assertExists()
            compose.onNodeWithText("До списку ліків").performClick()
            tab("Аналіз AI")
            compose.onNodeWithText("Опис симптому").performTextReplacement("Синтетична чернетка")
            capture("02-analysis-light")
            compose.onNodeWithText("Додаткові дані").performScrollTo().performClick()
            compose.onNodeWithText("Тривалість, днів").assertExists()
            capture("02b-analysis-details")
            tab("Історія")
            compose.onNodeWithText("Опис симптому").assertDoesNotExist()
            capture("03-history-light")
            tab("Аналіз AI")
            compose.onNodeWithText("Синтетична чернетка").assertExists()
            compose.onNodeWithContentDescription("Налаштування").performClick()
            capture("04-settings-light")
            compose.onNodeWithText("Про застосунок").performScrollTo().performClick()
            compose.onNodeWithText("Проєкт на GitHub").assertExists()
            compose.runOnIdle { dark.value=true }
            capture("05-about-dark")
            compose.onNodeWithText("Назад").performClick()
            compose.onNodeWithText("Діагностика").performScrollTo().performClick()
            compose.onNodeWithText("Перевірити backend").performScrollTo().performClick()
            compose.waitUntil(5000) { settings.uiState.value.health.startsWith("Синтетична перевірка") }
            compose.onNodeWithText("Назад").performScrollTo().performClick()
            compose.onNodeWithText("Готово").performClick()
            tab("Мої ліки")
            compose.runOnIdle { font.floatValue=1.5f }
            capture("06-medications-large-dark")
        } finally {
            instrumentation.runOnMainSync { models.clear() }
            names.forEach { target.deleteSharedPreferences(it) }
        }
    }
}
