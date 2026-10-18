package com.example.meditrackai.data.symptoms

import com.example.meditrackai.domain.symptoms.*
import com.example.meditrackai.network.MediTrackApi
import kotlinx.coroutines.CancellationException
import java.io.IOException

class DefaultSymptomRepository(
    private val api: MediTrackApi,
    private val history: SymptomHistorySource
) : SymptomRepository {
    override suspend fun analyze(input: SymptomInput): SymptomAnalysis = try {
        val response = api.analyzeSymptom(input.toRequest())
        if (!response.isSuccessful) throw SymptomFailure(
            if (response.code() == 400 || response.code() == 422) SymptomError.INVALID_INPUT else SymptomError.SERVER
        )
        val body = response.body() ?: throw SymptomFailure(SymptomError.INVALID_RESPONSE)
        val result = body.toDomain()
        if (input.context.isNotEmpty() && result.contextUsed != true) throw SymptomFailure(SymptomError.CONTEXT_UNSUPPORTED)
        result
    } catch (e: CancellationException) { throw e }
    catch (e: SymptomFailure) { throw e }
    catch (_: IOException) { throw SymptomFailure(SymptomError.NETWORK) }
    catch (_: Exception) { throw SymptomFailure(SymptomError.UNEXPECTED) }

    override suspend fun loadHistory() = storage { history.load().map { it.toDomain() } }
    override suspend fun saveHistory(entry: SymptomHistoryEntry) = storage { history.save(entry.toStoredItem()).map { it.toDomain() } }
    override suspend fun getHistoryItem(id: String) = storage { history.get(id)?.toDomain() }
    override suspend fun clearHistory() = storage { history.clear().map { it.toDomain() } }
    override suspend fun deleteHistoryItem(id: String) = storage { history.delete(id).map { it.toDomain() } }

    private suspend fun <T> storage(action: suspend () -> T): T = try { action() }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { throw SymptomFailure(SymptomError.STORAGE) }
}
