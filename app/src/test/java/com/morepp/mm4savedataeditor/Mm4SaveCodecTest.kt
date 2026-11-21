package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Mm4SaveCodecTest {
    private fun sample(): Mm4SaveCodec.Document {
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
        return Mm4SaveCodec.Document(info, data, "unit-test.msv")
    }

    @Test
    fun encodeDecodePreservesBothJsonTrees() {
        val original = sample()
        val encoded = Mm4SaveCodec.encode(original)
        val decoded = Mm4SaveCodec.decode(encoded, original.sourceName)

        assertEquals(original.info, decoded.info)
        assertEquals(original.data, decoded.data)
        assertEquals(true, Mm4SaveCodec.validate(original))
    }

    @Test
    fun decoderRejectsUnknownMagic() {
        val bytes = Mm4SaveCodec.encode(sample())
        bytes[0] = (bytes[0].toInt() xor 0x7F).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            Mm4SaveCodec.decode(bytes)
        }
    }

    @Test
    fun decoderRejectsUnknownContainerVersion() {
        val bytes = Mm4SaveCodec.encode(sample())
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 41)

        assertThrows(IllegalArgumentException::class.java) {
            Mm4SaveCodec.decode(bytes)
        }
    }

    @Test
    fun decoderRejectsTruncatedPayload() {
        val bytes = Mm4SaveCodec.encode(sample())
        val truncated = bytes.copyOf(bytes.size - 1)

        assertThrows(IllegalArgumentException::class.java) {
            Mm4SaveCodec.decode(truncated)
        }
    }

    @Test
    fun decoderRejectsHeaderLengthMismatch() {
        val bytes = Mm4SaveCodec.encode(sample())
        val originalLength = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(8)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, originalLength + 1)

        assertThrows(IllegalArgumentException::class.java) {
            Mm4SaveCodec.decode(bytes)
        }
    }

    @Test
    fun decoderRejectsFilesShorterThanHeader() {
        assertThrows(IllegalArgumentException::class.java) {
            Mm4SaveCodec.decode(ByteArray(23))
        }
    }
}
