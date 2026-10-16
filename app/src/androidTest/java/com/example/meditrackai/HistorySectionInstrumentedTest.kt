package com.example.meditrackai

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.meditrackai.network.SymptomHistoryItem
import com.example.meditrackai.network.SymptomResponse
import com.example.meditrackai.ui.HistorySection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HistorySectionInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val item = SymptomHistoryItem("12:00", "Синтетичний опис", SymptomResponse("інше", "Збережене пояснення"))

    @Test fun loadingIsDifferentFromEmptyHistory() {
        compose.setContent { MaterialTheme {
            HistorySection(emptyList(), true, false, null, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Завантажуємо історію…").assertIsDisplayed()
        compose.onNodeWithText("Історія порожня.", substring = true).assertDoesNotExist()
    }

    @Test fun unavailableDataIsNotPresentedAsEmptyHistory() {
        compose.setContent { MaterialTheme {
            HistorySection(emptyList(), false, true, null, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Історія недоступна", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Історія порожня.", substring = true).assertDoesNotExist()
    }

    @Test fun clearingNeedsConfirmationAndCanBeCancelled() {
        var cleared = 0
        compose.setContent { MaterialTheme {
            HistorySection(listOf(item), false, false, null, {}, {}, { cleared++ }, {})
        } }
        compose.onNodeWithText("Очистити").performClick()
        compose.runOnIdle { assertEquals(0, cleared) }
        compose.onNodeWithText("Скасувати").performClick()
        compose.runOnIdle { assertEquals(0, cleared) }
        compose.onNodeWithText("Очистити").performClick()
        compose.onNodeWithText("Видалити всі записи").performClick()
        compose.runOnIdle { assertEquals(1, cleared) }
    }

    @Test fun openingRecordUsesItsIdAndShowsUnknownLegacyDate() {
        var opened: String? = null
        compose.setContent { MaterialTheme {
            HistorySection(listOf(item), false, false, null, { opened = it }, {}, {}, {})
        } }
        compose.onNodeWithText("Дата невідома • 12:00").assertIsDisplayed()
        compose.onNodeWithText(item.description).performClick()
        compose.runOnIdle { assertEquals(item.id, opened) }
    }

    @Test fun detailsShowStoredInputResultAndHistoricalLabel() {
        compose.setContent { MaterialTheme {
            HistorySection(emptyList(), false, false, item, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Збережений результат").assertIsDisplayed()
        compose.onNodeWithText("Історичний результат перевірки").assertIsDisplayed()
        compose.onNodeWithText(item.description).assertIsDisplayed()
        compose.onNodeWithText(item.response.explanation).assertIsDisplayed()
        compose.onNodeWithText(item.response.disclaimer).assertExists()
    }

    @Test fun singleDeletionCanBeCancelledAndTargetsOnlyChosenId() {
        val other = item.copy(id = "another-record")
        var deleted: String? = null
        compose.setContent { MaterialTheme {
            HistorySection(listOf(item, other), false, false, null, {}, {}, {}, { deleted = it })
        } }
        compose.onAllNodesWithText("Видалити")[1].performClick()
        compose.onNodeWithText("Видалити цей запис?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, deleted) }
        compose.onNodeWithText("Скасувати").performClick()
        compose.runOnIdle { assertEquals(null, deleted) }
        compose.onAllNodesWithText("Видалити")[1].performClick()
        compose.onNodeWithText("Так, видалити").performClick()
        compose.runOnIdle { assertEquals(other.id, deleted) }
    }

    @Test fun deletingFromDetailsNeedsConfirmation() {
        var deleted: String? = null
        compose.setContent { MaterialTheme {
            HistorySection(listOf(item), false, false, item, {}, {}, {}, { deleted = it })
        } }
        compose.onNodeWithText("Видалити запис").performClick()
        compose.onNodeWithText("Видалити цей запис?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, deleted) }
        compose.onNodeWithText("Так, видалити").performClick()
        compose.runOnIdle { assertEquals(item.id, deleted) }
    }
}
