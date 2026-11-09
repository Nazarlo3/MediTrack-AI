package com.example.meditrackai

import com.example.meditrackai.domain.symptoms.*
import com.example.meditrackai.ui.SymptomAnalysisController
import com.example.meditrackai.ui.SymptomUiState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SymptomAnalysisControllerTest {
    @Test fun loadingSuccessAndHistoryArePublished() = scenario { repository, controller, _ ->
        val gate = CompletableDeferred<SymptomAnalysis>()
        repository.respond = { gate.await() }
        assertEquals(SymptomUiState.Idle, controller.uiState.value)
        controller.checkSymptom("Синтетичний опис")
        assertEquals(SymptomUiState.Loading, controller.uiState.value)
        yield()
        gate.complete(repository.result)
        yield()
        assertEquals(repository.result, (controller.uiState.value as SymptomUiState.Success).result)
        assertEquals(1, controller.historyState.value.size)
        assertTrue(controller.canClarify.value)
    }

    @Test fun errorAndRetryKeepTheSameClarificationContext() = scenario { repository, controller, _ ->
        controller.checkSymptom("Початковий опис"); yield()
        repository.respond = { throw SymptomFailure(SymptomError.NETWORK) }
        controller.clarifySymptom("З учора"); yield()
        val failed = repository.requests.last()
        assertEquals(SymptomUiState.Error(SymptomError.NETWORK), controller.uiState.value)
        assertTrue((controller.uiState.value as SymptomUiState.Error).canRetry)
        repository.respond = { repository.result.copy(contextUsed = true) }
        controller.retryLastCheck(); yield()
        assertEquals(failed, repository.requests.last())
        assertTrue(controller.uiState.value is SymptomUiState.Success)
        assertEquals(2, repository.saves)
    }

    @Test fun invalidInputDoesNotCallRepositoryOrOfferRetry() = scenario { repository, controller, _ ->
        controller.checkSymptom("  "); yield()
        val state = controller.uiState.value as SymptomUiState.Error
        assertEquals(SymptomError.INVALID_INPUT, state.reason)
        assertFalse(state.canRetry)
        assertTrue(repository.requests.isEmpty())
    }

    @Test fun storageFailureKeepsSuccessfulAnalysisVisible() = scenario { repository, controller, errors ->
        repository.failStorage = true
        controller.checkSymptom("Синтетичний опис"); yield()
        assertTrue(controller.uiState.value is SymptomUiState.Success)
        assertTrue(controller.historyState.value.isEmpty())
        assertEquals("Не вдалося зберегти історію перевірок.", errors.last())
    }

    @Test fun openingAndDeletingHistoryUpdatesSelectionAndPreservesOtherItems() = scenario { repository, controller, _ ->
        controller.checkSymptom("Перший опис"); yield()
        controller.checkSymptom("Другий опис"); yield()
        val first = controller.historyState.value.first()
        controller.openHistoryItem(first.id); yield()
        assertEquals(first, controller.selectedHistory.value)
        controller.deleteHistoryItem(first.id); yield()
        assertNull(controller.selectedHistory.value)
        assertEquals(1, repository.history.size)
        controller.clearHistory(); yield()
        assertTrue(controller.historyState.value.isEmpty())
    }

    @Test fun cancellingFirstRequestDoesNotPublishLateSuccessOrSaveHistory() = scenario { repository, controller, _ ->
        val gate = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        repository.respond = {
            // Simulate a provider that returns even after its caller was cancelled.
            withContext(NonCancellable) { gate.await() }
            returned.complete(Unit)
            repository.result
        }
        controller.checkSymptom("Синтетичний опис"); yield()
        controller.cancelAnalysis()
        assertEquals(SymptomUiState.Idle, controller.uiState.value)
        gate.complete(Unit)
        returned.await(); yield()
        assertEquals(SymptomUiState.Idle, controller.uiState.value)
        assertEquals(0, repository.saves)
        assertTrue(controller.historyState.value.isEmpty())
    }

    @Test fun cancellingClarificationRestoresPreviousSuccessAndAllowsRetry() = scenario { repository, controller, _ ->
        controller.checkSymptom("Початковий опис"); yield()
        val previous = controller.uiState.value
        val gate = CompletableDeferred<SymptomAnalysis>()
        repository.respond = { gate.await() }
        controller.clarifySymptom("З учора"); yield()
        val cancelled = repository.requests.last()
        controller.cancelAnalysis()
        assertEquals(previous, controller.uiState.value)
        assertEquals(1, repository.saves)
        repository.respond = { repository.result.copy(contextUsed = true) }
        controller.retryLastCheck(); yield()
        assertEquals(cancelled, repository.requests.last())
        assertEquals(2, repository.saves)
    }

    @Test fun cancelledOldFailureCannotOverwriteNewRequest() = scenario { repository, controller, _ ->
        val gate = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        repository.respond = {
            withContext(NonCancellable) { gate.await() }
            returned.complete(Unit)
            throw SymptomFailure(SymptomError.NETWORK)
        }
        controller.checkSymptom("Старий опис"); yield()
        controller.cancelAnalysis()
        repository.respond = { repository.result }
        controller.checkSymptom("Новий опис"); yield()
        val current = controller.uiState.value
        gate.complete(Unit)
        returned.await(); yield()
        assertEquals(current, controller.uiState.value)
        assertEquals("Новий опис", controller.historyState.value.single().description)
        assertEquals(1, repository.saves)
    }

    @Test fun retryClarificationAndResetPreserveOnlyCurrentCheckDetails() = scenario { repository, controller, _ ->
        controller.checkSymptom("Початковий опис", 3, 0); yield()
        repository.respond = { throw SymptomFailure(SymptomError.NETWORK) }
        controller.clarifySymptom("Стало краще"); yield()
        val failed = repository.requests.last()
        assertEquals(3, failed.durationDays)
        assertEquals(0, failed.intensity)
        repository.respond = { repository.result }
        controller.retryLastCheck(); yield()
        assertEquals(failed, repository.requests.last())
        assertTrue(controller.historyState.value.first().description.contains("0/10"))
        controller.reset()
        controller.checkSymptom("Нова перевірка"); yield()
        assertNull(repository.requests.last().durationDays)
        assertNull(repository.requests.last().intensity)
    }

    @Test fun unavailableResultCanBeClarifiedWithoutSavingIncompleteHistory() = scenario { repository, controller, _ ->
        repository.result = repository.result.copy(hasUsableResult = false)
        controller.checkSymptom("Монітор не працює"); yield()
        assertTrue(controller.uiState.value is SymptomUiState.NoResult)
        assertEquals(0, repository.saves)
        assertTrue(controller.canClarify.value)
        val previous = controller.uiState.value
        val gate = CompletableDeferred<SymptomAnalysis>()
        repository.respond = { gate.await() }
        controller.clarifySymptom("Болить голова"); yield()
        controller.cancelAnalysis()
        assertEquals(previous, controller.uiState.value)
        repository.respond = { repository.result.copy(hasUsableResult = true, contextUsed = true) }
        controller.retryLastCheck(); yield()
        assertTrue(controller.uiState.value is SymptomUiState.Success)
        assertEquals(2, repository.requests.last().context.size)
        assertEquals(1, repository.saves)
    }

    @Test fun repeatedLaunchWhileLoadingMakesOneRequestAndOneHistoryEntry() = scenario { repository, controller, _ ->
        val gate = CompletableDeferred<SymptomAnalysis>()
        repository.respond = { gate.await() }
        controller.checkSymptom("Перший опис"); yield()
        controller.checkSymptom("Другий опис")
        controller.retryLastCheck()
        controller.clarifySymptom("Уточнення")
        assertEquals(1, repository.requests.size)
        gate.complete(repository.result); yield()
        assertEquals(1, repository.saves)
        assertEquals("Перший опис", controller.historyState.value.single().description)
    }

    private fun scenario(block: suspend (FakeSymptomRepository, SymptomAnalysisController, MutableList<String?>) -> Unit) = runBlocking {
        val repository = FakeSymptomRepository()
        val errors = mutableListOf<String?>()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val controller = SymptomAnalysisController(scope, repository, AnalyzeSymptomUseCase(repository)) { errors += it }
            yield()
            block(repository, controller, errors)
        } finally { scope.cancel() }
    }
}
