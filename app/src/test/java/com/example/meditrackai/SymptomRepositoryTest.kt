package com.example.meditrackai

import com.example.meditrackai.data.symptoms.*
import com.example.meditrackai.domain.symptoms.*
import com.example.meditrackai.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class SymptomRepositoryTest {
    private var request: SymptomRequest? = null
    private val dto = SymptomResponse("інше", "Синтетичне пояснення")
    private var response: suspend () -> Response<SymptomResponse> = { Response.success(dto) }
    private val source = MemoryHistorySource()
    private val repository = DefaultSymptomRepository(object : MediTrackApi {
        override suspend fun analyzeSymptom(request: SymptomRequest): Response<SymptomResponse> {
            this@SymptomRepositoryTest.request = request
            return response()
        }
    }, source)

    @Test fun mapsContextRequestAndAllResponseFields() = runBlocking {
        response = { Response.success(dto.copy(urgency = "увага", followUpQuestion = "Коли?", contextUsed = true)) }
        val context = listOf(ConversationMessage("user", "Початковий опис"), ConversationMessage("assistant", "Пояснення"))
        val result = repository.analyze(SymptomInput("Уточнення", context))
        assertEquals(listOf(ConversationTurn("user", "Початковий опис"), ConversationTurn("assistant", "Пояснення")), request?.context)
        assertEquals("увага", result.urgency)
        assertEquals("Коли?", result.followUpQuestion)
        assertEquals(true, result.contextUsed)
        assertEquals(dto.disclaimer, result.disclaimer)
    }

    @Test fun classifiesHttpAndNetworkErrors() = runBlocking {
        listOf(400 to SymptomError.INVALID_INPUT, 422 to SymptomError.INVALID_INPUT, 500 to SymptomError.SERVER).forEach { (code, expected) ->
            response = { Response.error(code, "{}".toResponseBody()) }
            assertEquals(expected, failure { repository.analyze(SymptomInput("Опис")) })
        }
        response = { throw IOException("synthetic") }
        assertEquals(SymptomError.NETWORK, failure { repository.analyze(SymptomInput("Опис")) })
    }

    @Test fun detailsNeedServerAcknowledgementAndSurviveJsonAndHistoryMapping() = runBlocking {
        val input = SymptomInput("Синтетичний опис", durationDays = 3, intensity = 0)
        assertEquals(SymptomError.DETAILS_UNSUPPORTED, failure { repository.analyze(input) })
        response = { Response.success(dto.copy(detailsUsed = true)) }
        val result = repository.analyze(input)
        assertEquals(3, request?.durationDays)
        assertEquals(0, request?.intensity)
        val json = com.google.gson.Gson().toJson(request)
        assertTrue(json.contains("\"duration_days\":3"))
        assertTrue(json.contains("\"intensity\":0"))
        assertEquals(true, result.detailsUsed)
        val entry = SymptomHistoryEntry("12:00", "Тривалість: 3 дн.; Інтенсивність: 0/10", result)
        val codec = com.example.meditrackai.data.HistoryJsonCodec()
        assertEquals(entry, codec.decode(codec.encode(listOf(entry.toStoredItem()))).single().toDomain())
        assertFalse(com.google.gson.Gson().toJson(SymptomInput("Старий опис").toRequest()).contains("duration_days"))
    }

    @Test fun rejectsIncompleteResponseAndUnsupportedContext() = runBlocking {
        response = { Response.success(dto.copy(urgency = null)) }
        assertEquals(SymptomError.INVALID_RESPONSE, failure { repository.analyze(SymptomInput("Опис")) })
        response = { Response.success(dto) }
        assertEquals(SymptomError.CONTEXT_UNSUPPORTED, failure {
            repository.analyze(SymptomInput("Уточнення", listOf(ConversationMessage("user", "Опис"))))
        })
    }

    @Test fun cancellationIsNotConvertedIntoNetworkError() = runBlocking {
        response = { throw CancellationException("synthetic") }
        try { repository.analyze(SymptomInput("Опис")); fail("Expected cancellation") }
        catch (_: CancellationException) { /* expected */ }
    }

    @Test fun historyRoundTripPreservesLegacyMetadataAndSupportsSingleDelete() = runBlocking {
        val old = SymptomHistoryItem("12:00", "Збережений опис", dto, "stable-id", null, "готово")
        source.items = listOf(old)
        val domain = repository.loadHistory().single()
        assertEquals(old, domain.toStoredItem())
        val next = domain.copy(id = "new-id", createdAt = 1234L)
        repository.saveHistory(next)
        assertEquals(next, repository.getHistoryItem("new-id"))
        assertEquals(listOf(domain), repository.deleteHistoryItem("new-id"))
        assertEquals(emptyList<SymptomHistoryEntry>(), repository.clearHistory())
    }

    @Test fun storageFailureHasItsOwnErrorCategory() = runBlocking {
        source.fail = true
        assertEquals(SymptomError.STORAGE, failure { repository.loadHistory() })
    }

    private suspend fun failure(block: suspend () -> Unit): SymptomError {
        try { block(); fail("Expected domain failure") } catch (e: SymptomFailure) { return e.reason }
        error("No failure")
    }

    private class MemoryHistorySource : SymptomHistorySource {
        var items = emptyList<SymptomHistoryItem>()
        var fail = false
        override suspend fun load(): List<SymptomHistoryItem> { if (fail) error("synthetic"); return items }
        override suspend fun save(entry: SymptomHistoryItem): List<SymptomHistoryItem> { items = listOf(entry) + items; return items }
        override suspend fun get(id: String) = items.find { it.id == id }
        override suspend fun clear(): List<SymptomHistoryItem> { items = emptyList(); return items }
        override suspend fun delete(id: String): List<SymptomHistoryItem> { items = items.filterNot { it.id == id }; return items }
    }
}
