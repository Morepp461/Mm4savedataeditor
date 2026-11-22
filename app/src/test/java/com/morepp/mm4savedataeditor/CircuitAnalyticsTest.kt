package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CircuitAnalyticsTest {
    private fun fixture(): JsonObject {
        val circuit = JsonObject().apply {
            addProperty("\$id", "50")
            addProperty("circuitID", 0)
            addProperty("globeLocationID", 12)
            addProperty("trackLengthMiles", 3.1)
            add("trackStatsCharacteristics", JsonObject().apply {
                addProperty("topSpeed", 3.0)
                addProperty("acceleration", 2.0)
                addProperty("braking", 1.0)
                addProperty("lowSpeedCorners", 3.0)
                addProperty("mediumSpeedCorners", 2.0)
                addProperty("highSpeedCorners", 1.0)
            })
        }
        return JsonObject().apply {
            add("circuitManager", JsonObject().apply {
                add("m_Circuits", JsonArray().apply {
                    add(JsonObject().apply { addProperty("\$ref", "50") })
                })
            })
            add("entityManager", JsonObject().apply { add("m_Entities", JsonArray().apply { add(circuit) }) })
        }
    }

    @Test
    fun resolvesCircuitAndRoundsAllDisplayedValuesToIntegers() {
        val circuit = circuitSummaries(fixture()).single()
        assertEquals("Sirkuit 01", circuit.label)
        assertEquals(12, circuit.locationId)
        assertEquals(5, circuit.lengthKm)
        assertEquals(3, circuit.topSpeed)
        assertEquals(2, circuit.acceleration)
        assertEquals(1, circuit.braking)
    }

    @Test
    fun readingCircuitsDoesNotMutateSaveData() {
        val data = fixture()
        val before = data.toString()
        circuitSummaries(data)
        assertEquals(before, data.toString())
    }
}
