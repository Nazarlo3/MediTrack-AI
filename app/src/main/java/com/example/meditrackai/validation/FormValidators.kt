package com.example.meditrackai.validation

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

object FormValidators {
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu")
        .withResolverStyle(ResolverStyle.STRICT)
    private val days = setOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")

    fun symptomError(text: String): String? = when (text.trim().length) {
        in 3..500 -> null
        else -> "Опис має містити від 3 до 500 символів."
    }

    fun medicationError(name: String, dosage: String, time: String, daysText: String, endDate: String, today: LocalDate = LocalDate.now()): String? {
        if (name.isBlank()) return "Введіть або виберіть назву ліків."
        if (dosage.isBlank()) return "Вкажіть дозування."
        if (parseTimes(time) == null) return "Вкажіть від 1 до 12 різних часів ГГ:ХХ через кому, наприклад 08:00, 20:00."
        val selected = daysText.split(',').map(String::trim).filter(String::isNotEmpty)
        if (selected.isEmpty() || (selected != listOf("Щодня") && selected.any { it !in days && it != "Вихідні" })) {
            return "Оберіть дні тижня або «Щодня»."
        }
        val date = try { LocalDate.parse(endDate.trim(), dateFormat) }
        catch (_: DateTimeParseException) { return "Дата має бути реальною та у форматі ДД.ММ.РРРР." }
        if (date.isBefore(today)) return "Дата не може бути в минулому."
        return null
    }

    fun parseTimes(text: String): List<String>? = try {
        val values = text.split(',').map(String::trim)
        if (values.size !in 1..12 || values.distinct().size != values.size) null
        else values.map { value ->
            require(Regex("\\d{2}:\\d{2}").matches(value))
            LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)).toString()
        }.sorted()
    } catch (_: IllegalArgumentException) { null }
      catch (_: DateTimeParseException) { null }
}
