package org.schabi.newpipe.streams

import org.schabi.newpipe.streams.WebMReader.Cluster
import org.schabi.newpipe.streams.WebMReader.SimpleBlock
import org.schabi.newpipe.streams.WebMReader.WebMTrack
import org.schabi.newpipe.streams.io.SharpStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.ceil
import kotlin.math.min

/**
 * @author kapodamy
 */
class WebMWriter(vararg source: SharpStream) : Closeable {
    private var infoTracks: Array<WebMTrack?>?
    private var sourceTracks: Array<SharpStream>?
    private var readers: Array<WebMReader?>?
    private var done: Boolean = false
    private var parsed: Boolean = false
    private var written: Long = 0
    private var readersSegment: Array<WebMReader.Segment?>?
    private var readersCluster: Array<Cluster?>?
    private var clustersOffsetsSizes: ArrayList<ClusterInfo>?
    private var outBuffer: ByteArray?
    private var outByteBuffer: ByteBuffer?

    init {
        sourceTracks = source
        readers = arrayOfNulls(sourceTracks!!.size)
        infoTracks = arrayOfNulls(sourceTracks!!.size)
        outBuffer = ByteArray(BUFFER_SIZE)
        outByteBuffer = ByteBuffer.wrap(outBuffer)
        clustersOffsetsSizes = ArrayList(256)
    }

    @Throws(IllegalStateException::class)
    fun getTracksFromSource(sourceIndex: Int): Array<WebMTrack?>? {
        if (done) {
            throw IllegalStateException("already done")
        }
        if (!parsed) {
            throw IllegalStateException("All sources must be parsed first")
        }
        return readers!!.get(sourceIndex)!!.getAvailableTracks()
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun parseSources() {
        if (done) {
            throw IllegalStateException("already done")
        }
        if (parsed) {
            throw IllegalStateException("already parsed")
        }
        try {
            for (i in readers!!.indices) {
                readers!!.get(i) = WebMReader(sourceTracks!!.get(i))
                readers!!.get(i)!!.parse()
            }
        } finally {
            parsed = true
        }
    }

    @Throws(IOException::class)
    fun selectTracks(vararg trackIndex: Int) {
        try {
            readersSegment = arrayOfNulls(readers!!.size)
            readersCluster = arrayOfNulls(readers!!.size)
            for (i in readers!!.indices) {
                infoTracks!!.get(i) = readers!!.get(i)!!.selectTrack(trackIndex.get(i))
                readersSegment!!.get(i) = readers!!.get(i)!!.getNextSegment()
            }
        } finally {
            parsed = true
        }
    }

    fun isDone(): Boolean {
        return done
    }

    public override fun close() {
        done = true
        parsed = true
        for (src: SharpStream in sourceTracks!!) {
            src.close()
        }
        sourceTracks = null
        readers = null
        infoTracks = null
        readersSegment = null
        readersCluster = null
        outBuffer = null
        outByteBuffer = null
        clustersOffsetsSizes = null
    }

    @Throws(IOException::class, RuntimeException::class)
    fun build(out: SharpStream?) {
        if (!out!!.canRewind()) {
            throw IOException("The output stream must be allow seek")
        }
        makeEBML(out)
        val offsetSegmentSizeSet: Long = written + 5
        val offsetInfoDurationSet: Long = written + 94
        val offsetClusterSet: Long = written + 58
        val offsetCuesSet: Long = written + 75
        val listBuffer: ArrayList<ByteArray?> = ArrayList(4)

        /* segment */listBuffer.add(byteArrayOf(
                0x18, 0x53, 0x80.toByte(), 0x67, 0x01,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // segment content size
        ))
        val segmentOffset: Long = written + listBuffer.get(0)!!.size

        /* seek head */listBuffer.add(byteArrayOf(
                0x11, 0x4d, 0x9b.toByte(), 0x74, 0xbe.toByte(),
                0x4d, 0xbb.toByte(), 0x8b.toByte(),
                0x53, 0xab.toByte(), 0x84.toByte(), 0x15, 0x49, 0xa9.toByte(), 0x66, 0x53, 0xac.toByte(), 0x81.toByte(),  /*info offset*/
                0x43,
                0x4d, 0xbb.toByte(), 0x8b.toByte(), 0x53, 0xab.toByte(), 0x84.toByte(), 0x16, 0x54, 0xae.toByte(), 0x6b, 0x53, 0xac.toByte(), 0x81.toByte(),  /*tracks offset*/
                0x56,
                0x4d, 0xbb.toByte(), 0x8e.toByte(), 0x53, 0xab.toByte(), 0x84.toByte(), 0x1f,
                0x43, 0xb6.toByte(), 0x75, 0x53, 0xac.toByte(), 0x84.toByte(),  /*cluster offset [2]*/
                0x00, 0x00, 0x00, 0x00,
                0x4d, 0xbb.toByte(), 0x8e.toByte(), 0x53, 0xab.toByte(), 0x84.toByte(), 0x1c, 0x53, 0xbb.toByte(), 0x6b, 0x53, 0xac.toByte(), 0x84.toByte(),  /*cues offset [7]*/
                0x00, 0x00, 0x00, 0x00
        ))

        /* info */listBuffer.add(byteArrayOf(
                0x15, 0x49, 0xa9.toByte(), 0x66, 0x8e.toByte(), 0x2a, 0xd7.toByte(), 0xb1.toByte()))
        // the segment duration MUST NOT exceed 4 bytes
        listBuffer.add(encode(DEFAULT_TIMECODE_SCALE.toLong(), true))
        listBuffer.add(byteArrayOf(0x44, 0x89.toByte(), 0x84.toByte(),
                0x00, 0x00, 0x00, 0x00))

        /* tracks */listBuffer.addAll(makeTracks())
        dump(listBuffer, out)

        // reserve space for Cues element
        val cueOffset: Long = written
        makeEbmlVoid(out, CUE_RESERVE_SIZE, true)
        val defaultSampleDuration: IntArray = IntArray(infoTracks!!.size)
        val duration: LongArray = LongArray(infoTracks!!.size)
        for (i in infoTracks!!.indices) {
            if (infoTracks!!.get(i)!!.defaultDuration < 0) {
                defaultSampleDuration.get(i) = -1 // not available
            } else {
                defaultSampleDuration.get(i) = ceil((infoTracks!!.get(i)!!.defaultDuration
                        / DEFAULT_TIMECODE_SCALE.toFloat()).toDouble()).toInt()
            }
            duration.get(i) = -1
        }

        // Select a track for the cue
        val cuesForTrackId: Int = selectTrackForCue()
        var nextCueTime: Long = (if (infoTracks!!.get(cuesForTrackId)!!.trackType == 1) -1 else 0).toLong()
        val keyFrames: ArrayList<KeyFrame> = ArrayList(32)
        var firstClusterOffset: Int = written.toInt()
        var currentClusterOffset: Long = makeCluster(out, 0, 0, true)
        var baseTimecode: Long = 0
        var limitTimecode: Long = -1
        var limitTimecodeByTrackId: Int = cuesForTrackId
        var blockWritten: Int = Int.MAX_VALUE
        var newClusterByTrackId: Int = -1
        while (blockWritten > 0) {
            blockWritten = 0
            var i: Int = 0
            while (i < readers!!.size) {
                val bloq: Block? = getNextBlockFrom(i)
                if (bloq == null) {
                    i++
                    continue
                }
                if (bloq.data == null) {
                    blockWritten = 1 // fake block
                    newClusterByTrackId = i
                    i++
                    continue
                }
                if (newClusterByTrackId == i) {
                    limitTimecodeByTrackId = i
                    newClusterByTrackId = -1
                    baseTimecode = bloq.absoluteTimecode
                    limitTimecode = baseTimecode + INTERV
                    currentClusterOffset = makeCluster(out, baseTimecode, currentClusterOffset,
                            true)
                }
                if (cuesForTrackId == i) {
                    if (((nextCueTime > -1 && bloq.absoluteTimecode >= nextCueTime)
                                    || (nextCueTime < 0 && bloq.isKeyframe()))) {
                        if (nextCueTime > -1) {
                            nextCueTime += DEFAULT_CUES_EACH_MS.toLong()
                        }
                        keyFrames.add(KeyFrame(segmentOffset, currentClusterOffset, written,
                                bloq.absoluteTimecode))
                    }
                }
                writeBlock(out, bloq, baseTimecode)
                blockWritten++
                if (defaultSampleDuration.get(i) < 0 && duration.get(i) >= 0) {
                    // if the sample duration in unknown,
                    // calculate using current_duration - previous_duration
                    defaultSampleDuration.get(i) = (bloq.absoluteTimecode - duration.get(i)).toInt()
                }
                duration.get(i) = bloq.absoluteTimecode
                if (limitTimecode < 0) {
                    limitTimecode = bloq.absoluteTimecode + INTERV
                    continue
                }
                if (bloq.absoluteTimecode >= limitTimecode) {
                    if (limitTimecodeByTrackId != i) {
                        limitTimecode += INTERV - (bloq.absoluteTimecode - limitTimecode)
                    }
                    i++
                }
            }
        }
        makeCluster(out, -1, currentClusterOffset, false)
        val segmentSize: Long = written - offsetSegmentSizeSet - 7

        /* Segment size */seekTo(out, offsetSegmentSizeSet)
        outByteBuffer!!.putLong(0, segmentSize)
        out.write(outBuffer, 1, DataReader.Companion.LONG_SIZE - 1)

        /* Segment duration */
        var longestDuration: Long = 0
        for (i in duration.indices) {
            if (defaultSampleDuration.get(i) > 0) {
                duration.get(i) += defaultSampleDuration.get(i).toLong()
            }
            if (duration.get(i) > longestDuration) {
                longestDuration = duration.get(i)
            }
        }
        seekTo(out, offsetInfoDurationSet)
        outByteBuffer!!.putFloat(0, longestDuration.toFloat())
        dump(outBuffer, DataReader.Companion.FLOAT_SIZE, out)

        /* first Cluster offset */firstClusterOffset -= segmentOffset.toInt()
        writeInt(out, offsetClusterSet, firstClusterOffset)
        seekTo(out, cueOffset)

        /* Cue */
        var cueSize: Short = 0
        dump(byteArrayOf(0x1c, 0x53, 0xbb.toByte(), 0x6b, 0x20, 0x00, 0x00), out) // header size is 7
        for (keyFrame: KeyFrame in keyFrames) {
            val size: Int = makeCuePoint(cuesForTrackId, keyFrame, outBuffer)
            if ((cueSize + size + 7 + MINIMUM_EBML_VOID_SIZE) > CUE_RESERVE_SIZE) {
                break // no space left
            }
            cueSize = (cueSize + size).toShort()
            dump(outBuffer, size, out)
        }
        makeEbmlVoid(out, CUE_RESERVE_SIZE - cueSize - 7, false)
        seekTo(out, cueOffset + 5)
        outByteBuffer!!.putShort(0, cueSize)
        dump(outBuffer, DataReader.Companion.SHORT_SIZE, out)

        /* seek head, seek for cues element */writeInt(out, offsetCuesSet, (cueOffset - segmentOffset).toInt())
        for (cluster: ClusterInfo in clustersOffsetsSizes!!) {
            writeInt(out, cluster.offset, cluster.size or 0x10000000)
        }
    }

    @Throws(IOException::class)
    private fun getNextBlockFrom(internalTrackId: Int): Block? {
        if (readersSegment!!.get(internalTrackId) == null) {
            readersSegment!!.get(internalTrackId) = readers!!.get(internalTrackId)!!.getNextSegment()
            if (readersSegment!!.get(internalTrackId) == null) {
                return null // no more blocks in the selected track
            }
        }
        if (readersCluster!!.get(internalTrackId) == null) {
            readersCluster!!.get(internalTrackId) = readersSegment!!.get(internalTrackId)!!.getNextCluster()
            if (readersCluster!!.get(internalTrackId) == null) {
                readersSegment!!.get(internalTrackId) = null
                return getNextBlockFrom(internalTrackId)
            }
        }
        val res: SimpleBlock? = readersCluster!!.get(internalTrackId)!!.getNextSimpleBlock()
        if (res == null) {
            readersCluster!!.get(internalTrackId) = null
            return Block() // fake block to indicate the end of the cluster
        }
        val bloq: Block = Block()
        bloq.data = res.data
        bloq.dataSize = res.dataSize
        bloq.trackNumber = internalTrackId
        bloq.flags = res.flags
        bloq.absoluteTimecode = res.absoluteTimeCodeNs / DEFAULT_TIMECODE_SCALE
        return bloq
    }

    @Throws(IOException::class)
    private fun seekTo(stream: SharpStream?, offset: Long) {
        if (stream!!.canSeek()) {
            stream.seek(offset)
        } else {
            if (offset > written) {
                stream.skip(offset - written)
            } else {
                stream.rewind()
                stream.skip(offset)
            }
        }
        written = offset
    }

    @Throws(IOException::class)
    private fun writeInt(stream: SharpStream?, offset: Long, number: Int) {
        seekTo(stream, offset)
        outByteBuffer!!.putInt(0, number)
        dump(outBuffer, DataReader.Companion.INTEGER_SIZE, stream)
    }

    @Throws(IOException::class)
    private fun writeBlock(stream: SharpStream?, bloq: Block, clusterTimecode: Long) {
        val relativeTimeCode: Long = bloq.absoluteTimecode - clusterTimecode
        if (relativeTimeCode < Short.MIN_VALUE || relativeTimeCode > Short.MAX_VALUE) {
            throw IndexOutOfBoundsException("SimpleBlock timecode overflow.")
        }
        val listBuffer: ArrayList<ByteArray?> = ArrayList(5)
        listBuffer.add(byteArrayOf(0xa3.toByte()))
        listBuffer.add(null) // block size
        listBuffer.add(encode((bloq.trackNumber + 1).toLong(), false))
        listBuffer.add(ByteBuffer.allocate(DataReader.Companion.SHORT_SIZE).putShort(relativeTimeCode.toShort())
                .array())
        listBuffer.add(byteArrayOf(bloq.flags))
        var blockSize: Int = bloq.dataSize
        for (i in 2 until listBuffer.size) {
            blockSize += listBuffer.get(i)!!.size
        }
        listBuffer.set(1, encode(blockSize.toLong(), false))
        dump(listBuffer, stream)
        var read: Int
        while ((bloq.data!!.read(outBuffer).also({ read = it })) > 0) {
            dump(outBuffer, read, stream)
        }
    }

    @Throws(IOException::class)
    private fun makeCluster(stream: SharpStream?, timecode: Long, offsetStart: Long,
                            create: Boolean): Long {
        var cluster: ClusterInfo
        var offset: Long = offsetStart
        if (offset > 0) {
            // save the size of the previous cluster (maximum 256 MiB)
            cluster = clustersOffsetsSizes!!.get(clustersOffsetsSizes!!.size - 1)
            cluster.size = (written - offset - CLUSTER_HEADER_SIZE).toInt()
        }
        offset = written
        if (create) {
            /* cluster */
            dump(byteArrayOf(0x1f, 0x43, 0xb6.toByte(), 0x75), stream)
            cluster = ClusterInfo()
            cluster.offset = written
            clustersOffsetsSizes!!.add(cluster)
            dump(byteArrayOf(
                    0x10, 0x00, 0x00, 0x00, 0xe7.toByte()), stream)
            dump(encode(timecode, true), stream)
        }
        return offset
    }

    @Throws(IOException::class)
    private fun makeEBML(stream: SharpStream?) {
        // default values
        dump(byteArrayOf(
                0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x1F, 0x42, 0x86.toByte(), 0x81.toByte(), 0x01,
                0x42, 0xF7.toByte(), 0x81.toByte(), 0x01, 0x42, 0xF2.toByte(), 0x81.toByte(), 0x04,
                0x42, 0xF3.toByte(), 0x81.toByte(), 0x08, 0x42, 0x82.toByte(), 0x84.toByte(), 0x77,
                0x65, 0x62, 0x6D, 0x42, 0x87.toByte(), 0x81.toByte(), 0x02,
                0x42, 0x85.toByte(), 0x81.toByte(), 0x02
        ), stream)
    }

    private fun makeTracks(): ArrayList<ByteArray?> {
        val buffer: ArrayList<ByteArray?> = ArrayList(1)
        buffer.add(byteArrayOf(0x16, 0x54, 0xae.toByte(), 0x6b))
        buffer.add(null)
        for (i in infoTracks!!.indices) {
            buffer.addAll(makeTrackEntry(i, infoTracks!!.get(i)))
        }
        return lengthFor(buffer)
    }

    private fun makeTrackEntry(internalTrackId: Int, track: WebMTrack?): ArrayList<ByteArray?> {
        val id: ByteArray = encode((internalTrackId + 1).toLong(), true)
        val buffer: ArrayList<ByteArray?> = ArrayList(12)

        /* track */buffer.add(byteArrayOf(0xae.toByte()))
        buffer.add(null)

        /* track number */buffer.add(byteArrayOf(0xd7.toByte()))
        buffer.add(id)

        /* track uid */buffer.add(byteArrayOf(0x73, 0xc5.toByte()))
        buffer.add(id)

        /* flag lacing */buffer.add(byteArrayOf(0x9c.toByte(), 0x81.toByte(), 0x00))

        /* lang */buffer.add(byteArrayOf(0x22, 0xb5.toByte(), 0x9c.toByte(), 0x83.toByte(), 0x75, 0x6e, 0x64))

        /* codec id */buffer.add(byteArrayOf(0x86.toByte()))
        buffer.addAll(encode(track!!.codecId))

        /* codec delay*/if (track.codecDelay >= 0) {
            buffer.add(byteArrayOf(0x56, 0xAA.toByte()))
            buffer.add(encode(track.codecDelay, true))
        }

        /* codec seek pre-roll*/if (track.seekPreRoll >= 0) {
            buffer.add(byteArrayOf(0x56, 0xBB.toByte()))
            buffer.add(encode(track.seekPreRoll, true))
        }

        /* type */buffer.add(byteArrayOf(0x83.toByte()))
        buffer.add(encode(track.trackType.toLong(), true))

        /* default duration */if (track.defaultDuration >= 0) {
            buffer.add(byteArrayOf(0x23, 0xe3.toByte(), 0x83.toByte()))
            buffer.add(encode(track.defaultDuration, true))
        }

        /* audio/video */if ((track.trackType == 1 || track.trackType == 2) && valid(track.bMetadata)) {
            buffer.add(byteArrayOf((if (track.trackType == 1) 0xe0 else 0xe1).toByte()))
            buffer.add(encode(track.bMetadata.size.toLong(), false))
            buffer.add(track.bMetadata)
        }

        /* codec private*/if (valid(track.codecPrivate)) {
            buffer.add(byteArrayOf(0x63, 0xa2.toByte()))
            buffer.add(encode(track.codecPrivate!!.size.toLong(), false))
            buffer.add(track.codecPrivate)
        }
        return lengthFor(buffer)
    }

    private fun makeCuePoint(internalTrackId: Int, keyFrame: KeyFrame,
                             buffer: ByteArray?): Int {
        val cue: ArrayList<ByteArray?> = ArrayList(5)

        /* CuePoint */cue.add(byteArrayOf(0xbb.toByte()))
        cue.add(null)

        /* CueTime */cue.add(byteArrayOf(0xb3.toByte()))
        cue.add(encode(keyFrame.duration, true))

        /* CueTrackPosition */cue.addAll(makeCueTrackPosition(internalTrackId, keyFrame))
        var size: Int = 0
        lengthFor(cue)
        for (buff: ByteArray? in cue) {
            System.arraycopy(buff, 0, buffer, size, buff!!.size)
            size += buff.size
        }
        return size
    }

    private fun makeCueTrackPosition(internalTrackId: Int,
                                     keyFrame: KeyFrame): ArrayList<ByteArray?> {
        val buffer: ArrayList<ByteArray?> = ArrayList(8)

        /* CueTrackPositions */buffer.add(byteArrayOf(0xb7.toByte()))
        buffer.add(null)

        /* CueTrack */buffer.add(byteArrayOf(0xf7.toByte()))
        buffer.add(encode((internalTrackId + 1).toLong(), true))

        /* CueClusterPosition */buffer.add(byteArrayOf(0xf1.toByte()))
        buffer.add(encode(keyFrame.clusterPosition, true))

        /* CueRelativePosition */if (keyFrame.relativePosition > 0) {
            buffer.add(byteArrayOf(0xf0.toByte()))
            buffer.add(encode(keyFrame.relativePosition.toLong(), true))
        }
        return lengthFor(buffer)
    }

    @Throws(IOException::class)
    private fun makeEbmlVoid(out: SharpStream?, amount: Int, wipe: Boolean) {
        var size: Int = amount

        /* ebml void */outByteBuffer!!.putShort(0, 0xec20.toShort())
        outByteBuffer!!.putShort(2, (size - 4).toShort())
        dump(outBuffer, 4, out)
        if (wipe) {
            size -= 4
            while (size > 0) {
                val write: Int = min(size.toDouble(), outBuffer!!.size.toDouble()).toInt()
                dump(outBuffer, write, out)
                size -= write
            }
        }
    }

    @Throws(IOException::class)
    private fun dump(buffer: ByteArray, stream: SharpStream?) {
        dump(buffer, buffer.size, stream)
    }

    @Throws(IOException::class)
    private fun dump(buffer: ByteArray?, count: Int, stream: SharpStream?) {
        stream!!.write(buffer, 0, count)
        written += count.toLong()
    }

    @Throws(IOException::class)
    private fun dump(buffers: ArrayList<ByteArray?>, stream: SharpStream?) {
        for (buffer: ByteArray? in buffers) {
            stream!!.write(buffer)
            written += buffer!!.size.toLong()
        }
    }

    private fun lengthFor(buffer: ArrayList<ByteArray?>): ArrayList<ByteArray?> {
        var size: Long = 0
        for (i in 2 until buffer.size) {
            size += buffer.get(i)!!.size.toLong()
        }
        buffer.set(1, encode(size, false))
        return buffer
    }

    private fun encode(number: Long, withLength: Boolean): ByteArray {
        var length: Int = -1
        for (i in 1..7) {
            if (number < 2.pow((7 * i).toDouble())) {
                length = i
                break
            }
        }
        if (length < 1) {
            throw ArithmeticException("Can't encode a number of bigger than 7 bytes")
        }
        if (number.toDouble() == (2.pow((7 * length).toDouble())) - 1) {
            length++
        }
        val offset: Int = if (withLength) 1 else 0
        val buffer: ByteArray = ByteArray(offset + length)
        val marker: Long = Math.floorDiv(length - 1, 8).toLong()
        var shift: Int = 0
        var i: Int = length - 1
        while (i >= 0) {
            var b: Long = number ushr shift
            if (!withLength && i.toLong() == marker) {
                b = b or (0x80 ushr (length - 1)).toLong()
            }
            buffer.get(offset + i) = b.toByte()
            i--
            shift += 8
        }
        if (withLength) {
            buffer.get(0) = (0x80 or length).toByte()
        }
        return buffer
    }

    private fun encode(value: String?): ArrayList<ByteArray?> {
        val str: ByteArray = value!!.toByteArray(StandardCharsets.UTF_8) // or use "utf-8"
        val buffer: ArrayList<ByteArray?> = ArrayList(2)
        buffer.add(encode(str.size.toLong(), false))
        buffer.add(str)
        return buffer
    }

    private fun valid(buffer: ByteArray?): Boolean {
        return buffer != null && buffer.size > 0
    }

    private fun selectTrackForCue(): Int {
        var i: Int = 0
        var videoTracks: Int = 0
        var audioTracks: Int = 0
        while (i < infoTracks!!.size) {
            when (infoTracks!!.get(i)!!.trackType) {
                1 -> videoTracks++
                2 -> audioTracks++
            }
            i++
        }
        val kind: Int
        if (audioTracks == infoTracks!!.size) {
            kind = 2
        } else if (videoTracks == infoTracks!!.size) {
            kind = 1
        } else if (videoTracks > 0) {
            kind = 1
        } else if (audioTracks > 0) {
            kind = 2
        } else {
            return 0
        }

        // TODO: in the above code, find and select the shortest track for the desired kind
        i = 0
        while (i < infoTracks!!.size) {
            if (kind == infoTracks!!.get(i)!!.trackType) {
                return i
            }
            i++
        }
        return 0
    }

    internal class KeyFrame(segment: Long, cluster: Long, block: Long, val duration: Long) {
        val clusterPosition: Long
        val relativePosition: Int

        init {
            clusterPosition = cluster - segment
            relativePosition = (block - cluster - CLUSTER_HEADER_SIZE).toInt()
        }
    }

    internal class Block() {
        var data: InputStream? = null
        var trackNumber: Int = 0
        var flags: Byte = 0
        var dataSize: Int = 0
        var absoluteTimecode: Long = 0
        fun isKeyframe(): Boolean {
            return (flags.toInt() and 0x80) == 0x80
        }

        public override fun toString(): String {
            return String.format("trackNumber=%s  isKeyFrame=%S  absoluteTimecode=%s", trackNumber,
                    isKeyframe(), absoluteTimecode)
        }
    }

    internal class ClusterInfo() {
        var offset: Long = 0
        var size: Int = 0
    }

    companion object {
        private val BUFFER_SIZE: Int = 8 * 1024
        private val DEFAULT_TIMECODE_SCALE: Int = 1000000
        private val INTERV: Int = 100 // 100ms on 1000000us timecode scale
        private val DEFAULT_CUES_EACH_MS: Int = 5000 // 5000ms on 1000000us timecode scale
        private val CLUSTER_HEADER_SIZE: Byte = 8
        private val CUE_RESERVE_SIZE: Int = 65535
        private val MINIMUM_EBML_VOID_SIZE: Byte = 4
    }
}
