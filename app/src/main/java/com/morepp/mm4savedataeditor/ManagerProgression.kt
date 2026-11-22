package com.morepp.mm4savedataeditor

import com.google.gson.JsonObject

internal data class ManagerProgressionSummary(
    val level: Int,
    val experience: Int,
    val baseExperience: Int,
    val maxExperience: Int,
    val maximumLevel: Int
)

internal fun managerProgressionSummary(data: JsonObject): ManagerProgressionSummary? {
    val progress = data.getAsJsonObject("playerExperience") ?: return null
    fun number(key: String) = progress.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    val level = number("m_Level") ?: return null
    val experience = number("m_Experience") ?: return null
    val base = number("m_BaseExperience") ?: return null
    val max = number("m_MaxExperience") ?: return null
    val maximumLevel = number("maxLevel") ?: return null
    return ManagerProgressionSummary(level, experience, base, max, maximumLevel)
}

/** Applies only the four progression fields whose relationship is defined by the save's own level table. */
internal fun applyManagerExperience(data: JsonObject, experience: Int): JsonObject? {
    if (experience < 0) return null
    val progress = data.getAsJsonObject("playerExperience") ?: return null
    val ranges = progress.get("playerLevelRanges")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    val levels = ranges.mapNotNull { row ->
        if (!row.isJsonObject) return@mapNotNull null
        val item = row.asJsonObject
        val key = item.get("Key")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: return@mapNotNull null
        val value = item.get("Value")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val min = value.get("minExperience")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: return@mapNotNull null
        val max = value.get("maxExperience")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: return@mapNotNull null
        if (min < 0 || max <= min) return@mapNotNull null
        Triple(key, min, max)
    }.sortedBy { it.first }
    if (levels.isEmpty()) return null
    val maximum = levels.last().third
    if (experience > maximum) return null
    val selected = levels.firstOrNull { experience >= it.second && experience < it.third }
        ?: levels.lastOrNull()?.takeIf { experience == it.third }
        ?: return null
    val updated = data.deepCopy().asJsonObject
    val target = updated.getAsJsonObject("playerExperience") ?: return null
    target.addProperty("m_Level", selected.first)
    target.addProperty("m_Experience", experience)
    target.addProperty("m_BaseExperience", selected.second)
    target.addProperty("m_MaxExperience", selected.third)
    return updated
}
