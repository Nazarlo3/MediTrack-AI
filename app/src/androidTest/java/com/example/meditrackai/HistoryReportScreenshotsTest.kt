package com.example.meditrackai

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.network.SymptomHistoryItem
import com.example.meditrackai.network.SymptomResponse
import com.example.meditrackai.ui.HistorySection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Evidence uses the real UI and real storage, with isolated synthetic data only. */
class HistoryReportScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureDeletionAndEmptyHistory() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "report_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) =
                target.getSharedPreferences(prefix + name, mode)
        }
        val first = SymptomHistoryItem("10:00", "Синтетичний приклад: легка втома",
            SymptomResponse("загальне нездужання", "Приклад збереженої відповіді для перевірки історії."),
            createdAt = 1792220400000)
        val second = SymptomHistoryItem("10:05", "Синтетичний приклад: дискомфорт у зубі",
            SymptomResponse("стоматологічне", "Приклад іншого результату для перевірки видалення.", "увага"),
            createdAt = 1792220700000)
        val store = LocalDataStore(context)
        store.load()
        val history = mutableStateOf(requireNotNull(store.updateHistory { listOf(second, first) }))
        try {
            compose.setContent {
                MaterialTheme {
                    var selected by remember { mutableStateOf<SymptomHistoryItem?>(null) }
                    val scope = rememberCoroutineScope()
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text("MediTrack AI — ЛР4", style = MaterialTheme.typography.headlineSmall)
                        Text("СИНТЕТИЧНІ ТЕСТОВІ ДАНІ", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(12.dp))
                        HistorySection(history.value, false, false, selected,
                            onOpen = { id -> selected = history.value.find { it.id == id } },
                            onDismissDetails = { selected = null },
                            onClear = { scope.launch {
                                history.value = requireNotNull(store.updateHistory { emptyList() })
                                selected = null
                            } },
                            onDelete = { id -> scope.launch {
                                history.value = requireNotNull(store.deleteHistoryItem(id))
                                selected = null
                            } })
                    }
                }
            }
            save("03_test_history.png")
            compose.onAllNodesWithText("Відкрити деталі")[0].performScrollTo().performClick()
            compose.onNodeWithText("Збережений результат").assertIsDisplayed()
            save("04_test_details.png", dialog = true)
            compose.onNodeWithText("Видалити запис").performClick()
            compose.onNodeWithText("Видалити цей запис?").assertIsDisplayed()
            save("05_test_delete_confirmation.png", dialog = true)
            compose.onNodeWithText("Скасувати").performClick()
            compose.runOnIdle { assertEquals(2, history.value.size) }
            compose.onNodeWithText("Видалити запис").performClick()
            compose.onNodeWithText("Так, видалити").performClick()
            compose.waitUntil(5000) { history.value.size == 1 }
            assertEquals(listOf(first), LocalDataStore(context).load().first)
            save("06_test_after_single_delete.png")
            compose.onNodeWithText("Очистити").performScrollTo().performClick()
            save("07_test_clear_confirmation.png", dialog = true)
            compose.onNodeWithText("Видалити всі записи").performClick()
            compose.waitUntil(5000) { history.value.isEmpty() }
            assertEquals(emptyList<SymptomHistoryItem>(), LocalDataStore(context).load().first)
            compose.onNodeWithText("Історія порожня.", substring = true).assertIsDisplayed()
            save("08_test_empty_history.png")
        } finally {
            listOf("meditrack_prefs", "meditrack_history_prefs").forEach {
                context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }

    private fun save(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val node = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        val image = node.captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "lab4-report")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, name).outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
