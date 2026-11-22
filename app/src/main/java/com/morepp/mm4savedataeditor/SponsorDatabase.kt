package com.morepp.mm4savedataeditor

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal data class SponsorRecord(
    val recordKey: String,
    val dataId: Int,
    val runningContracts: Int,
    val initialCooldownExpired: Boolean,
    val randomOfferOrder: Int
)

internal fun sponsorRecords(data: JsonObject): List<SponsorRecord> {
    val idMap = mutableMapOf<String, JsonObject>()
    fun collect(node: JsonElement) {
        when {
            node.isJsonObject -> {
                val obj = node.asJsonObject
                obj.get("\$id")?.takeIf { it.isJsonPrimitive }?.asString?.let { idMap[it] = obj }
                obj.entrySet().forEach { (_, value) -> collect(value) }
            }
            node.isJsonArray -> node.asJsonArray.forEach { collect(it) }
        }
    }
    collect(data)
    fun resolve(value: JsonElement): JsonObject? {
        if (!value.isJsonObject) return null
        val obj = value.asJsonObject
        val ref = obj.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString
        return if (ref != null) idMap[ref] else obj
    }
    val manager = data.getAsJsonObject("sponsorManager") ?: return emptyList()
    val sponsorContent = manager.getAsJsonObject("m_Sponsors")?.get("\$content")
        ?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
    val randomized = resolve(manager.get("m_RandomisedSponsors") ?: return emptyList())
        ?.get("\$content")?.takeIf { it.isJsonArray }?.asJsonArray
    val orderById = randomized?.mapIndexedNotNull { index, item ->
        val ref = item.takeIf { it.isJsonObject }?.asJsonObject?.get("\$ref")?.asString
        if (ref == null) null else ref to index + 1
    }?.toMap().orEmpty()

    return sponsorContent.mapIndexedNotNull { index, reference ->
        val sponsor = resolve(reference) ?: return@mapIndexedNotNull null
        val dataId = sponsor.get("m_SponsorDataID")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: index
        val key = sponsor.get("\$id")?.takeIf { it.isJsonPrimitive }?.asString ?: index.toString()
        SponsorRecord(
            recordKey = key,
            dataId = dataId,
            runningContracts = sponsor.get("currentRunningSponsorships")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
            initialCooldownExpired = sponsor.get("m_InitialCooldownExpired")
                ?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            randomOfferOrder = orderById[key] ?: index + 1
        )
    }.sortedBy { it.dataId }
}
