package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ManagerProgressionTest {
    private fun fixture() = JsonObject().apply {
        add("playerExperience", JsonObject().apply {
            addProperty("maxLevel", 3)
            addProperty("m_Level", 2)
            addProperty("m_Experience", 125)
            addProperty("m_BaseExperience", 50)
            addProperty("m_MaxExperience", 1000)
            add("playerLevelRanges", JsonArray().apply {
                add(JsonObject().apply { addProperty("Key", 1); add("Value", JsonObject().apply { addProperty("minExperience", 0); addProperty("maxExperience", 50) }) })
                add(JsonObject().apply { addProperty("Key", 2); add("Value", JsonObject().apply { addProperty("minExperience", 50); addProperty("maxExperience", 1000) }) })
                add(JsonObject().apply { addProperty("Key", 3); add("Value", JsonObject().apply { addProperty("minExperience", 1000); addProperty("maxExperience", 2000) }) })
            })
        })
    }

    @Test fun derivesProgressionFromSaveLevelTable() {
        val updated = applyManagerExperience(fixture(), 1500)!!
        val summary = managerProgressionSummary(updated)!!
        assertEquals(3, summary.level)
        assertEquals(1500, summary.experience)
        assertEquals(1000, summary.baseExperience)
        assertEquals(2000, summary.maxExperience)
    }

    @Test fun rejectsValuesOutsideTheSaveDefinedRange() {
        assertNull(applyManagerExperience(fixture(), -1))
        assertNull(applyManagerExperience(fixture(), 2001))
    }

    @Test fun keepsOriginalDataUnchanged() {
        val data = fixture()
        val before = data.toString()
        applyManagerExperience(data, 1500)
        assertEquals(before, data.toString())
        assertNotEquals(data.toString(), applyManagerExperience(data, 1500)!!.toString())
    }
}
