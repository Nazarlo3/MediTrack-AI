package com.example.meditrackai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.ui.AddMedicationCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MedicationFormInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun editFormPreservesPhotoAndSavesSeveralTimesAtDialogWidth() {
        val item = MedicationItem(name = "Синтетичний препарат", dosage = "2 таб.", photoUri = "/test-not-real.jpg",
            mealSlot = "08:00 (Перед їжею)", daysOfWeek = listOf("Пн", "Ср"), endDate = "31.12.2099",
            times = listOf("08:00", "20:00"), takenDoseDates = emptyMap())
        var values: List<String?>? = null
        compose.setContent { MaterialTheme { Column(Modifier.width(260.dp).verticalScroll(rememberScrollState())) {
            AddMedicationCard(editingItem = item, onAddMedication = { name, dose, photo, days, slot, end ->
                values = listOf(name, dose, photo, days.joinToString(","), slot, end)
                false
            })
        } } }
        compose.onNodeWithText(item.name).assertExists()
        compose.onNodeWithText("08:00, 20:00").assertExists()
        compose.onNodeWithText("Зберегти").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5000) { values != null }
        assertEquals(listOf(item.name, item.dosage, item.photoUri, "Пн,Ср", "08:00, 20:00 (Перед їжею)", item.endDate), values)
        compose.onNodeWithText("Не вдалося зберегти препарат. Спробуйте ще раз.").assertExists()
    }
}
