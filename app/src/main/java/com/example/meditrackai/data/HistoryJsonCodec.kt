package com.example.meditrackai.data

import com.example.meditrackai.network.SymptomHistoryItem
import com.example.meditrackai.network.SymptomResponse
import com.example.meditrackai.validation.SymptomResponseValidator
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

/** Explicit migration avoids Gson defaults for fields missing from old records. */
class HistoryJsonCodec(private val gson: Gson = Gson()) {
    fun decode(json: String?, legacy: Boolean = false): List<SymptomHistoryItem> {
        if (json == null) return emptyList()
        val array = JsonParser.parseString(json)
        require(array.isJsonArray) { "History must be an array" }
        val ids = mutableSetOf<String>()
        return array.asJsonArray.map { element ->
            require(element.isJsonObject) { "Invalid history record" }
            val record = element.asJsonObject
            val description = record.requiredString("description")
            val timestamp = record.requiredString("timestamp")
            val responseJson = record.getAsJsonObject("response")
                ?: error("Missing history result")
            val response = if (legacy) {
                SymptomResponse(
                    category = responseJson.requiredString("category"),
                    explanation = responseJson.requiredString("explanation"),
                    urgency = responseJson.optionalString("urgency") ?: "звичайне",
                    disclaimer = responseJson.optionalString("disclaimer")
                        ?: SymptomResponse("інше", "").disclaimer
                )
            } else {
                gson.fromJson(responseJson, SymptomResponse::class.java)
            }
            require(SymptomResponseValidator.isComplete(response)) { "Incomplete history result" }
            val id = if (legacy) UUID.randomUUID().toString() else record.requiredString("id")
            require(ids.add(id)) { "Duplicate history ID" }
            val createdAt = if (legacy) null else record.get("createdAt")?.let {
                if (it.isJsonNull) null else {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
                    it.asBigDecimal.longValueExact().also { value -> require(value >= 0) }
                }
            }
            val status = if (legacy) "готово" else record.requiredString("status")
            require(status == "готово") { "History contains unfinished result" }
            SymptomHistoryItem(timestamp, description, response, id, createdAt, status)
        }.sortedByDescending { it.createdAt ?: Long.MIN_VALUE }.take(LIMIT)
    }

    fun encode(items: List<SymptomHistoryItem>): String = gson.toJson(items)

    private fun JsonObject.optionalString(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.requiredString(name: String): String =
        requireNotNull(optionalString(name)).also { require(it.isNotBlank()) }

    companion object {
        const val LIMIT = 20
    }
}
