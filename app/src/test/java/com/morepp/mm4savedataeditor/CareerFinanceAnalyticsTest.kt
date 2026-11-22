package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareerFinanceAnalyticsTest {
    @Test fun summarizesOnlyNumericHistoryAndKeepsSignsMeaningful() {
        val data = JsonObject().apply {
            add("financeManager", JsonObject().apply {
                addProperty("m_Balance", 2500)
                add("m_TransactionHistory", JsonArray().apply { add(-500); add(1200); add(0); add(-200) })
                add("m_PerRaceCosts", JsonArray().apply {
                    add(JsonObject().apply { addProperty("Key", 0); addProperty("Value", -500) })
                    add(JsonObject().apply { addProperty("Key", 1); addProperty("Value", -200) })
                })
            })
        }
        val summary = careerFinanceSummary(data)!!
        assertEquals(2500L, summary.balance)
        assertEquals(4, summary.transactionCount)
        assertEquals(1200L, summary.incomingTotal)
        assertEquals(700L, summary.outgoingTotal)
        assertEquals(1, summary.unchangedCount)
        assertEquals(2, summary.perRaceCostCount)
        assertEquals(700L, summary.perRaceCostTotal)
    }

    @Test fun missingFinanceManagerDoesNotInventSummary() {
        assertNull(careerFinanceSummary(JsonObject()))
    }
}
