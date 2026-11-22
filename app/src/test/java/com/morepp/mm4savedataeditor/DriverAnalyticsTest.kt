package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriverAnalyticsTest {
    private fun fixture(): JsonObject {
        val driver = JsonObject().apply {
            addProperty("\$id", "10")
            addProperty("m_DriverID", 7)
            add("m_PersonDetails", JsonObject().apply {
                addProperty("name", "Sample Driver")
                addProperty("dateOfBirth", "2000-01-01T00:00:00")
            })
            add("contract", JsonObject().apply {
                add("m_Team", JsonObject().apply { addProperty("name", "Sample Team") })
            })
            add("m_Stats", JsonObject().apply {
                add("m_Stats", JsonArray().apply {
                    add(JsonObject().apply { addProperty("m_Stat", 18); addProperty("m_Potential", 20) })
                    add(JsonObject().apply { addProperty("m_Stat", 16); addProperty("m_Potential", 19) })
                })
            })
            add("careerHistory", JsonObject().apply { add("m_Career", JsonArray().apply { add(2024); add(2025) }) })
            add("traits", JsonObject().apply { add("m_Traits", JsonArray().apply { add(1); add(2); add(3) }) })
        }
        return JsonObject().apply {
            add("driverManager", JsonObject().apply {
                add("m_Drivers", JsonArray().apply {
                    add(JsonObject().apply { addProperty("\$ref", "10") })
                })
            })
            add("entityManager", JsonObject().apply { add("m_Entities", JsonArray().apply { add(driver) }) })
        }
    }

    @Test
    fun resolvesDriverReferencesAndCreatesFriendlyAnalytics() {
        val records = driverAnalyticsRecords(fixture(), "2026-01-01")
        assertEquals(1, records.size)
        val driver = records.single()
        assertEquals(7, driver.driverId)
        assertEquals("Sample Driver", driver.name)
        assertEquals("Sample Team", driver.teamName)
        assertEquals(26, driver.age)
        assertEquals(2, driver.careerSeasons)
        assertEquals(3, driver.traitCount)
        assertEquals(listOf(18, 16), driver.attributes)
        assertEquals(listOf(20, 19), driver.potentials)
    }

    @Test
    fun missingCareerDateDoesNotInventDriverAge() {
        val records = driverAnalyticsRecords(fixture(), null)
        assertNull(records.single().age)
    }

    @Test
    fun analyticsReadDoesNotMutateSaveData() {
        val data = fixture()
        val before = data.toString()
        driverAnalyticsRecords(data, "2026-01-01")
        assertEquals(before, data.toString())
    }
}
