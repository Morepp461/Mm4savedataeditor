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
            "m_NodeDictionary": {
              "node-1": { "\$ref": "node-object-1" }
            },
            "m_StartNode": { "\$ref": "node-object-1" },
            "m_TargetNodes": []
          },
          "nodeObjects": [
            {
              "\$id": "node-object-1",
              "m_IsOwned": $owned,
              "m_HasOwnedBeenRevealed": $revealed
            }
          ]
        }
        """.trimIndent()
    ).asJsonObject

    @Test
    fun unlocksOwnershipAndRevealIndependently() {
        val data = sample(owned = false, revealed = true)

        val recognized = unlockSupplierNetwork(data)

        val node = data.getAsJsonArray("nodeObjects")[0].asJsonObject
        assertEquals(1, recognized)
        assertTrue(node.get("m_IsOwned").asBoolean)
        assertTrue(node.get("m_HasOwnedBeenRevealed").asBoolean)
    }

    @Test
    fun revealOnlyChangeIsRecognizedAsSuccessfulNode() {
        val data = sample(owned = true, revealed = false)

        val recognized = unlockSupplierNetwork(data)

        val node = data.getAsJsonArray("nodeObjects")[0].asJsonObject
        assertEquals(1, recognized)
        assertTrue(node.get("m_IsOwned").asBoolean)
        assertTrue(node.get("m_HasOwnedBeenRevealed").asBoolean)
    }

    @Test
    fun alreadyUnlockedNetworkIsIdempotentlySuccessful() {
        val data = sample(owned = true, revealed = true)

        val first = unlockSupplierNetwork(data)
        val second = unlockSupplierNetwork(data)

        assertEquals(1, first)
        assertEquals(1, second)
    }

    @Test
    fun emptyDictionaryIsNotTreatedAsUnlocked() {
        val data = JsonParser.parseString(
            """{"supplierNetworkManager":{"m_NodeDictionary":{}}}"""
        ).asJsonObject

        assertEquals(0, unlockSupplierNetwork(data))
    }
}
