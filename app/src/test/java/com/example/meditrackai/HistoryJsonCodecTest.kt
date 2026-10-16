package com.example.meditrackai

import com.example.meditrackai.data.HistoryJsonCodec
import com.example.meditrackai.network.SymptomHistoryItem
import com.example.meditrackai.network.SymptomResponse
import org.junit.Assert.*
import org.junit.Test

class HistoryJsonCodecTest {
    private val codec = HistoryJsonCodec()
    private val result = SymptomResponse("інше", "Приклад пояснення", "звичайне")
    private val oldJson = """[{"timestamp":"15:32","description":"Тестовий опис","response":{"category":"інше","explanation":"Приклад пояснення","urgency":"увага"}}]"""

    @Test fun migrationPreservesInputResultAndUnknownDate() {
        val migrated = codec.decode(oldJson, legacy = true).single()
        assertEquals("Тестовий опис", migrated.description)
        assertEquals("Приклад пояснення", migrated.response.explanation)
        assertEquals("увага", migrated.response.urgency)
        assertEquals("15:32", migrated.timestamp)
        assertNull(migrated.createdAt)
        assertEquals("готово", migrated.status)
        assertTrue(migrated.id.isNotBlank())
    }

    @Test fun migratedIdsSurviveSerializationAndRepeatedReads() {
        val first = codec.decode(oldJson, legacy = true)
        val stored = codec.encode(first)
        assertEquals(first, codec.decode(stored))
        assertEquals(first, codec.decode(stored))
    }

    @Test fun identicalLegacyDescriptionsAndTimesReceiveDifferentIds() {
        val record = oldJson.removePrefix("[").removeSuffix("]")
        val items = codec.decode("[$record,$record]", legacy = true)
        assertEquals(2, items.map { it.id }.toSet().size)
    }

    @Test fun oldRecordsKeepRelativeOrderWithoutInventingDates() {
        val newer = oldJson.removePrefix("[").removeSuffix("]")
        val older = newer.replace("Тестовий опис", "Другий опис").replace("15:32", "23:00")
        val items = codec.decode("[$newer,$older]", legacy = true)
        assertEquals(listOf("Тестовий опис", "Другий опис"), items.map { it.description })
        assertTrue(items.all { it.createdAt == null })
    }

    @Test fun newestDatedRecordsComeFirstAndRetainOwnResults() {
        val old = SymptomHistoryItem("11:00", "Старий опис", result.copy(explanation = "Стара відповідь"))
        val first = SymptomHistoryItem("12:00", "Перший опис", result, createdAt = 1000)
        val second = SymptomHistoryItem("13:00", "Другий опис", result.copy(explanation = "Друга відповідь"), createdAt = 2000)
        val items = codec.decode(codec.encode(listOf(old, first, second)))
        assertEquals(listOf(second.id, first.id, old.id), items.map { it.id })
        assertEquals("Друга відповідь", items.first().response.explanation)
        assertEquals("Стара відповідь", items.last().response.explanation)
    }

    @Test fun keepsOnlyTwentyNewestResults() {
        val items = (1..21).map { SymptomHistoryItem("12:00", "Запит $it", result, createdAt = it.toLong()) }
        val stored = codec.decode(codec.encode(items))
        assertEquals(20, stored.size)
        assertEquals(21L, stored.first().createdAt)
        assertEquals(2L, stored.last().createdAt)
    }

    @Test fun nullSourceMeansNoHistoryButJsonNullIsCorruption() {
        assertTrue(codec.decode(null).isEmpty())
        assertTrue(codec.decode("[]").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { codec.decode("null") }
    }

    @Test fun incompleteLegacyResultDoesNotSilentlyDropARecord() {
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(oldJson.replace("Приклад пояснення", ""), legacy = true)
        }
    }

    @Test fun duplicateIdsAreRejected() {
        val item = SymptomHistoryItem("12:00", "Опис", result, createdAt = 1000)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(codec.encode(listOf(item, item))) }
    }

    @Test fun unfinishedOrIncompleteNewRecordsAreRejected() {
        val item = SymptomHistoryItem("12:00", "Опис", result, createdAt = 1000)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(codec.encode(listOf(item.copy(status = "очікує")))) }
        assertThrows(IllegalArgumentException::class.java) { codec.decode(codec.encode(listOf(item.copy(id = "")))) }
        assertThrows(IllegalArgumentException::class.java) { codec.decode(oldJson) }
    }
}
