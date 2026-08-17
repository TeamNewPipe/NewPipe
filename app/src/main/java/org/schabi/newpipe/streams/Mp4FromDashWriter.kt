package org.schabi.newpipe.streams

import okio.IOException
import java.nio.ByteBuffer
import java.util.ArrayList
import kotlin.math.ceil
import kotlin.math.min
import org.schabi.newpipe.streams.Mp4DashReader.Hdlr
import org.schabi.newpipe.streams.Mp4DashReader.Mdia
import org.schabi.newpipe.streams.Mp4DashReader.Mp4DashChunk
import org.schabi.newpipe.streams.Mp4DashReader.Mp4DashSample
import org.schabi.newpipe.streams.Mp4DashReader.Mp4Track
import org.schabi.newpipe.streams.Mp4DashReader.TrackKind
import org.schabi.newpipe.streams.io.SharpStream

/**
 * @author kapodamy
 */
class Mp4FromDashWriter
@Throws(IOException::class)
constructor(vararg sources: SharpStream) {
    private val time: Long

    private var auxBuffer: ByteBuffer? = null
    private var outStream: SharpStream? = null

    private var lastWriteOffset: Long = -1
    private var writeOffset: Long = 0

    private var moovSimulation = true

    private var done = false
    private var parsed = false

    private var tracks: Array<Mp4Track>? = null
    private var sourceTracks: Array<out SharpStream>? = null

    private var readers: Array<Mp4DashReader>? = null
    private var readersChunks: Array<Mp4DashChunk?>? = null

    private var overrideMainBrand = 0x00

    private val compatibleBrands = ArrayList<Int>(5)

    init {
        for (src in sources) {
            if (!src.canRewind() && !src.canRead()) {
                throw IOException("All sources must be readable and allow rewind")
            }
        }

        sourceTracks = sources
        readers = Array(sourceTracks!!.size) { Mp4DashReader(sourceTracks!![it]) }
        readersChunks = arrayOfNulls(readers!!.size)
        time = System.currentTimeMillis() / 1000L + EPOCH_OFFSET

        compatibleBrands.add(0x6D703431) // mp41
        compatibleBrands.add(0x69736F6D) // isom
        compatibleBrands.add(0x69736F32) // iso2
    }

    fun getTracksFromSource(sourceIndex: Int): Array<Mp4Track>? {
        if (!parsed) {
            throw IllegalStateException("All sources must be parsed first")
        }

        return readers!![sourceIndex].getAvailableTracks()
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
                readers!![i].parse()
            }
        } finally {
            parsed = true
        }
    }

    @Throws(IOException::class)
    fun selectTracks(vararg trackIndex: Int) {
        if (done) {
            throw IOException("already done")
        }
        if (tracks != null) {
            throw IOException("tracks already selected")
        }

        try {
            val selectedTracks = Array(readers!!.size) { i ->
                readers!![i].selectTrack(trackIndex[i])
            }
            tracks = selectedTracks
        } finally {
            parsed = true
        }
    }

    fun setMainBrand(brand: Int) {
        overrideMainBrand = brand
    }

    fun isDone(): Boolean {
        return done
    }

    fun isParsed(): Boolean {
        return parsed
    }

    @Throws(IOException::class)
    fun close() {
        done = true
        parsed = true

        sourceTracks?.forEach { it.close() }

        tracks = null
        sourceTracks = null

        readers = null
        readersChunks = null

        auxBuffer = null
        outStream = null
    }

    @Throws(IOException::class)
    suspend fun build(output: SharpStream) {
        if (done) {
            throw RuntimeException("already done")
        }
        if (!output.canWrite()) {
            throw IOException("the provided output is not writable")
        }

        //
        // WARNING: the muxer requires at least 8 samples of every track
        //          not allowed for very short tracks (less than 0.5 seconds)
        //
        outStream = output
        var read: Long = 8 // mdat box header size
        var totalSampleSize: Long = 0
        val sampleExtra = IntArray(readers!!.size)
        val defaultMediaTime = IntArray(readers!!.size)
        val defaultSampleDuration = IntArray(readers!!.size)
        val sampleCount = IntArray(readers!!.size)

        val currentTracks = tracks ?: throw IllegalStateException("Tracks not selected")
        val tablesInfo = Array(currentTracks.size) { TablesInfo() }

        val singleSampleBuffer: Int
        if (currentTracks.size == 1 && currentTracks[0].kind == TrackKind.Audio) {
            // near 1 second of audio data per chunk, avoid split the audio stream in large chunks
            singleSampleBuffer = currentTracks[0].trak!!.mdia!!.mdhdTimeScale / 1000
        } else {
            singleSampleBuffer = -1
        }

        for (i in readers!!.indices) {
            var samplesSize = 0
            var sampleSizeChanges = 0
            var compositionOffsetLast = -1
            while (true) {
                val chunk = readers!![i].getNextChunk(true) ?: break
                val currentChunk = chunk
                if (defaultMediaTime[i] < 1 && currentChunk.moof!!.traf!!.tfhd!!.defaultSampleDuration > 0) {
                    defaultMediaTime[i] = currentChunk.moof!!.traf!!.tfhd!!.defaultSampleDuration
                }

                read += currentChunk.moof!!.traf!!.trun!!.chunkSize.toLong()
                sampleExtra[i] += currentChunk.moof!!.traf!!.trun!!.chunkDuration // calculate track duration

                var info = currentChunk.getNextSampleInfo()
                while (info != null) {
                    if (info.isKeyframe) {
                        tablesInfo[i].stss++
                    }

                    if (info.sampleDuration > defaultSampleDuration[i]) {
                        defaultSampleDuration[i] = info.sampleDuration
                    }

                    tablesInfo[i].stsz++
                    if (samplesSize != info.sampleSize) {
                        samplesSize = info.sampleSize
                        sampleSizeChanges++
                    }

                    if (info.hasCompositionTimeOffset) {
                        if (info.sampleCompositionTimeOffset != compositionOffsetLast) {
                            tablesInfo[i].ctts++
                            compositionOffsetLast = info.sampleCompositionTimeOffset
                        }
                    }

                    totalSampleSize += info.sampleSize.toLong()
                    info = currentChunk.getNextSampleInfo()
                }
            }

            if (defaultMediaTime[i] < 1) {
                defaultMediaTime[i] = defaultSampleDuration[i]
            }

            readers!![i].rewind()

            if (singleSampleBuffer > 0) {
                initChunkTables(tablesInfo[i], singleSampleBuffer, singleSampleBuffer)
            } else {
                initChunkTables(tablesInfo[i], SAMPLES_PER_CHUNK_INIT.toInt(), SAMPLES_PER_CHUNK.toInt())
            }

            sampleCount[i] = tablesInfo[i].stsz

            if (sampleSizeChanges == 1) {
                tablesInfo[i].stsz = 0
                tablesInfo[i].stszDefault = samplesSize
            } else {
                tablesInfo[i].stszDefault = 0
            }

            if (tablesInfo[i].stss == tablesInfo[i].stsz) {
                tablesInfo[i].stss = -1 // for audio tracks (all samples are keyframes)
            }

            // ensure track duration
            if (currentTracks[i].trak!!.tkhd!!.duration < 1) {
                currentTracks[i].trak!!.tkhd!!.duration = sampleExtra[i].toLong() // this never should happen
            }
        }

        val is64 = read > THRESHOLD_FOR_CO64

        // calculate the moov size
        val auxSize = makeMoov(defaultMediaTime, tablesInfo, is64)

        if (auxSize < THRESHOLD_MOOV_LENGTH) {
            auxBuffer = ByteBuffer.allocate(auxSize) // cache moov in the memory
        }

        moovSimulation = false
        writeOffset = 0

        val ftypSize = makeFtyp()

        // reserve moov space in the output stream
        if (auxSize > 0) {
            var length = auxSize
            val buffer = ByteArray(64 * 1024) // 64 KiB
            while (length > 0) {
                val count = min(length, buffer.size)
                outWrite(buffer, count)
                length -= count
            }
        }

        if (auxBuffer == null) {
            outSeek(ftypSize.toLong())
        }

        // tablesInfo contains row counts
        // and after returning from makeMoov() will contain those table offsets
        makeMoov(defaultMediaTime, tablesInfo, is64)

        // write tables: stts stsc sbgp
        // reset for ctts table: sampleCount sampleExtra
        for (i in readers!!.indices) {
            writeEntryArray(tablesInfo[i].stts, 2, sampleCount[i], defaultSampleDuration[i])
            writeEntryArray(
                tablesInfo[i].stsc,
                tablesInfo[i].stscBEntries!!.size,
                *tablesInfo[i].stscBEntries!!
            )
            tablesInfo[i].stscBEntries = null
            if (tablesInfo[i].ctts > 0) {
                sampleCount[i] = 1 // the index is not base zero
                sampleExtra[i] = -1
            }
            if (tablesInfo[i].sbgp > 0) {
                writeEntryArray(tablesInfo[i].sbgp, 1, sampleCount[i])
            }
        }

        if (auxBuffer == null) {
            outRestore()
        }

        outWrite(makeMdat(totalSampleSize, is64))

        val sampleIndex = IntArray(readers!!.size)
        val sizes = IntArray(if (singleSampleBuffer > 0) singleSampleBuffer else SAMPLES_PER_CHUNK.toInt())
        val sync = IntArray(if (singleSampleBuffer > 0) singleSampleBuffer else SAMPLES_PER_CHUNK.toInt())

        var written = readers!!.size
        while (written > 0) {
            written = 0

            for (i in readers!!.indices) {
                if (sampleIndex[i] < 0) {
                    continue // track is done
                }

                val chunkOffset = writeOffset
                var syncCount = 0
                val limit: Int = if (singleSampleBuffer > 0) {
                    singleSampleBuffer
                } else {
                    if (sampleIndex[i] == 0) SAMPLES_PER_CHUNK_INIT.toInt() else SAMPLES_PER_CHUNK.toInt()
                }

                var j = 0
                while (j < limit) {
                    val sample = getNextSample(i)

                    if (sample == null) {
                        if (tablesInfo[i].ctts > 0 && sampleExtra[i] >= 0) {
                            writeEntryArray(
                                tablesInfo[i].ctts,
                                1,
                                sampleCount[i],
                                sampleExtra[i]
                            ) // flush last entries
                            outRestore()
                        }
                        sampleIndex[i] = -1
                        break
                    }

                    sampleIndex[i]++

                    if (tablesInfo[i].ctts > 0) {
                        if (sample.info!!.sampleCompositionTimeOffset == sampleExtra[i]) {
                            sampleCount[i]++
                        } else {
                            if (sampleExtra[i] >= 0) {
                                tablesInfo[i].ctts = writeEntryArray(
                                    tablesInfo[i].ctts,
                                    2,
                                    sampleCount[i],
                                    sampleExtra[i]
                                )
                                outRestore()
                            }
                            sampleCount[i] = 1
                            sampleExtra[i] = sample.info!!.sampleCompositionTimeOffset
                        }
                    }

                    if (tablesInfo[i].stss > 0 && sample.isKeyframe) {
                        sync[syncCount++] = sampleIndex[i]
                    }

                    if (tablesInfo[i].stsz > 0) {
                        sizes[j] = sample.data!!.size
                    }

                    outWrite(sample.data!!, sample.data!!.size)
                    j++
                }

                if (j > 0) {
                    written++

                    if (tablesInfo[i].stsz > 0) {
                        tablesInfo[i].stsz = writeEntryArray(tablesInfo[i].stsz, j, *sizes)
                    }

                    if (syncCount > 0) {
                        tablesInfo[i].stss = writeEntryArray(tablesInfo[i].stss, syncCount, *sync)
                    }

                    if (tablesInfo[i].stco > 0) {
                        if (is64) {
                            tablesInfo[i].stco = writeEntry64(tablesInfo[i].stco, chunkOffset)
                        } else {
                            tablesInfo[i].stco = writeEntryArray(
                                tablesInfo[i].stco,
                                1,
                                chunkOffset.toInt()
                            )
                        }
                    }

                    outRestore()
                }
            }
        }

        if (auxBuffer != null) {
            // dump moov
            outSeek(ftypSize.toLong())
            outStream!!.write(auxBuffer!!.array(), 0, auxBuffer!!.capacity())
            auxBuffer = null
        }
    }

    @Throws(IOException::class)
    private suspend fun getNextSample(track: Int): Mp4DashSample? {
        if (readersChunks!![track] == null) {
            readersChunks!![track] = readers!![track].getNextChunk(false)
            if (readersChunks!![track] == null) {
                return null // EOF reached
            }
        }

        val sample = readersChunks!![track]!!.getNextSample()
        return if (sample == null) {
            readersChunks!![track] = null
            getNextSample(track)
        } else {
            sample
        }
    }

    @Throws(IOException::class)
    private suspend fun writeEntry64(offset: Int, value: Long): Int {
        outBackup()

        auxSeek(offset)
        auxWrite(ByteBuffer.allocate(8).putLong(value).array())

        return offset + 8
    }

    @Throws(IOException::class)
    private suspend fun writeEntryArray(offset: Int, count: Int, vararg values: Int): Int {
        outBackup()

        auxSeek(offset)

        val size = count * 4
        val buffer = ByteBuffer.allocate(size)

        for (i in 0 until count) {
            buffer.putInt(values[i])
        }

        auxWrite(buffer.array())

        return offset + size
    }

    private fun outBackup() {
        if (auxBuffer == null && lastWriteOffset < 0) {
            lastWriteOffset = writeOffset
        }
    }

    /**
     * Restore to the previous position before the first call to writeEntry64()
     * or writeEntryArray() methods.
     */
    @Throws(IOException::class)
    private suspend fun outRestore() {
        if (lastWriteOffset > 0) {
            outSeek(lastWriteOffset)
            lastWriteOffset = -1
        }
    }

    private fun initChunkTables(
        tables: TablesInfo,
        firstCount: Int,
        successiveCount: Int
    ) {
        // tables.stsz holds amount of samples of the track (total)
        val totalSamples = tables.stsz - firstCount
        val chunkAmount = totalSamples / successiveCount.toFloat()
        val remainChunkOffset = ceil(chunkAmount.toDouble()).toInt()
        val remain = remainChunkOffset != chunkAmount.toInt()
        var index = 0

        tables.stsc = 1
        if (firstCount != successiveCount) {
            tables.stsc++
        }
        if (remain) {
            tables.stsc++
        }

        // stsc_table_entry = [first_chunk, samples_per_chunk, sample_description_index]
        tables.stscBEntries = IntArray(tables.stsc * 3)
        tables.stco = remainChunkOffset + 1 // total entries in chunk offset box

        tables.stscBEntries!![index++] = 1
        tables.stscBEntries!![index++] = firstCount
        tables.stscBEntries!![index++] = 1

        if (firstCount != successiveCount) {
            tables.stscBEntries!![index++] = 2
            tables.stscBEntries!![index++] = successiveCount
            tables.stscBEntries!![index++] = 1
        }

        if (remain) {
            tables.stscBEntries!![index++] = remainChunkOffset + 1
            tables.stscBEntries!![index++] = totalSamples % successiveCount
            tables.stscBEntries!![index] = 1
        }
    }

    @Throws(IOException::class)
    private suspend fun outWrite(buffer: ByteArray) {
        outWrite(buffer, buffer.size)
    }

    @Throws(IOException::class)
    private suspend fun outWrite(buffer: ByteArray, count: Int) {
        writeOffset += count.toLong()
        outStream!!.write(buffer, 0, count)
    }

    @Throws(IOException::class)
    private suspend fun outSeek(offset: Long) {
        if (outStream!!.canSeek()) {
            outStream!!.seek(offset)
            writeOffset = offset
        } else if (outStream!!.canRewind()) {
            outStream!!.rewind()
            writeOffset = 0
            outSkip(offset)
        } else {
            throw IOException("cannot seek or rewind the output stream")
        }
    }

    @Throws(IOException::class)
    private suspend fun outSkip(amount: Long) {
        outStream!!.skip(amount)
        writeOffset += amount
    }

    @Throws(IOException::class)
    private suspend fun lengthFor(offset: Int): Int {
        val size = auxOffset() - offset

        if (moovSimulation) {
            return size
        }

        auxSeek(offset)
        auxWrite(size)
        auxSkip(size - 4)

        return size
    }

    @Throws(IOException::class)
    private suspend fun make(
        type: Int,
        extra: Int,
        columns: Int,
        rows: Int
    ): Int {
        val base: Byte = 16
        val size = columns * rows * 4
        var total = size + base
        var offset = auxOffset()

        if (extra >= 0) {
            total += 4
        }

        auxWrite(
            ByteBuffer.allocate(12)
                .putInt(total)
                .putInt(type)
                .putInt(0x00) // default version & flags
                .array()
        )

        if (extra >= 0) {
            offset += 4
            auxWrite(extra)
        }

        auxWrite(rows)
        auxSkip(size)

        return offset + base
    }

    @Throws(IOException::class)
    private suspend fun auxWrite(value: Int) {
        auxWrite(
            ByteBuffer.allocate(4)
                .putInt(value)
                .array()
        )
    }

    @Throws(IOException::class)
    private suspend fun auxWrite(buffer: ByteArray) {
        if (moovSimulation) {
            writeOffset += buffer.size.toLong()
        } else if (auxBuffer == null) {
            outWrite(buffer, buffer.size)
        } else {
            auxBuffer!!.put(buffer)
        }
    }

    @Throws(IOException::class)
    private suspend fun auxSeek(offset: Int) {
        if (moovSimulation) {
            writeOffset = offset.toLong()
        } else if (auxBuffer == null) {
            outSeek(offset.toLong())
        } else {
            auxBuffer!!.position(offset)
        }
    }

    @Throws(IOException::class)
    private suspend fun auxSkip(amount: Int) {
        if (moovSimulation) {
            writeOffset += amount.toLong()
        } else if (auxBuffer == null) {
            outSkip(amount.toLong())
        } else {
            auxBuffer!!.position(auxBuffer!!.position() + amount)
        }
    }

    private fun auxOffset(): Int {
        return if (auxBuffer == null) writeOffset.toInt() else auxBuffer!!.position()
    }

    @Throws(IOException::class)
    private suspend fun makeFtyp(): Int {
        var size = 16 + compatibleBrands.size * 4
        if (overrideMainBrand != 0) {
            size += 4
        }

        val buffer = ByteBuffer.allocate(size)
        buffer.putInt(size)
        buffer.putInt(0x66747970) // "ftyp"

        if (overrideMainBrand == 0) {
            buffer.putInt(0x6D703432) // mayor brand "mp42"
            buffer.putInt(512) // default minor version
        } else {
            buffer.putInt(overrideMainBrand)
            buffer.putInt(0)
            buffer.putInt(0x6D703432) // "mp42" compatible brand
        }

        for (brand in compatibleBrands) {
            buffer.putInt(brand) // compatible brand
        }

        outWrite(buffer.array())

        return size
    }

    private fun makeMdat(refSize: Long, is64: Boolean): ByteArray {
        var size = refSize
        if (is64) {
            size += 16
        } else {
            size += 8
        }

        val buffer = ByteBuffer.allocate(if (is64) 16 else 8)
            .putInt(if (is64) 0x01 else size.toInt())
            .putInt(0x6D646174) // mdat

        if (is64) {
            buffer.putLong(size)
        }

        return buffer.array()
    }

    @Throws(IOException::class)
    private suspend fun makeMvhd(longestTrack: Long) {
        auxWrite(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x78, 0x6D, 0x76, 0x68, 0x64, 0x01, 0x00, 0x00, 0x00
            )
        )
        auxWrite(
            ByteBuffer.allocate(28)
                .putLong(time)
                .putLong(time)
                .putInt(DEFAULT_TIMESCALE.toInt())
                .putLong(longestTrack)
                .array()
        )

        auxWrite(
            byteArrayOf(
                0x00, 0x01, 0x00, 0x00, 0x01, 0x00, // default volume and rate
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // reserved values
                // default matrix
                0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x40, 0x00, 0x00, 0x00
            )
        )
        auxWrite(ByteArray(24)) // predefined
        auxWrite(
            ByteBuffer.allocate(4)
                .putInt(tracks?.size?.plus(1) ?: 1)
                .array()
        )
    }

    @Throws(RuntimeException::class, IOException::class)
    private suspend fun makeMoov(
        defaultMediaTime: IntArray,
        tablesInfo: Array<TablesInfo>,
        is64: Boolean
    ): Int {
        val start = auxOffset()

        auxWrite(
            byteArrayOf(
                0x00,
                0x00,
                0x00,
                0x00,
                0x6D,
                0x6F,
                0x6F,
                0x76
            )
        )

        var longestTrack: Long = 0
        val durations = LongArray(tracks!!.size)

        for (i in durations.indices) {
            durations[i] = ceil(
                (tracks!![i].trak!!.tkhd!!.duration.toDouble() / tracks!![i].trak!!.mdia!!.mdhdTimeScale) *
                    DEFAULT_TIMESCALE
            ).toLong()

            if (durations[i] > longestTrack) {
                longestTrack = durations[i]
            }
        }

        makeMvhd(longestTrack)

        for (i in tracks!!.indices) {
            if (tracks!![i].trak!!.tkhd!!.matrix!!.size != 36) {
                throw RuntimeException("bad track matrix length (expected 36) in track n°$i")
            }
            makeTrak(i, durations[i], defaultMediaTime[i], tablesInfo[i], is64)
        }

        return lengthFor(start)
    }

    @Throws(IOException::class)
    private suspend fun makeTrak(
        index: Int,
        duration: Long,
        defaultMediaTime: Int,
        tables: TablesInfo,
        is64: Boolean
    ) {
        val start = auxOffset()

        auxWrite(
            byteArrayOf(
                // trak header
                0x00, 0x00, 0x00, 0x00, 0x74, 0x72, 0x61, 0x6B,
                // tkhd header
                0x00, 0x00, 0x00, 0x68, 0x74, 0x6B, 0x68, 0x64, 0x01, 0x00, 0x00, 0x03
            )
        )

        val buffer = ByteBuffer.allocate(48)
        buffer.putLong(time)
        buffer.putLong(time)
        buffer.putInt(index + 1)
        buffer.position(24)
        buffer.putLong(duration)
        buffer.position(40)
        buffer.putShort(tracks!![index].trak!!.tkhd!!.bLayer)
        buffer.putShort(tracks!![index].trak!!.tkhd!!.bAlternateGroup)
        buffer.putShort(tracks!![index].trak!!.tkhd!!.bVolume)
        auxWrite(buffer.array())

        auxWrite(tracks!![index].trak!!.tkhd!!.matrix!!)
        auxWrite(
            ByteBuffer.allocate(8)
                .putInt(tracks!![index].trak!!.tkhd!!.bWidth)
                .putInt(tracks!![index].trak!!.tkhd!!.bHeight)
                .array()
        )

        auxWrite(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x24, 0x65, 0x64, 0x74, 0x73, // edts header
                0x00, 0x00, 0x00, 0x1C, 0x65, 0x6C, 0x73, 0x74,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01 // elst header
            )
        )

        val bMediaRate: Int
        val mediaTime: Int

        if (tracks!![index].trak!!.edstElst == null) {
            // is a audio track ¿is edst/elst optional for audio tracks?
            mediaTime = 0x00 // ffmpeg set this value as zero, instead of defaultMediaTime
            bMediaRate = 0x00010000
        } else {
            mediaTime = tracks!![index].trak!!.edstElst!!.mediaTime.toInt()
            bMediaRate = tracks!![index].trak!!.edstElst!!.bMediaRate
        }

        auxWrite(
            ByteBuffer
                .allocate(12)
                .putInt(duration.toInt())
                .putInt(mediaTime)
                .putInt(bMediaRate)
                .array()
        )

        makeMdia(tracks!![index].trak!!.mdia!!, tables, is64, tracks!![index].kind == TrackKind.Audio)

        lengthFor(start)
    }

    @Throws(IOException::class)
    private suspend fun makeMdia(
        mdia: Mdia,
        tablesInfo: TablesInfo,
        is64: Boolean,
        isAudio: Boolean
    ) {
        val startMdia = auxOffset()
        auxWrite(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x6D, 0x64, 0x69, 0x61)) // mdia
        auxWrite(mdia.mdhd!!)
        auxWrite(makeHdlr(mdia.hdlr!!))

        val startMinf = auxOffset()
        auxWrite(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x6D, 0x69, 0x6E, 0x66)) // minf
        auxWrite(mdia.minf!!.mhd!!)
        auxWrite(mdia.minf!!.dinf!!)

        val startStbl = auxOffset()
        auxWrite(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x73, 0x74, 0x62, 0x6C)) // stbl
        auxWrite(mdia.minf!!.stblStsd!!)

        //
        // In audio tracks the following tables is not required: ssts ctts
        // And stsz can be empty if has a default sample size
        //
        if (moovSimulation) {
            make(0x73747473, -1, 2, 1) // stts
            if (tablesInfo.stss > 0) {
                make(0x73747373, -1, 1, tablesInfo.stss)
            }
            if (tablesInfo.ctts > 0) {
                make(0x63747473, -1, 2, tablesInfo.ctts)
            }
            make(0x73747363, -1, 3, tablesInfo.stsc)
            make(0x7374737A, tablesInfo.stszDefault, 1, tablesInfo.stsz)
            make(if (is64) 0x636F3634 else 0x7374636F, -1, if (is64) 2 else 1, tablesInfo.stco)
        } else {
            tablesInfo.stts = make(0x73747473, -1, 2, 1)
            if (tablesInfo.stss > 0) {
                tablesInfo.stss = make(0x73747373, -1, 1, tablesInfo.stss)
            }
            if (tablesInfo.ctts > 0) {
                tablesInfo.ctts = make(0x63747473, -1, 2, tablesInfo.ctts)
            }
            tablesInfo.stsc = make(0x73747363, -1, 3, tablesInfo.stsc)
            tablesInfo.stsz = make(0x7374737A, tablesInfo.stszDefault, 1, tablesInfo.stsz)
            tablesInfo.stco = make(
                if (is64) 0x636F3634 else 0x7374636F,
                -1,
                if (is64) 2 else 1,
                tablesInfo.stco
            )
        }

        if (isAudio) {
            auxWrite(makeSgpd())
            tablesInfo.sbgp = makeSbgp() // during simulation the returned offset is ignored
        }

        lengthFor(startStbl)
        lengthFor(startMinf)
        lengthFor(startMdia)
    }

    private fun makeHdlr(hdlr: Hdlr): ByteArray {
        val buffer = ByteBuffer.wrap(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x21, 0x68, 0x64, 0x6C, 0x72, // hdlr
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00 // null string character
            )
        )

        buffer.position(12)
        buffer.putInt(hdlr.type)
        buffer.putInt(hdlr.subType)
        buffer.put(hdlr.bReserved!!) // always is a zero array

        return buffer.array()
    }

    @Throws(IOException::class)
    private suspend fun makeSbgp(): Int {
        val offset = auxOffset()

        auxWrite(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x1C, // box size
                0x73, 0x62, 0x67, 0x70, // "sbpg"
                0x00, 0x00, 0x00, 0x00, // default box flags
                0x72, 0x6F, 0x6C, 0x6C, // group type "roll"
                0x00, 0x00, 0x00, 0x01, // group table size
                0x00, 0x00, 0x00, 0x00, // group[0] total samples (to be set later)
                0x00, 0x00, 0x00, 0x01 // group[0] description index
            )
        )

        return offset + 0x14
    }

    private fun makeSgpd(): ByteArray {
        /*
         * Sample Group Description Box
         *
         * ¿whats does?
         * the table inside of this box gives information about the
         * characteristics of sample groups. The descriptive information is any other
         * information needed to define or characterize the sample group.
         *
         * ¿is replicable this box?
         * NO due lacks of documentation about this box but...
         * most of m4a encoders and ffmpeg uses this box with dummy values (same values)
         */

        val buffer = ByteBuffer.wrap(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x1A, // box size
                0x73, 0x67, 0x70, 0x64, // "sgpd"
                0x01, 0x00, 0x00, 0x00, // box flags (unknown flag sets)
                0x72, 0x6F, 0x6C, 0x6C, // ¿¿group type??
                0x00, 0x00, 0x00, 0x02, // ¿¿??
                0x00, 0x00, 0x00, 0x01, // ¿¿??
                0xFF.toByte(), 0xFF.toByte() // ¿¿??
            )
        )

        return buffer.array()
    }

    internal class TablesInfo {
        var stts: Int = 0
        var stsc: Int = 0
        var stscBEntries: IntArray? = null
        var ctts: Int = 0
        var stsz: Int = 0
        var stszDefault: Int = 0
        var stss: Int = 0
        var stco: Int = 0
        var sbgp: Int = 0
    }

    companion object {
        private const val EPOCH_OFFSET = 2082844800
        private const val DEFAULT_TIMESCALE: Short = 1000
        private const val SAMPLES_PER_CHUNK_INIT: Byte = 2

        // ffmpeg uses 2, basic uses 1 (with 60fps uses 21 or 22). NewPipe will use 6
        private const val SAMPLES_PER_CHUNK: Byte = 6

        // near 3.999 GiB
        private const val THRESHOLD_FOR_CO64 = 0xFFFEFFFFL

        // 2.2 MiB enough for: 1080p 60fps 00h35m00s
        private const val THRESHOLD_MOOV_LENGTH = (256 * 1024) + (2048 * 1024)
    }
}
