package com.example.meditrackai

import com.example.meditrackai.network.SymptomResponse
import com.example.meditrackai.validation.SymptomResponseValidator
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class SymptomResponseValidatorTest {
    private val result = SymptomResponse("інше", "Приклад пояснення", "звичайне")

    @Test fun acceptsCompleteResponseForEveryUrgency() {
        listOf("звичайне", "увага", "терміново").forEach {
            assertTrue(SymptomResponseValidator.isComplete(result.copy(urgency = it)))
        }
    }

    @Test fun acceptsNewCategoriesWithoutBreakingExistingHistory() {
        listOf("травми та ушкодження", "серцево-судинне", "сечовидільне", "офтальмологічне",
            "ЛОР", "стоматологічне", "алергічне", "психоемоційне", "репродуктивне").forEach {
            assertTrue("Category $it", SymptomResponseValidator.isComplete(result.copy(category = it)))
        }
    }

    @Test fun rejectsNullAndBlankRequiredFields() {
        assertFalse(SymptomResponseValidator.isComplete(null))
        assertFalse(SymptomResponseValidator.isComplete(result.copy(category = "")))
        assertFalse(SymptomResponseValidator.isComplete(result.copy(explanation = " \n ")))
        assertFalse(SymptomResponseValidator.isComplete(result.copy(disclaimer = "")))
        assertFalse(SymptomResponseValidator.isComplete(result.copy(urgency = null)))
    }

    @Test fun rejectsUnknownCategoryAndUrgency() {
        assertFalse(SymptomResponseValidator.isComplete(result.copy(category = "невідома")))
        assertFalse(SymptomResponseValidator.isComplete(result.copy(urgency = "невідома")))
    }

    @Test fun rejectsEachMissingFieldAfterGsonDeserialization() {
        val gson = Gson()
        listOf("category", "explanation", "urgency", "disclaimer").forEach { field ->
            val json = gson.toJsonTree(result).asJsonObject.apply { remove(field) }
            assertFalse("Missing $field", SymptomResponseValidator.isComplete(gson.fromJson(json, SymptomResponse::class.java)))
        }
    }
}
