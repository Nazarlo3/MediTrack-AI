package com.example.meditrackai

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.data.symptoms.DefaultSymptomRepository
import com.example.meditrackai.data.symptoms.SharedPreferencesSymptomHistorySource
import com.example.meditrackai.domain.symptoms.AnalyzeSymptomUseCase
import com.example.meditrackai.domain.symptoms.SymptomError
import com.example.meditrackai.network.*
import com.example.meditrackai.ui.SymptomCheckScreen
import com.example.meditrackai.ui.SymptomUiState
import com.example.meditrackai.ui.SymptomViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Explicit opt-in; production UI and data flow with isolated preferences and marked fault injection. */
class Lab7PhoneScenarioTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val prefix = "lab7_phone_" + UUID.randomUUID() + "_"
    private val names = mutableSetOf<String>()
    private val application = object : Application() {
        init { attachBaseContext(target) }
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            synchronized(names) { names += prefix + name }
            return target.getSharedPreferences(prefix + name, mode)
        }
    }
    private val models = ViewModelStore()
    private lateinit var api: ControlledApi
    private lateinit var vm: SymptomViewModel

    private fun scenario(block: () -> Unit) {
        assumeTrue("Explicit opt-in required", InstrumentationRegistry.getArguments().getString("lab7Phone") == "true")
        require(BuildConfig.BACKEND_URL == "http://127.0.0.1:18765/") { "Build the documented local Lab7 APK" }
        val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
        api = ControlledApi(Retrofit.Builder().baseUrl(BuildConfig.BACKEND_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(MediTrackApi::class.java))
        val store = LocalDataStore(application)
        val repository = DefaultSymptomRepository(api, SharedPreferencesSymptomHistorySource(store))
        try {
            instrumentation.runOnMainSync {
                vm = SymptomViewModel(com.example.meditrackai.di.createMedicationRepository(application, store), repository, AnalyzeSymptomUseCase(repository))
                models.put("lab7", vm)
            }
            compose.setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        Text("ЛР7 • синтетичні дані • локальний backend", style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.weight(1f)) { SymptomCheckScreen(viewModel = vm) }
                    }
                }
            }
            compose.waitUntil(10000) { !vm.historyLoading.value }
            block()
        } finally {
            if (::vm.isInitialized) instrumentation.runOnMainSync { models.clear() }
            synchronized(names) { names.forEach { target.deleteSharedPreferences(it) } }
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun openAI() { compose.onNodeWithText("Аналіз AI").performClick() }
    private fun send(text: String) {
        compose.onNodeWithText("Опис симптому").performScrollTo().performTextReplacement(text)
        compose.onNodeWithText("Перевірити").performScrollTo().performClick()
    }
    private fun finished() {
        compose.waitUntil(60000) { vm.uiState.value !is SymptomUiState.Loading }
    }
    private fun success(historySize: Int) {
        finished()
        assertTrue("Actual: " + vm.uiState.value, vm.uiState.value is SymptomUiState.Success)
        compose.waitUntil(10000) { vm.historyState.value.size == historySize }
    }
    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val folder = File(target.cacheDir, "lab7-phone").apply { mkdirs() }
        File(folder, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun evidence(id: String, method: String, detail: String) {
        val folder = File(target.cacheDir, "lab7-phone").apply { mkdirs() }
        val value = org.json.JSONObject().put("id", id).put("passed", true).put("method", method)
            .put("detail", detail).put("real_groq_calls", api.realCalls).put("request_id", api.lastRequestId)
        File(folder, "$id.json").writeText(value.toString(2))
    }

    @Test fun f01RealAnalysisAndStoredHistory() = scenario {
        openAI()
        compose.onNodeWithText("Тривалість, днів").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("Інтенсивність").performScrollTo().performTextReplacement("5")
        send("Вдарив гомілку об край ліжка, болить у місці удару. Рани немає.")
        success(1)
        val result = (vm.uiState.value as SymptomUiState.Success).result
        assertEquals("травми та ушкодження", result.category)
        assertEquals(true, result.detailsUsed)
        assertEquals(true, result.hasUsableResult)
        assertTrue(result.explanation.contains("2 дн."))
        assertTrue(result.explanation.contains("5/10"))
        compose.onAllNodesWithText(result.explanation)[0].performScrollTo().assertIsDisplayed()
        save("01_success.png")
        val disk = runBlocking { LocalDataStore(application).load().first }
        assertEquals(1, disk.size)
        assertTrue(disk.single().description.contains("5/10"))
        compose.onNodeWithText("Відкрити деталі").performScrollTo().performClick()
        compose.onNodeWithText("Збережений результат").assertIsDisplayed()
        evidence("F01", "Real HTTP FastAPI/Groq and disk reread", "Analysis, details and history dialog verified")
    }

    @Test fun f02InvalidInputNeverCallsApi() = scenario {
        openAI(); send("")
        finished()
        assertEquals(SymptomUiState.Error(SymptomError.INVALID_INPUT), vm.uiState.value)
        compose.onNodeWithText("Введіть опис довжиною від 3 до 500 символів.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Тривалість, днів").performScrollTo().performTextReplacement("31")
        compose.onNodeWithText("Перевірити").performScrollTo().assertIsNotEnabled()
        assertEquals(0, api.calls)
        evidence("F02", "Real form validation", "Empty input rejected; duration 31 disables submit; 0 API calls")
    }

    @Test fun f03NetworkFailureAndF09RealRetry() = scenario {
        openAI(); api.failNext = true
        send("Після довгої прогулянки трохи втомився, інших скарг немає.")
        finished()
        assertEquals(SymptomUiState.Error(SymptomError.NETWORK), vm.uiState.value)
        compose.onNodeWithText("Спробувати ще раз").performScrollTo().assertIsDisplayed()
        assertTrue(vm.historyState.value.isEmpty())
        save("02_network_error.png")
        evidence("F03", "Controlled IOException at API boundary; Wi-Fi stays on", "Loading ends, public message and retry, no history")
        compose.onNodeWithText("Спробувати ще раз").performClick()
        success(1)
        assertEquals(1, api.realCalls)
        val result = (vm.uiState.value as SymptomUiState.Success).result
        compose.onAllNodesWithText(result.explanation)[0].performScrollTo().assertIsDisplayed()
        save("03_recovered.png")
        evidence("F09", "Real HTTP FastAPI/Groq after injected network failure", "Same session recovered and stored exactly one result")
    }

    @Test fun f04ServiceFailure() = scenario {
        openAI(); api.errorNext = true; send("Синтетична перевірка помилки сервісу")
        finished()
        assertEquals(SymptomUiState.Error(SymptomError.SERVER), vm.uiState.value)
        compose.onNodeWithText("Спробувати ще раз").performScrollTo().assertIsDisplayed()
        assertTrue(vm.historyState.value.isEmpty())
        evidence("F04", "Controlled HTTP 503 at API boundary", "No crash or history; actionable error")
    }

    @Test fun f05DuplicateLaunchAndF06BackgroundReturn() = scenario {
        openAI()
        api.gate = CompletableDeferred()
        send("Другий день кашляю. Дихати можу нормально, травми не було.")
        compose.onNodeWithText("Перевіряємо...").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { vm.checkSymptom("Дубль"); vm.retryLastCheck() }
        assertEquals(1, api.calls)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals(SymptomUiState.Loading, vm.uiState.value)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Скасувати аналіз").performScrollTo().assertIsDisplayed()
        api.gate?.complete(Unit)
        success(1)
        assertEquals(1, api.realCalls)
        assertEquals(1, api.calls)
        evidence("F05", "Real UI plus repeated ViewModel launch while request gated", "One network request and one history entry")
        evidence("F06", "ActivityScenario CREATED then RESUMED during loading; real API after gate", "State preserved, loading completed; no process death simulated")
    }

    @Test fun f07DeniedNotifications() = scenario {
        assertNotEquals(PackageManager.PERMISSION_GRANTED, target.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        compose.onNodeWithText("Дозволити сповіщення").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Налаштування сповіщень").performScrollTo().assertIsDisplayed()
        openAI()
        compose.onNodeWithText("Опис симптому").assertExists()
        evidence("F07", "Actual denied notification permission supplied before the test; operator restores it afterwards", "Permission actions visible; AI tab accessible; no new permission granted by test")
    }

    @Test fun f08NoResultAndClarification() = scenario {
        openAI(); send("Монітор не працює.")
        finished()
        assertTrue("Actual: " + vm.uiState.value, vm.uiState.value is SymptomUiState.NoResult)
        assertTrue(vm.historyState.value.isEmpty())
        compose.onNodeWithText("Потрібно більше інформації").performScrollTo().assertIsDisplayed()
        save("04_no_result.png")
        compose.onNodeWithText("Ваше уточнення").performScrollTo()
            .performTextReplacement("Після роботи за комп'ютером трохи болить голова. Травми не було.")
        compose.onNodeWithText("Надіслати уточнення").performScrollTo().performClick()
        success(1)
        val result = (vm.uiState.value as SymptomUiState.Success).result
        assertEquals(true, result.contextUsed)
        assertEquals("неврологічне", result.category)
        assertEquals(2, api.realCalls)
        evidence("F08", "Two real FastAPI/Groq requests", "NoResult has question and no history; clarification gives one usable stored result")
    }

    private class ControlledApi(private val real: MediTrackApi) : MediTrackApi {
        @Volatile var failNext = false
        @Volatile var errorNext = false
        @Volatile var gate: CompletableDeferred<Unit>? = null
        @Volatile var calls = 0
        @Volatile var realCalls = 0
        @Volatile var lastRequestId: String? = null
        override suspend fun analyzeSymptom(request: SymptomRequest): Response<SymptomResponse> {
            calls++
            if (failNext) { failNext = false; throw IOException("Controlled synthetic failure") }
            if (errorNext) { errorNext = false; return Response.error(503, "{}".toResponseBody()) }
            gate?.await()
            realCalls++
            return real.analyzeSymptom(request).also { lastRequestId = it.headers()["X-Request-ID"] }
        }
    }
}
