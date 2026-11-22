package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierNetworkTest {
    private fun fixture(): JsonObject {
        val data = JsonObject()
        val manager = JsonObject()
        val dictionary = JsonArray()
        dictionary.add(JsonObject().apply {
            addProperty("Key", 0)
            add("Value", JsonObject().apply { addProperty("\$ref", "10") })
        })
        dictionary.add(JsonObject().apply {
            addProperty("Key", 1)
            add("Value", JsonObject().apply { addProperty("\$ref", "11") })
        })
        dictionary.add(JsonObject().apply {
            addProperty("Key", 2)
            add("Value", JsonObject().apply { addProperty("\$ref", "12") })
        })
        manager.add("m_NodeDictionary", dictionary)
        manager.add("m_Companies", JsonArray().apply {
            add(JsonObject().apply { addProperty("\$ref", "12") })
        })
        manager.add("m_Manufacturers", JsonArray().apply {
            add(JsonObject().apply { addProperty("\$ref", "11") })
        })
        data.add("supplierNetworkManager", manager)

        val supplier = JsonObject().apply {
            addProperty("\$id", "10")
            addProperty("\$type", "SupplierNetworkSupplier")
            addProperty("m_ID", 0)
            addProperty("m_IsOwned", true)
            addProperty("m_HasOwnedBeenRevealed", false)
            add("m_Nodes", JsonArray().apply {
                add(JsonObject().apply { addProperty("\$ref", "11") })
            })
            add("m_NodeConnections", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("nodeID", 4)
                    addProperty("progress", 25)
                    addProperty("progressTarget", 50)
                })
            })
        }
        val manufacturer = JsonObject().apply {
            addProperty("\$id", "11")
            addProperty("\$type", "SupplierNetworkManufacturer")
            addProperty("m_ID", 3)
            addProperty("m_IsOwned", false)
            addProperty("m_HasOwnedBeenRevealed", true)
            add("m_Nodes", JsonArray())
        }
        val company = JsonObject().apply {
            addProperty("\$id", "12")
            addProperty("\$type", "SupplierNetworkCompany")
            addProperty("m_ID", 8)
        }
        data.add("entityManager", JsonObject().apply {
            add("m_Entities", JsonArray().apply {
                add(supplier)
                add(manufacturer)
                add(company)
            })
        })
        return data
    }

    @Test
    fun resolvesReferencesAndBuildsFriendlyDisplayData() {
        val data = fixture()
        val nodes = supplierNetworkNodes(data)
        assertEquals(3, nodes.size)
        val node = nodes.first()
        assertEquals("Supplier 01", node.label)
        assertEquals("Manufacturer 04", nodes[1].label)
        assertEquals("Supplier company 09", nodes[2].label)
        assertTrue(node.owned)
        assertFalse(node.revealed)
        assertEquals(1, node.childCount)
        assertEquals("Manufacturer 04", node.children.single().label)
        assertTrue(node.children.single().revealed)
        assertEquals(4, node.connections.single().nodeId)
        assertEquals(25, node.connections.single().progress)
        assertEquals(50, node.connections.single().target)
        assertEquals(1, supplierNetworkCompanyCount(data))
        assertEquals(1, supplierNetworkManufacturerCount(data))
    }

    @Test
    fun readingNetworkDoesNotMutateSaveData() {
        val data = fixture()
        val before = data.toString()
        supplierNetworkNodes(data)
        assertEquals(before, data.toString())
    }

    @Test
    fun malformedOrUnresolvedEntriesAreSkippedWithoutCrashing() {
        val data = fixture()
        data.getAsJsonObject("supplierNetworkManager").getAsJsonArray("m_NodeDictionary")
            .add(JsonObject().apply {
                addProperty("Key", 1)
                add("Value", JsonObject().apply { addProperty("\$ref", "missing") })
            })
        assertEquals(3, supplierNetworkNodes(data).size)
    }
    @Test
    fun supportsTheFullEightyNodeNetworkAndResolvesEveryConnection() {
        val data = JsonObject()
        val manager = JsonObject()
        val dictionary = JsonArray()
        val entities = JsonArray()
        repeat(80) { index ->
            val id = (1000 + index).toString()
            dictionary.add(JsonObject().apply {
                addProperty("Key", index)
                add("Value", JsonObject().apply { addProperty("\\$ref", id) })
            })
            entities.add(JsonObject().apply {
                addProperty("\\$id", id)
                addProperty("\\$type", when (index % 3) {
                    0 -> "SupplierNetworkSupplier"
                    1 -> "SupplierNetworkManufacturer"
                    else -> "SupplierNetworkCompany"
                })
                addProperty("m_ID", index)
                addProperty("m_IsOwned", index % 2 == 0)
                addProperty("m_HasOwnedBeenRevealed", true)
                add("m_Nodes", JsonArray())
                add("m_NodeConnections", JsonArray().apply {
                    if (index < 79) add(JsonObject().apply {
                        addProperty("nodeID", index + 1)
                        addProperty("progress", 1)
                        addProperty("progressTarget", 2)
                    })
                })
            })
        }
        manager.add("m_NodeDictionary", dictionary)
        data.add("supplierNetworkManager", manager)
        data.add("entityManager", JsonObject().apply { add("m_Entities", entities) })

        val nodes = supplierNetworkNodes(data)
        assertEquals(80, nodes.size)
        assertEquals(79, nodes.sumOf { it.connections.size })
        assertTrue(nodes.all { !it.label.contains("\\$") })
        assertTrue(nodes.flatMap { it.connections }.all { connection -> nodes.any { it.id == connection.nodeId } })
    }

    @Test
    fun ownershipEditRoundTripsWithoutChangingOtherSupplierData() {
        val original = fixture()
        val node = supplierNetworkNodes(original).first()
        val originalOwnership = resolvePath(original, node.path + "m_IsOwned")!!.deepCopy()
        val edited = original.deepCopy().asJsonObject
        assertTrue(setPath(edited, node.path + "m_IsOwned", JsonPrimitive(false)))

        val encoded = Mm4SaveCodec.encode(Mm4SaveCodec.Document(JsonObject(), edited, "supplier-test.msv"))
        val decoded = Mm4SaveCodec.decode(encoded)
        assertFalse(resolvePath(decoded.data, node.path + "m_IsOwned")!!.asBoolean)

        val restored = decoded.data.deepCopy().asJsonObject
        assertTrue(setPath(restored, node.path + "m_IsOwned", originalOwnership))
        assertEquals(original, restored)
    }

}
