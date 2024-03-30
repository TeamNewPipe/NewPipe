package org.schabi.newpipe.streams

import org.schabi.newpipe.streams.io.SharpStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 *
 * @author kapodamy
 */
class WebMReader(source: SharpStream) {
    enum class TrackKind {
        Audio /*2*/,
        Video /*1*/,
        Other
    }

    private val stream: DataReader
    private var segment: Segment? = null
    private var tracks: Array<WebMTrack?>?
    private var selectedTrack: Int = 0
    private var done: Boolean = false
    private var firstSegment: Boolean = false

    init {
        stream = DataReader(source)
    }

    @Throws(IOException::class)
    fun parse() {
        var elem: Element? = readElement(ID_EMBL)
        if (!readEbml(elem, 1, 2)) {
            throw UnsupportedOperationException("Unsupported EBML data (WebM)")
        }
        ensure(elem)
        elem = untilElement(null, ID_SEGMENT)
        if (elem == null) {
            throw IOException("Fragment element not found")
        }
        segment = readSegment(elem, 0, true)
        tracks = segment!!.tracks
        selectedTrack = -1
        done = false
        firstSegment = true
    }

    fun getAvailableTracks(): Array<WebMTrack?>? {
        return tracks
    }

    fun selectTrack(index: Int): WebMTrack? {
        selectedTrack = index
        return tracks!!.get(index)
    }

    @Throws(IOException::class)
    fun getNextSegment(): Segment? {
        if (done) {
            return null
        }
        if (firstSegment && segment != null) {
            firstSegment = false
            return segment
        }
        ensure(segment!!.ref)
        // WARNING: track cannot be the same or have different index in new segments
        val elem: Element? = untilElement(null, ID_SEGMENT)
        if (elem == null) {
            done = true
            return null
        }
        segment = readSegment(elem, 0, false)
        return segment
    }

    @Throws(IOException::class)
    private fun readNumber(parent: Element): Long {
        var length: Int = parent.contentSize.toInt()
        var value: Long = 0
        while (length-- > 0) {
            val read: Int = stream.read()
            if (read == -1) {
                throw EOFException()
            }
            value = (value shl 8) or read.toLong()
        }
        return value
    }

    @Throws(IOException::class)
    private fun readString(parent: Element): String {
        return String(readBlob(parent), StandardCharsets.UTF_8) // or use "utf-8"
    }

    @Throws(IOException::class)
    private fun readBlob(parent: Element): ByteArray {
        val length: Long = parent.contentSize
        val buffer: ByteArray = ByteArray(length.toInt())
        val read: Int = stream.read(buffer)
        if (read < length) {
            throw EOFException()
        }
        return buffer
    }

    @Throws(IOException::class)
    private fun readEncodedNumber(): Long {
        var value: Int = stream.read()
        if (value > 0) {
            var size: Byte = 1
            var mask: Int = 0x80
            while (size < 9) {
                if ((value and mask) == mask) {
                    mask = 0xFF
                    mask = mask shr size.toInt()
                    var number: Long = (value and mask).toLong()
                    for (i in 1 until size) {
                        value = stream.read()
                        number = number shl 8
                        number = number or value.toLong()
                    }
                    return number
                }
                mask = mask shr 1
                size++
            }
        }
        throw IOException("Invalid encoded length")
    }

    @Throws(IOException::class)
    private fun readElement(): Element {
        val elem: Element = Element()
        elem.offset = stream.position()
        elem.type = readEncodedNumber().toInt()
        elem.contentSize = readEncodedNumber()
        elem.size = elem.contentSize + stream.position() - elem.offset
        return elem
    }

    @Throws(IOException::class)
    private fun readElement(expected: Int): Element {
        val elem: Element = readElement()
        if (expected != 0 && elem.type != expected) {
            throw NoSuchElementException(("expected " + elementID(expected.toLong())
                    + " found " + elementID(elem.type.toLong())))
        }
        return elem
    }

    @Throws(IOException::class)
    private fun untilElement(ref: Element?, vararg expected: Int): Element? {
        var elem: Element
        while (if (ref == null) stream.available() else (stream.position() < (ref.offset + ref.size))) {
            elem = readElement()
            if (expected.size < 1) {
                return elem
            }
            for (type: Int in expected) {
                if (elem.type == type) {
                    return elem
                }
            }
            ensure(elem)
        }
        return null
    }

    private fun elementID(type: Long): String {
        return "0x" + java.lang.Long.toHexString(type)
    }

    @Throws(IOException::class)
    private fun ensure(ref: Element?) {
        val skip: Long = (ref!!.offset + ref.size) - stream.position()
        if (skip == 0L) {
            return
        } else if (skip < 0) {
            throw EOFException(String.format(
                    "parser go beyond limits of the Element. type=%s offset=%s size=%s position=%s",
                    elementID(ref.type.toLong()), ref.offset, ref.size, stream.position()
            ))
        }
        stream.skipBytes(skip)
    }

    @Throws(IOException::class)
    private fun readEbml(ref: Element?, minReadVersion: Int,
                         minDocTypeVersion: Int): Boolean {
        var elem: Element? = untilElement(ref, ID_EMBL_READ_VERSION)
        if (elem == null) {
            return false
        }
        if (readNumber(elem) > minReadVersion) {
            return false
        }
        elem = untilElement(ref, ID_EMBL_DOC_TYPE)
        if (elem == null) {
            return false
        }
        if (!(readString(elem) == "webm")) {
            return false
        }
        elem = untilElement(ref, ID_EMBL_DOC_TYPE_READ_VERSION)
        return elem != null && readNumber(elem) <= minDocTypeVersion
    }

    @Throws(IOException::class)
    private fun readInfo(ref: Element): Info {
        var elem: Element
        val info: Info = Info()
        while ((untilElement(ref, ID_TIMECODE_SCALE, ID_DURATION).also({ elem = (it)!! })) != null) {
            when (elem.type) {
                ID_TIMECODE_SCALE -> info.timecodeScale = readNumber(elem)
                ID_DURATION -> info.duration = readNumber(elem)
            }
            ensure(elem)
        }
        if (info.timecodeScale == 0L) {
            throw NoSuchElementException("Element Timecode not found")
        }
        return info
    }

    @Throws(IOException::class)
    private fun readSegment(ref: Element, trackLacingExpected: Int,
                            metadataExpected: Boolean): Segment {
        val obj: Segment = Segment(ref)
        var elem: Element
        while ((untilElement(ref, ID_INFO, ID_TRACKS, ID_CLUSTER).also({ elem = (it)!! })) != null) {
            if (elem.type == ID_CLUSTER) {
                obj.currentCluster = elem
                break
            }
            when (elem.type) {
                ID_INFO -> obj.info = readInfo(elem)
                ID_TRACKS -> obj.tracks = readTracks(elem, trackLacingExpected)
            }
            ensure(elem)
        }
        if (metadataExpected && (obj.info == null || obj.tracks == null)) {
            throw RuntimeException((
                    "Cluster element found without Info and/or Tracks element at position "
                            + ref.offset))
        }
        return obj
    }

    @Throws(IOException::class)
    private fun readTracks(ref: Element, lacingExpected: Int): Array<WebMTrack?> {
        val trackEntries: ArrayList<WebMTrack?> = ArrayList(2)
        var elemTrackEntry: Element?
        while ((untilElement(ref, ID_TRACK_ENTRY).also({ elemTrackEntry = it })) != null) {
            val entry: WebMTrack = WebMTrack()
            var drop: Boolean = false
            var elem: Element
            while ((untilElement(elemTrackEntry).also({ elem = (it)!! })) != null) {
                when (elem.type) {
                    ID_TRACK_NUMBER -> entry.trackNumber = readNumber(elem)
                    ID_TRACK_TYPE -> entry.trackType = readNumber(elem).toInt()
                    ID_CODEC_ID -> entry.codecId = readString(elem)
                    ID_CODEC_PRIVATE -> entry.codecPrivate = readBlob(elem)
                    ID_AUDIO, ID_VIDEO -> entry.bMetadata = readBlob(elem)
                    ID_DEFAULT_DURATION -> entry.defaultDuration = readNumber(elem)
                    ID_FLAG_LACING -> drop = readNumber(elem) != lacingExpected.toLong()
                    ID_CODEC_DELAY -> entry.codecDelay = readNumber(elem)
                    ID_SEEK_PRE_ROLL -> entry.seekPreRoll = readNumber(elem)
                    else -> {}
                }
                ensure(elem)
            }
            if (!drop) {
                trackEntries.add(entry)
            }
            ensure(elemTrackEntry)
        }
        val entries: Array<WebMTrack?> = trackEntries.toTypedArray<WebMTrack?>()
        for (entry: WebMTrack? in entries) {
            when (entry!!.trackType) {
                1 -> entry.kind = TrackKind.Video
                2 -> entry.kind = TrackKind.Audio
                else -> entry.kind = TrackKind.Other
            }
        }
        return entries
    }

    @Throws(IOException::class)
    private fun readSimpleBlock(ref: Element): SimpleBlock {
        val obj: SimpleBlock = SimpleBlock(ref)
        obj.trackNumber = readEncodedNumber()
        obj.relativeTimeCode = stream.readShort()
        obj.flags = stream.read().toByte()
        obj.dataSize = ((ref.offset + ref.size) - stream.position()).toInt()
        obj.createdFromBlock = ref.type == ID_BLOCK

        // NOTE: lacing is not implemented, and will be mixed with the stream data
        if (obj.dataSize < 0) {
            throw IOException(String.format(
                    "Unexpected SimpleBlock element size, missing %s bytes", -obj.dataSize))
        }
        return obj
    }

    @Throws(IOException::class)
    private fun readCluster(ref: Element?): Cluster {
        val obj: Cluster = Cluster(ref)
        val elem: Element? = untilElement(ref, ID_TIMECODE)
        if (elem == null) {
            throw NoSuchElementException(("Cluster at " + ref!!.offset
                    + " without Timecode element"))
        }
        obj.timecode = readNumber(elem)
        return obj
    }

    class Element() {
        var type: Int = 0
        var offset: Long = 0
        var contentSize: Long = 0
        var size: Long = 0
    }

    class Info() {
        var timecodeScale: Long = 0
        var duration: Long = 0
    }

    class WebMTrack() {
        var trackNumber: Long = 0
        var trackType: Int = 0
        var codecId: String? = null
        var codecPrivate: ByteArray?
        var bMetadata: ByteArray
        var kind: TrackKind? = null
        var defaultDuration: Long = -1
        var codecDelay: Long = -1
        var seekPreRoll: Long = -1
    }

    inner class Segment internal constructor(val ref: Element) {
        var info: Info? = null
        var tracks: Array<WebMTrack?>?
        private var currentCluster: Element? = null
        var firstClusterInSegment: Boolean = true
        @Throws(IOException::class)
        fun getNextCluster(): Cluster? {
            if (done) {
                return null
            }
            if (firstClusterInSegment && segment!!.currentCluster != null) {
                firstClusterInSegment = false
                return readCluster(segment!!.currentCluster)
            }
            ensure(segment!!.currentCluster)
            val elem: Element? = untilElement(segment!!.ref, ID_CLUSTER)
            if (elem == null) {
                return null
            }
            segment!!.currentCluster = elem
            return readCluster(segment!!.currentCluster)
        }
    }

    class SimpleBlock internal constructor(val ref: Element) {
        var data: InputStream? = null
        var createdFromBlock: Boolean = false
        var trackNumber: Long = 0
        var relativeTimeCode: Short = 0
        var absoluteTimeCodeNs: Long = 0
        var flags: Byte = 0
        var dataSize: Int = 0
        fun isKeyframe(): Boolean {
            return (flags.toInt() and 0x80) == 0x80
        }
    }

    inner class Cluster internal constructor(var ref: Element?) {
        var currentSimpleBlock: SimpleBlock? = null
        var currentBlockGroup: Element? = null
        var timecode: Long = 0
        fun insideClusterBounds(): Boolean {
            return stream.position() >= (ref!!.offset + ref!!.size)
        }

        @Throws(IOException::class)
        fun getNextSimpleBlock(): SimpleBlock? {
            if (insideClusterBounds()) {
                return null
            }
            if (currentBlockGroup != null) {
                ensure(currentBlockGroup)
                currentBlockGroup = null
                currentSimpleBlock = null
            } else if (currentSimpleBlock != null) {
                ensure(currentSimpleBlock!!.ref)
            }
            while (!insideClusterBounds()) {
                var elem: Element? = untilElement(ref, ID_SIMPLE_BLOCK, ID_GROUP_BLOCK)
                if (elem == null) {
                    return null
                }
                if (elem.type == ID_GROUP_BLOCK) {
                    currentBlockGroup = elem
                    elem = untilElement(currentBlockGroup, ID_BLOCK)
                    if (elem == null) {
                        ensure(currentBlockGroup)
                        currentBlockGroup = null
                        continue
                    }
                }
                currentSimpleBlock = readSimpleBlock(elem)
                if (currentSimpleBlock!!.trackNumber == tracks!!.get(selectedTrack)!!.trackNumber) {
                    currentSimpleBlock!!.data = stream.getView(currentSimpleBlock!!.dataSize)

                    // calculate the timestamp in nanoseconds
                    currentSimpleBlock!!.absoluteTimeCodeNs = (currentSimpleBlock!!.relativeTimeCode
                            + timecode)
                    currentSimpleBlock!!.absoluteTimeCodeNs *= segment!!.info!!.timecodeScale
                    return currentSimpleBlock
                }
                ensure(elem)
            }
            return null
        }
    }

    companion object {
        private val ID_EMBL: Int = 0x0A45DFA3
        private val ID_EMBL_READ_VERSION: Int = 0x02F7
        private val ID_EMBL_DOC_TYPE: Int = 0x0282
        private val ID_EMBL_DOC_TYPE_READ_VERSION: Int = 0x0285
        private val ID_SEGMENT: Int = 0x08538067
        private val ID_INFO: Int = 0x0549A966
        private val ID_TIMECODE_SCALE: Int = 0x0AD7B1
        private val ID_DURATION: Int = 0x489
        private val ID_TRACKS: Int = 0x0654AE6B
        private val ID_TRACK_ENTRY: Int = 0x2E
        private val ID_TRACK_NUMBER: Int = 0x57
        private val ID_TRACK_TYPE: Int = 0x03
        private val ID_CODEC_ID: Int = 0x06
        private val ID_CODEC_PRIVATE: Int = 0x23A2
        private val ID_VIDEO: Int = 0x60
        private val ID_AUDIO: Int = 0x61
        private val ID_DEFAULT_DURATION: Int = 0x3E383
        private val ID_FLAG_LACING: Int = 0x1C
        private val ID_CODEC_DELAY: Int = 0x16AA
        private val ID_SEEK_PRE_ROLL: Int = 0x16BB
        private val ID_CLUSTER: Int = 0x0F43B675
        private val ID_TIMECODE: Int = 0x67
        private val ID_SIMPLE_BLOCK: Int = 0x23
        private val ID_BLOCK: Int = 0x21
        private val ID_GROUP_BLOCK: Int = 0x20
    }
}
