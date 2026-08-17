package org.schabi.newpipe.streams

import org.schabi.newpipe.streams.WebMReader.*
import org.schabi.newpipe.streams.io.SharpStream
import okio.Closeable
import okio.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow

/**
 * @author kapodamy
 */
class WebMWriter(vararg source: SharpStream) : Closeable {
    private var infoTracks: Array<WebMTrack?> = arrayOfNulls(source.size)
    private var sourceTracks: Array<out SharpStream>? = source
    private var readers: Array<WebMReader?>? = arrayOfNulls(source.size)

    private var done = false
    private var parsed = false

    private var written = 0L

    private var readersSegment: Array<Segment?>? = null
    private var readersCluster: Array<Cluster?>? = null

    private var clustersOffsetsSizes: MutableList<ClusterInfo>? = ArrayList(256)

    private var outBuffer: ByteArray? = ByteArray(BUFFER_SIZE)
    private var outByteBuffer: ByteBuffer? = ByteBuffer.wrap(outBuffer!!)

    @Throws(IllegalStateException::class)
    fun getTracksFromSource(sourceIndex: Int): Array<WebMTrack>? {
        if (done) {
            throw IllegalStateException("already done")
        }
        if (!parsed) {
            throw IllegalStateException("All sources must be parsed first")
        }

        return readers!![sourceIndex]!!.getAvailableTracks()
    }

    @Throws(IOException::class, IllegalStateException::class)
    suspend fun parseSources() {
        if (done) {
            throw IllegalStateException("already done")
        }
        if (parsed) {
            throw IllegalStateException("already parsed")
        }

        try {
            for (i in readers!!.indices) {
                readers!![i] = WebMReader(sourceTracks!![i])
                readers!![i]!!.parse()
            }
        } finally {
            parsed = true
        }
    }

    @Throws(IOException::class)
    suspend fun selectTracks(vararg trackIndex: Int) {
        try {
            readersSegment = arrayOfNulls(readers!!.size)
            readersCluster = arrayOfNulls(readers!!.size)

            for (i in readers!!.indices) {
                infoTracks[i] = readers!![i]!!.selectTrack(trackIndex[i])
                readersSegment!![i] = readers!![i]!!.getNextSegment()
            }
        } finally {
            parsed = true
        }
    }

    fun isDone(): Boolean = done

    override fun close() {
        done = true
        parsed = true

        sourceTracks?.forEach { it.close() }

        sourceTracks = null
        readers = null
        infoTracks = emptyArray()
        readersSegment = null
        readersCluster = null
        outBuffer = null
        outByteBuffer = null
        clustersOffsetsSizes = null
    }

    @Throws(IOException::class, RuntimeException::class)
    suspend fun build(out: SharpStream) {
        if (!out.canRewind()) {
            throw IOException("The output stream must be allow seek")
        }

        makeEBML(out)

        val offsetSegmentSizeSet = written + 5
        val offsetInfoDurationSet = written + 94
        val offsetClusterSet = written + 58
        val offsetCuesSet = written + 75

        val listBuffer = mutableListOf<ByteArray>()

        /* segment */
        listBuffer.add(
            byteArrayOf(
                0x18, 0x53, 0x80.toByte(), 0x67, 0x01,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // segment content size
            )
        )

        val segmentOffset = written + listBuffer[0].size

        /* seek head */
        listBuffer.add(
            byteArrayOf(
                0x11, 0x4d, 0x9b.toByte(), 0x74, 0xbe.toByte(),
                0x4d, 0xbb.toByte(), 0x8b.toByte(),
                0x53, 0xab.toByte(), 0x84.toByte(), 0x15, 0x49, 0xa9.toByte(), 0x66, 0x53,
                0xac.toByte(), 0x81.toByte(),
                /*info offset*/ 0x43,
                0x4d, 0xbb.toByte(), 0x8b.toByte(), 0x53, 0xab.toByte(),
                0x84.toByte(), 0x16, 0x54, 0xae.toByte(), 0x6b, 0x53, 0xac.toByte(), 0x81.toByte(),
                /*tracks offset*/ 0x56,
                0x4d, 0xbb.toByte(), 0x8e.toByte(), 0x53, 0xab.toByte(), 0x84.toByte(), 0x1f,
                0x43, 0xb6.toByte(), 0x75, 0x53, 0xac.toByte(), 0x84.toByte(),
                /*cluster offset [2]*/ 0x00, 0x00, 0x00, 0x00,
                0x4d, 0xbb.toByte(), 0x8e.toByte(), 0x53, 0xab.toByte(), 0x84.toByte(), 0x1c, 0x53,
                0xbb.toByte(), 0x6b, 0x53, 0xac.toByte(), 0x84.toByte(),
                /*cues offset [7]*/ 0x00, 0x00, 0x00, 0x00
            )
        )

        /* info */
        listBuffer.add(
            byteArrayOf(
                0x15, 0x49, 0xa9.toByte(), 0x66, 0x8e.toByte(), 0x2a, 0xd7.toByte(), 0xb1.toByte()
            )
        )
        // the segment duration MUST NOT exceed 4 bytes
        listBuffer.add(encode(DEFAULT_TIMECODE_SCALE.toLong(), true))
        listBuffer.add(
            byteArrayOf(
                0x44, 0x89.toByte(), 0x84.toByte(),
                0x00, 0x00, 0x00, 0x00 // info.duration
            )
        )

        /* tracks */
        listBuffer.addAll(makeTracks())

        dump(listBuffer, out)

        // reserve space for Cues element
        val cueOffset = written
        makeEbmlVoid(out, CUE_RESERVE_SIZE, true)

        val defaultSampleDuration = IntArray(infoTracks.size)
        val duration = LongArray(infoTracks.size)

        for (i in infoTracks.indices) {
            val track = infoTracks[i]!!
            if (track.defaultDuration < 0) {
                defaultSampleDuration[i] = -1 // not available
            } else {
                defaultSampleDuration[i] = ceil(track.defaultDuration.toDouble() / DEFAULT_TIMECODE_SCALE).toInt()
            }
            duration[i] = -1
        }

        // Select a track for the cue
        val cuesForTrackId = selectTrackForCue()
        var nextCueTime = if (infoTracks[cuesForTrackId]!!.trackType == 1) -1L else 0L
        val keyFrames = mutableListOf<KeyFrame>()

        var firstClusterOffset = written.toInt()
        var currentClusterOffset = makeCluster(out, 0, 0, true)

        var baseTimecode = 0L
        var limitTimecode = -1L
        var limitTimecodeByTrackId = cuesForTrackId

        var blockWritten = Int.MAX_VALUE

        var newClusterByTrackId = -1

        while (blockWritten > 0) {
            blockWritten = 0
            var i = 0
            while (i < readers!!.size) {
                val bloq = getNextBlockFrom(i)
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
                    currentClusterOffset = makeCluster(out, baseTimecode, currentClusterOffset, true)
                }

                if (cuesForTrackId == i) {
                    if ((nextCueTime > -1 && bloq.absoluteTimecode >= nextCueTime) || (nextCueTime < 0 && bloq.isKeyframe())) {
                        if (nextCueTime > -1) {
                            nextCueTime += DEFAULT_CUES_EACH_MS.toLong()
                        }
                        keyFrames.add(KeyFrame(segmentOffset.toLong(), currentClusterOffset, written, bloq.absoluteTimecode))
                    }
                }

                writeBlock(out, bloq, baseTimecode)
                blockWritten++

                if (defaultSampleDuration[i] < 0 && duration[i] >= 0) {
                    // if the sample duration in unknown,
                    // calculate using current_duration - previous_duration
                    defaultSampleDuration[i] = (bloq.absoluteTimecode - duration[i]).toInt()
                }
                duration[i] = bloq.absoluteTimecode

                if (limitTimecode < 0) {
                    limitTimecode = bloq.absoluteTimecode + INTERV
                    continue
                }

                if (bloq.absoluteTimecode >= limitTimecode) {
                    if (limitTimecodeByTrackId != i) {
                        limitTimecode += (INTERV - (bloq.absoluteTimecode - limitTimecode))
                    }
                    i++
                }
            }
        }

        makeCluster(out, -1, currentClusterOffset, false)

        val segmentSize = written - offsetSegmentSizeSet - 7

        /* Segment size */
        seekTo(out, offsetSegmentSizeSet)
        outByteBuffer!!.putLong(0, segmentSize)
        out.write(outBuffer!!, 1, DataReader.LONG_SIZE - 1)
        written += (DataReader.LONG_SIZE - 1)

        /* Segment duration */
        var longestDuration = 0L
        for (i in duration.indices) {
            if (defaultSampleDuration[i] > 0) {
                duration[i] += defaultSampleDuration[i].toLong()
            }
            if (duration[i] > longestDuration) {
                longestDuration = duration[i]
            }
        }
        seekTo(out, offsetInfoDurationSet)
        outByteBuffer!!.putFloat(0, longestDuration.toFloat())
        dump(outBuffer!!, DataReader.FLOAT_SIZE, out)

        /* first Cluster offset */
        writeInt(out, offsetClusterSet, (firstClusterOffset - segmentOffset).toInt())

        seekTo(out, cueOffset)

        /* Cue */
        var cueSize: Short = 0
        dump(byteArrayOf(0x1c, 0x53, 0xbb.toByte(), 0x6b, 0x20, 0x00, 0x00), out) // header size is 7

        for (keyFrame in keyFrames) {
            val size = makeCuePoint(cuesForTrackId, keyFrame, outBuffer!!)

            if (cueSize + size + 7 + MINIMUM_EBML_VOID_SIZE > CUE_RESERVE_SIZE) {
                break // no space left
            }

            cueSize = (cueSize + size).toShort()
            dump(outBuffer!!, size, out)
        }

        makeEbmlVoid(out, CUE_RESERVE_SIZE - cueSize - 7, false)

        seekTo(out, cueOffset + 5)
        outByteBuffer!!.putShort(0, cueSize)
        dump(outBuffer!!, DataReader.SHORT_SIZE, out)

        /* seek head, seek for cues element */
        writeInt(out, offsetCuesSet, (cueOffset - segmentOffset).toInt())

        for (cluster in clustersOffsetsSizes!!) {
            writeInt(out, cluster.offset, cluster.size or 0x10000000)
        }
    }

    private suspend fun getNextBlockFrom(internalTrackId: Int): Block? {
        if (readersSegment!![internalTrackId] == null) {
            readersSegment!![internalTrackId] = readers!![internalTrackId]!!.getNextSegment()
            if (readersSegment!![internalTrackId] == null) {
                return null // no more blocks in the selected track
            }
        }

        if (readersCluster!![internalTrackId] == null) {
            readersCluster!![internalTrackId] = readersSegment!![internalTrackId]!!.getNextCluster()
            if (readersCluster!![internalTrackId] == null) {
                readersSegment!![internalTrackId] = null
                return getNextBlockFrom(internalTrackId)
            }
        }

        val res = readersCluster!![internalTrackId]!!.getNextSimpleBlock()
        if (res == null) {
            readersCluster!![internalTrackId] = null
            return Block() // fake block to indicate the end of the cluster
        }

        val bloq = Block()
        bloq.data = res.data
        bloq.dataSize = res.dataSize
        bloq.trackNumber = internalTrackId
        bloq.flags = res.flags
        bloq.absoluteTimecode = res.absoluteTimeCodeNs / DEFAULT_TIMECODE_SCALE

        return bloq
    }

    private suspend fun seekTo(stream: SharpStream, offset: Long) {
        if (stream.canSeek()) {
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

    private suspend fun writeInt(stream: SharpStream, offset: Long, number: Int) {
        seekTo(stream, offset)
        outByteBuffer!!.putInt(0, number)
        dump(outBuffer!!, DataReader.INTEGER_SIZE, stream)
    }

    private suspend fun writeBlock(stream: SharpStream, bloq: Block, clusterTimecode: Long) {
        val relativeTimeCode = bloq.absoluteTimecode - clusterTimecode

        if (relativeTimeCode < Short.MIN_VALUE || relativeTimeCode > Short.MAX_VALUE) {
            throw IndexOutOfBoundsException("SimpleBlock timecode overflow.")
        }

        val listBuffer = mutableListOf<ByteArray?>()
        listBuffer.add(byteArrayOf(0xa3.toByte()))
        listBuffer.add(null) // block size
        listBuffer.add(encode((bloq.trackNumber + 1).toLong(), false))
        listBuffer.add(
            ByteBuffer.allocate(DataReader.SHORT_SIZE).putShort(relativeTimeCode.toShort()).array()
        )
        listBuffer.add(byteArrayOf(bloq.flags))

        var blockSize = bloq.dataSize
        for (i in 2 until listBuffer.size) {
            blockSize += listBuffer[i]!!.size
        }
        listBuffer[1] = encode(blockSize.toLong(), false)

        dump(listBuffer as List<ByteArray>, stream)

        var read: Int
        while (bloq.data!!.read(outBuffer!!).also { read = it } > 0) {
            dump(outBuffer!!, read, stream)
        }
    }

    private suspend fun makeCluster(
        stream: SharpStream,
        timecode: Long,
        offsetStart: Long,
        create: Boolean
    ): Long {
        var offset = offsetStart

        if (offset > 0) {
            // save the size of the previous cluster (maximum 256 MiB)
            val cluster = clustersOffsetsSizes!![clustersOffsetsSizes!!.size - 1]
            cluster.size = (written - offset - CLUSTER_HEADER_SIZE).toInt()
        }

        offset = written

        if (create) {
            /* cluster */
            dump(byteArrayOf(0x1f, 0x43, 0xb6.toByte(), 0x75), stream)

            val cluster = ClusterInfo()
            cluster.offset = written
            clustersOffsetsSizes!!.add(cluster)

            dump(
                byteArrayOf(
                    0x10, 0x00, 0x00, 0x00,
                    /* timestamp */
                    0xe7.toByte()
                ),
                stream
            )

            dump(encode(timecode, true), stream)
        }

        return offset
    }

    private suspend fun makeEBML(stream: SharpStream) {
        // default values
        dump(
            byteArrayOf(
                0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x1F, 0x42, 0x86.toByte(), 0x81.toByte(), 0x01,
                0x42, 0xF7.toByte(), 0x81.toByte(), 0x01, 0x42, 0xF2.toByte(), 0x81.toByte(), 0x04,
                0x42, 0xF3.toByte(), 0x81.toByte(), 0x08, 0x42, 0x82.toByte(), 0x84.toByte(), 0x77,
                0x65, 0x62, 0x6D, 0x42, 0x87.toByte(), 0x81.toByte(), 0x02,
                0x42, 0x85.toByte(), 0x81.toByte(), 0x02
            ),
            stream
        )
    }

    private fun makeTracks(): List<ByteArray> {
        val buffer = mutableListOf<ByteArray?>()
        buffer.add(byteArrayOf(0x16, 0x54, 0xae.toByte(), 0x6b))
        buffer.add(null)

        for (i in infoTracks.indices) {
            buffer.addAll(makeTrackEntry(i, infoTracks[i]!!))
        }

        return lengthFor(buffer)
    }

    private fun makeTrackEntry(internalTrackId: Int, track: WebMTrack): List<ByteArray?> {
        val id = encode((internalTrackId + 1).toLong(), true)
        val buffer = mutableListOf<ByteArray?>()

        /* track */
        buffer.add(byteArrayOf(0xae.toByte()))
        buffer.add(null)

        /* track number */
        buffer.add(byteArrayOf(0xd7.toByte()))
        buffer.add(id)

        /* track uid */
        buffer.add(byteArrayOf(0x73, 0xc5.toByte()))
        buffer.add(id)

        /* flag lacing */
        buffer.add(byteArrayOf(0x9c.toByte(), 0x81.toByte(), 0x00))

        /* lang */
        buffer.add(byteArrayOf(0x22, 0xb5.toByte(), 0x9c.toByte(), 0x83.toByte(), 0x75, 0x6e, 0x64))

        /* codec id */
        buffer.add(byteArrayOf(0x86.toByte()))
        buffer.addAll(encode(track.codecId!!).map { it })

        /* codec delay*/
        if (track.codecDelay >= 0) {
            buffer.add(byteArrayOf(0x56, 0xAA.toByte()))
            buffer.add(encode(track.codecDelay, true))
        }

        /* codec seek pre-roll*/
        if (track.seekPreRoll >= 0) {
            buffer.add(byteArrayOf(0x56, 0xBB.toByte()))
            buffer.add(encode(track.seekPreRoll, true))
        }

        /* type */
        buffer.add(byteArrayOf(0x83.toByte()))
        buffer.add(encode(track.trackType.toLong(), true))

        /* default duration */
        if (track.defaultDuration >= 0) {
            buffer.add(byteArrayOf(0x23, 0xe3.toByte(), 0x83.toByte()))
            buffer.add(encode(track.defaultDuration, true))
        }

        /* audio/video */
        if ((track.trackType == 1 || track.trackType == 2) && valid(track.bMetadata)) {
            buffer.add(byteArrayOf((if (track.trackType == 1) 0xe0 else 0xe1).toByte()))
            buffer.add(encode(track.bMetadata!!.size.toLong(), false))
            buffer.add(track.bMetadata)
        }

        /* codec private*/
        if (valid(track.codecPrivate)) {
            buffer.add(byteArrayOf(0x63, 0xa2.toByte()))
            buffer.add(encode(track.codecPrivate!!.size.toLong(), false))
            buffer.add(track.codecPrivate)
        }

        return lengthFor(buffer)
    }

    private fun makeCuePoint(internalTrackId: Int, keyFrame: KeyFrame, buffer: ByteArray): Int {
        val cue = mutableListOf<ByteArray?>()

        /* CuePoint */
        cue.add(byteArrayOf(0xbb.toByte()))
        cue.add(null)

        /* CueTime */
        cue.add(byteArrayOf(0xb3.toByte()))
        cue.add(encode(keyFrame.duration, true))

        /* CueTrackPosition */
        cue.addAll(makeCueTrackPosition(internalTrackId, keyFrame))

        lengthFor(cue)

        var size = 0
        for (buff in cue) {
            System.arraycopy(buff!!, 0, buffer, size, buff.size)
            size += buff.size
        }

        return size
    }

    private fun makeCueTrackPosition(internalTrackId: Int, keyFrame: KeyFrame): List<ByteArray?> {
        val buffer = mutableListOf<ByteArray?>()

        /* CueTrackPositions */
        buffer.add(byteArrayOf(0xb7.toByte()))
        buffer.add(null)

        /* CueTrack */
        buffer.add(byteArrayOf(0xf7.toByte()))
        buffer.add(encode((internalTrackId + 1).toLong(), true))

        /* CueClusterPosition */
        buffer.add(byteArrayOf(0xf1.toByte()))
        buffer.add(encode(keyFrame.clusterPosition, true))

        /* CueRelativePosition */
        if (keyFrame.relativePosition > 0) {
            buffer.add(byteArrayOf(0xf0.toByte()))
            buffer.add(encode(keyFrame.relativePosition.toLong(), true))
        }

        return lengthFor(buffer)
    }

    private suspend fun makeEbmlVoid(out: SharpStream, amount: Int, wipe: Boolean) {
        var size = amount

        /* ebml void */
        outByteBuffer!!.putShort(0, 0xec20.toShort())
        outByteBuffer!!.putShort(2, (size - 4).toShort())

        dump(outBuffer!!, 4, out)

        if (wipe) {
            size -= 4
            while (size > 0) {
                val write = min(size, outBuffer!!.size)
                dump(outBuffer!!, write, out)
                size -= write
            }
        }
    }

    private suspend fun dump(buffer: ByteArray, stream: SharpStream) {
        dump(buffer, buffer.size, stream)
    }

    private suspend fun dump(buffer: ByteArray, count: Int, stream: SharpStream) {
        stream.write(buffer, 0, count)
        written += count.toLong()
    }

    private suspend fun dump(buffers: List<ByteArray>, stream: SharpStream) {
        for (buffer in buffers) {
            stream.write(buffer)
            written += buffer.size.toLong()
        }
    }

    private fun lengthFor(buffer: MutableList<ByteArray?>): List<ByteArray> {
        var size = 0L
        for (i in 2 until buffer.size) {
            size += buffer[i]!!.size.toLong()
        }
        buffer[1] = encode(size, false)
        return buffer as List<ByteArray>
    }

    private fun encode(number: Long, withLength: Boolean): ByteArray {
        var length = -1
        for (i in 1..7) {
            if (number < 2.0.pow((7 * i).toDouble())) {
                length = i
                break
            }
        }

        if (length < 1) {
            throw ArithmeticException("Can't encode a number of bigger than 7 bytes")
        }

        var finalLength = length
        if (number == (2.0.pow((7 * finalLength).toDouble())).toLong() - 1) {
            finalLength++
        }

        val offset = if (withLength) 1 else 0
        val buffer = ByteArray(offset + finalLength)
        val marker = (finalLength - 1) / 8

        var shift = 0
        for (i in finalLength - 1 downTo 0) {
            var b = number ushr shift
            if (!withLength && i == marker) {
                b = b or (0x80 ushr (finalLength - 1)).toLong()
            }
            buffer[offset + i] = b.toByte()
            shift += 8
        }

        if (withLength) {
            buffer[0] = (0x80 or finalLength).toByte()
        }

        return buffer
    }

    private fun encode(value: String): List<ByteArray> {
        val str = value.toByteArray(StandardCharsets.UTF_8)
        val buffer = mutableListOf<ByteArray>()
        buffer.add(encode(str.size.toLong(), false))
        buffer.add(str)
        return buffer
    }

    private fun valid(buffer: ByteArray?): Boolean {
        return buffer != null && buffer.isNotEmpty()
    }

    private fun selectTrackForCue(): Int {
        var videoTracks = 0
        var audioTracks = 0

        for (i in infoTracks.indices) {
            when (infoTracks[i]!!.trackType) {
                1 -> videoTracks++
                2 -> audioTracks++
            }
        }

        val kind = if (audioTracks == infoTracks.size) {
            2
        } else if (videoTracks == infoTracks.size) {
            1
        } else if (videoTracks > 0) {
            1
        } else if (audioTracks > 0) {
            2
        } else {
            return 0
        }

        for (i in infoTracks.indices) {
            if (kind == infoTracks[i]!!.trackType) {
                return i
            }
        }

        return 0
    }

    class KeyFrame(segment: Long, cluster: Long, block: Long, val duration: Long) {
        val clusterPosition: Long = cluster - segment
        val relativePosition: Int = (block - cluster - CLUSTER_HEADER_SIZE).toInt()
    }

    class Block {
        var data: InputStream? = null
        var trackNumber: Int = 0
        var flags: Byte = 0
        var dataSize: Int = 0
        var absoluteTimecode: Long = 0

        fun isKeyframe(): Boolean {
            return (flags.toInt() and 0x80) == 0x80
        }

        override fun toString(): String {
            return String.format(
                "trackNumber=%s  isKeyFrame=%S  absoluteTimecode=%s",
                trackNumber, isKeyframe(), absoluteTimecode
            )
        }
    }

    class ClusterInfo {
        var offset: Long = 0
        var size: Int = 0
    }

    companion object {
        private const val BUFFER_SIZE = 8 * 1024
        private const val DEFAULT_TIMECODE_SCALE = 1000000
        private const val INTERV = 100 // 100ms on 1000000us timecode scale
        private const val DEFAULT_CUES_EACH_MS = 5000 // 5000ms on 1000000us timecode scale
        private const val CLUSTER_HEADER_SIZE = 8
        private const val CUE_RESERVE_SIZE = 65535
        private const val MINIMUM_EBML_VOID_SIZE = 4
    }
}
