package com.uniban.campo.util.tiff

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

class TiffDecodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Decodificador TIFF puro en Kotlin (sin dependencias nativas) orientado a
 * ortomosaicos UAV: tiras o teselas, compresión ninguna/LZW/Deflate/PackBits,
 * 8 o 16 bits, escala de grises, RGB o paleta. Descodifica por bandas para
 * no cargar la imagen completa en arreglos intermedios.
 */
object TiffDecoder {

    private const val TAG_IMAGE_WIDTH = 256
    private const val TAG_IMAGE_LENGTH = 257
    private const val TAG_BITS_PER_SAMPLE = 258
    private const val TAG_COMPRESSION = 259
    private const val TAG_PHOTOMETRIC = 262
    private const val TAG_STRIP_OFFSETS = 273
    private const val TAG_SAMPLES_PER_PIXEL = 277
    private const val TAG_ROWS_PER_STRIP = 278
    private const val TAG_STRIP_BYTE_COUNTS = 279
    private const val TAG_COLOR_MAP = 320
    private const val TAG_TILE_WIDTH = 322
    private const val TAG_TILE_LENGTH = 323
    private const val TAG_TILE_OFFSETS = 324
    private const val TAG_TILE_BYTE_COUNTS = 325

    private const val TYPE_BYTE = 1
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4

    private const val COMPRESSION_NONE = 1
    private const val COMPRESSION_LZW = 5
    private const val COMPRESSION_DEFLATE = 8
    private const val COMPRESSION_PACKBITS = 32773
    private const val COMPRESSION_DEFLATE_RAW = 32946

    fun isTiff(file: File): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 8) return false
            val b = ByteArray(4)
            raf.readFully(b)
            val header = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN)
            val b0 = header.get(0).toInt() and 0xFF
            val b1 = header.get(1).toInt() and 0xFF
            if (b0 == 'M'.code && b1 == 'M'.code) {
                return header.getShort(2).toInt() == 42
            }
            if (b0 == 'I'.code && b1 == 'I'.code) {
                return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort(2).toInt() == 42
            }
            return false
        }
    }

    fun decode(file: File): Bitmap {
        if (!isTiff(file)) throw TiffDecodeException("El archivo no es un TIFF válido")
        RandomAccessFile(file, "r").use { raf ->
            val header = readBytes(raf, 0, 8)
            val order = when {
                header[0] == 'I'.code.toByte() && header[1] == 'I'.code.toByte() -> ByteOrder.LITTLE_ENDIAN
                header[0] == 'M'.code.toByte() && header[1] == 'M'.code.toByte() -> ByteOrder.BIG_ENDIAN
                else -> throw TiffDecodeException("Cabecera TIFF desconocida")
            }
            val bb = ByteBuffer.wrap(header).order(order)
            val magic = bb.getShort(2).toInt()
            if (magic == 43) throw TiffDecodeException("BigTIFF no soportado; convierta a TIFF clásico")
            if (magic != 42) throw TiffDecodeException("Cabecera TIFF inválida (magic=$magic)")
            val ifdOffset = bb.getInt(4).toLong() and 0xFFFFFFFFL

            val tags = readIfd(raf, order, ifdOffset)
            return decodeIfd(raf, tags, order)
        }
    }

    private fun readIfd(raf: RandomAccessFile, order: ByteOrder, offset: Long): Map<Int, LongArray> {
        val countBuf = readBytes(raf, offset, 2)
        val entryCount = ByteBuffer.wrap(countBuf).order(order).getShort(0).toInt() and 0xFFFF
        val entriesRaw = readBytes(raf, offset + 2, entryCount * 12L)
        val entries = HashMap<Int, LongArray>(entryCount)

        for (i in 0 until entryCount) {
            val bb = ByteBuffer.wrap(entriesRaw, i * 12, 12).order(order)
            val tag = bb.getShort(0).toInt() and 0xFFFF
            val type = bb.getShort(2).toInt() and 0xFFFF
            val count = bb.getInt(4).toLong() and 0xFFFFFFFFL
            val valueField = bb.getInt(8).toLong() and 0xFFFFFFFFL

            val bytesPer = when (type) {
                TYPE_BYTE, TYPE_SHORT, TYPE_LONG -> when (type) {
                    TYPE_BYTE -> 1
                    TYPE_SHORT -> 2
                    else -> 4
                }
                else -> continue // tipos no usados (ASCII, RATIONAL, etc.)
            }
            val totalBytes = count * bytesPer
            val values = LongArray(count.toInt().coerceAtMost(4096))

            if (totalBytes <= 4) {
                val vb = ByteBuffer.allocate(4).order(order)
                vb.putInt(0, (valueField and 0xFFFFFFFFL).toInt())
                when (type) {
                    TYPE_BYTE -> for (j in values.indices) values[j] = (vb.get(j).toInt() and 0xFF).toLong()
                    TYPE_SHORT -> for (j in values.indices) values[j] = (vb.getShort(j * 2).toInt() and 0xFFFF).toLong()
                    else -> for (j in values.indices) values[j] = vb.getInt(j * 4).toLong() and 0xFFFFFFFFL
                }
            } else {
                val data = readBytes(raf, valueField, totalBytes)
                val db = ByteBuffer.wrap(data).order(order)
                when (type) {
                    TYPE_BYTE -> for (j in values.indices) values[j] = (db.get(j).toInt() and 0xFF).toLong()
                    TYPE_SHORT -> for (j in values.indices) values[j] = (db.getShort(j * 2).toInt() and 0xFFFF).toLong()
                    else -> for (j in values.indices) values[j] = db.getInt(j * 4).toLong() and 0xFFFFFFFFL
                }
            }
            entries[tag] = values
        }
        return entries
    }

    private fun decodeIfd(raf: RandomAccessFile, tags: Map<Int, LongArray>, order: ByteOrder): Bitmap {
        val width = tags[TAG_IMAGE_WIDTH]?.get(0)?.toInt()
            ?: throw TiffDecodeException("Falta ImageWidth")
        val height = tags[TAG_IMAGE_LENGTH]?.get(0)?.toInt()
            ?: throw TiffDecodeException("Falta ImageLength")
        val bpsValues = tags[TAG_BITS_PER_SAMPLE] ?: longArrayOf(1)
        val bps = bpsValues[0].toInt()
        val compression = tags[TAG_COMPRESSION]?.get(0)?.toInt() ?: COMPRESSION_NONE
        val photometric = tags[TAG_PHOTOMETRIC]?.get(0)?.toInt() ?: 0
        val spp = tags[TAG_SAMPLES_PER_PIXEL]?.get(0)?.toInt() ?: 1
        val tiled = tags.containsKey(TAG_TILE_OFFSETS)

        if (bps != 8 && bps != 16) {
            throw TiffDecodeException("BitsPerSample=$bps no soportado (solo 8 o 16 bits)")
        }
        if (spp < 1 || spp > 4) {
            throw TiffDecodeException("SamplesPerPixel=$spp no soportado")
        }
        when (photometric) {
            0, 1, 2, 3 -> Unit
            5 -> throw TiffDecodeException("Imágenes CMYK no soportadas; convierta a RGB")
            else -> throw TiffDecodeException("PhotometricInterpretation=$photometric no soportado")
        }
        if (photometric == 3 && spp > 1) {
            throw TiffDecodeException("Paleta con múltiples muestras no soportada")
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val bytesPerSample = bps / 8
        val rowBytes = width * spp * bytesPerSample

        if (tiled) {
            decodeTiles(raf, bitmap, tags, width, height, bps, compression, photometric, spp, bytesPerSample)
        } else {
            decodeStrips(raf, bitmap, tags, width, height, bps, compression, photometric, spp, rowBytes, bytesPerSample)
        }
        return bitmap
    }

    private fun decodeStrips(
        raf: RandomAccessFile,
        bitmap: Bitmap,
        tags: Map<Int, LongArray>,
        width: Int,
        height: Int,
        bps: Int,
        compression: Int,
        photometric: Int,
        spp: Int,
        rowBytes: Int,
        bytesPerSample: Int
    ) {
        val offsets = tags[TAG_STRIP_OFFSETS] ?: throw TiffDecodeException("Falta StripOffsets")
        val counts = tags[TAG_STRIP_BYTE_COUNTS] ?: throw TiffDecodeException("Falta StripByteCounts")
        val rowsPerStrip = (tags[TAG_ROWS_PER_STRIP]?.get(0)?.toInt() ?: height).coerceAtLeast(1)
        val numStrips = (height + rowsPerStrip - 1) / rowsPerStrip
        val palette = readPalette(tags, bps, photometric)

        val chunkRows = 64
        val pixelBuf = IntArray(width * chunkRows)

        for (strip in 0 until numStrips) {
            val y0 = strip * rowsPerStrip
            val rows = minOf(rowsPerStrip, height - y0)
            val expected = rows * rowBytes
            val compressed = readBytes(raf, offsets[strip], counts[strip])
            val raw = decompress(compression, compressed, expected)

            var offset = 0
            var processed = 0
            while (processed < rows) {
                val take = minOf(chunkRows, rows - processed)
                for (row in 0 until take) {
                    for (x in 0 until width) {
                        val base = offset + (row * width + x) * spp * bytesPerSample
                        pixelBuf[row * width + x] =
                            toArgb(raw, base, bps, spp, photometric, palette)
                    }
                }
                bitmap.setPixels(pixelBuf, 0, width, 0, y0 + processed, width, take)
                offset += take * rowBytes
                processed += take
            }
        }
    }

    private fun decodeTiles(
        raf: RandomAccessFile,
        bitmap: Bitmap,
        tags: Map<Int, LongArray>,
        width: Int,
        height: Int,
        bps: Int,
        compression: Int,
        photometric: Int,
        spp: Int,
        bytesPerSample: Int
    ) {
        val offsets = tags[TAG_TILE_OFFSETS] ?: throw TiffDecodeException("Falta TileOffsets")
        val counts = tags[TAG_TILE_BYTE_COUNTS] ?: throw TiffDecodeException("Falta TileByteCounts")
        val tileW = tags[TAG_TILE_WIDTH]?.get(0)?.toInt() ?: throw TiffDecodeException("Falta TileWidth")
        val tileH = tags[TAG_TILE_LENGTH]?.get(0)?.toInt() ?: throw TiffDecodeException("Falta TileLength")
        val tilesPerRow = (width + tileW - 1) / tileW
        val tilesPerCol = (height + tileH - 1) / tileH
        val palette = readPalette(tags, bps, photometric)
        val rowBytes = tileW * spp * bytesPerSample
        val expected = tileH * rowBytes

        val pixelBuf = IntArray(tileW * tileH)

        for (row in 0 until tilesPerCol) {
            for (col in 0 until tilesPerRow) {
                val index = row * tilesPerRow + col
                val compressed = readBytes(raf, offsets[index], counts[index])
                val raw = decompress(compression, compressed, expected)

                var p = 0
                for (ty in 0 until tileH) {
                    for (tx in 0 until tileW) {
                        val base = (ty * tileW + tx) * spp * bytesPerSample
                        pixelBuf[p++] = toArgb(raw, base, bps, spp, photometric, palette)
                    }
                }

                val dx = col * tileW
                val dy = row * tileH
                val copyW = minOf(tileW, width - dx)
                val copyH = minOf(tileH, height - dy)
                if (copyW == tileW && copyH == tileH) {
                    bitmap.setPixels(pixelBuf, 0, tileW, dx, dy, tileW, tileH)
                } else {
                    for (y in 0 until copyH) {
                        bitmap.setPixels(
                            pixelBuf, y * tileW, tileW,
                            dx, dy + y, copyW, 1
                        )
                    }
                }
            }
        }
    }

    private fun readPalette(tags: Map<Int, LongArray>, bps: Int, photometric: Int): IntArray? {
        if (photometric != 3) return null
        val map = tags[TAG_COLOR_MAP] ?: throw TiffDecodeException("Falta ColorMap para imagen con paleta")
        val entries = 1 shl bps
        if (map.size < entries * 3) throw TiffDecodeException("ColorMap incompleto")
        val palette = IntArray(entries)
        for (i in 0 until entries) {
            val r = ((map[i].toInt() and 0xFFFF) shr 8)
            val g = ((map[entries + i].toInt() and 0xFFFF) shr 8)
            val b = ((map[entries * 2 + i].toInt() and 0xFFFF) shr 8)
            palette[i] = Color.rgb(r, g, b)
        }
        return palette
    }

    private fun toArgb(
        raw: ByteArray,
        base: Int,
        bps: Int,
        spp: Int,
        photometric: Int,
        palette: IntArray?
    ): Int {
        if (base >= raw.size) return Color.BLACK
        fun sample(i: Int): Int {
            val o = base + i * (bps / 8)
            if (o + (bps / 8) - 1 >= raw.size) return 0
            return if (bps == 8) {
                raw[o].toInt() and 0xFF
            } else {
                (((raw[o].toInt() and 0xFF) shl 8) or (raw[o + 1].toInt() and 0xFF))
            }
        }

        return when {
            photometric == 3 -> {
                val idx = sample(0)
                palette?.getOrNull(idx) ?: Color.BLACK
            }
            spp == 1 -> {
                val v = if (bps == 8) sample(0) else sample(0) shr 8
                Color.rgb(v, v, v)
            }
            else -> {
                val r = if (bps == 8) sample(0) else sample(0) shr 8
                val g = if (bps == 8) sample(1) else sample(1) shr 8
                val b = if (bps == 8) sample(2) else sample(2) shr 8
                Color.rgb(r, g, b)
            }
        }
    }

    private fun decompress(compression: Int, data: ByteArray, expectedSize: Int): ByteArray {
        return when (compression) {
            COMPRESSION_NONE -> data
            COMPRESSION_LZW -> TiffLzwDecoder(data).decode(expectedSize)
            COMPRESSION_DEFLATE -> inflate(data, expectedSize, nowrap = false)
                ?: inflate(data, expectedSize, nowrap = true)
                ?: throw TiffDecodeException("No se pudo descomprimir datos Deflate")
            COMPRESSION_DEFLATE_RAW -> inflate(data, expectedSize, nowrap = true)
                ?: inflate(data, expectedSize, nowrap = false)
                ?: throw TiffDecodeException("No se pudo descomprimir datos Deflate")
            COMPRESSION_PACKBITS -> packBitsDecode(data, expectedSize)
            else -> throw TiffDecodeException("Compresión TIFF no soportada: $compression")
        }
    }

    private fun inflate(data: ByteArray, expectedSize: Int, nowrap: Boolean): ByteArray? = try {
        val inflater = Inflater(nowrap)
        inflater.setInput(data)
        val out = ByteArrayOutputStream(expectedSize.coerceAtMost(8 * 1024 * 1024))
        val buf = ByteArray(64 * 1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                out.write(buf, 0, n)
            }
        }
        inflater.end()
        val result = out.toByteArray()
        if (result.isEmpty() && data.isNotEmpty()) null else result
    } catch (e: Exception) {
        null
    }

    private fun packBitsDecode(data: ByteArray, expectedSize: Int): ByteArray {
        val out = ByteArrayOutputStream(expectedSize.coerceAtMost(8 * 1024 * 1024))
        var i = 0
        while (i < data.size) {
            val n = data[i].toInt()
            i++
            when {
                n >= 0 -> {
                    val count = n + 1
                    if (i + count > data.size) break
                    out.write(data, i, count)
                    i += count
                }
                n > -128 -> {
                    if (i >= data.size) break
                    val value = data[i]
                    i++
                    repeat(1 - n) { out.write(value.toInt()) }
                }
                else -> Unit // -128: no-op
            }
        }
        return out.toByteArray()
    }

    private fun readBytes(raf: RandomAccessFile, offset: Long, length: Long): ByteArray {
        if (offset < 0 || length < 0 || offset + length > raf.length()) {
            throw TiffDecodeException("Lectura fuera de rango (offset=$offset, len=$length)")
        }
        raf.seek(offset)
        val buf = ByteArray(length.toInt())
        raf.readFully(buf)
        return buf
    }
}

/**
 * Decodificador LZW variantes TIFF (códigos de 9 a 12 bits, "early change").
 */
private class TiffLzwDecoder(private val input: ByteArray) {

    private var bytePos = 0
    private var bitBuffer = 0
    private var bitCount = 0

    private val dictPrefix = IntArray(4096)
    private val dictSuffix = ByteArray(4096)
    private val dictLength = IntArray(4096)
    private var nextCode = 258
    private var codeBits = 9

    private fun readCode(): Int {
        while (bitCount < codeBits) {
            if (bytePos >= input.size) return -1
            bitBuffer = bitBuffer or ((input[bytePos].toInt() and 0xFF) shl bitCount)
            bytePos++
            bitCount += 8
        }
        val code = bitBuffer and ((1 shl codeBits) - 1)
        bitBuffer = bitBuffer ushr codeBits
        bitCount -= codeBits
        return code
    }

    private fun resetDict() {
        for (i in 0 until 256) {
            dictPrefix[i] = -1
            dictSuffix[i] = i.toByte()
            dictLength[i] = 1
        }
        nextCode = 258
        codeBits = 9
    }

    private fun emit(code: Int, out: ByteArrayOutputStream) {
        val stack = IntArray(4096)
        var sp = 0
        var c = code
        while (c >= 256 && sp < 4095) {
            stack[sp++] = dictSuffix[c].toInt() and 0xFF
            c = dictPrefix[c]
            if (c < 0) throw TiffDecodeException("Secuencia LZW corrupta")
        }
        stack[sp++] = c
        for (i in sp - 1 downTo 0) out.write(stack[i])
    }

    private fun firstByte(code: Int): Byte = when {
        code < 256 -> code.toByte()
        else -> {
            var c = code
            var guard = 0
            while (dictPrefix[c] >= 256 && guard++ < 4096) c = dictPrefix[c]
            dictSuffix[c]
        }
    }

    fun decode(expectedSize: Int): ByteArray {
        val out = ByteArrayOutputStream(expectedSize.coerceAtMost(16 * 1024 * 1024))
        resetDict()
        var prev = -1

        while (true) {
            val code = readCode()
            if (code < 0 || code == 257) break // EOI o fin de datos
            if (code == 256) { // CLEAR
                resetDict()
                prev = -1
                continue
            }
            if (prev < 0) {
                emit(code, out)
                prev = code
                continue
            }
            if (code < nextCode) {
                emit(code, out)
                if (nextCode < 4096) {
                    dictPrefix[nextCode] = prev
                    dictSuffix[nextCode] = firstByte(code)
                    dictLength[nextCode] = dictLength[prev] + 1
                    nextCode++
                }
            } else if (code == nextCode) {
                val fb = firstByte(prev)
                emit(prev, out)
                out.write(fb.toInt() and 0xFF)
                if (nextCode < 4096) {
                    dictPrefix[nextCode] = prev
                    dictSuffix[nextCode] = fb
                    dictLength[nextCode] = dictLength[prev] + 1
                    nextCode++
                }
            } else {
                throw TiffDecodeException("Código LZW inválido: $code (next=$nextCode)")
            }

            // Early change de TIFF: aumentar bits cuando el siguiente código libre es 2^bits - 1
            if (nextCode == (1 shl codeBits) - 1 && codeBits < 12) {
                codeBits++
            }
        }
        return out.toByteArray()
    }
}
