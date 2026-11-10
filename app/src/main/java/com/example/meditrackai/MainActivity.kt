package com.example.meditrackai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.example.meditrackai.data.ThemeMode
import com.example.meditrackai.data.ThemeSettings
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import com.example.meditrackai.ui.SymptomCheckScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meditrackai.ui.SymptomViewModel
import com.example.meditrackai.ui.theme.MediTrackAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dependencies = (application as MediTrackApplication).dependencies
            val symptomViewModel: SymptomViewModel = viewModel(factory = dependencies.viewModelFactory)
            val settings = remember { ThemeSettings(applicationContext) }
            val mode by settings.mode.collectAsState()
            val scope = rememberCoroutineScope()
            val snackbar = remember { SnackbarHostState() }
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            MediTrackAITheme(darkTheme = dark) {
                Scaffold(modifier = Modifier.fillMaxSize(), snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        SymptomCheckScreen(viewModel = symptomViewModel, themeMode = mode,
                            collectionSettings = dependencies.collectionSettings, onThemeChange = { selected ->
                            scope.launch {
                                if (!settings.save(selected)) snackbar.showSnackbar("Не вдалося зберегти тему.")
                            }
                        })
                    }
                }
            }
        }
    }
}
