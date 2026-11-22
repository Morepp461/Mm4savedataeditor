package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamSwapTest {
    private fun fixture(): JsonObject {
        val championshipA = JsonObject().apply {
            addProperty("\$id", "100")
            add("m_TeamIDs", JsonArray().apply { add(11); add(12) })
        }
        val championshipB = JsonObject().apply {
            addProperty("\$id", "200")
            add("m_TeamIDs", JsonArray().apply { add(21); add(22) })
        }
        val teamA = JsonObject().apply {
            addProperty("\$id", "1")
            addProperty("m_TeamID", 11)
            add("m_Championship", JsonObject().apply { addProperty("\$ref", "100") })
        }
        val teamB = JsonObject().apply {
            addProperty("\$id", "2")
            addProperty("m_TeamID", 21)
            add("m_Championship", JsonObject().apply { addProperty("\$ref", "200") })
        }
        return JsonObject().apply {
            add("championshipManager", JsonObject().apply {
                add("championships", JsonArray().apply {
                    add(JsonObject().apply { addProperty("\$ref", "100") })
                    add(JsonObject().apply { addProperty("\$ref", "200") })
                })
            })
            add("teamManager", JsonObject().apply {
                add("m_Teams", JsonArray().apply {
                    add(JsonObject().apply { addProperty("\$ref", "1") })
                    add(JsonObject().apply { addProperty("\$ref", "2") })
                })
            })
            add("entityManager", JsonObject().apply {
                add("m_Entities", JsonArray().apply { add(championshipA); add(championshipB); add(teamA); add(teamB) })
            })
        }
    }

    @Test fun swapsNumericDivisionSlotsAndTeamChampionshipReferences() {
        val data = fixture()
        assertTrue(swapTeamsBetweenDivisions(data, "1", "2"))
        val entities = data.getAsJsonObject("entityManager").getAsJsonArray("m_Entities")
        val champs = entities.filter { it.isJsonObject && it.asJsonObject.has("m_TeamIDs") }.map { it.asJsonObject }
        assertEquals(listOf(21, 12), champs[0].getAsJsonArray("m_TeamIDs").map { it.asInt })
        assertEquals(listOf(11, 22), champs[1].getAsJsonArray("m_TeamIDs").map { it.asInt })
        val teams = entities.filter { it.isJsonObject && it.asJsonObject.has("m_TeamID") }.map { it.asJsonObject }
        assertEquals("200", teams[0].getAsJsonObject("m_Championship").get("\$ref").asString)
        assertEquals("100", teams[1].getAsJsonObject("m_Championship").get("\$ref").asString)
    }

    @Test fun refusesSameTeamAndLeavesSaveUnchanged() {
        val data = fixture()
        val before = data.toString()
        assertFalse(swapTeamsBetweenDivisions(data, "1", "1"))
        assertEquals(before, data.toString())
    }
}