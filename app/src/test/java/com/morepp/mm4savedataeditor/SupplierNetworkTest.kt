package com.morepp.mm4savedataeditor

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierNetworkTest {
    private fun fixture(): JsonObject = JsonParser.parseString(
        """
        {
          "supplierNetworkManager": {
            "m_NodeDictionary": [
              {"Key": 0, "Value": {"\$ref": "10"}}
            ],
            "m_Companies": [{"\$ref": "12"}],
            "m_Manufacturers": [{"\$ref": "11"}]
          },
          "entityManager": {
            "m_Entities": [
              {
                "\$id": "10",
                "\$type": "SupplierNetworkSupplier",
                "m_ID": 0,
                "m_IsOwned": true,
                "m_HasOwnedBeenRevealed": false,
                "m_Nodes": [{"\$ref": "11"}],
                "m_NodeConnections": [
                  {"nodeID": 4, "progress": 25, "progressTarget": 50}
                ]
              },
              {
                "\$id": "11",
                "\$type": "SupplierNetworkManufacturer",
                "m_ID": 3,
                "m_IsOwned": false,
                "m_HasOwnedBeenRevealed": true,
                "m_Nodes": []
              },
              {
                "\$id": "12",
                "\$type": "SupplierNetworkCompany",
                "m_ID": 8
              }
            ]
          }
        }
        """.trimIndent()
    ).asJsonObject

    @Test
    fun resolvesReferencesAndBuildsFriendlyDisplayData() {
        val data = fixture()
        val nodes = supplierNetworkNodes(data)
        assertEquals(1, nodes.size)
        val node = nodes.single()
        assertEquals("Supplier 01", node.label)
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
            .add(JsonParser.parseString("""{"Key": 1, "Value": {"\$ref": "missing"}}"""))
        assertEquals(1, supplierNetworkNodes(data).size)
    }
}
