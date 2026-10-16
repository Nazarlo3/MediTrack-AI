package com.example.meditrackai.validation

import com.example.meditrackai.network.SymptomResponse

/** Check deserialized values too: Gson may put null into Kotlin non-null fields. */
object SymptomResponseValidator {
    private val categories = setOf(
        "неврологічне", "шлунково-кишкове", "респіраторне",
        "опорно-руховий апарат", "шкірне", "загальне нездужання", "інше"
    )
    private val urgencies = setOf("звичайне", "увага", "терміново")

    fun isComplete(response: SymptomResponse?): Boolean = response != null &&
        response.category in categories &&
        !response.explanation.isNullOrBlank() &&
        response.urgency in urgencies &&
        !response.disclaimer.isNullOrBlank()
}
