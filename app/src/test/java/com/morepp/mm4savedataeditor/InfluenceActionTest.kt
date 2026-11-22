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
                    add(JsonObject().apply { addProperty("m_InfluenceActionDatabaseIndex", 4); addProperty("m_CurrentCooldown", 3) })
                    add(JsonObject().apply { addProperty("m_InfluenceActionDatabaseIndex", 9); addProperty("m_CurrentCooldown", 0) })
                })
            })
        }
        val actions = influenceActionRecords(data)
        assertEquals("Mencari pembalap", actions[0].label)
        assertEquals("Tanpa cooldown", actions[0].status)
        assertEquals("Memotivasi pembalap", actions[1].label)
        assertEquals("Cooldown 3 event", actions[1].status)
        assertEquals("Mempercepat jaringan supplier", actions[2].label)
        assertEquals("Siap digunakan", actions[2].status)
    }

    @Test
    fun resetsOnlyPositiveCooldownWithoutMutatingOriginalData() {
        val data = JsonObject().apply {
            add("influenceManager", JsonObject().apply {
                add("m_Influences", JsonArray().apply {
                    add(JsonObject().apply { addProperty("m_InfluenceActionDatabaseIndex", 4); addProperty("m_CurrentCooldown", 3); addProperty("extra", "preserve") })
                    add(JsonObject().apply { addProperty("m_InfluenceActionDatabaseIndex", 9); addProperty("m_CurrentCooldown", 0) })
                })
            })
        }
        val before = data.toString()
        val updated = resetInfluenceCooldown(data, 0)!!
        assertEquals(0, updated.getAsJsonObject("influenceManager").getAsJsonArray("m_Influences")[0].asJsonObject.get("m_CurrentCooldown").asInt)
        assertEquals("preserve", updated.getAsJsonObject("influenceManager").getAsJsonArray("m_Influences")[0].asJsonObject.get("extra").asString)
        assertEquals(before, data.toString())
        assertEquals(null, resetInfluenceCooldown(data, 1))
        assertEquals(null, resetInfluenceCooldown(data, 8))
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
