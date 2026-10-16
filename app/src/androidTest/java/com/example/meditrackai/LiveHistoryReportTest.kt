package com.example.meditrackai

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Read-only screenshots of the user's existing history; never submits or deletes a record. */
class LiveHistoryReportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun captureExistingHistoryAndDetails() {
        val phase = InstrumentationRegistry.getArguments().getString("reportPhase")
        // A normal test run must also work on a fresh device with no user history.
        assumeTrue("Read-only evidence capture requires an explicit reportPhase", phase != null)
        require(phase in setOf("before", "after"))
        compose.onNodeWithText("Аналіз AI").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Відкрити деталі").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Історія перевірок", substring = true).performScrollTo()
        save("01_live_${phase}_history.png")
        compose.onAllNodesWithText("Відкрити деталі")[0].performScrollTo().performClick()
        compose.onNodeWithText("Збережений результат").assertIsDisplayed()
        save("02_live_${phase}_details.png", dialog = true)
        compose.onNodeWithText("Закрити").performClick()
    }

    private fun save(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val node = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "lab4-report")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, name).outputStream().use {
            check(node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
