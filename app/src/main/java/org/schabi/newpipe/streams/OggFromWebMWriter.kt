package org.schabi.newpipe.streams

import android.util.Log
import okio.Closeable
import okio.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.min
import org.schabi.newpipe.DebugConstants.DEBUG
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.streams.WebMReader.*
import org.schabi.newpipe.streams.io.SharpStream

/**
 * @author kapodamy
 */
class OggFromWebMWriter(
    private val source: SharpStream,
    private val output: SharpStream,
    private val streamInfo: StreamInfo?
) : Closeable {

    private var done = false
    private var parsed = false

    private var sequenceCount = 0
    private val streamId: Int = System.currentTimeMillis().toInt()
    private var packetFlag: Byte = FLAG_FIRST

    private var webm: WebMReader? = null
    private var webmTrack: WebMTrack? = null
    private var webmSegment: Segment? = null
    private var webmCluster: Cluster? = null
    private var webmBlock: SimpleBlock? = null

    private var webmBlockLastTimecode = 0L
    private var webmBlockNearDuration = 0L

    private var segmentTableSize = 0
    private val segmentTable = ByteArray(255)
    private var segmentTableNextTimestamp = TIME_SCALE_NS.toLong()

    private val crc32Table = IntArray(256)

    init {
        require(source.canRead() && source.canRewind()) { "source stream must be readable and allows seeking" }
        require(output.canWrite() && output.canRewind()) { "output stream must be writable and allows seeking" }

        populateCrc32Table()
    }

    fun isDone(): Boolean = done

    fun isParsed(): Boolean = parsed

    @Throws(IllegalStateException::class)
    fun getTracksFromSource(): Array<WebMTrack>? {
        if (!parsed) {
            throw IllegalStateException("source must be parsed first")
        }
        return webm!!.getAvailableTracks()
    }

    @Throws(IOException::class, IllegalStateException::class)
    suspend fun parseSource() {
        if (done) throw IllegalStateException("already done")
        if (parsed) throw IllegalStateException("already parsed")

        try {
            val w = WebMReader(source)
            webm = w
            w.parse()
            webmSegment = w.getNextSegment() ?: throw IOException("No segment found")
        } finally {
            parsed = true
        }
    }

    @Throws(IOException::class)
    suspend fun selectTrack(trackIndex: Int) {
        if (!parsed) throw IllegalStateException("source must be parsed first")
        if (done) throw IOException("already done")
        if (webmTrack != null) throw IOException("tracks already selected")

        val track = webm!!.getAvailableTracks()?.get(trackIndex) ?: throw Exception("Track not found")
        when (track.kind) {
            TrackKind.Audio, TrackKind.Video -> {}
            else -> throw UnsupportedOperationException("the track must an audio or video stream")
        }

        try {
            webmTrack = webm!!.selectTrack(trackIndex)
        } finally {
            parsed = true
        }
    }

    @Throws(IOException::class)
    override fun close() {
        done = true
        parsed = true
        webmTrack = null
        webm = null
        if (!output.isClosed()) {
            output.close()
        }
        source.close()
    }

    @Throws(IOException::class)
    suspend fun build() {
        val resolution: Float
        val header = ByteBuffer.allocate(27 + (255 * 255))
        val page = ByteBuffer.allocate(64 * 1024)

        header.order(ByteOrder.LITTLE_ENDIAN)

        /* step 1: get the amount of frames per seconds */
        val currentTrack = webmTrack!!
        resolution = when (currentTrack.kind ?: WebMReader.TrackKind.Audio) {
            TrackKind.Audio -> {
                val res = getSampleFrequencyFromTrack(currentTrack.bMetadata ?: ByteArray(0))
                if (res == 0f) throw RuntimeException("cannot get the audio sample rate")
                res
            }

            TrackKind.Video -> {
                if (currentTrack.defaultDuration == 0L) throw RuntimeException("missing default frame time")
                1000f / (currentTrack.defaultDuration!!.toFloat() / (webmSegment?.info?.timecodeScale ?: 1000000L))
            }

            TrackKind.Other -> throw RuntimeException("not implemented")
        }

        /* step 2: create packet with code init data */
        if (currentTrack.codecPrivate != null) {
            addPacketSegment(currentTrack.codecPrivate?.size ?: 0)
            makePacketheader(0x00, header, currentTrack.codecPrivate)
            write(header)
            output.write(currentTrack.codecPrivate ?: ByteArray(0))
        }

        /* step 3: create packet with metadata */
        val buffer = makeMetadata()
        if (buffer != null) {
            addPacketSegment(buffer.size)
            makePacketheader(0x00, header, buffer)
            write(header)
            output.write(buffer)
        }

        /* step 4: calculate amount of packets */
        while (webmSegment != null) {
            val bloq = getNextBlock()

            if (bloq != null && addPacketSegment(bloq)) {
                val pos = page.position()
                val bytesRead = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    bloq.data!!.read(page.array(), pos, bloq.dataSize)
                }
                page.position(pos + bytesRead)
                continue
            }

            // calculate the current packet duration using the next block
            var elapsedNs = currentTrack.codecDelay.toDouble()

            if (bloq == null) {
                packetFlag = FLAG_LAST // note: if the flag is FLAG_CONTINUED, is changed
                elapsedNs += webmBlockLastTimecode.toDouble()

                if (currentTrack.defaultDuration > 0) {
                    elapsedNs += currentTrack.defaultDuration.toDouble()
                } else {
                    // hardcoded way, guess the sample duration
                    elapsedNs += webmBlockNearDuration.toDouble()
                }
            } else {
                elapsedNs += bloq.absoluteTimeCodeNs.toDouble()
            }

            // get the sample count in the page
            elapsedNs /= TIME_SCALE_NS.toDouble()
            elapsedNs = ceil(elapsedNs * resolution)

            // create header and calculate page checksum
            var checksum = makePacketheader(elapsedNs.toLong(), header, null)
            checksum = calcCrc32(checksum, page.array(), page.position())

            header.putInt(HEADER_CHECKSUM_OFFSET.toInt(), checksum)

            // dump data
            write(header)
            write(page)

            webmBlock = bloq
        }
    }

    private suspend fun makePacketheader(
        granPos: Long,
        buffer: ByteBuffer,
        immediatePage: ByteArray?
    ): Int {
        var length = HEADER_SIZE.toInt()

        buffer.putInt(0x5367674f) // "OggS" binary string in little-endian
        buffer.put(0x00.toByte()) // version
        buffer.put(packetFlag) // type

        buffer.putLong(granPos) // granulate position

        buffer.putInt(streamId) // bitstream serial number
        buffer.putInt(sequenceCount++) // page sequence number

        buffer.putInt(0x00) // page checksum

        buffer.put(segmentTableSize.toByte()) // segment table
        buffer.put(segmentTable, 0, segmentTableSize) // segment size

        length += segmentTableSize

        clearSegmentTable() // clear segment table for next header

        var checksumCrc32 = calcCrc32(0x00, buffer.array(), length)

        if (immediatePage != null) {
            checksumCrc32 = calcCrc32(checksumCrc32, immediatePage, immediatePage.size)
            buffer.putInt(HEADER_CHECKSUM_OFFSET.toInt(), checksumCrc32)
            segmentTableNextTimestamp -= TIME_SCALE_NS.toLong()
        }

        return checksumCrc32
    }

    private fun makeMetadata(): ByteArray? {
        val track = webmTrack!!
        if (DEBUG) {
            Log.d("OggFromWebMWriter", "Downloading media with codec ID ${track.codecId}")
        }

        return if ("A_OPUS" == track.codecId) {
            val metadata = mutableListOf<Pair<String, String>>()
            streamInfo?.let {
                metadata.add(Pair("COMMENT", it.url))
                metadata.add(Pair("GENRE", it.category))
                metadata.add(Pair("ARTIST", it.uploaderName))
                metadata.add(Pair("TITLE", it.name))
                metadata.add(
                    Pair(
                        "DATE",
                        it.uploadDate.localDateTime
                            .format(DateTimeFormatter.ISO_DATE)
                    )
                )
            }

            if (DEBUG) {
                Log.d("OggFromWebMWriter", "Creating metadata header with this data:")
                metadata.forEach { p ->
                    Log.d("OggFromWebMWriter", "${p.first}=${p.second}")
                }
            }

            makeOpusTagsHeader(metadata)
        } else if ("A_VORBIS" == track.codecId) {
            byteArrayOf(
                0x03, // ¿¿¿???
                0x76, 0x6f, 0x72, 0x62, 0x69, 0x73, // "vorbis" binary string
                0x00, 0x00, 0x00, 0x00, // writing application string size (not present)
                0x00, 0x00, 0x00, 0x00 // additional tags count (zero means no tags)
            )
        } else {
            null
        }
    }

    private suspend fun write(buffer: ByteBuffer) {
        output.write(buffer.array(), 0, buffer.position())
        buffer.position(0)
    }

    @Throws(IOException::class)
    private suspend fun getNextBlock(): SimpleBlock? {
        var res: SimpleBlock?

        if (webmBlock != null) {
            res = webmBlock
            webmBlock = null
            return res
        }

        if (webmSegment == null) {
            webmSegment = webm!!.getNextSegment()
            if (webmSegment == null) {
                return null // no more blocks in the selected track
            }
        }

        if (webmCluster == null) {
            webmCluster = webmSegment!!.getNextCluster()
            if (webmCluster == null) {
                webmSegment = null
                return getNextBlock()
            }
        }

        res = webmCluster!!.getNextSimpleBlock()
        if (res == null) {
            webmCluster = null
            return getNextBlock()
        }

        webmBlockNearDuration = res.absoluteTimeCodeNs - webmBlockLastTimecode
        webmBlockLastTimecode = res.absoluteTimeCodeNs

        return res
    }

    private fun getSampleFrequencyFromTrack(bMetadata: ByteArray): Float {
        val buffer = ByteBuffer.wrap(bMetadata)
        while (buffer.remaining() >= 6) {
            val id = buffer.short.toInt() and 0xFFFF
            if (id == 0x0000B584) {
                return buffer.float
            }
        }
        return 0.0f
    }

    private fun clearSegmentTable() {
        segmentTableNextTimestamp += TIME_SCALE_NS.toLong()
        packetFlag = FLAG_UNSET
        segmentTableSize = 0
    }

    private fun addPacketSegment(block: SimpleBlock): Boolean {
        val timestamp = block.absoluteTimeCodeNs + webmTrack!!.codecDelay
        if (timestamp >= segmentTableNextTimestamp) {
            return false
        }
        return addPacketSegment(block.dataSize)
    }

    private fun addPacketSegment(size: Int): Boolean {
        if (size > 65025) {
            throw UnsupportedOperationException("page size cannot be larger than 65025")
        }

        var available = (segmentTable.size - segmentTableSize) * 255
        val extra = size % 255 == 0

        if (extra) {
            available -= 255
        }

        if (available < size) {
            return false
        }

        var seg = size
        while (seg > 0) {
            segmentTable[segmentTableSize++] = min(seg, 255).toByte()
            seg -= 255
        }

        if (extra) {
            segmentTable[segmentTableSize++] = 0x00
        }

        return true
    }

    private fun populateCrc32Table() {
        for (i in 0 until 0x100) {
            var crc = i shl 24
            for (j in 0 until 8) {
                val b = crc.toLong() ushr 31
                crc = crc shl 1
                crc = crc xor ((0x100000000L - b).toInt() and 0x04c11db7)
            }
            crc32Table[i] = crc
        }
    }

    private fun calcCrc32(initialCrc: Int, buffer: ByteArray, size: Int): Int {
        var crc = initialCrc
        for (i in 0 until size) {
            val reg = crc ushr 24 and 0xff
            crc = (crc shl 8) xor crc32Table[reg xor (buffer[i].toInt() and 0xff)]
        }
        return crc
    }

    companion object {
        private const val FLAG_UNSET: Byte = 0x00
        private const val FLAG_FIRST: Byte = 0x02
        private const val FLAG_LAST: Byte = 0x04

        private const val HEADER_CHECKSUM_OFFSET: Byte = 22
        private const val HEADER_SIZE: Byte = 27

        private const val TIME_SCALE_NS = 1000000000

        private fun makeOpusMetadataTag(pair: Pair<String, String>): ByteArray {
            val keyValue = "${pair.first.uppercase()}=${pair.second.trim()}"
            val bytes = keyValue.toByteArray()
            val buf = ByteBuffer.allocate(4 + bytes.size)
            buf.order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(bytes.size)
            buf.put(bytes)
            return buf.array()
        }

        private fun makeOpusTagsHeader(keyValueLines: List<Pair<String, String>>): ByteArray {
            val tags = keyValueLines
                .filter { it.second.isNotBlank() }
                .map { makeOpusMetadataTag(it) }

            val tagsBytesSize = tags.sumOf { it.size }
            val byteCount = 16 + tagsBytesSize

            val head = ByteBuffer.allocate(byteCount)
            head.order(ByteOrder.LITTLE_ENDIAN)
            head.put(
                byteArrayOf(
                    0x4F, 0x70, 0x75, 0x73, 0x54, 0x61, 0x67, 0x73, // "OpusTags" binary string
                    0x00, 0x00, 0x00, 0x00 // vendor (aka. Encoder) string of length 0
                )
            )
            head.putInt(tags.size)
            tags.forEach { head.put(it) }

            return head.array()
        }
    }
}
