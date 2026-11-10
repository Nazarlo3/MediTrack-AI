package com.example.meditrackai

import com.example.meditrackai.data.settings.SettingsRepository
import com.example.meditrackai.ui.SettingsViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SettingsViewModelTest {
    @Test fun healthCannotStartTwiceAndFailureCanBeRetried() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<String>()
        var calls = 0
        val repository = object : SettingsRepository {
            override suspend fun diagnostics(medications: Int, history: Int) = "$medications/$history"
            override suspend fun health(): String { calls++; return if (calls == 1) gate.await() else "ok" }
        }
        try {
            val vm = SettingsViewModel(repository, scope)
            vm.refresh(2, 3)
            assertEquals("2/3", vm.uiState.value.technical)
            vm.checkBackend(); vm.checkBackend()
            assertEquals(1, calls); assertTrue(vm.uiState.value.checking)
            gate.completeExceptionally(java.io.IOException("synthetic"))
            yield()
            assertFalse(vm.uiState.value.checking)
            assertTrue(vm.uiState.value.health.contains("недоступний"))
            vm.checkBackend()
            assertEquals(2, calls); assertEquals("ok", vm.uiState.value.health)
        } finally { scope.cancel() }
    }

    @Test fun cancelledStaleDiagnosticsCannotOverwriteNewerResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val old = CompletableDeferred<String>()
        val repository = object : SettingsRepository {
            override suspend fun diagnostics(medications: Int, history: Int) = if (medications == 1) old.await() else "new"
            override suspend fun health() = "ok"
        }
        try {
            val vm = SettingsViewModel(repository, scope)
            vm.refresh(1, 0); vm.refresh(2, 0)
            old.complete("old"); yield()
            assertEquals("new", vm.uiState.value.technical)
        } finally { scope.cancel() }
    }
}
