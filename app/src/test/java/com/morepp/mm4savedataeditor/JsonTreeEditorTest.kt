package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTreeEditorTest {
    @Test
    fun editsNestedObjectAndArrayValues() {
        val root = JsonObject().apply {
            add("career", JsonObject().apply { addProperty("season", 2) })
            add("names", JsonArray().apply { add("Alpha"); add("Beta") })
        }

        assertTrue(setPath(root, listOf("career", "season"), JsonPrimitive(3)))
        assertTrue(appendChild(root, listOf("names"), null, JsonPrimitive("Gamma")))
        assertTrue(removePath(root, listOf("names", "[0]")))

        assertEquals(3, root.getAsJsonObject("career").get("season").asInt)
        assertEquals(2, root.getAsJsonArray("names").size())
        assertEquals("Beta", root.getAsJsonArray("names")[0].asString)
        assertEquals("Gamma", root.getAsJsonArray("names")[1].asString)
    }

    @Test
    fun objectAppendRejectsDuplicateProperty() {
        val root = JsonObject().apply { addProperty("balance", 100) }

        assertFalse(appendChild(root, emptyList(), "balance", JsonPrimitive(200)))
        assertEquals(100, root.get("balance").asInt)
    }

    @Test
    fun flattenIncludesContainersAndNestedLeaves() {
        val root = JsonObject().apply {
            add("team", JsonObject().apply { addProperty("name", "Apex") })
        }

        val paths = flatten(root).map { it.first.joinToString("/") }

        assertTrue(paths.contains("team"))
        assertTrue(paths.contains("team/name"))
    }

    @Test
    fun mutateDocumentCopiesTheEditedRoot() {
        val original = Mm4SaveCodec.Document(
            JsonObject().apply { addProperty("career", "original") },
            JsonObject().apply { addProperty("balance", 100) },
            "copy-test.msv"
        )

        val edited = mutateDocument(original, "Game Data") {
            setPath(it, listOf("balance"), JsonPrimitive(250))
        }

        assertNotNull(edited)
        assertEquals(100, original.data.get("balance").asInt)
        assertEquals(250, edited!!.data.get("balance").asInt)
        assertEquals("original", original.info.get("career").asString)
    }

    @Test
    fun teamCatalogOnlyAllowsIdentityReputationAndFinancialFields() {
        assertTrue(isCatalogPrimitiveEditable("team", listOf("name")))
        assertTrue(isCatalogPrimitiveEditable("team", listOf("shortName")))
        assertTrue(isCatalogPrimitiveEditable("team", listOf("m_Reputation")))
        assertTrue(isCatalogPrimitiveEditable("team", listOf("m_Financials", "m_Balance")))
        assertFalse(isCatalogPrimitiveEditable("team", listOf("aggression")))
        assertFalse(isCatalogPrimitiveEditable("team", listOf("m_EngineerRacePerformanceModifier")))
    }

    @Test
    fun staffCatalogOnlyAllowsProfileContractAndCompensationFields() {
        assertTrue(isCatalogPrimitiveEditable("staff", listOf("m_PersonDetails", "name")))
        assertTrue(isCatalogPrimitiveEditable("staff", listOf("contract", "m_PerRaceCost")))
        assertTrue(isCatalogPrimitiveEditable("staff", listOf("m_Salary")))
        assertFalse(isCatalogPrimitiveEditable("staff", listOf("m_ExperiencePoints")))
        assertFalse(isCatalogPrimitiveEditable("staff", listOf("stats", "m_Stats", "[0]", "m_Stat")))
    }

    @Test
    fun carCatalogOnlyExposesItsDedicatedAttributeCards() {
        assertFalse(isCatalogPrimitiveEditable("car", listOf("m_Stats", "[0]")))
        assertFalse(isCatalogPrimitiveEditable("car", listOf("m_EngineLevel")))
    }

    @Test
    fun teamFinancialWhitelistDoesNotLeakNestedCarFields() {
        assertTrue(isCatalogPrimitiveEditable("team", listOf("m_Financials", "m_Balance")))
        assertFalse(isCatalogPrimitiveEditable("team", listOf("car", "m_MaintenanceCost")))
        assertFalse(isCatalogPrimitiveEditable("team", listOf("driver", "m_Salary")))
    }

    @Test
    fun staffCompensationWhitelistDoesNotLeakNestedAttributeFields() {
        assertTrue(isCatalogPrimitiveEditable("staff", listOf("m_Salary")))
        assertFalse(isCatalogPrimitiveEditable("staff", listOf("stats", "m_Salary")))
    }

    @Test
    fun numericSteppingPreservesLargeIntegerPrecisionAndDecimals() {
        assertEquals("9007199254740994", stepNumericValue("9007199254740993", 1.0, 1))
        assertEquals("2.5", stepNumericValue("1.5", 1.0, 1))
        assertEquals("0.25", stepNumericValue("0.5", 0.25, -1))
    }

    @Test
    fun driverAgeUsesCareerDateInsteadOfPhoneDate() {
        assertEquals("29", ageFromDate("1990-06-10", "2020-06-09"))
        assertEquals("30", ageFromDate("1990-06-10", "2020-06-10"))
    }

    @Test
    fun financeHistorySupportsPrimitiveAndObjectRecords() {
        assertEquals("£1,250", financeHistoryLabel(JsonPrimitive(1250)))
        val record = JsonObject().apply { addProperty("amount", 8750) }
        assertEquals("£8,750", financeHistoryLabel(record))
    }
    @Test
    fun driverTransferUpdatesContractAndExistingTeamRoster() {
        val data = JsonObject().apply {
            add("teamManager", JsonObject().apply {
                add("m_Teams", JsonArray().apply { add(JsonObject().apply { addProperty("\$ref", "team-a") }); add(JsonObject().apply { addProperty("\$ref", "team-b") }) })
            })
            add("entities", JsonArray().apply {
                add(JsonObject().apply { addProperty("\$id", "team-a"); add("m_Drivers", JsonArray().apply { add(JsonObject().apply { addProperty("\$ref", "driver-1") }) }) })
                add(JsonObject().apply { addProperty("\$id", "team-b"); add("m_Drivers", JsonArray()) })
                add(JsonObject().apply {
                    addProperty("\$id", "driver-1")
                    add("contract", JsonObject().apply { add("m_Team", JsonObject().apply { addProperty("\$ref", "team-a") }) })
                })
            })
        }
        assertTrue(moveDriverToTeam(data, "driver-1", "team-b"))
        val driver = collectObjectsWithId(data).first { it.second.get("\$id").asString == "driver-1" }.second
        assertEquals("team-b", driver.getAsJsonObject("contract").getAsJsonObject("m_Team").get("\$ref").asString)
        val oldRoster = collectObjectsWithId(data).first { it.second.get("\$id").asString == "team-a" }.second.getAsJsonArray("m_Drivers")
        val newRoster = collectObjectsWithId(data).first { it.second.get("\$id").asString == "team-b" }.second.getAsJsonArray("m_Drivers")
        assertEquals(0, oldRoster.size())
        assertEquals("driver-1", newRoster[0].asJsonObject.get("\$ref").asString)
    }

    @Test
    fun teamSwapExchangesChampionshipReferences() {
        val data = JsonObject().apply {
            add("teamManager", JsonObject().apply {
                add("m_Teams", JsonArray().apply { add(JsonObject().apply { addProperty("\$ref", "team-a") }); add(JsonObject().apply { addProperty("\$ref", "team-b") }) })
            })
            add("entities", JsonArray().apply {
                add(JsonObject().apply { addProperty("\$id", "team-a"); addProperty("name", "Alpha") })
                add(JsonObject().apply { addProperty("\$id", "team-b"); addProperty("name", "Beta") })
            })
            add("championshipManager", JsonObject().apply {
                add("divisions", JsonArray().apply {
                    add(JsonObject().apply { addProperty("name", "F1"); add("m_Teams", JsonArray().apply { add(JsonObject().apply { addProperty("\$ref", "team-a") }) }) })
                    add(JsonObject().apply { addProperty("name", "F2"); add("m_Teams", JsonArray().apply { add(JsonObject().apply { addProperty("\$ref", "team-b") }) }) })
                })
            })
        }
        assertTrue(swapTeamsBetweenDivisions(data, "team-a", "team-b"))
        val divisions = data.getAsJsonObject("championshipManager").getAsJsonArray("divisions")
        assertEquals("team-b", divisions[0].asJsonObject.getAsJsonArray("m_Teams")[0].asJsonObject.get("\$ref").asString)
        assertEquals("team-a", divisions[1].asJsonObject.getAsJsonArray("m_Teams")[0].asJsonObject.get("\$ref").asString)
    }

    @Test
    fun profileDisplayHidesGeneratedTweetHandlesAndFormatsBirthDate() {
        assertEquals("Belum diatur", driverProfileDisplayValue("tweetHandle", JsonPrimitive("MMM_TweetHandles_10000001")))
        assertEquals("30 Jul 1993", driverProfileDisplayValue("dateOfBirth", JsonPrimitive("1993-07-30T00:00:00.0000000")))
    }

    @Test
    fun displayedAttributeValuesAreRoundedToIntegers() {
        assertEquals("14", displayIntegerStat(JsonPrimitive(13.7)))
        assertEquals("13", displayIntegerStat(JsonPrimitive(13.2)))
    }

}
