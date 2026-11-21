package com.morepp.mm4savedataeditor
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.jpountz.lz4.LZ4Factory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
object Mm4SaveCodec {
 private const val MAGIC=0x99131871.toInt()
 private const val VERSION=40
 private const val HEADER=24
 private val lz4=LZ4Factory.fastestInstance()
 private val gson=GsonBuilder().disableHtmlEscaping().create()
 data class Document(val info:JsonObject,val data:JsonObject,val sourceName:String)
 fun decode(bytes:ByteArray,sourceName:String="save.msv"):Document {
  require(bytes.size>=HEADER){"File terlalu kecil untuk header MM4."}
  val h=ByteBuffer.wrap(bytes,0,HEADER).order(ByteOrder.LITTLE_ENDIAN)
  val magic=h.int;val version=h.int;val ic=h.int;val ip=h.int;val dc=h.int;val dp=h.int
  require(magic==MAGIC){"Magic header tidak cocok."}
  require(version==VERSION){"Versi container tidak didukung: $version."}
  require(ic>0&&dc>0&&ip>=0&&dp>=0){"Panjang blok tidak valid."}
  require(HEADER.toLong()+ic+dc==bytes.size.toLong()){"Ukuran blok tidak cocok."}
  val split=HEADER+ic
  val info=JsonParser.parseString(inflate(bytes.copyOfRange(HEADER,split),ip).toString(Charsets.UTF_8))
  val data=JsonParser.parseString(inflate(bytes.copyOfRange(split,bytes.size),dp).toString(Charsets.UTF_8))
  require(info.isJsonObject&&data.isJsonObject){"Blok save bukan JSON object."}
  return Document(info.asJsonObject,data.asJsonObject,sourceName)
 }
 fun encode(doc:Document):ByteArray {
  val ip=gson.toJson(doc.info).toByteArray(Charsets.UTF_8);val dp=gson.toJson(doc.data).toByteArray(Charsets.UTF_8)
  val ic=deflate(ip);val dc=deflate(dp)
  val h=ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN).putInt(MAGIC).putInt(VERSION)
   .putInt(ic.size).putInt(ip.size).putInt(dc.size).putInt(dp.size).array()
  return ByteArrayOutputStream(HEADER+ic.size+dc.size).apply{write(h);write(ic);write(dc)}.toByteArray()
 }
 fun validate(doc:Document):Boolean {val d=decode(encode(doc),doc.sourceName);return d.info==doc.info&&d.data==doc.data}
 private fun inflate(src:ByteArray,size:Int):ByteArray {val out=ByteArray(size);require(lz4.safeDecompressor().decompress(src,out)==size){"Panjang dekompresi tidak sesuai."};return out}
 private fun deflate(src:ByteArray):ByteArray {val c=lz4.fastCompressor();val out=ByteArray(c.maxCompressedLength(src.size));return out.copyOf(c.compress(src,0,src.size,out,0,out.size))}
}