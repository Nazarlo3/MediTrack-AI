package com.example.meditrackai.domain.medications

import com.example.meditrackai.network.MedicationItem

interface MedicationRepository {
    suspend fun load(): List<MedicationItem>
    suspend fun update(change: (List<MedicationItem>) -> List<MedicationItem>): List<MedicationItem>?
    suspend fun synchronize(): String?
    suspend fun deletePhoto(path: String?)
}
