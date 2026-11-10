package com.example.meditrackai

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.data.symptoms.DefaultSymptomRepository
import com.example.meditrackai.data.symptoms.SharedPreferencesSymptomHistorySource
import com.example.meditrackai.domain.medications.MedicationRepository
import com.example.meditrackai.domain.symptoms.AnalyzeSymptomUseCase
import com.example.meditrackai.domain.symptoms.SymptomError
import com.example.meditrackai.network.*
import com.example.meditrackai.ui.*
import com.example.meditrackai.ui.theme.MediTrackAITheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.UUID

/** Opt-in report capture: production UI, isolated data, one real uncollected API call. */
class ReportScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun currentUiErrorAndRealRecovery() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("reportScreens") == "true")
        require(BuildConfig.BACKEND_URL == "https://meditrack-ai-xbk5.onrender.com/")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val prefix = "report_capture_${UUID.randomUUID()}_"
        val names = mutableSetOf<String>()
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                names += prefix + name
                return target.getSharedPreferences(prefix + name, mode)
            }
        }
        val today = LocalDate.now().toString()
        val items = listOf(
            MedicationItem(name="Вітамін C", dosage="1 таблетка", times=listOf("08:00"),
                endDate="31.12.2030", takenDoseDates=mapOf("08:00" to today)),
            MedicationItem(name="Омега-3", dosage="1 капсула", times=listOf("18:00"),
                endDate="31.12.2030", takenDoseDates=emptyMap())
        )
        val medications = object : MedicationRepository {
            override suspend fun load() = items
            override suspend fun update(change: (List<MedicationItem>) -> List<MedicationItem>) = change(items)
            override suspend fun synchronize(): String? = null
            override suspend fun deletePhoto(path: String?) {}
        }
        var calls = 0
        var realCalls = 0
        val api = object : MediTrackApi {
            override suspend fun analyzeSymptom(request: SymptomRequest): retrofit2.Response<SymptomResponse> {
                calls++
                if (calls == 1) throw IOException("Controlled report capture failure")
                assertNull("Report requests must not collect data", request.collection)
                realCalls++
                return ApiClient.api.analyzeSymptom(request)
            }
        }
        val repository = DefaultSymptomRepository(api, SharedPreferencesSymptomHistorySource(LocalDataStore(context)))
        val models = ViewModelStore()
        lateinit var vm: SymptomViewModel
        lateinit var focus: androidx.compose.ui.focus.FocusManager
        var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
        val folder = File(target.cacheDir, "report-current").apply { mkdirs() }
        fun capture(name: String) {
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(300) // Finish the system window animation before capture.
            File(folder, "$name.png").outputStream().use {
                check(instrumentation.uiAutomation.takeScreenshot()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        try {
            instrumentation.runOnMainSync {
                vm = SymptomViewModel(medications, repository, AnalyzeSymptomUseCase(repository))
                models.put("report", vm)
            }
            compose.setContent {
                focus = androidx.compose.ui.platform.LocalFocusManager.current
                keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                MediTrackAITheme(darkTheme=false) {
                    Scaffold { padding -> Box(Modifier.padding(padding)) { SymptomCheckScreen(vm) } }
                }
            }
            compose.waitUntil(10000) { !vm.historyLoading.value && vm.medicationsState.value.size == 2 }
            compose.onNodeWithText("1 із 2 прийомів виконано").assertExists()
            capture("01-medications")
            compose.onNode(hasText("Аналіз AI") and isSelectable()).performClick()
            compose.onNodeWithText("Опис симптому").performTextReplacement(
                "Після довгої прогулянки трохи втомився, інших скарг немає.")
            compose.waitForIdle()
            compose.runOnIdle { focus.clearFocus(force = true); keyboard?.hide() }
            android.os.SystemClock.sleep(300)
            compose.onNodeWithText("Перевірити").performScrollTo().performClick()
            compose.waitUntil(10000) { vm.uiState.value is SymptomUiState.Error }
            assertEquals(SymptomUiState.Error(SymptomError.NETWORK), vm.uiState.value)
            assertTrue(vm.historyState.value.isEmpty())
            compose.onNodeWithText("Спробувати ще раз").performScrollTo().assertIsDisplayed()
            capture("02-network-error")
            compose.onNodeWithText("Спробувати ще раз").performClick()
            compose.waitUntil(90000) { vm.uiState.value !is SymptomUiState.Loading }
            assertTrue("Real backend must return usable success", vm.uiState.value is SymptomUiState.Success)
            compose.waitUntil(10000) { vm.historyState.value.size == 1 }
            val result = (vm.uiState.value as SymptomUiState.Success).result
            compose.onNodeWithText(result.explanation).performScrollTo().assertIsDisplayed()
            capture("03-recovered")
            assertEquals(2, calls)
            assertEquals(1, realCalls)
            File(folder, "verification.json").writeText(org.json.JSONObject()
                .put("passed", true).put("injected_network_failures", 1)
                .put("real_api_calls", realCalls).put("history_entries", vm.historyState.value.size)
                .put("collection_enabled", false).put("version", BuildConfig.VERSION_NAME)
                .put("device_android", android.os.Build.VERSION.RELEASE).toString(2))
        } finally {
            instrumentation.runOnMainSync { models.clear() }
            names.forEach { target.deleteSharedPreferences(it) }
        }
    }
}
