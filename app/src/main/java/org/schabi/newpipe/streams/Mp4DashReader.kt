package org.schabi.newpipe.streams

import org.schabi.newpipe.streams.io.SharpStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * @author kapodamy
 */
class Mp4DashReader(source: SharpStream) {
    private val stream: DataReader
    private var tracks: Array<Mp4Track?>? = null
    private var brands: IntArray? = null
    private var box: Box? = null
    private var moof: Moof? = null
    private var chunkZero: Boolean = false
    private var selectedTrack: Int = -1
    private var backupBox: Box? = null

    enum class TrackKind {
        Audio,
        Video,
        Subtitles,
        Other
    }

    init {
        stream = DataReader(source)
    }

    @Throws(IOException::class, NoSuchElementException::class)
    fun parse() {
        if (selectedTrack > -1) {
            return
        }
        box = readBox(ATOM_FTYP)
        brands = parseFtyp(box)
        when (brands!!.get(0)) {
            BRAND_DASH, BRAND_ISO5 -> {}
            else -> throw NoSuchElementException((
                    "Not a MPEG-4 DASH container, major brand is not 'dash' or 'iso5' is "
                            + boxName(brands!!.get(0))
                    ))
        }
        var moov: Moov? = null
        var i: Int
        while (box!!.type != ATOM_MOOF) {
            ensure(box)
            box = readBox()
            when (box!!.type) {
                ATOM_MOOV -> moov = parseMoov(box)
                ATOM_SIDX, ATOM_MFRA -> {}
            }
        }
        if (moov == null) {
            throw IOException("The provided Mp4 doesn't have the 'moov' box")
        }
        tracks = arrayOfNulls(moov.trak.size)
        i = 0
        while (i < tracks!!.size) {
            tracks!!.get(i) = Mp4Track()
            tracks!!.get(i)!!.trak = moov.trak.get(i)
            if (moov.mvexTrex != null) {
                for (mvexTrex: Trex in moov.mvexTrex!!) {
                    if (tracks!!.get(i)!!.trak!!.tkhd!!.trackId == mvexTrex.trackId) {
                        tracks!!.get(i)!!.trex = mvexTrex
                    }
                }
            }
            when (moov.trak.get(i).mdia!!.hdlr!!.subType) {
                HANDLER_VIDE -> tracks!!.get(i)!!.kind = TrackKind.Video
                HANDLER_SOUN -> tracks!!.get(i)!!.kind = TrackKind.Audio
                HANDLER_SUBT -> tracks!!.get(i)!!.kind = TrackKind.Subtitles
                else -> tracks!!.get(i)!!.kind = TrackKind.Other
            }
            i++
        }
        backupBox = box
    }

    fun selectTrack(index: Int): Mp4Track? {
        selectedTrack = index
        return tracks!!.get(index)
    }

    fun getBrands(): IntArray {
        if (brands == null) {
            throw IllegalStateException("Not parsed")
        }
        return brands
    }

    @Throws(IOException::class)
    fun rewind() {
        if (!stream.canRewind()) {
            throw IOException("The provided stream doesn't allow seek")
        }
        if (box == null) {
            return
        }
        box = backupBox
        chunkZero = false
        stream.rewind()
        stream.skipBytes(backupBox!!.offset + (DataReader.Companion.INTEGER_SIZE * 2))
    }

    fun getAvailableTracks(): Array<Mp4Track?>? {
        return tracks
    }

    @Throws(IOException::class)
    fun getNextChunk(infoOnly: Boolean): Mp4DashChunk? {
        val track: Mp4Track? = tracks!!.get(selectedTrack)
        while (stream.available()) {
            if (chunkZero) {
                ensure(box)
                if (!stream.available()) {
                    break
                }
                box = readBox()
            } else {
                chunkZero = true
            }
            when (box!!.type) {
                ATOM_MOOF -> {
                    if (moof != null) {
                        throw IOException("moof found without mdat")
                    }
                    moof = parseMoof(box, track!!.trak!!.tkhd!!.trackId)
                    if (moof!!.traf != null) {
                        if (hasFlag(moof!!.traf!!.trun!!.bFlags, 0x0001)) {
                            moof!!.traf!!.trun!!.dataOffset -= (box!!.size + 8).toInt()
                            if (moof!!.traf!!.trun!!.dataOffset < 0) {
                                throw IOException(("trun box has wrong data offset, "
                                        + "points outside of concurrent mdat box"))
                            }
                        }
                        if (moof!!.traf!!.trun!!.chunkSize < 1) {
                            if (hasFlag(moof!!.traf!!.tfhd!!.bFlags, 0x10)) {
                                moof!!.traf!!.trun!!.chunkSize = (moof!!.traf!!.tfhd!!.defaultSampleSize
                                        * moof!!.traf!!.trun!!.entryCount)
                            } else {
                                moof!!.traf!!.trun!!.chunkSize = (box!!.size - 8).toInt()
                            }
                        }
                        if ((!hasFlag(moof!!.traf!!.trun!!.bFlags, 0x900)
                                        && moof!!.traf!!.trun!!.chunkDuration == 0)) {
                            if (hasFlag(moof!!.traf!!.tfhd!!.bFlags, 0x20)) {
                                moof!!.traf!!.trun!!.chunkDuration = (moof!!.traf!!.tfhd!!.defaultSampleDuration
                                        * moof!!.traf!!.trun!!.entryCount)
                            }
                        }
                    }
                }

                ATOM_MDAT -> {
                    if (moof == null) {
                        throw IOException("mdat found without moof")
                    }
                    if (moof!!.traf == null) {
                        moof = null
                        continue  // find another chunk
                    }
                    val chunk: Mp4DashChunk = Mp4DashChunk()
                    chunk.moof = moof
                    if (!infoOnly) {
                        chunk.data = stream.getView(moof!!.traf!!.trun!!.chunkSize)
                    }
                    moof = null
                    stream.skipBytes(chunk.moof!!.traf!!.trun!!.dataOffset.toLong())
                    return chunk
                }

                else -> {}
            }
        }
        return null
    }

    private fun boxName(ref: Box?): String {
        return boxName(ref!!.type)
    }

    private fun boxName(type: Int): String {
        return String(ByteBuffer.allocate(4).putInt(type).array(), StandardCharsets.UTF_8)
    }

    @Throws(IOException::class)
    private fun readBox(): Box {
        val b: Box = Box()
        b.offset = stream.position()
        b.size = stream.readUnsignedInt()
        b.type = stream.readInt()
        if (b.size == 1L) {
            b.size = stream.readLong()
        }
        return b
    }

    @Throws(IOException::class)
    private fun readBox(expected: Int): Box {
        val b: Box = readBox()
        if (b.type != expected) {
            throw NoSuchElementException(("expected " + boxName(expected)
                    + " found " + boxName(b)))
        }
        return b
    }

    @Throws(IOException::class)
    private fun readFullBox(ref: Box): ByteArray {
        // full box reading is limited to 2 GiB, and should be enough
        val size: Int = ref.size.toInt()
        val buffer: ByteBuffer = ByteBuffer.allocate(size)
        buffer.putInt(size)
        buffer.putInt(ref.type)
        val read: Int = size - 8
        if (stream.read(buffer.array(), 8, read) != read) {
            throw EOFException(String.format("EOF reached in box: type=%s offset=%s size=%s",
                    boxName(ref.type), ref.offset, ref.size))
        }
        return buffer.array()
    }

    @Throws(IOException::class)
    private fun ensure(ref: Box?) {
        val skip: Long = ref!!.offset + ref.size - stream.position()
        if (skip == 0L) {
            return
        } else if (skip < 0) {
            throw EOFException(String.format(
                    "parser go beyond limits of the box. type=%s offset=%s size=%s position=%s",
                    boxName(ref), ref.offset, ref.size, stream.position()
            ))
        }
        stream.skipBytes((skip.toInt()).toLong())
    }

    @Throws(IOException::class)
    private fun untilBox(ref: Box?, vararg expected: Int): Box? {
        var b: Box
        while (stream.position() < (ref!!.offset + ref.size)) {
            b = readBox()
            for (type: Int in expected) {
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
        if (stream.position() >= (ref.offset + ref.size)) {
            return null
        }
        return readBox()
    }

    @Throws(IOException::class)
    private fun parseMoof(ref: Box?, trackId: Int): Moof {
        val obj: Moof = Moof()
        var b: Box? = readBox(ATOM_MFHD)
        obj.mfhdSequenceNumber = parseMfhd()
        ensure(b)
        while ((untilBox(ref, ATOM_TRAF).also({ b = it })) != null) {
            obj.traf = parseTraf(b, trackId)
            ensure(b)
            if (obj.traf != null) {
                return obj
            }
        }
        return obj
    }

    @Throws(IOException::class)
    private fun parseMfhd(): Int {
        // version
        // flags
        stream.skipBytes(4)
        return stream.readInt()
    }

    @Throws(IOException::class)
    private fun parseTraf(ref: Box?, trackId: Int): Traf? {
        val traf: Traf = Traf()
        var b: Box? = readBox(ATOM_TFHD)
        traf.tfhd = parseTfhd(trackId)
        ensure(b)
        if (traf.tfhd == null) {
            return null
        }
        b = untilBox(ref, ATOM_TRUN, ATOM_TFDT)
        if (b!!.type == ATOM_TFDT) {
            traf.tfdt = parseTfdt()
            ensure(b)
            b = readBox(ATOM_TRUN)
        }
        traf.trun = parseTrun()
        ensure(b)
        return traf
    }

    @Throws(IOException::class)
    private fun parseTfhd(trackId: Int): Tfhd? {
        val obj: Tfhd = Tfhd()
        obj.bFlags = stream.readInt()
        obj.trackId = stream.readInt()
        if (trackId != -1 && obj.trackId != trackId) {
            return null
        }
        if (hasFlag(obj.bFlags, 0x01)) {
            stream.skipBytes(8)
        }
        if (hasFlag(obj.bFlags, 0x02)) {
            stream.skipBytes(4)
        }
        if (hasFlag(obj.bFlags, 0x08)) {
            obj.defaultSampleDuration = stream.readInt()
        }
        if (hasFlag(obj.bFlags, 0x10)) {
            obj.defaultSampleSize = stream.readInt()
        }
        if (hasFlag(obj.bFlags, 0x20)) {
            obj.defaultSampleFlags = stream.readInt()
        }
        return obj
    }

    @Throws(IOException::class)
    private fun parseTfdt(): Long {
        val version: Int = stream.read()
        stream.skipBytes(3) // flags
        return if (version == 0) stream.readUnsignedInt() else stream.readLong()
    }

    @Throws(IOException::class)
    private fun parseTrun(): Trun {
        val obj: Trun = Trun()
        obj.bFlags = stream.readInt()
        obj.entryCount = stream.readInt() // unsigned int
        obj.entriesRowSize = 0
        if (hasFlag(obj.bFlags, 0x0100)) {
            obj.entriesRowSize += 4
        }
        if (hasFlag(obj.bFlags, 0x0200)) {
            obj.entriesRowSize += 4
        }
        if (hasFlag(obj.bFlags, 0x0400)) {
            obj.entriesRowSize += 4
        }
        if (hasFlag(obj.bFlags, 0x0800)) {
            obj.entriesRowSize += 4
        }
        obj.bEntries = ByteArray(obj.entriesRowSize * obj.entryCount)
        if (hasFlag(obj.bFlags, 0x0001)) {
            obj.dataOffset = stream.readInt()
        }
        if (hasFlag(obj.bFlags, 0x0004)) {
            obj.bFirstSampleFlags = stream.readInt()
        }
        stream.read(obj.bEntries)
        for (i in 0 until obj.entryCount) {
            val entry: TrunEntry = obj.getEntry(i)
            if (hasFlag(obj.bFlags, 0x0100)) {
                obj.chunkDuration += entry.sampleDuration
            }
            if (hasFlag(obj.bFlags, 0x0200)) {
                obj.chunkSize += entry.sampleSize
            }
            if (hasFlag(obj.bFlags, 0x0800)) {
                if (!hasFlag(obj.bFlags, 0x0100)) {
                    obj.chunkDuration += entry.sampleCompositionTimeOffset
                }
            }
        }
        return obj
    }

    @Throws(IOException::class)
    private fun parseFtyp(ref: Box?): IntArray {
        var i: Int = 0
        val list: IntArray = IntArray((((ref!!.offset + ref.size) - stream.position() - 4) / 4).toInt())
        list.get(i++) = stream.readInt() // major brand
        stream.skipBytes(4) // minor version
        while (i < list.size) {
            list.get(i) = stream.readInt() // compatible brands
            i++
        }
        return list
    }

    @Throws(IOException::class)
    private fun parseMvhd(): Mvhd {
        val version: Int = stream.read()
        stream.skipBytes(3) // flags

        // creation entries_time
        // modification entries_time
        stream.skipBytes((2 * (if (version == 0) 4 else 8)).toLong())
        val obj: Mvhd = Mvhd()
        obj.timeScale = stream.readUnsignedInt()

        // chunkDuration
        stream.skipBytes((if (version == 0) 4 else 8).toLong())

        // rate
        // volume
        // reserved
        // matrix array
        // predefined
        stream.skipBytes(76)
        obj.nextTrackId = stream.readUnsignedInt()
        return obj
    }

    @Throws(IOException::class)
    private fun parseTkhd(): Tkhd {
        val version: Int = stream.read()
        val obj: Tkhd = Tkhd()

        // flags
        // creation entries_time
        // modification entries_time
        stream.skipBytes((3 + (2 * (if (version == 0) 4 else 8))).toLong())
        obj.trackId = stream.readInt()
        stream.skipBytes(4) // reserved
        obj.duration = if (version == 0) stream.readUnsignedInt() else stream.readLong()
        stream.skipBytes((2 * 4).toLong()) // reserved
        obj.bLayer = stream.readShort()
        obj.bAlternateGroup = stream.readShort()
        obj.bVolume = stream.readShort()
        stream.skipBytes(2) // reserved
        obj.matrix = ByteArray(9 * 4)
        stream.read(obj.matrix)
        obj.bWidth = stream.readInt()
        obj.bHeight = stream.readInt()
        return obj
    }

    @Throws(IOException::class)
    private fun parseTrak(ref: Box): Trak {
        val trak: Trak = Trak()
        var b: Box = readBox(ATOM_TKHD)
        trak.tkhd = parseTkhd()
        ensure(b)
        while ((untilBox(ref, ATOM_MDIA, ATOM_EDTS).also({ b = (it)!! })) != null) {
            when (b.type) {
                ATOM_MDIA -> trak.mdia = parseMdia(b)
                ATOM_EDTS -> trak.edstElst = parseEdts(b)
            }
            ensure(b)
        }
        return trak
    }

    @Throws(IOException::class)
    private fun parseMdia(ref: Box): Mdia {
        val obj: Mdia = Mdia()
        var b: Box
        while ((untilBox(ref, ATOM_MDHD, ATOM_HDLR, ATOM_MINF).also({ b = (it)!! })) != null) {
            when (b.type) {
                ATOM_MDHD -> {
                    obj.mdhd = readFullBox(b)

                    // read time scale
                    val buffer: ByteBuffer = ByteBuffer.wrap(obj.mdhd)
                    val version: Byte = buffer.get(8)
                    buffer.position(12 + ((if (version.toInt() == 0) 4 else 8) * 2))
                    obj.mdhdTimeScale = buffer.getInt()
                }

                ATOM_HDLR -> obj.hdlr = parseHdlr(b)
                ATOM_MINF -> obj.minf = parseMinf(b)
            }
            ensure(b)
        }
        return obj
    }

    @Throws(IOException::class)
    private fun parseHdlr(ref: Box): Hdlr {
        // version
        // flags
        stream.skipBytes(4)
        val obj: Hdlr = Hdlr()
        obj.bReserved = ByteArray(12)
        obj.type = stream.readInt()
        obj.subType = stream.readInt()
        stream.read(obj.bReserved)

        // component name (is a ansi/ascii string)
        stream.skipBytes((ref.offset + ref.size) - stream.position())
        return obj
    }

    @Throws(IOException::class)
    private fun parseMoov(ref: Box?): Moov {
        var b: Box = readBox(ATOM_MVHD)
        val moov: Moov = Moov()
        moov.mvhd = parseMvhd()
        ensure(b)
        val tmp: ArrayList<Trak> = ArrayList(moov.mvhd!!.nextTrackId.toInt())
        while ((untilBox(ref, ATOM_TRAK, ATOM_MVEX).also({ b = (it)!! })) != null) {
            when (b.type) {
                ATOM_TRAK -> tmp.add(parseTrak(b))
                ATOM_MVEX -> moov.mvexTrex = parseMvex(b, moov.mvhd!!.nextTrackId.toInt())
            }
            ensure(b)
        }
        moov.trak = tmp.toTypedArray<Trak>()
        return moov
    }

    @Throws(IOException::class)
    private fun parseMvex(ref: Box, possibleTrackCount: Int): Array<Trex> {
        val tmp: ArrayList<Trex> = ArrayList(possibleTrackCount)
        var b: Box?
        while ((untilBox(ref, ATOM_TREX).also({ b = it })) != null) {
            tmp.add(parseTrex())
            ensure(b)
        }
        return tmp.toTypedArray<Trex>()
    }

    @Throws(IOException::class)
    private fun parseTrex(): Trex {
        // version
        // flags
        stream.skipBytes(4)
        val obj: Trex = Trex()
        obj.trackId = stream.readInt()
        obj.defaultSampleDescriptionIndex = stream.readInt()
        obj.defaultSampleDuration = stream.readInt()
        obj.defaultSampleSize = stream.readInt()
        obj.defaultSampleFlags = stream.readInt()
        return obj
    }

    @Throws(IOException::class)
    private fun parseEdts(ref: Box): Elst? {
        val b: Box? = untilBox(ref, ATOM_ELST)
        if (b == null) {
            return null
        }
        val obj: Elst = Elst()
        val v1: Boolean = stream.read() == 1
        stream.skipBytes(3) // flags
        val entryCount: Int = stream.readInt()
        if (entryCount < 1) {
            obj.bMediaRate = 0x00010000 // default media rate (1.0)
            return obj
        }
        if (v1) {
            stream.skipBytes(DataReader.Companion.LONG_SIZE.toLong()) // segment duration
            obj.mediaTime = stream.readLong()
            // ignore all remain entries
            stream.skipBytes(((entryCount - 1) * (DataReader.Companion.LONG_SIZE * 2)).toLong())
        } else {
            stream.skipBytes(DataReader.Companion.INTEGER_SIZE.toLong()) // segment duration
            obj.mediaTime = stream.readInt().toLong()
        }
        obj.bMediaRate = stream.readInt()
        return obj
    }

    @Throws(IOException::class)
    private fun parseMinf(ref: Box): Minf {
        val obj: Minf = Minf()
        var b: Box
        while ((untilAnyBox(ref).also({ b = (it)!! })) != null) {
            when (b.type) {
                ATOM_DINF -> obj.dinf = readFullBox(b)
                ATOM_STBL -> obj.stblStsd = parseStbl(b)
                ATOM_VMHD, ATOM_SMHD -> obj.mhd = readFullBox(b)
            }
            ensure(b)
        }
        return obj
    }

    /**
     * This only reads the "stsd" box inside.
     *
     * @param ref stbl box
     * @return stsd box inside
     */
    @Throws(IOException::class)
    private fun parseStbl(ref: Box): ByteArray {
        val b: Box? = untilBox(ref, ATOM_STSD)
        if (b == null) {
            return ByteArray(0) // this never should happens (missing codec startup data)
        }
        return readFullBox(b)
    }

    internal class Box() {
        var type: Int = 0
        var offset: Long = 0
        var size: Long = 0
    }

    class Moof() {
        var mfhdSequenceNumber: Int = 0
        var traf: Traf? = null
    }

    class Traf() {
        var tfhd: Tfhd? = null
        var tfdt: Long = 0
        var trun: Trun? = null
    }

    class Tfhd() {
        var bFlags: Int = 0
        var trackId: Int = 0
        var defaultSampleDuration: Int = 0
        var defaultSampleSize: Int = 0
        var defaultSampleFlags: Int = 0
    }

    class TrunEntry() {
        var sampleDuration: Int = 0
        var sampleSize: Int = 0
        var sampleFlags: Int = 0
        var sampleCompositionTimeOffset: Int = 0
        var hasCompositionTimeOffset: Boolean = false
        var isKeyframe: Boolean = false
    }

    class Trun() {
        var chunkDuration: Int = 0
        var chunkSize: Int = 0
        var bFlags: Int = 0
        var bFirstSampleFlags: Int = 0
        var dataOffset: Int = 0
        var entryCount: Int = 0
        var bEntries: ByteArray
        var entriesRowSize: Int = 0
        fun getEntry(i: Int): TrunEntry {
            val buffer: ByteBuffer = ByteBuffer.wrap(bEntries, i * entriesRowSize, entriesRowSize)
            val entry: TrunEntry = TrunEntry()
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

        fun getAbsoluteEntry(i: Int, header: Tfhd?): TrunEntry {
            val entry: TrunEntry = getEntry(i)
            if (!hasFlag(bFlags, 0x0100) && hasFlag(header!!.bFlags, 0x20)) {
                entry.sampleFlags = header.defaultSampleFlags
            }
            if (!hasFlag(bFlags, 0x0200) && hasFlag(header!!.bFlags, 0x10)) {
                entry.sampleSize = header.defaultSampleSize
            }
            if (!hasFlag(bFlags, 0x0100) && hasFlag(header!!.bFlags, 0x08)) {
                entry.sampleDuration = header.defaultSampleDuration
            }
            if (i == 0 && hasFlag(bFlags, 0x0004)) {
                entry.sampleFlags = bFirstSampleFlags
            }
            return entry
        }
    }

    class Tkhd() {
        var trackId: Int = 0
        var duration: Long = 0
        var bVolume: Short = 0
        var bWidth: Int = 0
        var bHeight: Int = 0
        var matrix: ByteArray
        var bLayer: Short = 0
        var bAlternateGroup: Short = 0
    }

    class Trak() {
        var tkhd: Tkhd? = null
        var edstElst: Elst? = null
        var mdia: Mdia? = null
    }

    internal class Mvhd() {
        var timeScale: Long = 0
        var nextTrackId: Long = 0
    }

    internal class Moov() {
        var mvhd: Mvhd? = null
        var trak: Array<Trak>
        var mvexTrex: Array<Trex>?
    }

    class Trex() {
        val trackId: Int = 0
        var defaultSampleDescriptionIndex: Int = 0
        var defaultSampleDuration: Int = 0
        var defaultSampleSize: Int = 0
        var defaultSampleFlags: Int = 0
    }

    class Elst() {
        var mediaTime: Long = 0
        var bMediaRate: Int = 0
    }

    class Mdia() {
        var mdhdTimeScale: Int = 0
        var mdhd: ByteArray
        var hdlr: Hdlr? = null
        var minf: Minf? = null
    }

    class Hdlr() {
        var type: Int = 0
        var subType: Int = 0
        var bReserved: ByteArray
    }

    class Minf() {
        var dinf: ByteArray
        var stblStsd: ByteArray
        var mhd: ByteArray
    }

    class Mp4Track() {
        var kind: TrackKind? = null
        var trak: Trak? = null
        var trex: Trex? = null
    }

    class Mp4DashChunk() {
        var data: InputStream? = null
        var moof: Moof? = null
        private var i: Int = 0
        fun getNextSampleInfo(): TrunEntry? {
            if (i >= moof!!.traf!!.trun!!.entryCount) {
                return null
            }
            return moof!!.traf!!.trun!!.getAbsoluteEntry(i++, moof!!.traf!!.tfhd)
        }

        @Throws(IOException::class)
        fun getNextSample(): Mp4DashSample? {
            if (data == null) {
                throw IllegalStateException("This chunk has info only")
            }
            if (i >= moof!!.traf!!.trun!!.entryCount) {
                return null
            }
            val sample: Mp4DashSample = Mp4DashSample()
            sample.info = moof!!.traf!!.trun!!.getAbsoluteEntry(i++, moof!!.traf!!.tfhd)
            sample.data = ByteArray(sample.info!!.sampleSize)
            if (data!!.read(sample.data) != sample.info!!.sampleSize) {
                throw EOFException("EOF reached while reading a sample")
            }
            return sample
        }
    }

    class Mp4DashSample() {
        var info: TrunEntry? = null
        var data: ByteArray
    }

    companion object {
        private val ATOM_MOOF: Int = 0x6D6F6F66
        private val ATOM_MFHD: Int = 0x6D666864
        private val ATOM_TRAF: Int = 0x74726166
        private val ATOM_TFHD: Int = 0x74666864
        private val ATOM_TFDT: Int = 0x74666474
        private val ATOM_TRUN: Int = 0x7472756E
        private val ATOM_MDIA: Int = 0x6D646961
        private val ATOM_FTYP: Int = 0x66747970
        private val ATOM_SIDX: Int = 0x73696478
        private val ATOM_MOOV: Int = 0x6D6F6F76
        private val ATOM_MDAT: Int = 0x6D646174
        private val ATOM_MVHD: Int = 0x6D766864
        private val ATOM_TRAK: Int = 0x7472616B
        private val ATOM_MVEX: Int = 0x6D766578
        private val ATOM_TREX: Int = 0x74726578
        private val ATOM_TKHD: Int = 0x746B6864
        private val ATOM_MFRA: Int = 0x6D667261
        private val ATOM_MDHD: Int = 0x6D646864
        private val ATOM_EDTS: Int = 0x65647473
        private val ATOM_ELST: Int = 0x656C7374
        private val ATOM_HDLR: Int = 0x68646C72
        private val ATOM_MINF: Int = 0x6D696E66
        private val ATOM_DINF: Int = 0x64696E66
        private val ATOM_STBL: Int = 0x7374626C
        private val ATOM_STSD: Int = 0x73747364
        private val ATOM_VMHD: Int = 0x766D6864
        private val ATOM_SMHD: Int = 0x736D6864
        private val BRAND_DASH: Int = 0x64617368
        private val BRAND_ISO5: Int = 0x69736F35
        private val HANDLER_VIDE: Int = 0x76696465
        private val HANDLER_SOUN: Int = 0x736F756E
        private val HANDLER_SUBT: Int = 0x73756274
        fun hasFlag(flags: Int, mask: Int): Boolean {
            return (flags and mask) == mask
        }
    }
}
