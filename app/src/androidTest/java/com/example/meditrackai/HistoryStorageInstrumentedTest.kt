package com.example.meditrackai

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meditrackai.data.LocalDataStore
import com.example.meditrackai.network.SymptomHistoryItem
import com.example.meditrackai.network.SymptomResponse
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/** Real SharedPreferences, isolated names: never clears the user's application data. */
class HistoryStorageInstrumentedTest {
    private lateinit var context: Context
    private val names = listOf("meditrack_prefs", "meditrack_history_prefs")
    private val legacy = """[{"timestamp":"15:32","description":"Тестовий опис","response":{"category":"інше","explanation":"Приклад пояснення","urgency":"звичайне"}}]"""

    @Before fun isolatePreferences() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "lab4_test_${UUID.randomUUID()}_"
        context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) =
                target.getSharedPreferences(prefix + name, mode)
        }
    }

    @After fun removeOnlyTestPreferences() {
        names.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun resultAndDetailsSurviveStoreRecreation() = runBlocking {
        val store = LocalDataStore(context)
        assertTrue(store.load().first.isEmpty())
        val first = SymptomHistoryItem("12:00", "Перший опис", SymptomResponse("інше", "Перший результат"), createdAt = 1000)
        val second = SymptomHistoryItem("13:00", "Другий опис", SymptomResponse("шкірне", "Другий результат", "увага"), createdAt = 2000)
        assertNotNull(store.updateHistory { listOf(first) })
        assertNotNull(store.updateHistory { listOf(second) + it })
        val reopened = LocalDataStore(context)
        assertEquals(listOf(second, first), reopened.load().first)
        assertEquals(first, reopened.getHistoryItem(first.id))
        assertEquals(second, reopened.getHistoryItem(second.id))
    }

    @Test fun migratesBothLegacyLocationsAndKeepsStableIds() = runBlocking {
        for ((preference, key) in listOf("meditrack_prefs" to "symptom_history", "meditrack_history_prefs" to "symptom_history_v1")) {
            names.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
            context.getSharedPreferences(preference, Context.MODE_PRIVATE).edit().putString(key, legacy).commit()
            val first = LocalDataStore(context).load().first.single()
            assertNull(first.createdAt)
            assertEquals(first, LocalDataStore(context).load().first.single())
        }
    }

    @Test fun clearingRemovesLegacyCopiesAndStaysEmptyAfterReopen() = runBlocking {
        val prefs = context.getSharedPreferences("meditrack_prefs", Context.MODE_PRIVATE)
        val oldPrefs = context.getSharedPreferences("meditrack_history_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("symptom_history", legacy).commit()
        oldPrefs.edit().putString("symptom_history_v1", legacy).commit()
        val store = LocalDataStore(context)
        assertEquals(1, store.load().first.size)
        assertEquals(emptyList<SymptomHistoryItem>(), store.updateHistory { emptyList() })
        assertFalse(prefs.contains("symptom_history"))
        assertFalse(oldPrefs.contains("symptom_history_v1"))
        assertTrue(LocalDataStore(context).load().first.isEmpty())
    }

    @Test fun corruptedHistoryIsNotOverwrittenOrReplacedByLegacy() = runBlocking {
        val prefs = context.getSharedPreferences("meditrack_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("symptom_history_v2", "broken-json").putString("symptom_history", legacy).commit()
        val store = LocalDataStore(context)
        assertTrue(store.load().first.isEmpty())
        assertTrue(store.readError)
        assertNull(store.updateHistory { emptyList() })
        assertEquals("broken-json", prefs.getString("symptom_history_v2", null))
        assertEquals(legacy, prefs.getString("symptom_history", null))
    }

    @Test fun failedMigrationKeepsOriginalAndBlocksFurtherWrites() = runBlocking {
        val prefs = context.getSharedPreferences("meditrack_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("symptom_history", legacy).commit()
        val originalContext = context
        val failingContext = object : ContextWrapper(originalContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val actual = originalContext.getSharedPreferences(name, mode)
                if (name != "meditrack_prefs") return actual
                return object : SharedPreferences by actual {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = actual.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                editor.putString(key, value)
                                return this
                            }
                            override fun commit() = false
                        }
                    }
                }
            }
        }
        val store = LocalDataStore(failingContext)
        assertTrue(store.load().first.isEmpty())
        assertTrue(store.readError)
        assertNull(store.updateHistory { emptyList() })
        assertFalse(prefs.contains("symptom_history_v2"))
        assertEquals(legacy, prefs.getString("symptom_history", null))
    }
}
