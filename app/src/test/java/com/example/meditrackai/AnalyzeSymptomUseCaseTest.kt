package com.example.meditrackai

import com.example.meditrackai.domain.symptoms.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AnalyzeSymptomUseCaseTest {
    private val repository = FakeSymptomRepository()
    private val analyze = AnalyzeSymptomUseCase(repository) { 123456789L }

    @Test fun normalizesInputAndPreparesHistoryWithoutSaving() = runBlocking {
        val outcome = analyze(SymptomInput("  Синтетична втома  "))
        assertEquals("Синтетична втома", repository.requests.single().description)
        assertEquals("Синтетична втома", outcome.historyEntry.description)
        assertEquals(123456789L, outcome.historyEntry.createdAt)
        assertEquals(listOf("user", "assistant"), outcome.nextContext.map { it.role })
        assertEquals(0, repository.saves)
    }

    @Test fun rejectsShortLongAndBlankInputBeforeRepository() = runBlocking {
        listOf("", "  ", "аб", "а".repeat(501)).forEach { text ->
            assertEquals(SymptomError.INVALID_INPUT, failure { analyze(SymptomInput(text)) })
        }
        assertTrue(repository.requests.isEmpty())
    }

    @Test fun clarificationKeepsInitialDescriptionAndBuildsNextContext() = runBlocking {
        val first = analyze(SymptomInput("Синтетичний опис"))
        val second = analyze(SymptomInput("З учора", first.nextContext))
        assertEquals(first.nextContext, repository.requests.last().context)
        assertEquals("Початковий опис: Синтетичний опис\n\nУточнення 1: З учора", second.historyEntry.description)
        assertEquals(4, second.nextContext.size)
    }

    @Test fun rejectsInvalidOrExhaustedContextBeforeNetwork() = runBlocking {
        val pair = listOf(ConversationMessage("user", "Опис"), ConversationMessage("assistant", "Відповідь"))
        listOf(pair + pair + pair + pair, pair.reversed(), pair.take(1),
            listOf(pair[0], pair[1].copy(content = " "))).forEach { context ->
            assertEquals(SymptomError.CONTEXT_LIMIT, failure { analyze(SymptomInput("Уточнення", context)) })
        }
        assertTrue(repository.requests.isEmpty())
    }

    private suspend fun failure(block: suspend () -> Unit): SymptomError {
        try { block(); fail("Expected domain failure") } catch (e: SymptomFailure) { return e.reason }
        error("No failure")
    }
}
