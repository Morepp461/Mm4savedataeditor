package com.morepp.mm4savedataeditor

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.LocalDate
import java.time.Period

internal data class DriverCareerSeason(
    val year: Int,
    val teamName: String,
    val wins: Int,
    val podiums: Int,
    val races: Int,
    val dnfs: Int,
    val points: Int
)

internal data class DriverAnalyticsRecord(
    val recordKey: String,
    val driverId: Int,
    val name: String,
    val teamName: String,
    val age: Int?,
    val careerSeasons: Int,
    val careerHistory: List<DriverCareerSeason>,
    val traitCount: Int,
    val attributes: List<Int>,
    val potentials: List<Int>
)

internal fun driverAnalyticsRecords(data: JsonObject, careerDate: String?): List<DriverAnalyticsRecord> {
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

    val roster = data.getAsJsonObject("driverManager")?.get("m_Drivers")
        ?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
    val referenceDate = runCatching { LocalDate.parse(careerDate?.take(10)) }.getOrNull()

    return roster.mapIndexedNotNull { index, reference ->
        val driver = resolve(reference) ?: return@mapIndexedNotNull null
        val details = driver.getAsJsonObject("m_PersonDetails")
        val contract = driver.getAsJsonObject("contract")
        val team = resolve(contract?.get("m_Team"))
        val name = details?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
            ?.trim()?.takeIf { it.isNotEmpty() } ?: "Pembalap " + (index + 1)
        val birthDate = runCatching {
            LocalDate.parse(details?.get("dateOfBirth")?.asString?.take(10))
        }.getOrNull()
        val age = if (birthDate != null && referenceDate != null) {
            Period.between(birthDate, referenceDate).years.coerceAtLeast(0)
        } else null
        val stats = driver.getAsJsonObject("m_Stats")?.getAsJsonArray("m_Stats")
        val attributes = stats?.mapNotNull { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("m_Stat")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        }.orEmpty()
        val potentials = stats?.mapNotNull { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("m_Potential")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        }.orEmpty()
        val careerRows = driver.getAsJsonObject("careerHistory")?.get("m_Career")
            ?.takeIf { it.isJsonArray }?.asJsonArray
        val careerHistory = careerRows?.mapNotNull { entry ->
            if (!entry.isJsonObject) return@mapNotNull null
            val row = entry.asJsonObject
            val careerTeam = resolve(row.get("team"))
            DriverCareerSeason(
                year = row.get("year")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
                teamName = careerTeam?.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    ?.takeIf { it.isNotBlank() } ?: "Tim tidak diketahui",
                wins = row.get("wins")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
                podiums = row.get("podiums")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
                races = row.get("races")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
                dnfs = row.get("DNFs")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
                points = row.get("points")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
            )
        }.orEmpty().sortedByDescending { it.year }
        val career = careerHistory.size
        val traits = driver.getAsJsonObject("traits")?.get("m_Traits")
            ?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
        val id = driver.get("m_DriverID")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
            ?: index
        DriverAnalyticsRecord(
            recordKey = driver.get("\$id")?.takeIf { it.isJsonPrimitive }?.asString ?: index.toString(),
            driverId = id,
            name = name,
            teamName = team?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() } ?: "Belum bergabung dengan tim",
            age = age,
            careerSeasons = career,
            careerHistory = careerHistory,
            traitCount = traits,
            attributes = attributes,
            potentials = potentials
        )
    }.sortedBy { it.name.lowercase() }
}
