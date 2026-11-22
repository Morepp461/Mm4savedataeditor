package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierNetworkUnlockTest {
    private fun ref(id: String) = JsonObject().apply { addProperty("\$ref", id) }

    private fun node(id: String, owned: Boolean, revealed: Boolean, children: List<String> = emptyList()) =
        JsonObject().apply {
            addProperty("\$id", id)
            addProperty("m_IsOwned", owned)
            addProperty("m_HasOwnedBeenRevealed", revealed)
            add("m_Nodes", JsonArray().apply { children.forEach { add(ref(it)) } })
        }

    private fun mapEntries(vararg values: Int) = JsonArray().apply {
        values.forEachIndexed { index, value ->
            add(JsonObject().apply {
                addProperty("Key", index)
                addProperty("Value", value)
            })
        }
    }

    private fun sample() = JsonObject().apply {
        val supplier = JsonObject().apply {
            add("m_NodeDictionary", JsonArray().apply {
                repeat(3) { index ->
                    add(JsonObject().apply {
                        addProperty("Key", index)
                        add("Value", ref("node-$index"))
                    })
                }
            })
            add("m_SupplierUnlocked", mapEntries(1, 0, 0))
            add("m_SupplierAvailable", mapEntries(1, 0, 0))
        }
        add("supplierNetworkManager", supplier)
        add("nodeObjects", JsonArray().apply {
            add(node("node-0", owned = true, revealed = true, children = listOf("node-1")))
            add(node("node-1", owned = false, revealed = true, children = listOf("node-2")))
            add(node("node-2", owned = true, revealed = false))
        })
    }

    @Test
    fun readsKeyValueDictionaryAndUnlocksEveryReferencedNode() {
        val data = sample()
        val recognized = unlockSupplierNetwork(data)
        assertEquals(3, recognized)
        data.getAsJsonArray("nodeObjects").forEach { element ->
            val value = element.asJsonObject
            assertTrue(value.get("m_IsOwned").asBoolean)
            assertTrue(value.get("m_HasOwnedBeenRevealed").asBoolean)
        }
    }

    @Test
    fun unlocksAllSupplierCategories() {
        val data = sample()
        unlockSupplierNetwork(data)
        val supplier = data.getAsJsonObject("supplierNetworkManager")
        listOf("m_SupplierUnlocked", "m_SupplierAvailable").forEach { field ->
            supplier.getAsJsonArray(field).forEach { item ->
                assertEquals(1, item.asJsonObject.get("Value").asInt)
            }
        }
    }

    @Test
    fun alreadyUnlockedNetworkIsIdempotentlySuccessful() {
        val data = sample()
        assertEquals(3, unlockSupplierNetwork(data))
        assertEquals(3, unlockSupplierNetwork(data))
    }

    @Test
    fun emptyDictionaryIsNotTreatedAsUnlocked() {
        val data = JsonObject().apply {
            add("supplierNetworkManager", JsonObject().apply {
                add("m_NodeDictionary", JsonArray())
            })
        }
        assertEquals(0, unlockSupplierNetwork(data))
    }

    @Test
    fun ignoresUnrelatedObjectsOutsideSupplierGraph() {
        val data = sample()
        val unrelated = node("unrelated", owned = false, revealed = false)
        data.add("unrelatedManager", unrelated)
        unlockSupplierNetwork(data)
        assertFalse(unrelated.get("m_IsOwned").asBoolean)
        assertFalse(unrelated.get("m_HasOwnedBeenRevealed").asBoolean)
    }
}
