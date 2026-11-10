package com.example.meditrackai

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.meditrackai.data.DataCollectionSettings
import com.example.meditrackai.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CollectionSettingsTest {
    private val base: Context = ApplicationProvider.getApplicationContext()
    private val prefix = "collection_test_${UUID.randomUUID()}_"
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
    }
    @After fun cleanup() { base.deleteSharedPreferences(prefix + "meditrack_collection") }

    @Test fun explicitChoicePersistsAndCapabilityIsStableUntilDeletion() = runBlocking {
        val settings = DataCollectionSettings(context)
        assertTrue(settings.choice.value.enabled)
        assertTrue(settings.choice.value.mayCollect)
        assertNotNull(settings.tokenForCollection())
        assertTrue(settings.choose(true))
        val token = settings.tokenForCollection()
        assertTrue(token!!.matches(Regex("[a-f0-9]{64}")))
        assertEquals(token, DataCollectionSettings(context).tokenForCollection())
        assertTrue(settings.choose(false))
        assertNull(DataCollectionSettings(context).tokenForCollection())
        val api = FakeApi()
        settings.deleteRemote(api)
        assertEquals(token, api.receivedToken)
        assertFalse(settings.choice.value.enabled)
        settings.choose(true)
        assertNotEquals(token, settings.tokenForCollection())
    }

    @Test fun failedDeletionRetainsCapabilityForRetryAndKeepsCollectionOff() = runBlocking {
        val settings = DataCollectionSettings(context)
        settings.choose(true)
        val token = settings.tokenForCollection()
        val api = FakeApi().apply { fail = true }
        settings.deleteRemote(api)
        assertFalse(settings.choice.value.enabled)
        assertTrue(settings.status.value.contains("не підтверджено"))
        api.fail = false
        settings.deleteRemote(api)
        assertEquals(token, api.receivedToken)
        assertTrue(settings.status.value.contains("видалено"))
    }

    @Test fun deletingBeforeAnyUploadDoesNotCallServer() = runBlocking {
        val settings = DataCollectionSettings(context)
        val api = FakeApi()
        settings.deleteRemote(api)
        assertEquals(0, api.calls)
        assertFalse(settings.choice.value.enabled)
    }

    @Test fun recreatedSettingsDisableRetainedRepositoryImmediately() = runBlocking {
        val retained = DataCollectionSettings(context)
        val token = retained.tokenForCollection()
        val recreated = DataCollectionSettings(context)
        assertTrue(recreated.choose(false))
        assertNull(retained.tokenForCollection())
        assertTrue(recreated.choose(true))
        assertEquals(token, retained.tokenForCollection())
        assertTrue(retained.choose(false))
        assertNull(recreated.tokenForCollection())
    }

    private class FakeApi : CollectionApi {
        var fail = false
        var calls = 0
        var receivedToken: String? = null
        override suspend fun analyze(request: SymptomRequest, token: String): Response<SymptomResponse> = error("Unused")
        override suspend fun deleteMine(token: String): Response<CollectionDeletion> {
            calls++; receivedToken = token
            return if (fail) Response.error(503, "{}".toResponseBody()) else Response.success(CollectionDeletion(1))
        }
    }
}
