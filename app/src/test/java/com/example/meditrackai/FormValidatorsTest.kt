package com.example.meditrackai

import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.validation.FormValidators
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FormValidatorsTest {
    @Test fun symptomLengthMatchesBackend() {
        assertNotNull(FormValidators.symptomError("  ab  "))
        assertNull(FormValidators.symptomError(" abc "))
        assertNull(FormValidators.symptomError("x".repeat(500)))
        assertNotNull(FormValidators.symptomError("x".repeat(501)))
    }

    @Test fun medicationRejectsImpossibleDatesAndTimes() {
        val today = LocalDate.of(2026, 10, 12)
        assertNotNull(FormValidators.medicationError("Ліки", "1 таб.", "25:00", "Щодня", "13.10.2026", today))
        assertNotNull(FormValidators.medicationError("Ліки", "1 таб.", "08:00", "Щодня", "31.02.2027", today))
        assertNotNull(FormValidators.medicationError("Ліки", "1 таб.", "08:00", "Щодня", "11.10.2026", today))
        assertNull(FormValidators.medicationError("Ліки", "1 таб.", "08:00", "Пн, Ср, Пт", "13.10.2026", today))
    }

    @Test fun oldMedicationDoesNotBecomeTakenToday() {
        val oldJson = """{"id":"old-id","name":"Ліки","dosage":"1 таб.","daysOfWeek":["Щодня"],"mealSlot":"08:00 (Після їжі)","endDate":"19.10.2026","isTaken":true}"""
        val medication = Gson().fromJson(oldJson, MedicationItem::class.java)
        assertEquals("old-id", medication.id)
        assertFalse(medication.isTakenOn("2026-10-12"))
        val takenToday = medication.copy(isTaken = true, lastTakenDate = "2026-10-12")
        assertEquals(true, takenToday.isTakenOn("2026-10-12"))
        assertFalse(takenToday.isTakenOn("2026-10-13"))
    }
}
