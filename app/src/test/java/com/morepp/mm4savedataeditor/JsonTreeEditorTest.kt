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
}
