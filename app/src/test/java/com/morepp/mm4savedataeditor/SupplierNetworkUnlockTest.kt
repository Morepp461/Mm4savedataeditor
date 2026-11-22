package com.morepp.mm4savedataeditor

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierNetworkUnlockTest {
    private fun sample(owned: Boolean, revealed: Boolean) = JsonParser.parseString(
        """
        {
          "supplierNetworkManager": {
            "m_NodeDictionary": [
              {"Key": 0, "Value": {"\$ref": "node-1"}},
              {"Key": 1, "Value": {"\$ref": "node-2"}}
            ],
            "m_SupplierUnlocked": [
              {"Key": 0, "Value": 1},
              {"Key": 1, "Value": 0},
              {"Key": 2, "Value": 0}
            ],
            "m_SupplierAvailable": [
              {"Key": 0, "Value": 1},
              {"Key": 1, "Value": 0},
              {"Key": 2, "Value": 0}
            ]
          },
          "supplierObjects": [
            {
              "\$id": "node-1",
              "m_ID": 1,
              "m_IsOwned": $owned,
              "m_HasOwnedBeenRevealed": $revealed,
              "m_Nodes": [{"\$ref": "node-2"}]
            },
            {
              "\$id": "node-2",
              "m_ID": 2,
              "m_IsOwned": false,
              "m_HasOwnedBeenRevealed": false,
              "m_Nodes": []
            }
          ],
          "unrelated": {"m_IsOwned": false, "m_HasOwnedBeenRevealed": false}
        }
        """.trimIndent()
    ).asJsonObject

    @Test
    fun resolvesKeyValueArrayAndNestedReferences() {
        val data = sample(owned = false, revealed = true)

        val count = unlockSupplierNetwork(data)

        val nodes = data.getAsJsonArray("supplierObjects")
        assertEquals(2, count)
        for (i in 0 until nodes.size()) {
            val node = nodes[i].asJsonObject
            assertTrue(node.get("m_IsOwned").asBoolean)
            assertTrue(node.get("m_HasOwnedBeenRevealed").asBoolean)
        }
        val unrelated = data.getAsJsonObject("unrelated")
        assertTrue(!unrelated.get("m_IsOwned").asBoolean)
        assertTrue(!unrelated.get("m_HasOwnedBeenRevealed").asBoolean)
    }

    @Test
    fun unlocksEverySupplierCategory() {
        val data = sample(owned = true, revealed = true)

        unlockSupplierNetwork(data)

        val manager = data.getAsJsonObject("supplierNetworkManager")
        for (field in listOf("m_SupplierUnlocked", "m_SupplierAvailable")) {
            val values = manager.getAsJsonArray(field)
            assertEquals(3, values.size())
            for (entry in values) assertEquals(1, entry.asJsonObject.get("Value").asInt)
        }
    }

    @Test
    fun alreadyUnlockedNetworkIsIdempotentlySuccessful() {
        val data = sample(owned = true, revealed = true)

        assertEquals(2, unlockSupplierNetwork(data))
        assertEquals(2, unlockSupplierNetwork(data))
    }

    @Test
    fun emptyDictionaryIsNotTreatedAsUnlocked() {
        val data = JsonParser.parseString(
            """{"supplierNetworkManager":{"m_NodeDictionary":[]}}"""
        ).asJsonObject

        assertEquals(0, unlockSupplierNetwork(data))
    }
}
