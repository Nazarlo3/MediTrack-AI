package com.example.meditrackai

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.data.medications.DefaultMedicationRepository
import com.example.meditrackai.data.symptoms.*
import com.example.meditrackai.domain.symptoms.AnalyzeSymptomUseCase
import com.example.meditrackai.network.*
import com.example.meditrackai.ui.SymptomViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class MedicationViewModelInstrumentedTest {
    @Test fun medicationEditsTakenStateAndDeletionUseInjectedEffectsAndPersist() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val prefix = "vm_medication_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        val store = LocalDataStore(context)
        val deletedPhotos = mutableListOf<String?>()
        var synchronized = 0
        val medications = DefaultMedicationRepository(store, { synchronized++; null }, { deletedPhotos += it })
        val api = object : MediTrackApi {
            override suspend fun analyzeSymptom(request: SymptomRequest): retrofit2.Response<SymptomResponse> = error("No network permitted")
        }
        val symptoms = DefaultSymptomRepository(api, SharedPreferencesSymptomHistorySource(store))
        val models = ViewModelStore()
        lateinit var vm: SymptomViewModel
        try {
            instrumentation.runOnMainSync {
                vm = SymptomViewModel(medications, symptoms, AnalyzeSymptomUseCase(symptoms))
                models.put("medications", vm)
            }
            assertTrue(vm.addMedication("Synthetic", "test", "old-photo", listOf("Щодня"), "08:00", "31.12.2030"))
            val item = vm.medicationsState.value.single()
            instrumentation.runOnMainSync { vm.toggleMedicationStatus(item.id, "08:00") }
            withTimeout(5000) { vm.medicationsState.first { it.single().isDoseTakenOn("08:00", LocalDate.now().toString()) } }
            assertTrue(vm.editMedication(item.copy(name = "Synthetic edited", photoUri = "new-photo")))
            val saved = LocalDataStore(context).load().second.single()
            assertEquals("Synthetic edited", saved.name)
            assertTrue(saved.isDoseTakenOn("08:00", LocalDate.now().toString()))
            assertTrue(deletedPhotos.contains("old-photo"))
            instrumentation.runOnMainSync { vm.deleteMedication(item.id) }
            withTimeout(5000) { vm.medicationsState.first { it.isEmpty() } }
            assertTrue(LocalDataStore(context).load().second.isEmpty())
            // Wait for the rest of the launched delete action, not just its StateFlow emission.
            withTimeout(5000) { while (!deletedPhotos.contains("new-photo")) delay(10) }
            assertTrue(synchronized >= 4)
        } finally {
            instrumentation.runOnMainSync { models.clear() }
            listOf("meditrack_prefs", "meditrack_history_prefs").forEach { base.deleteSharedPreferences(prefix + it) }
        }
    }
}
