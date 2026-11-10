package com.example.meditrackai.di

import android.content.Context
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.data.medications.DefaultMedicationRepository
import com.example.meditrackai.reminders.MedicationReminderScheduler
import com.example.meditrackai.ui.PhotoStorage

fun createMedicationRepository(context: Context, store: LocalDataStore): DefaultMedicationRepository {
    val app = context.applicationContext
    val scheduler = MedicationReminderScheduler(app)
    return DefaultMedicationRepository(store, { scheduler.synchronize() },
        { path -> PhotoStorage.deletePhotoFromInternalStorage(app, path) })
}
