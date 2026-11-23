/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.download.artwork

import java.io.File
import java.io.RandomAccessFile
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/** Original encoded image, never a thumbnail-sized bitmap or an upscaled/cropped replacement. */
data class AudioArtwork(val bytes: ByteArray, val mimeType: String) : Serializable {
    init {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES)
        require(mimeType == "image/jpeg" || mimeType == "image/png")
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        private const val serialVersionUID = 1L
    }
}

/** Bounded container edits. All audio data and unrelated metadata remain byte-for-byte intact. */
object AudioArtworkTags {
    const val MAX_METADATA_BYTES = 16 * 1024 * 1024
    private val latin = StandardCharsets.ISO_8859_1

    interface Source {
        val length: Long
        fun read(position: Long, size: Int): ByteArray
    }

    private class FileSource(private val file: RandomAccessFile) : Source {
        override val length: Long get() = file.length()
        override fun read(position: Long, size: Int): ByteArray {
            require(position >= 0 && size >= 0 && position <= length - size)
            return ByteArray(size).also {
                file.seek(position)
                file.readFully(it)
            }
        }
    }

    fun detectFormat(source: Source): String? {
        if (source.length < 8) return null
        val header = source.read(0, 8)
        return when {
            String(header, 0, 3, latin) == "ID3" || header[0].toInt() and 255 == 255 && header[1].toInt() and 224 == 224 -> "mp3"
            String(header, 4, 4, latin) == "ftyp" -> "m4a"
            else -> null
        }
    }

    fun readCover(source: Source, format: String): AudioArtwork? = when (format) {
        "mp3" -> readId3(source)?.frames?.firstNotNullOfOrNull { frame ->
            if (frame.id != "APIC" && frame.id != "PIC") return@firstNotNullOfOrNull null
            picture(frame.payload, frame.id == "PIC")
        }

        "m4a" -> readMoov(source)?.let { (_, moov) -> findCover(moov) }

        else -> null
    }

    /** Returns false without touching output when a cover already exists or the tag is unsupported. */
    fun embed(input: File, output: File, format: String, artwork: AudioArtwork, cancelled: () -> Boolean = { false }): Boolean = RandomAccessFile(input, "r").use { file ->
        val source = FileSource(file)
        when (format) {
            "mp3" -> embedMp3(source, input, output, artwork, cancelled)
            "m4a" -> embedMp4(source, input, output, artwork, cancelled)
            else -> false
        }
    }

    private data class Frame(val id: String, val payload: ByteArray)
    private data class Id3(val version: Int, val flags: Int, val body: ByteArray, val total: Long, val frames: List<Frame>)

    private fun readId3(source: Source): Id3? {
        if (source.length < 10) return null
        val header = source.read(0, 10)
        if (String(header, 0, 3, latin) != "ID3") return null
        val version = header[3].toInt() and 255
        require(version in 2..4)
        val flags = header[5].toInt() and 255
        val size = synchsafe(header, 6)
        require(size <= MAX_METADATA_BYTES && source.length >= 10L + size)
        var body = source.read(10, size)
        if (flags and 128 != 0 && version < 4) body = removeUnsynchronization(body)
        var position = 0
        if (version == 2) require(flags and 64 == 0)
        if (version >= 3 && flags and 64 != 0) {
            require(body.size >= 4)
            position = if (version == 4) synchsafe(body, 0) else checkedInt(uint(body, 0)) + 4
            require(position in 4..body.size)
        }
        val frames = mutableListOf<Frame>()
        val headerSize = if (version == 2) 6 else 10
        while (position <= body.size - headerSize && body[position].toInt() != 0) {
            val idSize = if (version == 2) 3 else 4
            val id = String(body, position, idSize, latin)
            require(id.all { it in 'A'..'Z' || it in '0'..'9' })
            val frameSize = when (version) {
                2 -> ((body[position + 3].toInt() and 255) shl 16) or ((body[position + 4].toInt() and 255) shl 8) or (body[position + 5].toInt() and 255)
                3 -> checkedInt(uint(body, position + 4))
                else -> synchsafe(body, position + 4)
            }
            require(frameSize > 0 && frameSize <= body.size - position - headerSize)
            var payload = body.copyOfRange(position + headerSize, position + headerSize + frameSize)
            val frameFlags = if (version == 2) 0 else body[position + 9].toInt() and 255
            if (version == 4 && (flags and 128 != 0 || frameFlags and 2 != 0)) payload = removeUnsynchronization(payload)
            // An encrypted/compressed picture still counts as an existing cover, but is not decoded.
            if ((version == 3 && frameFlags and 224 != 0) || (version == 4 && frameFlags and 77 != 0)) payload = byteArrayOf()
            frames += Frame(id, payload)
            require(frames.size <= 100_000)
            position += headerSize + frameSize
        }
        val total = 10L + size + if (version == 4 && flags and 16 != 0) 10 else 0
        require(total <= source.length)
        return Id3(version, flags, body, total, frames)
    }

    private fun embedMp3(source: Source, input: File, output: File, art: AudioArtwork, cancelled: () -> Boolean): Boolean {
        val old = readId3(source)
        if (old?.frames?.any { it.id == "APIC" || it.id == "PIC" } == true) return false
        // Extended-header CRCs, encrypted tags and footer variants are preserved unchanged.
        if (old != null && old.flags and 127 != 0) return false
        if (old == null) {
            require(source.length >= 2)
            val sync = source.read(0, 2)
            require(sync[0].toInt() and 255 == 255 && sync[1].toInt() and 224 == 224)
        }
        val version = old?.version ?: 4
        val picture = if (version == 2) {
            byteArrayOf(0) + (if (art.mimeType == "image/png") "PNG" else "JPG").toByteArray(latin) + byteArrayOf(3, 0) + art.bytes
        } else {
            byteArrayOf(0) + art.mimeType.toByteArray(latin) + byteArrayOf(0, 3, 0) + art.bytes
        }
        val frame = if (version == 2) {
            "PIC".toByteArray(latin) + byteArrayOf((picture.size ushr 16).toByte(), (picture.size ushr 8).toByte(), picture.size.toByte()) + picture
        } else {
            "APIC".toByteArray(latin) + (if (version == 4) toSynchsafe(picture.size) else intBytes(picture.size)) + byteArrayOf(0, 0) + picture
        }
        var body = frame + (old?.body ?: byteArrayOf())
        val flags = old?.flags ?: 0
        if (flags and 128 != 0) {
            // Re-encode the complete v2.2/v2.3 tag. v2.4 uses frame-level unsynchronization.
            if (version == 4) return false
            body = addUnsynchronization(body)
        }
        require(body.size <= MAX_METADATA_BYTES + AudioArtwork.MAX_BYTES)
        output.outputStream().use { target ->
            target.write(byteArrayOf(73, 68, 51, version.toByte(), 0, flags.toByte()) + toSynchsafe(body.size))
            target.write(body)
            input.inputStream().use { audio ->
                skipFully(audio, old?.total ?: 0)
                copy(audio, target, cancelled)
            }
            target.fd.sync()
        }
        return true
    }

    private data class Atom(val start: Int, val size: Int, val header: Int, val type: String)
    private data class FileAtom(val start: Long, val size: Long, val type: String, val toEnd: Boolean)

    private fun fileAtoms(source: Source): List<FileAtom> {
        val atoms = mutableListOf<FileAtom>()
        var position = 0L
        while (position < source.length) {
            require(source.length - position >= 8)
            val bytes = source.read(position, 8)
            val short = uint(bytes, 0)
            val size = when (short) {
                0L -> source.length - position

                1L -> {
                    require(source.length - position >= 16)
                    ByteBuffer.wrap(source.read(position + 8, 8)).long
                }

                else -> short
            }
            require(size >= (if (short == 1L) 16 else 8) && size <= source.length - position)
            atoms += FileAtom(position, size, String(bytes, 4, 4, latin), short == 0L)
            require(atoms.size <= 100_000)
            position += size
        }
        return atoms
    }

    private fun readMoov(source: Source): Pair<FileAtom, ByteArray>? {
        val moovs = fileAtoms(source).filter { it.type == "moov" }
        require(moovs.size <= 1)
        val moov = moovs.singleOrNull() ?: return null
        require(moov.size <= MAX_METADATA_BYTES)
        return moov to source.read(moov.start, moov.size.toInt())
    }

    private fun atoms(bytes: ByteArray, start: Int = 8): List<Atom> {
        val atoms = mutableListOf<Atom>()
        var position = start
        while (position < bytes.size) {
            require(bytes.size - position >= 8)
            val short = uint(bytes, position)
            val header = if (short == 1L) 16 else 8
            val size = when (short) {
                0L -> bytes.size - position

                1L -> {
                    require(bytes.size - position >= 16)
                    checkedInt(ByteBuffer.wrap(bytes, position + 8, 8).long)
                }

                else -> checkedInt(short)
            }
            require(size >= header && size <= bytes.size - position)
            atoms += Atom(position, size, header, String(bytes, position + 4, 4, latin))
            require(atoms.size <= 100_000)
            position += size
        }
        return atoms
    }

    private fun slice(bytes: ByteArray, atom: Atom) = bytes.copyOfRange(atom.start, atom.start + atom.size)
    private fun header(bytes: ByteArray): Int = if (uint(bytes, 0) == 1L) 16 else 8

    private fun coverItems(moov: ByteArray): List<ByteArray> = atoms(moov, header(moov)).filter { it.type == "udta" }.flatMap { udta ->
        val user = slice(moov, udta)
        atoms(user, header(user)).filter { it.type == "meta" }.flatMap { meta ->
            val metadata = slice(user, meta)
            atoms(metadata, header(metadata) + 4).filter { it.type == "ilst" }.flatMap { ilst ->
                val list = slice(metadata, ilst)
                atoms(list, header(list)).filter { it.type == "covr" }.map { slice(list, it) }
            }
        }
    }

    private fun findCover(moov: ByteArray): AudioArtwork? = coverItems(moov).firstNotNullOfOrNull { cover ->
        atoms(cover, header(cover)).firstNotNullOfOrNull data@{ data ->
            if (data.type != "data" || data.size < data.header + 8) return@data null
            val payload = slice(cover, data)
            val type = uint(payload, data.header).toInt() and 0xFFFFFF
            if (type != 13 && type != 14) return@data null
            val bytes = payload.copyOfRange(data.header + 8, payload.size)
            if (bytes.isEmpty() || bytes.size > AudioArtwork.MAX_BYTES) return@data null
            AudioArtwork(bytes, if (type == 13) "image/jpeg" else "image/png")
        }
    }

    private fun embedMp4(source: Source, input: File, output: File, art: AudioArtwork, cancelled: () -> Boolean): Boolean {
        val (oldAtom, oldMoov) = readMoov(source) ?: return false
        if (coverItems(oldMoov).isNotEmpty()) return false
        val all = fileAtoms(source)
        require(all.any { it.type == "ftyp" } && all.any { it.type == "mdat" })
        val cover = box("covr", box("data", intBytes(if (art.mimeType == "image/png") 14 else 13) + intBytes(0) + art.bytes))
        val moov = addCover(oldMoov, cover)
        require(moov.size <= MAX_METADATA_BYTES + AudioArtwork.MAX_BYTES)
        // Keep every mdat and chunk offset in place; the enlarged moov is appended at EOF.
        // An original size-to-EOF atom must become bounded before appending another atom.
        val unbounded = all.filter { it.toEnd }
        if (unbounded.any { it.size > 0xFFFFFFFFL }) return false
        output.outputStream().use { target ->
            input.inputStream().use { copy(it, target, cancelled) }
            target.write(moov)
            target.fd.sync()
        }
        RandomAccessFile(output, "rw").use { target ->
            target.seek(oldAtom.start + 4)
            target.write("free".toByteArray(latin))
            unbounded.forEach { atom ->
                target.seek(atom.start)
                target.writeInt(atom.size.toInt())
            }
            target.fd.sync()
        }
        return true
    }

    private fun addCover(moov: ByteArray, cover: ByteArray): ByteArray {
        var inserted = false
        val children = atoms(moov, header(moov)).map { child ->
            val bytes = slice(moov, child)
            if (child.type != "udta" || inserted) return@map bytes
            val userChildren = atoms(bytes, header(bytes)).map { userChild ->
                val userBytes = slice(bytes, userChild)
                if (userChild.type != "meta" || inserted) return@map userBytes
                val metadata = atoms(userBytes, header(userBytes) + 4)
                val list = metadata.firstOrNull { it.type == "ilst" } ?: return@map userBytes
                // mdta uses numeric item keys. Only extend an existing iTunes mdir handler.
                val handler = metadata.firstOrNull { it.type == "hdlr" }
                if (handler == null || handler.size < handler.header + 12 || String(userBytes, handler.start + handler.header + 8, 4, latin) != "mdir") return@map userBytes
                val replaced = metadata.map { entry ->
                    val raw = slice(userBytes, entry)
                    if (entry != list) raw else box("ilst", raw.copyOfRange(entry.header, raw.size) + cover)
                }
                inserted = true
                box("meta", userBytes.copyOfRange(header(userBytes), header(userBytes) + 4) + join(replaced))
            }.toMutableList()
            if (!inserted) {
                userChildren += newMeta(cover)
                inserted = true
            }
            box("udta", join(userChildren))
        }.toMutableList()
        if (!inserted) children += box("udta", newMeta(cover))
        return box("moov", join(children))
    }

    private fun newMeta(cover: ByteArray): ByteArray = box("meta", intBytes(0) + box("hdlr", ByteArray(8) + "mdir".toByteArray(latin) + ByteArray(13)) + box("ilst", cover))
    private fun join(values: List<ByteArray>): ByteArray = values.fold(java.io.ByteArrayOutputStream()) { out, value -> out.apply { write(value) } }.toByteArray()
    private fun box(type: String, bytes: ByteArray): ByteArray = intBytes(bytes.size + 8) + type.toByteArray(latin) + bytes

    private fun picture(payload: ByteArray, shortMime: Boolean): AudioArtwork? = runCatching {
        if (payload.size < 6) return null
        val encoding = payload[0].toInt() and 255
        require(encoding in 0..3)
        var cursor: Int
        val mime = if (shortMime) {
            cursor = 4
            when (String(payload, 1, 3, latin)) {
                "PNG" -> "image/png"
                "JPG" -> "image/jpeg"
                else -> return null
            }
        } else {
            val end = payload.indexOf(0, 1)
            require(end > 1)
            cursor = end + 1
            String(payload, 1, end - 1, latin)
        }
        if (mime != "image/png" && mime != "image/jpeg") return null
        cursor++ // Picture type; retain any available cover rather than replacing source artwork.
        require(cursor < payload.size)
        if (encoding == 1 || encoding == 2) {
            while (cursor + 1 < payload.size && (payload[cursor].toInt() != 0 || payload[cursor + 1].toInt() != 0)) cursor += 2
            cursor += 2
        } else {
            cursor = payload.indexOf(0, cursor) + 1
            require(cursor > 0)
        }
        require(cursor < payload.size && payload.size - cursor <= AudioArtwork.MAX_BYTES)
        AudioArtwork(payload.copyOfRange(cursor, payload.size), mime)
    }.getOrNull()

    private fun ByteArray.indexOf(value: Byte, start: Int): Int = (start until size).firstOrNull { this[it] == value } ?: -1
    private fun uint(bytes: ByteArray, offset: Int): Long = ByteBuffer.wrap(bytes, offset, 4).int.toLong() and 0xFFFFFFFFL
    private fun checkedInt(value: Long): Int {
        require(value in 0..Int.MAX_VALUE.toLong())
        return value.toInt()
    }
    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()
    private fun synchsafe(bytes: ByteArray, offset: Int): Int {
        require((offset until offset + 4).all { bytes[it].toInt() and 128 == 0 })
        return (0..3).fold(0) { value, index -> (value shl 7) or bytes[offset + index].toInt() }
    }
    private fun toSynchsafe(value: Int): ByteArray = byteArrayOf((value ushr 21).toByte(), ((value ushr 14) and 127).toByte(), ((value ushr 7) and 127).toByte(), (value and 127).toByte())
    private fun removeUnsynchronization(bytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(bytes.size)
        var index = 0
        while (index < bytes.size) {
            val value = bytes[index++]
            out.write(value.toInt())
            if (value.toInt() and 255 == 255 && index < bytes.size && bytes[index].toInt() == 0) index++
        }
        return out.toByteArray()
    }
    private fun addUnsynchronization(bytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(bytes.size)
        bytes.forEachIndexed { index, value ->
            out.write(value.toInt())
            if (value.toInt() and 255 == 255 && (index + 1 == bytes.size || bytes[index + 1].toInt() and 224 == 224 || bytes[index + 1].toInt() == 0)) out.write(0)
        }
        return out.toByteArray()
    }
    private fun skipFully(input: java.io.InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            require(skipped > 0)
            remaining -= skipped
        }
    }
    private fun copy(input: java.io.InputStream, output: java.io.OutputStream, cancelled: () -> Boolean) {
        val buffer = ByteArray(128 * 1024)
        while (true) {
            if (cancelled()) throw java.io.InterruptedIOException()
            val count = input.read(buffer)
            if (count < 0) return
            output.write(buffer, 0, count)
        }
    }
}
