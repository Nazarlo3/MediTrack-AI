package com.example.meditrackai.data

import com.example.meditrackai.network.MedicationItem
import com.example.meditrackai.reminders.MedicationSchedule
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.time.LocalDate
import java.util.UUID

/** v3 preserves each dose separately and keeps invalid old clocks unscheduled. */
class MedicationJsonCodec(private val gson: Gson = Gson()) {
    fun decode(json: String?, legacy: Boolean = false): List<MedicationItem> {
        if (json == null) return emptyList()
        require(JsonParser.parseString(json).isJsonArray)
        val items = requireNotNull(gson.fromJson(json, Array<MedicationItem>::class.java))
        val ids = mutableSetOf<String>()
        return items.map { raw ->
            val item = requireNotNull(raw)
            require(!item.id.isNullOrBlank() && ids.add(item.id))
            requireNotNull(item.name); requireNotNull(item.dosage)
            requireNotNull(item.daysOfWeek); requireNotNull(item.mealSlot); requireNotNull(item.endDate)
            if (legacy) {
                val clocks = MedicationSchedule.times(item)?.map { it.toString() }.orEmpty()
                val taken = if (item.isTaken && item.lastTakenDate != null) {
                    clocks.associateWith { item.lastTakenDate }
                } else emptyMap()
                item.copy(times = clocks, takenDoseDates = taken, scheduleVersion = UUID.randomUUID().toString())
            } else {
                requireNotNull(item.times)
                require(item.times.isEmpty() || MedicationSchedule.times(item) != null)
                require(!item.scheduleVersion.isNullOrBlank())
                requireNotNull(item.takenDoseDates).forEach { (clock, date) ->
                    require(clock in item.times)
                    LocalDate.parse(date)
                }
                item
            }
        }
    }

    fun encode(items: List<MedicationItem>): String = gson.toJson(items)
}
