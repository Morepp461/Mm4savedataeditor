package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTreeEditorTest {
    @Test
    fun editsNestedObjectAndArrayValues() {
        val root = JsonObject().apply {
            add("career", JsonObject().apply { addProperty("season", 2) })
            add("names", JsonArray().apply { add("Alpha"); add("Beta") })
        }

        assertTrue(setPath(root, listOf("career", "season"), JsonPrimitive(3)))
        assertTrue(appendChild(root, listOf("names"), null, JsonPrimitive("Gamma")))
        assertTrue(removePath(root, listOf("names", "[0]")))

        assertEquals(3, root.getAsJsonObject("career").get("season").asInt)
        assertEquals(2, root.getAsJsonArray("names").size())
        assertEquals("Beta", root.getAsJsonArray("names")[0].asString)
        assertEquals("Gamma", root.getAsJsonArray("names")[1].asString)
    }

    @Test
    fun objectAppendRejectsDuplicateProperty() {
        val root = JsonObject().apply { addProperty("balance", 100) }

        assertFalse(appendChild(root, emptyList(), "balance", JsonPrimitive(200)))
        assertEquals(100, root.get("balance").asInt)
    }

    @Test
    fun flattenIncludesContainersAndNestedLeaves() {
        val root = JsonObject().apply {
            add("team", JsonObject().apply { addProperty("name", "Apex") })
        }

        val paths = flatten(root).map { it.first.joinToString("/") }

        assertTrue(paths.contains("team"))
        assertTrue(paths.contains("team/name"))
    }

    @Test
    fun mutateDocumentCopiesTheEditedRoot() {
        val original = Mm4SaveCodec.Document(
            JsonObject().apply { addProperty("career", "original") },
            JsonObject().apply { addProperty("balance", 100) },
            "copy-test.msv"
        )

        val edited = mutateDocument(original, "Game Data") {
            setPath(it, listOf("balance"), JsonPrimitive(250))
        }

        assertNotNull(edited)
        assertEquals(100, original.data.get("balance").asInt)
        assertEquals(250, edited!!.data.get("balance").asInt)
        assertEquals("original", original.info.get("career").asString)
    }
    @Test
    fun invalidArrayIndicesAndPrimitiveParentsAreRejected() {
        val root = JsonObject().apply {
            add("drivers", JsonArray().apply { add(JsonObject().apply { addProperty("rating", 75) }) })
            addProperty("teamName", "Caleris")
        }

        assertFalse(setPath(root, listOf("drivers", "[4]", "rating"), JsonPrimitive(90)))
        assertFalse(removePath(root, listOf("drivers", "[x]")))
        assertFalse(appendChild(root, listOf("teamName"), "extra", JsonPrimitive(true)))
        assertNull(resolvePath(root, listOf("drivers", "[x]")))
        assertEquals(75, root.getAsJsonArray("drivers")[0].asJsonObject.get("rating").asInt)
    }

    @Test
    fun objectAppendRejectsBlankAndDuplicateKeysWithoutChangingTree() {
        val root = JsonObject().apply { addProperty("existing", 1) }

        assertFalse(appendChild(root, emptyList(), " ", JsonPrimitive(2)))
        assertFalse(appendChild(root, emptyList(), "existing", JsonPrimitive(2)))
        assertEquals(1, root.size())
        assertEquals(1, root.get("existing").asInt)
    }

}
