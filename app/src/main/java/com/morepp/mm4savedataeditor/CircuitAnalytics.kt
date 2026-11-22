package com.morepp.mm4savedataeditor

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.math.roundToInt

internal data class CircuitSummary(
    val recordKey: String,
    val circuitId: Int,
    val label: String,
    val locationId: Int,
    val lengthKm: Int,
    val topSpeed: Int,
    val acceleration: Int,
    val braking: Int,
    val lowSpeedCorners: Int,
    val mediumSpeedCorners: Int,
    val highSpeedCorners: Int
)

internal fun circuitSummaries(data: JsonObject): List<CircuitSummary> {
    val idMap = mutableMapOf<String, JsonObject>()
    fun collect(node: JsonElement) {
        when {
            node.isJsonObject -> {
                val obj = node.asJsonObject
                obj.get("\$id")?.takeIf { it.isJsonPrimitive }?.asString?.let { idMap[it] = obj }
                obj.entrySet().forEach { (_, value) -> collect(value) }
            }
            node.isJsonArray -> node.asJsonArray.forEach { collect(it) }
        }
    }
    collect(data)
    fun resolve(value: JsonElement): JsonObject? {
        if (!value.isJsonObject) return null
        val obj = value.asJsonObject
        val ref = obj.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString
        return if (ref != null) idMap[ref] else obj
    }
    val list = data.getAsJsonObject("circuitManager")?.get("m_Circuits")
        ?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
    fun rating(stats: JsonObject?, key: String): Int =
        stats?.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.roundToInt() ?: 0

    return list.mapIndexedNotNull { index, reference ->
        val circuit = resolve(reference) ?: return@mapIndexedNotNull null
        val id = circuit.get("circuitID")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: index
        val location = circuit.get("globeLocationID")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
        val miles = circuit.get("trackLengthMiles")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: 0.0
        val stats = circuit.getAsJsonObject("trackStatsCharacteristics")
        CircuitSummary(
            recordKey = circuit.get("\$id")?.takeIf { it.isJsonPrimitive }?.asString ?: index.toString(),
            circuitId = id,
            label = "Sirkuit " + (id + 1).toString().padStart(2, '0'),
            locationId = location,
            lengthKm = (miles * 1.609344).roundToInt(),
            topSpeed = rating(stats, "topSpeed"),
            acceleration = rating(stats, "acceleration"),
            braking = rating(stats, "braking"),
            lowSpeedCorners = rating(stats, "lowSpeedCorners"),
            mediumSpeedCorners = rating(stats, "mediumSpeedCorners"),
            highSpeedCorners = rating(stats, "highSpeedCorners")
        )
    }.sortedBy { it.circuitId }
}
