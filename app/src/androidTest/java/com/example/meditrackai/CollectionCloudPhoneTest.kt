package com.example.meditrackai

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meditrackai.data.*
import com.example.meditrackai.data.symptoms.*
import com.example.meditrackai.domain.symptoms.AnalyzeSymptomUseCase
import com.example.meditrackai.network.*
import com.example.meditrackai.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Opt-in cloud integration, separate preferences, synthetic data only. */
class CollectionCloudPhoneTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun settingsSwitchControlsRealCloudCollection() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("collectionCloud") == "true")
        require(BuildConfig.BACKEND_URL == "https://meditrack-ai-xbk5.onrender.com/")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val target=instrumentation.targetContext
        val names=mutableSetOf<String>(); val prefix="cloud_phone_${UUID.randomUUID()}_"
        val app=object: Application() {
            init { attachBaseContext(target) }
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                names += prefix+name
                return target.getSharedPreferences(prefix+name,mode)
            }
        }
        val settings=DataCollectionSettings(app)
        val store=LocalDataStore(app)
        val models=ViewModelStore()
        var token: String?=null; var id: String?=null; var collectionCalls=0; var legacyCalls=0
        val unique=UUID.randomUUID().toString().take(8)
        var passed=false
        val collected=object: CollectionApi {
            override suspend fun analyze(request: SymptomRequest, token: String): retrofit2.Response<SymptomResponse> {
                collectionCalls++
                id=request.collection?.requestId
                return ApiClient.collectionApi.analyze(request,token).also {
                    assertTrue(it.headers()["X-Collection-Status"] in setOf("saved", "queued"))
                }
            }
            override suspend fun deleteMine(token: String) = ApiClient.collectionApi.deleteMine(token)
        }
        val legacy=object: MediTrackApi {
            override suspend fun analyzeSymptom(request: SymptomRequest): retrofit2.Response<SymptomResponse> {
                legacyCalls++; assertNull(request.collection)
                return ApiClient.api.analyzeSymptom(request)
            }
        }
        val repository=DefaultSymptomRepository(legacy,SharedPreferencesSymptomHistorySource(store),settings,collected)
        lateinit var vm: SymptomViewModel
        try {
            instrumentation.runOnMainSync {
                vm=SymptomViewModel(app,store,repository,AnalyzeSymptomUseCase(repository)); models.put("cloud",vm)
            }
            token=runBlocking { settings.tokenForCollection() }
            compose.setContent { MaterialTheme { SymptomCheckScreen(vm,collectionSettings=settings) } }
            compose.waitUntil(10000) { !vm.historyLoading.value }
            compose.onNodeWithText("Допомогти покращувати AI").assertDoesNotExist()
            compose.onNodeWithText("Аналіз AI").performClick()
            fun send(text: String) {
                compose.onNodeWithText("Опис симптому").performScrollTo().performTextReplacement(text)
                compose.onNodeWithText("Перевірити").performScrollTo().performClick()
                compose.waitUntil(90000) { vm.uiState.value !is SymptomUiState.Loading }
                assertTrue("Cloud result must be success",vm.uiState.value is SymptomUiState.Success)
            }
            send("Синтетичний тест $unique ON: легкий головний біль після роботи за комп’ютером.")
            compose.onNodeWithContentDescription("Налаштування").performScrollTo().performClick()
            compose.onNode(isToggleable()).performScrollTo().performClick()
            compose.waitUntil(10000) { !settings.choice.value.enabled }
            compose.onNodeWithText("Докладніше про покращення AI").performScrollTo().performClick()
            compose.onNodeWithText("Для покращення AI та статистики",substring=true).assertExists()
            compose.onNodeWithText("Видалити мої серверні записи").assertDoesNotExist()
            compose.onNodeWithText("Готово").performClick()
            compose.onNodeWithText("Нова перевірка").performScrollTo().performClick()
            send("Синтетичний тест $unique OFF: легка втома після прогулянки, інших скарг немає.")
            assertEquals(1,collectionCalls); assertEquals(1,legacyCalls)
            passed=true
        } finally {
            val folder=File(target.cacheDir,"cloud-phone").apply { mkdirs() }
            File(folder,"private.json").writeText(org.json.JSONObject().put("token",token).put("request_id",id)
                .put("off_marker",unique+" OFF").put("passed",passed).toString())
            instrumentation.runOnMainSync { models.clear() }
            names.forEach { target.deleteSharedPreferences(it) }
        }
    }
}
