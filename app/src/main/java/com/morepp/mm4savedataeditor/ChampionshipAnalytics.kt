package com.morepp.mm4savedataeditor

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal data class ChampionshipTeamStanding(
    val position: Int,
    val teamName: String,
    val points: Int,
    val wins: Int,
    val podiums: Int,
    val races: Int,
    val dnfs: Int
)

internal data class ChampionshipSummary(
    val recordKey: String,
    val id: Int,
    val displayName: String,
    val tier: Int,
    val series: Int,
    val category: Int,
    val eventNumber: Int,
    val teamCount: Int,
    val driverCount: Int,
    val raceCount: Int,
    val standings: List<ChampionshipTeamStanding>
)

internal fun championshipSummaries(data: JsonObject): List<ChampionshipSummary> {
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

    fun resolve(value: JsonElement?): JsonObject? {
        if (value == null || !value.isJsonObject) return null
        val obj = value.asJsonObject
        val ref = obj.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString
        return if (ref != null) idMap[ref] else obj
    }

    val list = data.getAsJsonObject("championshipManager")?.get("championships")
        ?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()

    return list.mapIndexedNotNull { index, reference ->
        val championship = resolve(reference) ?: return@mapIndexedNotNull null
        val standingsObject = championship.getAsJsonObject("standings")
        val teamRows = standingsObject?.get("m_Teams")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapIndexedNotNull { rowIndex, row ->
                if (!row.isJsonObject) return@mapIndexedNotNull null
                val item = row.asJsonObject
                val team = resolve(item.get("m_Entity"))
                val points = item.get("m_Points")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.sumOf { score ->
                        if (score.isJsonPrimitive && score.asJsonPrimitive.isNumber) score.asInt else 0
                    } ?: 0
                ChampionshipTeamStanding(
                    position = item.get("m_CurrentPosition")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                        ?: rowIndex + 1,
                    teamName = team?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                        ?.takeIf { it.isNotBlank() } ?: "Tim " + (rowIndex + 1),
                    points = points,
                    wins = item.get("wins")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
                    podiums = item.get("podiums")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
                    races = item.get("races")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
                    dnfs = item.get("DNFs")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
                )
            }.orEmpty().sortedBy { it.position }

        ChampionshipSummary(
            recordKey = championship.get("\$id")?.takeIf { it.isJsonPrimitive }?.asString ?: index.toString(),
            id = championship.get("m_ID")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: index,
            displayName = listOf("name", "m_Name", "displayName", "title").firstNotNullOfOrNull { key -> championship.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()?.takeIf { it.isNotEmpty() } } ?: careerDivisionLabel(championship.get("m_ID")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: index),
            tier = championship.get("tier")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
            series = championship.get("series")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
            category = championship.get("category")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
            eventNumber = championship.get("m_EventNumber")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
            teamCount = championship.get("m_TeamIDs")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: teamRows.size,
            driverCount = standingsObject?.get("m_Drivers")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0,
            raceCount = championship.get("m_Races")?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0,
            standings = teamRows
        )
    }.sortedWith(compareBy({ it.tier }, { it.series }, { it.id }))
}
