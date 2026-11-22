package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ChampionshipAnalyticsTest {
    private fun fixture(): JsonObject {
        val championship = JsonObject().apply {
            addProperty("\$id", "20")
            addProperty("m_ID", 0)
            addProperty("tier", 1)
            addProperty("series", 0)
            addProperty("category", 0)
            addProperty("m_EventNumber", 3)
            add("m_TeamIDs", JsonArray().apply { add(4) })
            add("m_Races", JsonArray().apply { add(1); add(2); add(3) })
            add("standings", JsonObject().apply {
                add("m_Drivers", JsonArray().apply { add(1); add(2) })
                add("m_Teams", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("m_CurrentPosition", 1)
                        add("m_Entity", JsonObject().apply { addProperty("\$ref", "30") })
                        add("m_Points", JsonArray().apply { add(10); add(15) })
                        addProperty("wins", 2)
                        addProperty("podiums", 2)
                        addProperty("races", 2)
                        addProperty("DNFs", 0)
                    })
                })
            })
        }
        val team = JsonObject().apply {
            addProperty("\$id", "30")
            addProperty("name", "Alpha Racing")
        }
        return JsonObject().apply {
            add("championshipManager", JsonObject().apply {
                add("championships", JsonArray().apply {
                    add(JsonObject().apply { addProperty("\$ref", "20") })
                })
            })
            add("entityManager", JsonObject().apply { add("m_Entities", JsonArray().apply { add(championship); add(team) }) })
        }
    }

    @Test
    fun resolvesChampionshipAndTeamStandingReferences() {
        val data = fixture()
        val result = championshipSummaries(data).single()
        assertEquals(0, result.id)
        assertEquals(1, result.tier)
        assertEquals(3, result.eventNumber)
        assertEquals(1, result.teamCount)
        assertEquals(2, result.driverCount)
        assertEquals(3, result.raceCount)
        val team = result.standings.single()
        assertEquals("Alpha Racing", team.teamName)
        assertEquals(25, team.points)
        assertEquals(2, team.wins)
        assertEquals(2, team.podiums)
        assertEquals(listOf("entityManager", "m_Entities", "[0]"), result.path)
        assertEquals(listOf("entityManager", "m_Entities", "[0]", "standings", "m_Teams", "[0]"), team.path)
        assertEquals(listOf("entityManager", "m_Entities", "[0]", "standings", "m_Teams", "[0]", "m_Points", "[0]"), team.pointPaths.first())
        assertEquals(10, resolvePath(data, team.pointPaths.first())?.asInt)
    }

    @Test
    fun championshipEditorPathsResolveToExistingFields() {
        val data = fixture()
        val summary = championshipSummaries(data).single()
        assertEquals(3, resolvePath(data, summary.path + "m_EventNumber")?.asInt)
        assertEquals(2, resolvePath(data, summary.standings.single().path + "wins")?.asInt)
        assertEquals(25, summary.standings.single().points)
    }

    @Test
    fun readingChampionshipDoesNotMutateSaveData() {
        val data = fixture()
        val before = data.toString()
        championshipSummaries(data)
        assertEquals(before, data.toString())
    }

    @Test
    fun fallbackUsesOriginalInGameChampionshipNames() {
        assertEquals("World Motorsport", careerDivisionLabel(0))
        assertEquals("Asia Pacific Super Cup", careerDivisionLabel(1))
        assertEquals("European Racing", careerDivisionLabel(2))
        assertEquals("Open Wheel Sprint", careerDivisionLabel(3))
    }

}
