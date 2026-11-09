package com.example.meditrackai

import com.example.meditrackai.data.CollectionChoice
import com.example.meditrackai.data.CollectionPreferences
import com.example.meditrackai.data.symptoms.DefaultSymptomRepository
import com.example.meditrackai.data.symptoms.SymptomHistorySource
import com.example.meditrackai.domain.symptoms.SymptomInput
import com.example.meditrackai.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class CollectionPolicyTest {
    @Test fun defaultEnabledDoesNotMeanConsent() {
        assertTrue(CollectionChoice().enabled)
        assertFalse(CollectionChoice().mayCollect)
        assertFalse(CollectionChoice(false, true).mayCollect)
        assertTrue(CollectionChoice(true, true).mayCollect)
    }

    @Test fun noConsentUsesLegacyApiAndSendsNoCollectionMetadata() = runBlocking {
        val prefs = Preferences(null)
        val remote = Remote()
        var legacyCalls = 0
        val api = object : MediTrackApi {
            override suspend fun analyzeSymptom(request: SymptomRequest): Response<SymptomResponse> {
                legacyCalls++; assertNull(request.collection)
                return Response.success(dto)
            }
        }
        DefaultSymptomRepository(api, UnusedHistory(), prefs, remote).analyze(SymptomInput("synthetic"))
        assertEquals(1, legacyCalls); assertEquals(0, remote.calls)
    }

    @Test fun consentAndOptOutAreReadForEachNewRequestAndStorageFailureDoesNotLoseResult() = runBlocking {
        val prefs = Preferences("b".repeat(64))
        val remote = Remote()
        var legacyCalls = 0
        val api = object : MediTrackApi {
            override suspend fun analyzeSymptom(request: SymptomRequest): Response<SymptomResponse> {
                legacyCalls++; return Response.success(dto)
            }
        }
        val repository = DefaultSymptomRepository(api, UnusedHistory(), prefs, remote)
        assertEquals(dto.category, repository.analyze(SymptomInput("synthetic")).category)
        assertEquals("unavailable", prefs.lastStatus)
        assertNotNull(remote.requests.single().collection)
        repository.analyze(SymptomInput("another"))
        assertNotEquals(remote.requests[0].collection?.requestId, remote.requests[1].collection?.requestId)
        prefs.token = null
        repository.analyze(SymptomInput("declined"))
        assertEquals(2, remote.calls); assertEquals(1, legacyCalls)
    }

    private class Preferences(var token: String?) : CollectionPreferences {
        var lastStatus: String? = null
        override suspend fun tokenForCollection() = token
        override fun report(status: String?) { lastStatus = status }
    }
    private class Remote : CollectionApi {
        var calls = 0
        val requests = mutableListOf<SymptomRequest>()
        override suspend fun analyze(request: SymptomRequest, token: String): Response<SymptomResponse> {
            calls++; requests += request
            assertEquals(64, token.length)
            return Response.success(dto, Headers.headersOf("X-Collection-Status", "unavailable"))
        }
        override suspend fun deleteMine(token: String): Response<CollectionDeletion> = error("Not used")
    }
    private class UnusedHistory : SymptomHistorySource {
        override suspend fun load(): List<SymptomHistoryItem> = error("Not used")
        override suspend fun save(entry: SymptomHistoryItem): List<SymptomHistoryItem> = error("Not used")
        override suspend fun get(id: String): SymptomHistoryItem? = error("Not used")
        override suspend fun clear(): List<SymptomHistoryItem> = error("Not used")
        override suspend fun delete(id: String): List<SymptomHistoryItem> = error("Not used")
    }
    companion object { private val dto = SymptomResponse("інше", "synthetic") }
}
