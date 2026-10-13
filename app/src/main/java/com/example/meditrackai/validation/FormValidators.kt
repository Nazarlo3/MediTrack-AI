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
        try {
            if (time.length != 5) return "Час має бути у форматі ГГ:ХХ."
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT))
        } catch (_: DateTimeParseException) { return "Час має бути у форматі ГГ:ХХ." }
        val selected = daysText.split(',').map(String::trim).filter(String::isNotEmpty)
        if (selected.isEmpty() || (selected != listOf("Щодня") && selected.any { it !in days && it != "Вихідні" })) {
            return "Оберіть дні тижня або «Щодня»."
        }
        val date = try { LocalDate.parse(endDate.trim(), dateFormat) }
        catch (_: DateTimeParseException) { return "Дата має бути реальною та у форматі ДД.ММ.РРРР." }
        if (date.isBefore(today)) return "Дата не може бути в минулому."
        return null
    }
}
