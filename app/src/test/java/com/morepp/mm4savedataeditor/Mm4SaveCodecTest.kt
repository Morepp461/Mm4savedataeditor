package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Mm4SaveCodecTest {
    @Test
    fun encodeDecodePreservesBothJsonTrees() {
        val info = JsonObject().apply {
            addProperty("playerName", "Test Driver")
            addProperty("season", 4)
            add("careerFlags", JsonArray().apply {
                add(true)
                add(false)
            })
        }
        val data = JsonObject().apply {
            add("teams", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("id", 101)
                    addProperty("balance", 987654.25)
                })
            })
            add("unknownFutureField", JsonObject().apply {
                addProperty("opaque", "preserve-me")
            })
        }
        val original = Mm4SaveCodec.Document(info, data, "unit-test.msv")

        val encoded = Mm4SaveCodec.encode(original)
        val decoded = Mm4SaveCodec.decode(encoded, original.sourceName)

        assertEquals(info, decoded.info)
        assertEquals(data, decoded.data)
        assertEquals(true, Mm4SaveCodec.validate(original))
    }

    @Test
    fun decoderRejectsUnknownMagic() {
        val info = JsonObject().apply { addProperty("x", 1) }
        val data = JsonObject().apply { addProperty("y", 2) }
        val bytes = Mm4SaveCodec.encode(Mm4SaveCodec.Document(info, data, "bad.msv"))
        bytes[0] = (bytes[0].toInt() xor 0x7F).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            Mm4SaveCodec.decode(bytes)
        }
    }

    @Test
    fun decoderRejectsTruncatedPayload() {
        val info = JsonObject().apply { addProperty("x", 1) }
        val data = JsonObject().apply { addProperty("y", 2) }
        val bytes = Mm4SaveCodec.encode(Mm4SaveCodec.Document(info, data, "short.msv"))
        val truncated = bytes.copyOf(bytes.size - 1)

        assertThrows(IllegalArgumentException::class.java) {
            Mm4SaveCodec.decode(truncated)
        }
    }
}
