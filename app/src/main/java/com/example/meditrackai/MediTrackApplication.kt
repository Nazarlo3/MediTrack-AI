package com.example.meditrackai

import android.app.Application
import com.example.meditrackai.di.SymptomDependencies

/** One dependency graph for UI and retained ViewModels across Activity recreation. */
class MediTrackApplication : Application() {
    val dependencies by lazy { SymptomDependencies(this) }
}
