package com.morepp.mm4savedataeditor

import com.google.gson.JsonObject

internal data class CareerFinanceSummary(
    val balance: Long,
    val transactionCount: Int,
    val incomingTotal: Long,
    val outgoingTotal: Long,
    val unchangedCount: Int,
    val perRaceCostCount: Int,
    val perRaceCostTotal: Long
)

internal fun careerFinanceSummary(data: JsonObject): CareerFinanceSummary? {
    val finance = data.getAsJsonObject("financeManager") ?: return null
    val balance = finance.get("m_Balance")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
        ?: return null
    val history = finance.get("m_TransactionHistory")?.takeIf { it.isJsonArray }?.asJsonArray
    val values = history?.mapNotNull { it.takeIf { value -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber }?.asLong }.orEmpty()
    val costs = finance.get("m_PerRaceCosts")?.takeIf { it.isJsonArray }?.asJsonArray
    val costValues = costs?.mapNotNull { row ->
        if (!row.isJsonObject) return@mapNotNull null
        row.asJsonObject.get("Value")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
    }.orEmpty()
    return CareerFinanceSummary(
        balance = balance,
        transactionCount = values.size,
        incomingTotal = values.filter { it > 0 }.sum(),
        outgoingTotal = values.filter { it < 0 }.sumOf { -it },
        unchangedCount = values.count { it == 0L },
        perRaceCostCount = costValues.size,
        perRaceCostTotal = costValues.sumOf { -it }
    )
}
