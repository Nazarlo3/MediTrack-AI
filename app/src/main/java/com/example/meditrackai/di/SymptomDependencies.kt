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
    private val store = LocalDataStore(application)
    private val repository = DefaultSymptomRepository(ApiClient.api, SharedPreferencesSymptomHistorySource(store))
    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == SymptomViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return SymptomViewModel(application, store, repository, AnalyzeSymptomUseCase(repository)) as T
        }
    }
}
