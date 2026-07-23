package com.rameshta.formready.core.processing

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.util.zip.CRC32
import kotlin.math.roundToInt

object PngDpiMetadata {
    fun read(file: File): Int? {
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            val signature = ByteArray(PNG_SIGNATURE.size)
            input.readFully(signature)
            if (!signature.contentEquals(PNG_SIGNATURE)) return null
            while (true) {
                val length = runCatching { input.readInt() }.getOrNull() ?: return null
                if (length < 0 || length > MAX_CHUNK_BYTES) return null
                val type = ByteArray(4).also(input::readFully)
                if (type.contentEquals(PHYS_TYPE) && length == PHYS_DATA_BYTES) {
                    val pixelsPerMetreX = input.readInt()
                    input.readInt()
                    val unit = input.readUnsignedByte()
                    return if (unit == 1 && pixelsPerMetreX > 0) {
                        (pixelsPerMetreX * METRES_PER_INCH).roundToInt()
                    } else {
                        null
                    }
                }
                input.skipFully(length.toLong() + CRC_BYTES)
                if (type.contentEquals(IDAT_TYPE) || type.contentEquals(IEND_TYPE)) return null
            }
        }
    }

    fun write(file: File, dpi: Int) {
        require(dpi > 0)
        val temporary = File(file.parentFile, "${file.name}.dpi.part")
        temporary.delete()
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            DataOutputStream(BufferedOutputStream(temporary.outputStream())).use { output ->
                val signature = ByteArray(PNG_SIGNATURE.size).also(input::readFully)
                require(signature.contentEquals(PNG_SIGNATURE))
                output.write(signature)
                var inserted = false
                while (true) {
                    val length = input.readInt()
                    require(length in 0..MAX_CHUNK_BYTES)
                    val type = ByteArray(4).also(input::readFully)
                    if (type.contentEquals(PHYS_TYPE)) {
                        input.skipFully(length.toLong() + CRC_BYTES)
                    } else {
                        output.writeInt(length)
                        output.write(type)
                        input.copyExactly(output, length.toLong() + CRC_BYTES)
                    }
                    if (!inserted && type.contentEquals(IHDR_TYPE)) {
                        output.writePhysChunk(dpi)
                        inserted = true
                    }
                    if (type.contentEquals(IEND_TYPE)) break
                }
                check(inserted)
            }
        }
        check(temporary.isFile && temporary.length() > 0L)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun DataOutputStream.writePhysChunk(dpi: Int) {
        val pixelsPerMetre = (dpi / METRES_PER_INCH).roundToInt()
        val data = java.io.ByteArrayOutputStream(PHYS_DATA_BYTES).also { bytes ->
            DataOutputStream(bytes).use { chunk ->
                chunk.writeInt(pixelsPerMetre)
                chunk.writeInt(pixelsPerMetre)
                chunk.writeByte(1)
            }
        }.toByteArray()
        val crc = CRC32().apply {
            update(PHYS_TYPE)
            update(data)
        }
        writeInt(data.size)
        write(PHYS_TYPE)
        write(data)
        writeInt(crc.value.toInt())
    }

    private fun DataInputStream.copyExactly(output: DataOutputStream, byteCount: Long) {
        var remaining = byteCount
        val buffer = ByteArray(BUFFER_BYTES)
        while (remaining > 0L) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException()
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun DataInputStream.skipFully(byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                if (read() < 0) throw EOFException()
                remaining--
            }
        }
    }

    private const val METRES_PER_INCH = 0.0254
    private const val PHYS_DATA_BYTES = 9
    private const val CRC_BYTES = 4
    private const val MAX_CHUNK_BYTES = 64 * 1024 * 1024
    private const val BUFFER_BYTES = 64 * 1024
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
    private val IHDR_TYPE = "IHDR".encodeToByteArray()
    private val PHYS_TYPE = "pHYs".encodeToByteArray()
    private val IDAT_TYPE = "IDAT".encodeToByteArray()
    private val IEND_TYPE = "IEND".encodeToByteArray()
}
