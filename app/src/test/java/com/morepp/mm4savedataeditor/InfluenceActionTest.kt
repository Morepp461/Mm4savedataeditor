package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class InfluenceActionTest {
    @Test
    fun mapsKnownActionsAndCooldownStatesToFriendlyLabels() {
        val data = JsonObject().apply {
            add("influenceManager", JsonObject().apply {
                add("m_Influences", JsonArray().apply {
                    add(JsonObject().apply { addProperty("m_InfluenceActionDatabaseIndex", 0); addProperty("m_CurrentCooldown", -1) })
                    add(JsonObject().apply { addProperty("m_InfluenceActionDatabaseIndex", 9); addProperty("m_CurrentCooldown", 0) })
                    add(JsonObject().apply { addProperty("m_InfluenceActionDatabaseIndex", 4); addProperty("m_CurrentCooldown", 3) })
                })
            })
        }
        val actions = influenceActionRecords(data)
        assertEquals("Mencari pembalap", actions[0].label)
        assertEquals("Tanpa cooldown", actions[0].status)
        assertEquals("Mempercepat jaringan supplier", actions[1].label)
        assertEquals("Siap digunakan", actions[1].status)
        assertEquals("Cooldown 3 event", actions[2].status)
    }

    @Test
    fun readingInfluenceActionsDoesNotMutateSaveData() {
        val data = JsonObject().apply {
            add("influenceManager", JsonObject().apply {
                add("m_Influences", JsonArray().apply {
                    add(JsonObject().apply { addProperty("m_InfluenceActionDatabaseIndex", 2); addProperty("m_CurrentCooldown", 1) })
                })
            })
        }
        val before = data.toString()
        influenceActionRecords(data)
        assertEquals(before, data.toString())
    }
}
