package com.morepp.mm4savedataeditor

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.jpountz.lz4.LZ4Factory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reader/writer for the MM4 save container profile observed in the supplied sample.
 * The format is deliberately version-gated: unknown container versions are rejected.
 */
object Mm4SaveCodec {
    private const val MAGIC = 0x99131871.toInt()
    private const val VERSION = 40
    private const val HEADER_SIZE = 24
    private const val MAX_CONTAINER_BYTES = 64 * 1024 * 1024
    private const val MAX_BLOCK_BYTES = 64 * 1024 * 1024

    private val lz4 = LZ4Factory.fastestInstance()
    private val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()

    data class Document(
        val info: JsonObject,
        val data: JsonObject,
        val sourceName: String,
        internal val originalInfoCompressed: ByteArray? = null,
        internal val originalDataCompressed: ByteArray? = null,
        internal val originalInfoSnapshot: JsonObject? = null,
        internal val originalDataSnapshot: JsonObject? = null
    )

    fun decode(bytes: ByteArray, sourceName: String = "save.msv"): Document {
        require(bytes.size in HEADER_SIZE..MAX_CONTAINER_BYTES) {
            "Ukuran file di luar batas yang didukung."
        }

        val header = ByteBuffer.wrap(bytes, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val magic = header.int
        val version = header.int
        val infoCompressedLength = header.int
        val infoPlainLength = header.int
        val dataCompressedLength = header.int
        val dataPlainLength = header.int

        require(magic == MAGIC) { "Magic header tidak cocok. File bukan save MM4 yang dikenali." }
        require(version == VERSION) { "Versi container tidak didukung: $version." }
        require(
            infoCompressedLength > 0 &&
                dataCompressedLength > 0 &&
                infoPlainLength in 1..MAX_BLOCK_BYTES &&
                dataPlainLength in 1..MAX_BLOCK_BYTES
        ) {
            "Panjang blok pada header tidak valid."
        }
        require(
            HEADER_SIZE.toLong() + infoCompressedLength.toLong() + dataCompressedLength.toLong() ==
                bytes.size.toLong()
        ) {
            "Ukuran blok tidak cocok. File mungkin terpotong atau formatnya berbeda."
        }

        val split = HEADER_SIZE + infoCompressedLength
        val infoBytes = inflate(bytes.copyOfRange(HEADER_SIZE, split), infoPlainLength)
        val dataBytes = inflate(bytes.copyOfRange(split, bytes.size), dataPlainLength)
        val infoJson = JsonParser.parseString(infoBytes.toString(Charsets.UTF_8))
        val dataJson = JsonParser.parseString(dataBytes.toString(Charsets.UTF_8))

        require(infoJson.isJsonObject && dataJson.isJsonObject) {
            "Blok Save Info atau Game Data bukan JSON object."
        }
        return Document(
            info = infoJson.asJsonObject,
            data = dataJson.asJsonObject,
            sourceName = sourceName,
            originalInfoCompressed = bytes.copyOfRange(HEADER_SIZE, split),
            originalDataCompressed = bytes.copyOfRange(split, bytes.size),
            originalInfoSnapshot = infoJson.asJsonObject.deepCopy(),
            originalDataSnapshot = dataJson.asJsonObject.deepCopy()
        )
    }

    fun prepareForExport(document: Document): Document {
        val snapshot=document.originalDataSnapshot ?: return document
        val repaired=document.data.deepCopy().asJsonObject
        val originalEntities=snapshot.get("entityManager")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("m_Entities")?.takeIf { it.isJsonArray }?.asJsonArray ?: return document
        val currentEntities=repaired.get("entityManager")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("m_Entities")?.takeIf { it.isJsonArray }?.asJsonArray ?: return document
        var restored=0
        val count=minOf(originalEntities.size(),currentEntities.size())
        for(index in 0 until count) {
            val originalEntity=originalEntities[index].takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val currentEntity=currentEntities[index].takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val originalContract=originalEntity.get("contract")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val currentContract=currentEntity.get("contract")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val originalTeam=originalContract.get("m_Team")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val currentTeam=currentContract.get("m_Team")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val originalChamp=originalTeam.get("m_Championship")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val currentChamp=currentTeam.get("m_Championship")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val originalId=originalChamp.get("\$id")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            val currentRef=currentChamp.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString
            if(currentChamp.size()==1 && currentRef==originalId) {
                currentTeam.add("m_Championship",originalChamp.deepCopy())
                restored++
            }
        }
        return if(restored>0) document.copy(data=repaired) else document
    }

    fun encode(document: Document): ByteArray {
        val exportDocument = prepareForExport(document)
        val infoPlain = gson.toJson(exportDocument.info).toByteArray(Charsets.UTF_8)
        val dataPlain = gson.toJson(exportDocument.data).toByteArray(Charsets.UTF_8)
        require(infoPlain.size in 1..MAX_BLOCK_BYTES && dataPlain.size in 1..MAX_BLOCK_BYTES) {
            "Data hasil edit melebihi batas ukuran blok."
        }

        // Keep untouched game-generated LZ4 blocks byte-for-byte. This avoids
        // needlessly rewriting opaque save sections when only one JSON tree changed.
        val infoCompressed = if (
            exportDocument.originalInfoCompressed != null &&
            exportDocument.originalInfoSnapshot != null &&
            canonical(exportDocument.info) == canonical(exportDocument.originalInfoSnapshot)
        ) exportDocument.originalInfoCompressed else deflate(infoPlain)
        val dataCompressed = if (
            exportDocument.originalDataCompressed != null &&
            exportDocument.originalDataSnapshot != null &&
            canonical(exportDocument.data) == canonical(exportDocument.originalDataSnapshot)
        ) exportDocument.originalDataCompressed else deflate(dataPlain)
        val totalSize = HEADER_SIZE.toLong() + infoCompressed.size + dataCompressed.size
        require(totalSize <= MAX_CONTAINER_BYTES) { "Save hasil edit terlalu besar." }

        val header = ByteBuffer.allocate(HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(MAGIC)
            .putInt(VERSION)
            .putInt(infoCompressed.size)
            .putInt(infoPlain.size)
            .putInt(dataCompressed.size)
            .putInt(dataPlain.size)
            .array()

        return ByteArrayOutputStream(totalSize.toInt()).apply {
            write(header)
            write(infoCompressed)
            write(dataCompressed)
        }.toByteArray()
    }

    fun validate(document: Document): Boolean {
        val decoded = decode(encode(document), document.sourceName)
        return sameContent(decoded, document)
    }

    fun sameContent(left: Document, right: Document): Boolean {
        // Compare canonical JSON. Object key order and Gson's numeric wrapper types
        // must not make a valid encode/decode round-trip fail.
        return canonical(left.info) == canonical(right.info) &&
            canonical(left.data) == canonical(right.data)
    }

    fun firstDifference(left: Document, right: Document): String? {
        fun find(a: com.google.gson.JsonElement, b: com.google.gson.JsonElement, path: String): String? {
            if (a.isJsonObject && b.isJsonObject) {
                val keys = (a.asJsonObject.keySet() + b.asJsonObject.keySet()).toSortedSet()
                for (key in keys) {
                    val av = a.asJsonObject.get(key)
                    val bv = b.asJsonObject.get(key)
                    if (av == null || bv == null) return "$path.$key (field missing)"
                    find(av, bv, "$path.$key")?.let { return it }
                }
                return null
            }
            if (a.isJsonArray && b.isJsonArray) {
                if (a.asJsonArray.size() != b.asJsonArray.size()) return "$path (jumlah item berubah)"
                for (index in 0 until a.asJsonArray.size()) {
                    find(a.asJsonArray[index], b.asJsonArray[index], "$path[$index]")?.let { return it }
                }
                return null
            }
            return if (canonical(a) == canonical(b)) null else "$path (nilai berbeda)"
        }
        return find(left.info, right.info, "Save Info")
            ?: find(left.data, right.data, "Game Data")
    }

    private fun canonical(value: com.google.gson.JsonElement): String = when {
        value.isJsonObject -> value.asJsonObject.entrySet()
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}") { (key, child) ->
                gson.toJson(key) + ":" + canonical(child)
            }
        value.isJsonArray -> value.asJsonArray.joinToString(prefix = "[", postfix = "]") { canonical(it) }
        value.isJsonNull -> "null"
        value.asJsonPrimitive.isString -> gson.toJson(value.asString)
        value.asJsonPrimitive.isBoolean -> value.asBoolean.toString()
        value.asJsonPrimitive.isNumber -> try {
            java.math.BigDecimal(value.asString).stripTrailingZeros().toPlainString()
        } catch (_: NumberFormatException) {
            value.asString
        }
        else -> value.toString()
    }

    private fun inflate(source: ByteArray, expectedSize: Int): ByteArray {
        val output = ByteArray(expectedSize)
        val actualSize = lz4.safeDecompressor().decompress(source, output)
        require(actualSize == expectedSize) { "Panjang hasil dekompresi tidak sesuai header." }
        return output
    }

    private fun deflate(source: ByteArray): ByteArray {
        val compressor = lz4.fastCompressor()
        val output = ByteArray(compressor.maxCompressedLength(source.size))
        val written = compressor.compress(source, 0, source.size, output, 0, output.size)
        return output.copyOf(written)
    }
}
