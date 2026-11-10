package com.example.meditrackai

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.meditrackai.data.DataCollectionSettings
import com.example.meditrackai.data.ThemeMode
import com.example.meditrackai.ui.AppSettingsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SettingsDiagnosticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun settingsShowActualVersionAndControlRetainedCollection() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "settings_diagnostics_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
        }
        val retained = DataCollectionSettings(context)
        val recreated = DataCollectionSettings(context)
        var closed = false
        try {
            assertNotNull(runBlocking { retained.tokenForCollection() })
            compose.setContent {
                MaterialTheme { AppSettingsScreen(ThemeMode.SYSTEM, {}, recreated, 0, 0, null, { closed = true }) }
            }
            compose.onNode(isToggleable()).performScrollTo().performClick()
            compose.waitUntil(5000) { !recreated.choice.value.enabled }
            assertNull(runBlocking { retained.tokenForCollection() })
            compose.onNodeWithText("Докладніше про покращення AI").performScrollTo().performClick()
            compose.onNodeWithText("Для покращення AI та статистики", substring = true).assertExists()
            compose.waitUntil(5000) {
                compose.onAllNodesWithText("MediTrack AI ${BuildConfig.VERSION_NAME}", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Копіювати діагностику").performScrollTo().performClick()
            compose.onNodeWithText("Діагностику скопійовано.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Проєкт на GitHub").assertExists()
            compose.onNodeWithText("Готово").performScrollTo().performClick()
            compose.runOnIdle { assertTrue(closed) }
        } finally {
            base.deleteSharedPreferences(prefix + "meditrack_collection")
        }
    }
}
