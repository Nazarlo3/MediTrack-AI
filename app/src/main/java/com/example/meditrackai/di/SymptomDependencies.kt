package com.example.meditrackai.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.data.symptoms.DefaultSymptomRepository
import com.example.meditrackai.data.symptoms.SharedPreferencesSymptomHistorySource
import com.example.meditrackai.domain.symptoms.AnalyzeSymptomUseCase
import com.example.meditrackai.network.ApiClient
import com.example.meditrackai.ui.SymptomViewModel

/** Manual composition root: real dependencies are not created inside the ViewModel. */
class SymptomDependencies(application: Application) {
    val collectionSettings = com.example.meditrackai.data.DataCollectionSettings(application)
    private val store = LocalDataStore(application)
    private val repository = DefaultSymptomRepository(ApiClient.api, SharedPreferencesSymptomHistorySource(store),
        collectionSettings, ApiClient.collectionApi)
    val settingsViewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == com.example.meditrackai.ui.SettingsViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return com.example.meditrackai.ui.SettingsViewModel(
                com.example.meditrackai.data.settings.AndroidSettingsRepository(application, ApiClient.diagnosticsApi)) as T
        }
    }
    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == SymptomViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return SymptomViewModel(createMedicationRepository(application, store), repository, AnalyzeSymptomUseCase(repository)) as T
        }
    }
}
