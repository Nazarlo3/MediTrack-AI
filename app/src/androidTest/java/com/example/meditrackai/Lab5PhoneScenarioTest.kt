package com.example.meditrackai

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.data.symptoms.DefaultSymptomRepository
import com.example.meditrackai.data.symptoms.SharedPreferencesSymptomHistorySource
import com.example.meditrackai.data.symptoms.toDomain
import com.example.meditrackai.domain.symptoms.AnalyzeSymptomUseCase
import com.example.meditrackai.network.*
import com.example.meditrackai.ui.SymptomCheckScreen
import com.example.meditrackai.ui.SymptomUiState
import com.example.meditrackai.ui.SymptomViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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

/** Opt-in device evidence. Real API + isolated local data; no user's history is changed. */
class Lab5PhoneScenarioTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val prefix = "lab5_phone_" + UUID.randomUUID() + "_"
    private val preferenceNames = mutableSetOf<String>()
    private val application = object : Application() {
        init { attachBaseContext(target) }
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            synchronized(preferenceNames) { preferenceNames += prefix + name }
            return target.getSharedPreferences(prefix + name, mode)
        }
    }
    private val viewModels = ViewModelStore()
    private lateinit var api: ControlledApi
    private lateinit var vm: SymptomViewModel

    private fun scenario(label: String = "ЛР5", block: () -> Unit) {
        assumeTrue("Explicit opt-in required", InstrumentationRegistry.getArguments().getString("lab5Phone") == "true")
        val baseUrl = requireNotNull(InstrumentationRegistry.getArguments().getString("lab5BaseUrl"))
        require(baseUrl == "http://127.0.0.1:18765/") { "Only the local test backend is permitted" }
        val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
        api = ControlledApi(Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(MediTrackApi::class.java))
        val store = LocalDataStore(application)
        val repository = DefaultSymptomRepository(api, SharedPreferencesSymptomHistorySource(store))
        try {
            instrumentation.runOnMainSync {
                vm = SymptomViewModel(application, store, repository, AnalyzeSymptomUseCase(repository))
                viewModels.put("lab5", vm)
            }
            compose.setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        Text("$label • СИНТЕТИЧНИЙ ТЕСТ • локальний backend → Groq", style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.weight(1f)) { SymptomCheckScreen(viewModel = vm) }
                    }
                }
            }
            compose.waitUntil(10000) { !vm.historyLoading.value }
            compose.onNodeWithText("Аналіз AI").performClick()
            block()
        } finally {
            if (::vm.isInitialized) instrumentation.runOnMainSync { viewModels.clear() }
            synchronized(preferenceNames) { preferenceNames.forEach { target.deleteSharedPreferences(it) } }
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun send(description: String) {
        compose.onNodeWithText("Опис симптому").performScrollTo().performTextReplacement(description)
        compose.onNodeWithText("Перевірити").performScrollTo().performClick()
    }

    private fun waitForSuccess(expectedHistory: Int) {
        compose.waitUntil(60000) { vm.uiState.value !is SymptomUiState.Loading }
        assertTrue("Expected success; actual: " + vm.uiState.value, vm.uiState.value is SymptomUiState.Success)
        compose.waitUntil(10000) { vm.historyState.value.size == expectedHistory }
        assertNotNull("Updated backend must provide X-Request-ID", api.lastRequestId)
    }

    @Test fun liveAnalysisClarificationAndPersistentHistory() = scenario {
        send("Після прогулянки трохи втомився, інших скарг немає.")
        waitForSuccess(1)
        val initial = vm.uiState.value as SymptomUiState.Success
        compose.onAllNodesWithText(initial.result.explanation)[0].performScrollTo().assertIsDisplayed()
        save("01_mobile_success.png")
        compose.onNodeWithText("Ваше уточнення").performScrollTo()
            .performTextReplacement("Це почалося сьогодні, після відпочинку стає краще.")
        compose.onNodeWithText("Надіслати уточнення").performScrollTo().performClick()
        waitForSuccess(2)
        val clarified = vm.uiState.value as SymptomUiState.Success
        assertEquals(true, clarified.result.contextUsed)
        assertNotEquals(initial.analysisId, clarified.analysisId)
        val saved = runBlocking { LocalDataStore(application).load().first }
        assertEquals(2, saved.size)
        assertTrue(saved.first().description.contains("Уточнення 1:"))
        compose.onAllNodesWithText("Відкрити деталі")[0].performScrollTo().performClick()
        compose.onNodeWithText("Збережений результат").assertIsDisplayed()
        assertEquals(saved.first().id, vm.selectedHistory.value?.id)
        assertEquals(2, api.realCalls)
    }

    @Test fun controlledNetworkErrorAndRealRetry() = scenario {
        api.failNext = true
        val description = "Після звичайного навантаження відчуваю легку втому."
        send(description)
        compose.waitUntil(10000) { vm.uiState.value is SymptomUiState.Error }
        assertTrue(vm.historyState.value.isEmpty())
        compose.onNodeWithText("Спробувати ще раз").performScrollTo().assertIsDisplayed()
        save("02_mobile_error.png")
        compose.onNodeWithText("Спробувати ще раз").performClick()
        waitForSuccess(1)
        assertEquals(description, vm.historyState.value.single().description)
        compose.onNodeWithText("Опис симптому").performScrollTo().assertTextContains(description)
        assertEquals(1, api.realCalls)
    }

    @Test fun cancelInitialAndClarificationKeepsDraftsAndHistory() = scenario {
        api.gate = CompletableDeferred()
        val description = "Трохи втомився після довгої прогулянки."
        send(description)
        compose.onNodeWithText("Скасувати аналіз").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SymptomUiState.Idle, vm.uiState.value) }
        compose.onNodeWithText("Опис симптому").performScrollTo().assertTextContains(description)
        assertTrue(vm.historyState.value.isEmpty())
        assertEquals(0, api.realCalls)
        api.gate = null
        compose.onNodeWithText("Перевірити").performScrollTo().performClick()
        waitForSuccess(1)
        val previous = vm.uiState.value
        val draft = "Стан поліпшився після відпочинку."
        api.gate = CompletableDeferred()
        compose.onNodeWithText("Ваше уточнення").performScrollTo().performTextReplacement(draft)
        compose.onNodeWithText("Надіслати уточнення").performScrollTo().performClick()
        compose.onNodeWithText("Скасувати аналіз").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(previous, vm.uiState.value) }
        compose.onNodeWithText("Ваше уточнення").performScrollTo().assertTextContains(draft)
        save("03_mobile_cancelled_clarification.png")
        assertEquals(1, vm.historyState.value.size)
        assertEquals(1, runBlocking { LocalDataStore(application).load().first.size })
        assertEquals(1, api.realCalls)
    }

    /** Reuses the isolated local-backend harness; run this method alone for Lab6. */
    @Test fun lab6InjuryClarificationChangesCategoryWithoutRewritingHistory() = scenario("ЛР6") {
        send("розбив монітор ногою")
        waitForSuccess(1)
        val initial = vm.uiState.value as SymptomUiState.Success
        assertEquals("травми та ушкодження", initial.result.category)
        assertEquals("увага", initial.result.urgency)
        assertFalse(initial.result.followUpQuestion.isNullOrBlank())
        compose.onAllNodesWithText(initial.result.explanation)[0].performScrollTo().assertIsDisplayed()
        save("04_lab6_initial.png")
        compose.onNodeWithText("Ваше уточнення").performScrollTo().performTextReplacement(
            "Ні, взагалі нічого не болить, не травмувався. Питаю лише про ремонт."
        )
        compose.onNodeWithText("Надіслати уточнення").performScrollTo().performClick()
        waitForSuccess(2)
        val clarified = vm.uiState.value as SymptomUiState.Success
        assertTrue(clarified.result.contextUsed == true)
        assertEquals("інше", clarified.result.category)
        assertEquals("звичайне", clarified.result.urgency)
        compose.onAllNodesWithText(clarified.result.explanation)[0].performScrollTo().assertIsDisplayed()
        save("05_lab6_clarified.png")
        val history = runBlocking { LocalDataStore(application).load().first }
        assertEquals(2, history.size)
        assertEquals(initial.result, history.last().response.toDomain())
        assertEquals(clarified.result, history.first().response.toDomain())
        assertTrue(history.first().description.contains("Уточнення 1:"))
        assertEquals(2, api.realCalls)
    }

    @Test fun lab6DurationAndIntensityReachAIAndHistory() = scenario("ЛР6 • тривалість та інтенсивність") {
        compose.onNodeWithText("Тривалість, днів").performScrollTo().performTextReplacement("31")
        compose.onNodeWithText("Перевірити").assertIsNotEnabled()
        compose.onNodeWithText("Тривалість, днів").performScrollTo().performTextReplacement("3")
        compose.onNodeWithText("Інтенсивність").performScrollTo().performTextReplacement("5")
        send("Вдарив гомілку об край ліжка, болить у місці удару. Рани немає.")
        waitForSuccess(1)
        val initial = vm.uiState.value as SymptomUiState.Success
        assertEquals("травми та ушкодження", initial.result.category)
        assertEquals(true, initial.result.detailsUsed)
        assertTrue(initial.result.explanation.contains("3 дн."))
        assertTrue(initial.result.explanation.contains("5/10"))
        compose.onAllNodesWithText(initial.result.explanation)[0].performScrollTo().assertIsDisplayed()
        save("06_lab6_details.png")
        compose.onNodeWithText("Ваше уточнення").performScrollTo().performTextReplacement("Після відпочинку стало легше.")
        compose.onNodeWithText("Надіслати уточнення").performScrollTo().performClick()
        waitForSuccess(2)
        val clarified = vm.uiState.value as SymptomUiState.Success
        assertEquals(true, clarified.result.contextUsed)
        assertEquals(true, clarified.result.detailsUsed)
        val history = runBlocking { LocalDataStore(application).load().first }
        assertEquals(2, history.size)
        assertEquals(initial.result, history.last().response.toDomain())
        assertTrue(history.first().description.contains("Тривалість: 3 дн.; Інтенсивність: 5/10"))
        compose.onAllNodesWithText("Відкрити деталі")[0].performScrollTo().performClick()
        compose.onNodeWithText("Збережений результат").assertIsDisplayed()
        save("07_lab6_details_history.png", historyDialog = true)
        assertEquals(2, api.realCalls)
    }

    private fun save(name: String, historyDialog: Boolean = false) {
        compose.waitForIdle()
        val root = if (historyDialog) compose.onNode(isRoot() and hasAnyDescendant(hasText("Збережений результат")))
            else compose.onRoot()
        val image = root.captureToImage().asAndroidBitmap()
        val directory = File(target.cacheDir, "lab5-phone")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private class ControlledApi(private val real: MediTrackApi) : MediTrackApi {
        @Volatile var failNext = false
        @Volatile var gate: CompletableDeferred<Unit>? = null
        @Volatile var realCalls = 0
        @Volatile var lastRequestId: String? = null
        override suspend fun analyzeSymptom(request: SymptomRequest): Response<SymptomResponse> {
            if (failNext) { failNext = false; throw IOException("Controlled lab5 network failure") }
            gate?.await()
            realCalls++
            return real.analyzeSymptom(request).also { lastRequestId = it.headers()["X-Request-ID"] }
        }
    }
}
