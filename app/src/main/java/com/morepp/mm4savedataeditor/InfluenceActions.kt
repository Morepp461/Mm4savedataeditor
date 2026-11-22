package com.morepp.mm4savedataeditor

import com.google.gson.JsonObject

internal data class InfluenceActionRecord(
    val index: Int,
    val label: String,
    val cooldown: Int,
    val status: String
)

private val influenceActionLabels = mapOf(
    0 to "Mencari pembalap",
    1 to "Mengisi posisi insinyur",
    2 to "Mengisi posisi mekanik",
    3 to "Bonus produksi komponen",
    4 to "Memotivasi pembalap",
    5 to "Tur media pembalap",
    6 to "Mengakhiri kontrak sponsor",
    7 to "Meningkatkan kekuatan voting",
    8 to "Memperpanjang perlindungan kebangkrutan",
    9 to "Mempercepat jaringan supplier",
    10 to "Melihat perkembangan pembalap",
    11 to "Mengisi posisi strategist"
)

internal fun influenceActionRecords(data: JsonObject): List<InfluenceActionRecord> {
    val actions = data.getAsJsonObject("influenceManager")?.get("m_Influences")
        ?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
    return actions.mapIndexedNotNull { position, element ->
        if (!element.isJsonObject) return@mapIndexedNotNull null
        val item = element.asJsonObject
        val index = item.get("m_InfluenceActionDatabaseIndex")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: position
        val cooldown = item.get("m_CurrentCooldown")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: -1
        val status = when {
            cooldown < 0 -> "Tanpa cooldown"
            cooldown == 0 -> "Siap digunakan"
            else -> "Cooldown $cooldown event"
        }
        InfluenceActionRecord(
            index = index,
            label = influenceActionLabels[index] ?: "Aksi influence ${index + 1}",
            cooldown = cooldown,
            status = status
        )
    }.sortedBy { it.index }
}
