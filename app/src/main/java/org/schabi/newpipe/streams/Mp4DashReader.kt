/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.streams

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.NoSuchElementException
import java.io.InputStream
import okio.EOFException
import okio.IOException
import org.schabi.newpipe.streams.io.SharpStream

/**
 * Full MP4 DASH Reader implementing complete MP4 box parsing for audio/video muxing.
 * Powered by Okio IO and SharpStream.
 */
class Mp4DashReader(private val source: SharpStream) {
    private val stream: DataReader = DataReader(source)

    private var tracks: Array<Mp4Track>? = null
    private var brands: IntArray? = null

    private var box: Box? = null
    private var moof: Moof? = null

    private var chunkZero = false
    private var selectedTrack = -1
    private var backupBox: Box? = null

    enum class TrackKind {
        Audio,
        Video,
        Subtitles,
        Other
    }

    @Throws(IOException::class, NoSuchElementException::class)
    fun parse() {
        if (selectedTrack > -1) {
            return
        }

        val b = readBox(ATOM_FTYP)
        brands = parseFtyp(b)
        val brandsArray = brands ?: throw NoSuchElementException("Brands not found")
        val isRecognizedBrand = brandsArray.any {
            it == BRAND_DASH || it == BRAND_ISO5 || it == BRAND_ISOM ||
            it == BRAND_MP41 || it == BRAND_MP42 || it == BRAND_M4A || it == BRAND_AVC1
        }
        if (!isRecognizedBrand && brandsArray.isNotEmpty()) {
            // Accept diverse MP4/DASH container brands without throwing
        }
        ensure(b)

        var moov: Moov? = null

        while (stream.available()) {
            val nextBox = readBox()
            when (nextBox.type) {
                ATOM_MOOV -> {
                    moov = parseMoov(nextBox)
                }
                ATOM_MOOF -> {
                    box = nextBox
                    break
                }
                else -> {}
            }
            if (box?.type == ATOM_MOOF) {
                break
            }
            ensure(nextBox)
        }

        if (moov == null || moov.trak == null || moov.trak!!.isEmpty()) {
            throw IOException("The provided Mp4 doesn't have valid tracks in the 'moov' box")
        }

        val trakList = moov.trak!!
        val resultTracks = Array(trakList.size) { Mp4Track() }

        for (i in trakList.indices) {
            resultTracks[i].trak = trakList[i]

            if (moov.mvexTrex != null) {
                for (mvexTrex in moov.mvexTrex!!) {
                    if (resultTracks[i].trak?.tkhd?.trackId == mvexTrex.trackId) {
                        resultTracks[i].trex = mvexTrex
                    }
                }
            }

            val subType = trakList[i].mdia?.hdlr?.subType ?: 0
            when (subType) {
                HANDLER_VIDE -> resultTracks[i].kind = TrackKind.Video
                HANDLER_SOUN -> resultTracks[i].kind = TrackKind.Audio
                HANDLER_SUBT -> resultTracks[i].kind = TrackKind.Subtitles
                else -> resultTracks[i].kind = TrackKind.Other
            }
        }

        tracks = resultTracks
        backupBox = box
    }

    fun selectTrack(index: Int): Mp4Track {
        val currentTracks = tracks ?: throw IllegalStateException("Tracks not parsed yet")
        if (currentTracks.isEmpty()) {
            throw IOException("No tracks found in MP4 stream")
        }
        val safeIndex = if (index >= currentTracks.size || index < 0) 0 else index
        selectedTrack = safeIndex
        return currentTracks[safeIndex]
    }

    fun getBrands(): IntArray {
        return brands ?: intArrayOf()
    }

    fun getAvailableTracks(): Array<Mp4Track>? {
        return tracks
    }

    @Throws(IOException::class)
    fun rewind() {
        if (!stream.canRewind()) {
            throw IOException("The provided stream doesn't allow seek")
        }
        val bBox = backupBox ?: return

        box = bBox
        chunkZero = false

        stream.rewind()
        stream.skipBytes(bBox.offset + (DataReader.INTEGER_SIZE * 2))
    }

    @Throws(IOException::class)
    fun getNextChunk(infoOnly: Boolean): Mp4DashChunk? {
        val currentTracks = tracks ?: throw IllegalStateException("Tracks not parsed")
        if (selectedTrack < 0 || selectedTrack >= currentTracks.size) {
            throw IllegalStateException("Track not selected")
        }
        val track = currentTracks[selectedTrack]
        val trackId = track.trak?.tkhd?.trackId ?: -1

        while (stream.available()) {
            if (chunkZero) {
                ensure(box!!)
                if (!stream.available()) {
                    break
                }
                box = readBox()
            } else {
                chunkZero = true
            }

            val currentBox = box ?: break

            when (currentBox.type) {
                ATOM_MOOF -> {
                    if (moof != null) {
                        throw IOException("moof found without mdat")
                    }

                    moof = parseMoof(currentBox, trackId)

                    val currentTraf = moof?.traf
                    if (currentTraf != null) {
                        val trun = currentTraf.trun
                        val tfhd = currentTraf.tfhd

                        if (trun != null && tfhd != null) {
                            if (hasFlag(trun.bFlags, 0x0001)) {
                                trun.dataOffset -= (currentBox.size + 8).toInt()
                                if (trun.dataOffset < 0) {
                                    throw IOException("trun box has wrong data offset, points outside of concurrent mdat box")
                                }
                            }

                            if (trun.chunkSize < 1) {
                                if (hasFlag(tfhd.bFlags, 0x10)) {
                                    trun.chunkSize = tfhd.defaultSampleSize * trun.entryCount
                                } else {
                                    trun.chunkSize = (currentBox.size - 8).toInt()
                                }
                            }

                            if (!hasFlag(trun.bFlags, 0x900) && trun.chunkDuration == 0) {
                                if (hasFlag(tfhd.bFlags, 0x20)) {
                                    trun.chunkDuration = tfhd.defaultSampleDuration * trun.entryCount
                                }
                            }
                        }
                    }
                }
                ATOM_MDAT -> {
                    val currentMoof = moof ?: throw IOException("mdat found without moof")

                    if (currentMoof.traf == null) {
                        moof = null
                        continue // find another chunk
                    }

                    val chunk = Mp4DashChunk()
                    chunk.moof = currentMoof
                    val trun = currentMoof.traf?.trun
                    if (trun != null) {
                        if (!infoOnly) {
                            chunk.data = stream.getView(trun.chunkSize)
                        }
                        moof = null
                        stream.skipBytes(trun.dataOffset.toLong())
                        return chunk
                    }
                }
                else -> {}
            }
        }

        return null
    }

    private fun readUint(): Long {
        return stream.readUnsignedInt()
    }

    private fun boxName(type: Int): String {
        val bytes = ByteBuffer.allocate(4).putInt(type).array()
        return String(bytes, StandardCharsets.US_ASCII)
    }

    private fun boxName(ref: Box): String {
        return boxName(ref.type)
    }

    @Throws(IOException::class)
    private fun readBox(): Box {
        val b = Box()
        b.offset = stream.position
        b.size = stream.readUnsignedInt()
        b.type = stream.readInt()

        if (b.size == 1L) {
            b.size = stream.readLong()
        }

        return b
    }

    @Throws(IOException::class)
    private fun readBox(expected: Int): Box {
        val b = readBox()
        if (b.type != expected) {
            throw NoSuchElementException("expected " + boxName(expected) + " found " + boxName(b))
        }
        return b
    }

    @Throws(IOException::class)
    private fun readFullBox(ref: Box): ByteArray {
        val size = ref.size.toInt()
        val buffer = ByteBuffer.allocate(size)
        buffer.putInt(size)
        buffer.putInt(ref.type)

        val read = size - 8
        if (stream.read(buffer.array(), 8, read) != read) {
            throw EOFException("EOF reached in box: type=${boxName(ref.type)} offset=${ref.offset} size=${ref.size}")
        }

        return buffer.array()
    }

    @Throws(IOException::class)
    private fun ensure(ref: Box) {
        val skip = ref.offset + ref.size - stream.position
        if (skip <= 0L) {
            return
        }
        stream.skipBytes(skip)
    }

    @Throws(IOException::class)
    private fun untilBox(ref: Box, vararg expected: Int): Box? {
        while (stream.position < (ref.offset + ref.size)) {
            val b = readBox()
            for (type in expected) {
                if (b.type == type) {
                    return b
                }
            }
            ensure(b)
        }
        return null
    }

    @Throws(IOException::class)
    private fun untilAnyBox(ref: Box): Box? {
        if (stream.position >= (ref.offset + ref.size)) {
            return null
        }
        return readBox()
    }

    @Throws(IOException::class)
    private fun parseFtyp(ref: Box): IntArray {
        var i = 0
        val count = ((ref.offset + ref.size - stream.position - 4) / 4).toInt()
        val list = IntArray(count)
        list[i++] = stream.readInt() // major brand
        stream.skipBytes(4) // minor version

        while (i < list.size) {
            list[i++] = stream.readInt() // compatible brands
        }
        return list
    }

    @Throws(IOException::class)
    private fun parseMoov(ref: Box): Moov {
        val moov = Moov()
        val tmp = ArrayList<Trak>()
        var nextBox: Box?
        while (untilBox(ref, ATOM_MVHD, ATOM_TRAK, ATOM_MVEX).also { nextBox = it } != null) {
            val current = nextBox!!
            when (current.type) {
                ATOM_MVHD -> moov.mvhd = parseMvhd()
                ATOM_TRAK -> tmp.add(parseTrak(current))
                ATOM_MVEX -> moov.mvexTrex = parseMvex(current)
            }
            ensure(current)
        }

        moov.trak = tmp.toTypedArray()
        return moov
    }

    @Throws(IOException::class)
    private fun parseMvhd(): Mvhd {
        val version = stream.read()
        stream.skipBytes(3) // flags
        stream.skipBytes((2 * if (version == 0) 4 else 8).toLong()) // creation/mod time

        val obj = Mvhd()
        obj.timeScale = readUint()
        stream.skipBytes((if (version == 0) 4 else 8).toLong()) // duration
        stream.skipBytes(76) // rate, volume, reserved, matrix, predefined
        obj.nextTrackId = readUint()
        return obj
    }

    @Throws(IOException::class)
    private fun parseTrak(ref: Box): Trak {
        val trak = Trak()
        var nextBox: Box?
        while (untilBox(ref, ATOM_TKHD, ATOM_MDIA, ATOM_EDTS).also { nextBox = it } != null) {
            val current = nextBox!!
            when (current.type) {
                ATOM_TKHD -> trak.tkhd = parseTkhd()
                ATOM_MDIA -> trak.mdia = parseMdia(current)
                ATOM_EDTS -> trak.edstElst = parseEdts(current)
            }
            ensure(current)
        }

        return trak
    }

    @Throws(IOException::class)
    private fun parseTkhd(): Tkhd {
        val version = stream.read()
        val obj = Tkhd()
        stream.skipBytes((3 + (2 * if (version == 0) 4 else 8)).toLong())
        obj.trackId = stream.readInt()
        stream.skipBytes(4) // reserved
        obj.duration = if (version == 0) readUint() else stream.readLong()
        stream.skipBytes(8) // reserved
        obj.bLayer = stream.readShort()
        obj.bAlternateGroup = stream.readShort()
        obj.bVolume = stream.readShort()
        stream.skipBytes(2) // reserved
        obj.matrix = ByteArray(36)
        stream.read(obj.matrix!!)
        obj.bWidth = stream.readInt()
        obj.bHeight = stream.readInt()
        return obj
    }

    @Throws(IOException::class)
    private fun parseMdia(ref: Box): Mdia {
        val obj = Mdia()
        var nextBox: Box?
        while (untilBox(ref, ATOM_MDHD, ATOM_HDLR, ATOM_MINF).also { nextBox = it } != null) {
            val current = nextBox!!
            when (current.type) {
                ATOM_MDHD -> {
                    obj.mdhd = readFullBox(current)
                    val buffer = ByteBuffer.wrap(obj.mdhd!!)
                    val version = buffer.get(8).toInt()
                    buffer.position(12 + ((if (version == 0) 4 else 8) * 2))
                    obj.mdhdTimeScale = buffer.getInt()
                }
                ATOM_HDLR -> obj.hdlr = parseHdlr(current)
                ATOM_MINF -> obj.minf = parseMinf(current)
            }
            ensure(current)
        }
        return obj
    }

    @Throws(IOException::class)
    private fun parseHdlr(ref: Box): Hdlr {
        stream.skipBytes(4) // version & flags
        val obj = Hdlr()
        obj.bReserved = ByteArray(12)
        obj.type = stream.readInt()
        obj.subType = stream.readInt()
        stream.read(obj.bReserved!!)
        stream.skipBytes(ref.offset + ref.size - stream.position)
        return obj
    }

    @Throws(IOException::class)
    private fun parseEdts(ref: Box): EdstElst? {
        val b = untilBox(ref, ATOM_ELST) ?: return null
        val obj = EdstElst()
        val v1 = stream.read() == 1
        stream.skipBytes(3)
        val entryCount = stream.readInt()
        if (entryCount < 1) {
            obj.bMediaRate = 0x00010000
            return obj
        }

        if (v1) {
            stream.skipBytes(DataReader.LONG_SIZE.toLong())
            obj.mediaTime = stream.readLong()
            stream.skipBytes(((entryCount - 1) * (DataReader.LONG_SIZE * 2)).toLong())
        } else {
            stream.skipBytes(DataReader.INTEGER_SIZE.toLong())
            obj.mediaTime = stream.readInt().toLong()
        }

        obj.bMediaRate = stream.readInt()
        return obj
    }

    @Throws(IOException::class)
    private fun parseMinf(ref: Box): Minf {
        val obj = Minf()
        var nextBox: Box?
        while (untilAnyBox(ref).also { nextBox = it } != null) {
            val current = nextBox!!
            when (current.type) {
                ATOM_DINF -> obj.dinf = readFullBox(current)
                ATOM_STBL -> obj.stblStsd = parseStbl(current)
                ATOM_VMHD, ATOM_SMHD -> obj.mhd = readFullBox(current)
            }
            ensure(current)
        }
        return obj
    }

    @Throws(IOException::class)
    private fun parseStbl(ref: Box): ByteArray {
        val b = untilBox(ref, ATOM_STSD) ?: return ByteArray(0)
        return readFullBox(b)
    }

    @Throws(IOException::class)
    private fun parseMvex(ref: Box): Array<Trex> {
        val tmp = ArrayList<Trex>()
        var nextBox: Box?
        while (untilBox(ref, ATOM_TREX).also { nextBox = it } != null) {
            tmp.add(parseTrex())
            ensure(nextBox!!)
        }
        return tmp.toTypedArray()
    }

    @Throws(IOException::class)
    private fun parseTrex(): Trex {
        stream.skipBytes(4) // version & flags
        val obj = Trex()
        obj.trackId = stream.readInt()
        obj.defaultSampleDescriptionIndex = stream.readInt()
        obj.defaultSampleDuration = stream.readInt()
        obj.defaultSampleSize = stream.readInt()
        obj.defaultSampleFlags = stream.readInt()
        return obj
    }

    @Throws(IOException::class)
    private fun parseMoof(ref: Box, trackId: Int): Moof {
        val obj = Moof()
        val b = readBox(ATOM_MFHD)
        obj.mfhdSequenceNumber = parseMfhd()
        ensure(b)

        var nextBox: Box?
        while (untilBox(ref, ATOM_TRAF).also { nextBox = it } != null) {
            val current = nextBox!!
            obj.traf = parseTraf(current, trackId)
            ensure(current)

            if (obj.traf != null) {
                return obj
            }
        }
        return obj
    }

    @Throws(IOException::class)
    private fun parseMfhd(): Int {
        stream.skipBytes(4)
        return stream.readInt()
    }

    @Throws(IOException::class)
    private fun parseTraf(ref: Box, trackId: Int): Traf? {
        val traf = Traf()
        val b = readBox(ATOM_TFHD)
        traf.tfhd = parseTfhd(trackId)
        ensure(b)

        if (traf.tfhd == null) {
            return null
        }

        var nextBox = untilBox(ref, ATOM_TRUN, ATOM_TFDT)
        if (nextBox != null && nextBox.type == ATOM_TFDT) {
            traf.tfdt = parseTfdt()
            ensure(nextBox)
            nextBox = readBox(ATOM_TRUN)
        }

        if (nextBox != null) {
            traf.trun = parseTrun()
            ensure(nextBox)
        }

        return traf
    }

    @Throws(IOException::class)
    private fun parseTfhd(trackId: Int): Tfhd? {
        val obj = Tfhd()
        obj.bFlags = stream.readInt()
        obj.trackId = stream.readInt()

        if (trackId != -1 && obj.trackId != trackId) {
            return null
        }

        if (hasFlag(obj.bFlags, 0x01)) stream.skipBytes(8)
        if (hasFlag(obj.bFlags, 0x02)) stream.skipBytes(4)
        if (hasFlag(obj.bFlags, 0x08)) obj.defaultSampleDuration = stream.readInt()
        if (hasFlag(obj.bFlags, 0x10)) obj.defaultSampleSize = stream.readInt()
        if (hasFlag(obj.bFlags, 0x20)) obj.defaultSampleFlags = stream.readInt()

        return obj
    }

    @Throws(IOException::class)
    private fun parseTfdt(): Long {
        val version = stream.read()
        stream.skipBytes(3)
        return if (version == 0) readUint() else stream.readLong()
    }

    @Throws(IOException::class)
    private fun parseTrun(): Trun {
        val obj = Trun()
        obj.bFlags = stream.readInt()
        obj.entryCount = stream.readInt()

        obj.entriesRowSize = 0
        if (hasFlag(obj.bFlags, 0x0100)) obj.entriesRowSize += 4
        if (hasFlag(obj.bFlags, 0x0200)) obj.entriesRowSize += 4
        if (hasFlag(obj.bFlags, 0x0400)) obj.entriesRowSize += 4
        if (hasFlag(obj.bFlags, 0x0800)) obj.entriesRowSize += 4
        obj.bEntries = ByteArray(obj.entriesRowSize * obj.entryCount)

        if (hasFlag(obj.bFlags, 0x0001)) obj.dataOffset = stream.readInt()
        if (hasFlag(obj.bFlags, 0x0004)) obj.bFirstSampleFlags = stream.readInt()

        if (obj.bEntries!!.isNotEmpty()) {
            stream.read(obj.bEntries!!)
        }

        for (i in 0 until obj.entryCount) {
            val entry = obj.getEntry(i)
            if (hasFlag(obj.bFlags, 0x0100)) obj.chunkDuration += entry.sampleDuration
            if (hasFlag(obj.bFlags, 0x0200)) obj.chunkSize += entry.sampleSize
            if (hasFlag(obj.bFlags, 0x0800)) {
                if (!hasFlag(obj.bFlags, 0x0100)) {
                    obj.chunkDuration += entry.sampleCompositionTimeOffset
                }
            }
        }

        return obj
    }

    companion object {
        private const val ATOM_MOOF = 0x6D6F6F66
        private const val ATOM_MFHD = 0x6D666864
        private const val ATOM_TRAF = 0x74726166
        private const val ATOM_TFHD = 0x74666864
        private const val ATOM_TFDT = 0x74666474
        private const val ATOM_TRUN = 0x7472756E
        private const val ATOM_MDIA = 0x6D646961
        private const val ATOM_FTYP = 0x66747970
        private const val ATOM_SIDX = 0x73696478
        private const val ATOM_MOOV = 0x6D6F6F76
        private const val ATOM_MDAT = 0x6D646174
        private const val ATOM_MVHD = 0x6D766864
        private const val ATOM_TRAK = 0x7472616B
        private const val ATOM_MVEX = 0x6D766578
        private const val ATOM_TREX = 0x74726578
        private const val ATOM_TKHD = 0x746B6864
        private const val ATOM_MFRA = 0x6D667261
        private const val ATOM_MDHD = 0x6D646864
        private const val ATOM_EDTS = 0x65647473
        private const val ATOM_ELST = 0x656C7374
        private const val ATOM_HDLR = 0x68646C72
        private const val ATOM_MINF = 0x6D696E66
        private const val ATOM_DINF = 0x64696E66
        private const val ATOM_STBL = 0x7374626C
        private const val ATOM_STSD = 0x73747364
        private const val ATOM_VMHD = 0x766D6864
        private const val ATOM_SMHD = 0x736D6864

        private const val BRAND_DASH = 0x64617368
        private const val BRAND_ISO5 = 0x69736F35
        private const val BRAND_ISOM = 0x69736F6D
        private const val BRAND_MP41 = 0x6D703431
        private const val BRAND_MP42 = 0x6D703432
        private const val BRAND_M4A = 0x4D344120
        private const val BRAND_AVC1 = 0x61766331

        private const val HANDLER_VIDE = 0x76696465
        private const val HANDLER_SOUN = 0x736F756E
        private const val HANDLER_SUBT = 0x73756274

        fun hasFlag(flags: Int, mask: Int): Boolean {
            return (flags and mask) == mask
        }
    }

    // region nested classes
    class Mp4Track(
        var kind: TrackKind = TrackKind.Other,
        var trak: Trak? = null,
        var trex: Trex? = null
    )

    class Trak {
        var tkhd: Tkhd? = null
        var edstElst: EdstElst? = null
        var mdia: Mdia? = null
    }

    class Tkhd {
        var trackId: Int = 0
        var duration: Long = 0
        var bLayer: Short = 0
        var bAlternateGroup: Short = 0
        var bVolume: Short = 0
        var matrix: ByteArray? = null
        var bWidth: Int = 0
        var bHeight: Int = 0
    }

    class EdstElst {
        var mediaTime: Long = 0
        var bMediaRate: Int = 0
    }

    class Mdia {
        var mdhd: ByteArray? = null
        var hdlr: Hdlr? = null
        var minf: Minf? = null
        var mdhdTimeScale: Int = 0
    }

    class Hdlr {
        var type: Int = 0
        var subType: Int = 0
        var bReserved: ByteArray? = null
    }

    class Minf {
        var mhd: ByteArray? = null
        var dinf: ByteArray? = null
        var stblStsd: ByteArray? = null
    }

    class Trex {
        var trackId: Int = 0
        var defaultSampleDescriptionIndex: Int = 0
        var defaultSampleDuration: Int = 0
        var defaultSampleSize: Int = 0
        var defaultSampleFlags: Int = 0
    }

    class Mvhd {
        var timeScale: Long = 0
        var nextTrackId: Long = 0
    }

    class Moov {
        var mvhd: Mvhd? = null
        var trak: Array<Trak>? = null
        var mvexTrex: Array<Trex>? = null
    }

    class Mp4DashChunk {
        var data: InputStream? = null
        var moof: Moof? = null
        private var i = 0

        fun getNextSampleInfo(): Mp4DashSample? {
            val currentMoof = moof ?: return null
            val currentTraf = currentMoof.traf ?: return null
            val currentTrun = currentTraf.trun ?: return null
            val currentTfhd = currentTraf.tfhd ?: return null
            if (i >= currentTrun.entryCount) return null
            val entry = currentTrun.getAbsoluteEntry(i++, currentTfhd)
            val sample = Mp4DashSample(
                info = null,
                data = null,
                isKeyframe = entry.isKeyframe,
                sampleDuration = entry.sampleDuration,
                sampleSize = entry.sampleSize,
                hasCompositionTimeOffset = entry.hasCompositionTimeOffset,
                sampleCompositionTimeOffset = entry.sampleCompositionTimeOffset
            )
            sample.info = sample
            return sample
        }

        @Throws(IOException::class)
        fun getNextSample(): Mp4DashSample? {
            val currentData = data ?: throw IllegalStateException("This chunk has info only")
            val currentMoof = moof ?: return null
            val currentTraf = currentMoof.traf ?: return null
            val currentTrun = currentTraf.trun ?: return null
            val currentTfhd = currentTraf.tfhd ?: return null
            if (i >= currentTrun.entryCount) return null
            val entry = currentTrun.getAbsoluteEntry(i++, currentTfhd)
            val sampleBytes = ByteArray(entry.sampleSize)
            var totalRead = 0
            while (totalRead < entry.sampleSize) {
                val r = currentData.read(sampleBytes, totalRead, entry.sampleSize - totalRead)
                if (r <= 0) throw EOFException("EOF reached while reading a sample")
                totalRead += r
            }
            val sample = Mp4DashSample(
                info = null,
                data = sampleBytes,
                isKeyframe = entry.isKeyframe,
                sampleDuration = entry.sampleDuration,
                sampleSize = entry.sampleSize,
                hasCompositionTimeOffset = entry.hasCompositionTimeOffset,
                sampleCompositionTimeOffset = entry.sampleCompositionTimeOffset
            )
            sample.info = sample
            return sample
        }
    }

    class Moof(var mfhdSequenceNumber: Int = 0) {
        var traf: Traf? = null
    }

    class Traf {
        var tfhd: Tfhd? = null
        var tfdt: Long = 0
        var trun: Trun? = null
    }

    class Tfhd {
        var bFlags: Int = 0
        var trackId: Int = 0
        var defaultSampleDuration: Int = 0
        var defaultSampleSize: Int = 0
        var defaultSampleFlags: Int = 0
    }

    class TrunEntry {
        var sampleDuration: Int = 0
        var sampleSize: Int = 0
        var sampleFlags: Int = 0
        var sampleCompositionTimeOffset: Int = 0
        var hasCompositionTimeOffset: Boolean = false
        var isKeyframe: Boolean = false
    }

    class Trun {
        var chunkDuration: Int = 0
        var chunkSize: Int = 0
        var bFlags: Int = 0
        var bFirstSampleFlags: Int = 0
        var dataOffset: Int = 0
        var entryCount: Int = 0
        var bEntries: ByteArray? = null
        var entriesRowSize: Int = 0

        fun getEntry(i: Int): TrunEntry {
            val buffer = ByteBuffer.wrap(bEntries!!, i * entriesRowSize, entriesRowSize)
            val entry = TrunEntry()

            if (hasFlag(bFlags, 0x0100)) {
                entry.sampleDuration = buffer.getInt()
            }
            if (hasFlag(bFlags, 0x0200)) {
                entry.sampleSize = buffer.getInt()
            }
            if (hasFlag(bFlags, 0x0400)) {
                entry.sampleFlags = buffer.getInt()
            }
            if (hasFlag(bFlags, 0x0800)) {
                entry.sampleCompositionTimeOffset = buffer.getInt()
            }

            entry.hasCompositionTimeOffset = hasFlag(bFlags, 0x0800)
            entry.isKeyframe = !hasFlag(entry.sampleFlags, 0x10000)

            return entry
        }

        fun getAbsoluteEntry(i: Int, header: Tfhd): TrunEntry {
            val entry = getEntry(i)

            if (!hasFlag(bFlags, 0x0100) && hasFlag(header.bFlags, 0x20)) {
                entry.sampleFlags = header.defaultSampleFlags
            }

            if (!hasFlag(bFlags, 0x0200) && hasFlag(header.bFlags, 0x10)) {
                entry.sampleSize = header.defaultSampleSize
            }

            if (!hasFlag(bFlags, 0x0100) && hasFlag(header.bFlags, 0x08)) {
                entry.sampleDuration = header.defaultSampleDuration
            }

            if (i == 0 && hasFlag(bFlags, 0x0004)) {
                entry.sampleFlags = bFirstSampleFlags
            }

            return entry
        }
    }

    class Mp4DashSample(
        var info: Mp4DashSample? = null,
        var data: ByteArray? = null,
        var isKeyframe: Boolean = false,
        var sampleDuration: Int = 0,
        var sampleSize: Int = 0,
        var hasCompositionTimeOffset: Boolean = false,
        var sampleCompositionTimeOffset: Int = 0
    )

    private class Box(
        var type: Int = 0,
        var offset: Long = 0,
        var size: Long = 0
    )
    // endregion
}
