package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorDatabaseTest {
    private fun fixture(): JsonObject {
        val sponsor = JsonObject().apply {
            addProperty("\$id", "40")
            addProperty("m_SponsorDataID", 7)
            addProperty("currentRunningSponsorships", 2)
            addProperty("m_HQMultiplier", 1.0)
            addProperty("m_InitialCooldownExpired", true)
        }
        val randomList = JsonObject().apply {
            addProperty("\$id", "50")
            add("\$content", JsonArray().apply {
                add(JsonObject().apply { addProperty("\$ref", "40") })
            })
        }
        return JsonObject().apply {
            add("sponsorManager", JsonObject().apply {
                add("m_Sponsors", JsonObject().apply {
                    add("\$content", JsonArray().apply { add(JsonObject().apply { addProperty("\$ref", "40") }) })
                })
                add("m_RandomisedSponsors", JsonObject().apply { addProperty("\$ref", "50") })
            })
            add("entityManager", JsonObject().apply { add("m_Entities", JsonArray().apply { add(sponsor); add(randomList) }) })
        }
    }

    @Test
    fun resolvesSponsorReferencesAndRandomOfferOrder() {
        val sponsor = sponsorRecords(fixture()).single()
        assertEquals(7, sponsor.dataId)
        assertEquals(2, sponsor.runningContracts)
        assertTrue(sponsor.initialCooldownExpired)
        assertEquals(1, sponsor.randomOfferOrder)
    }

    @Test
    fun readingSponsorDatabaseDoesNotMutateSaveData() {
        val data = fixture()
        val before = data.toString()
        sponsorRecords(data)
        assertEquals(before, data.toString())
    }
}
