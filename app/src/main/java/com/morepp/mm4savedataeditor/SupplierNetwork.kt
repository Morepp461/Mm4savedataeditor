package com.morepp.mm4savedataeditor

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal data class SupplierNetworkConnection(
    val nodeId: Int,
    val progress: Int?,
    val target: Int?
)

internal data class SupplierNetworkChild(
    val label: String,
    val kind: String,
    val owned: Boolean,
    val revealed: Boolean
)

internal data class SupplierNetworkNode(
    val key: Int,
    val id: Int,
    val label: String,
    val owned: Boolean,
    val revealed: Boolean,
    val childCount: Int,
    val path: List<String>,
    val children: List<SupplierNetworkChild>,
    val connections: List<SupplierNetworkConnection>
)

/**
 * Builds a safe, display-ready view of the supplier graph.
 * Reference metadata and localization keys are deliberately never exposed to the UI.
 */
internal fun supplierNetworkNodes(data: JsonObject): List<SupplierNetworkNode> {
    val manager = data.get("supplierNetworkManager")?.takeIf { it.isJsonObject }?.asJsonObject
        ?: return emptyList()
    val dictionary = manager.get("m_NodeDictionary")?.takeIf { it.isJsonArray }?.asJsonArray
        ?: return emptyList()

    val idPaths = mutableMapOf<String, List<String>>()
    fun indexEntities(node: JsonElement, path: List<String>) {
        when {
            node.isJsonObject -> {
                val obj = node.asJsonObject
                obj.get("\$id")?.takeIf { it.isJsonPrimitive }?.asString?.let { idPaths[it] = path }
                obj.entrySet().forEach { (key, value) -> indexEntities(value, path + key) }
            }
            node.isJsonArray -> node.asJsonArray.forEachIndexed { index, value ->
                indexEntities(value, path + "[$index]")
            }
        }
    }
    indexEntities(data, emptyList())

    fun atPath(path: List<String>): JsonElement? {
        var current: JsonElement = data
        for (part in path) {
            current = when {
                current.isJsonObject -> current.asJsonObject.get(part) ?: return null
                current.isJsonArray && part.startsWith("[") -> {
                    val index = part.removePrefix("[").removeSuffix("]").toIntOrNull() ?: return null
                    if (index !in 0 until current.asJsonArray.size()) return null
                    current.asJsonArray[index]
                }
                else -> return null
            }
        }
        return current
    }

    fun resolve(value: JsonElement, fallbackPath: List<String>): Pair<JsonObject, List<String>>? {
        if (!value.isJsonObject) return null
        val obj = value.asJsonObject
        val ref = obj.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString
        val path = if (ref != null) idPaths[ref] ?: return null else fallbackPath
        val resolved = atPath(path)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return resolved to path
    }

    fun kindOf(obj: JsonObject): String = when (obj.get("\$type")?.asString) {
        "SupplierNetworkSupplier" -> "Supplier"
        "SupplierNetworkManufacturer" -> "Manufacturer"
        "SupplierNetworkCompany" -> "Supplier company"
        else -> "Network item"
    }

    fun displayLabel(obj: JsonObject, fallback: Int): String {
        val id = obj.get("m_ID")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
            ?: fallback
        return kindOf(obj) + " " + (id + 1).coerceAtLeast(1).toString().padStart(2, '0')
    }

    return dictionary.mapIndexedNotNull { index, entry ->
        if (!entry.isJsonObject) return@mapIndexedNotNull null
        val row = entry.asJsonObject
        val key = row.get("Key")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
            ?: index
        val value = row.get("Value") ?: return@mapIndexedNotNull null
        val (node, path) = resolve(value, listOf("supplierNetworkManager", "m_NodeDictionary", "[$index]", "Value"))
            ?: return@mapIndexedNotNull null
        if (kindOf(node) == "Network item") return@mapIndexedNotNull null

        val children = node.get("m_Nodes")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapIndexedNotNull { childIndex, child ->
                val childPath = path + "m_Nodes" + "[$childIndex]"
                val resolvedChild = resolve(child, childPath)?.first ?: return@mapIndexedNotNull null
                SupplierNetworkChild(
                    label = displayLabel(resolvedChild, childIndex),
                    kind = kindOf(resolvedChild),
                    owned = resolvedChild.get("m_IsOwned")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                    revealed = resolvedChild.get("m_HasOwnedBeenRevealed")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                )
            }.orEmpty()

        val connections = node.get("m_NodeConnections")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { connection ->
                if (!connection.isJsonObject) return@mapNotNull null
                val item = connection.asJsonObject
                val targetId = item.get("nodeID")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                    ?: return@mapNotNull null
                SupplierNetworkConnection(
                    nodeId = targetId,
                    progress = item.get("progress")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt,
                    target = item.get("progressTarget")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                )
            }.orEmpty()

        val id = node.get("m_ID")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: key
        SupplierNetworkNode(
            key = key,
            id = id,
            label = displayLabel(node, key),
            owned = node.get("m_IsOwned")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            revealed = node.get("m_HasOwnedBeenRevealed")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            childCount = children.size,
            path = path,
            children = children,
            connections = connections
        )
    }.sortedBy { it.key }
}

internal fun supplierNetworkCompanyCount(data: JsonObject): Int =
    data.getAsJsonObject("supplierNetworkManager")?.get("m_Companies")
        ?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0

internal fun supplierNetworkManufacturerCount(data: JsonObject): Int =
    data.getAsJsonObject("supplierNetworkManager")?.get("m_Manufacturers")
        ?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0
