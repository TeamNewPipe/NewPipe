package org.schabi.newpipe.streams

import org.schabi.newpipe.streams.Mp4DashReader.Hdlr
import org.schabi.newpipe.streams.Mp4DashReader.Mdia
import org.schabi.newpipe.streams.Mp4DashReader.Mp4DashChunk
import org.schabi.newpipe.streams.Mp4DashReader.Mp4DashSample
import org.schabi.newpipe.streams.Mp4DashReader.TrunEntry
import org.schabi.newpipe.streams.io.SharpStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.min

/**
 * @author kapodamy
 */
class Mp4FromDashWriter(vararg sources: SharpStream) {
    private val time: Long
    private var auxBuffer: ByteBuffer? = null
    private var outStream: SharpStream? = null
    private var lastWriteOffset: Long = -1
    private var writeOffset: Long = 0
    private var moovSimulation: Boolean = true
    private var done: Boolean = false
    private var parsed: Boolean = false
    private var tracks: Array<Mp4DashReader.Mp4Track?>?
    private var sourceTracks: Array<SharpStream>?
    private var readers: Array<Mp4DashReader?>?
    private var readersChunks: Array<Mp4DashChunk?>?
    private var overrideMainBrand: Int = 0x00
    private val compatibleBrands: ArrayList<Int> = ArrayList(5)

    init {
        for (src: SharpStream in sources) {
            if (!src.canRewind() && !src.canRead()) {
                throw IOException("All sources must be readable and allow rewind")
            }
        }
        sourceTracks = sources
        readers = arrayOfNulls(sourceTracks!!.size)
        readersChunks = arrayOfNulls(readers!!.size)
        time = (System.currentTimeMillis() / 1000L) + EPOCH_OFFSET
        compatibleBrands.add(0x6D703431) // mp41
        compatibleBrands.add(0x69736F6D) // isom
        compatibleBrands.add(0x69736F32) // iso2
    }

    @Throws(IllegalStateException::class)
    fun getTracksFromSource(sourceIndex: Int): Array<Mp4DashReader.Mp4Track?>? {
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
                readers!!.get(i) = Mp4DashReader(sourceTracks!!.get(i))
                readers!!.get(i)!!.parse()
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
            tracks = arrayOfNulls(readers!!.size)
            for (i in readers!!.indices) {
                tracks!!.get(i) = readers!!.get(i)!!.selectTrack(trackIndex.get(i))
            }
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
        for (src: SharpStream in sourceTracks!!) {
            src.close()
        }
        tracks = null
        sourceTracks = null
        readers = null
        readersChunks = null
        auxBuffer = null
        outStream = null
    }

    @Throws(IOException::class)
    fun build(output: SharpStream?) {
        if (done) {
            throw RuntimeException("already done")
        }
        if (!output!!.canWrite()) {
            throw IOException("the provided output is not writable")
        }

        //
        // WARNING: the muxer requires at least 8 samples of every track
        //          not allowed for very short tracks (less than 0.5 seconds)
        //
        outStream = output
        var read: Long = 8 // mdat box header size
        var totalSampleSize: Long = 0
        val sampleExtra: IntArray = IntArray(readers!!.size)
        val defaultMediaTime: IntArray = IntArray(readers!!.size)
        val defaultSampleDuration: IntArray = IntArray(readers!!.size)
        val sampleCount: IntArray = IntArray(readers!!.size)
        val tablesInfo: Array<TablesInfo?> = arrayOfNulls(tracks!!.size)
        for (i in tablesInfo.indices) {
            tablesInfo.get(i) = TablesInfo()
        }
        val singleSampleBuffer: Int
        if (tracks!!.size == 1 && tracks!!.get(0)!!.kind == Mp4DashReader.TrackKind.Audio) {
            // near 1 second of audio data per chunk, avoid split the audio stream in large chunks
            singleSampleBuffer = tracks!!.get(0)!!.trak!!.mdia!!.mdhdTimeScale / 1000
        } else {
            singleSampleBuffer = -1
        }
        for (i in readers!!.indices) {
            var samplesSize: Int = 0
            var sampleSizeChanges: Int = 0
            var compositionOffsetLast: Int = -1
            var chunk: Mp4DashChunk
            while ((readers!!.get(i)!!.getNextChunk(true).also({ chunk = (it)!! })) != null) {
                if (defaultMediaTime.get(i) < 1 && chunk.moof!!.traf!!.tfhd!!.defaultSampleDuration > 0) {
                    defaultMediaTime.get(i) = chunk.moof!!.traf!!.tfhd!!.defaultSampleDuration
                }
                read += chunk.moof!!.traf!!.trun!!.chunkSize.toLong()
                sampleExtra.get(i) += chunk.moof!!.traf!!.trun!!.chunkDuration // calculate track duration
                var info: TrunEntry
                while ((chunk.getNextSampleInfo().also({ info = (it)!! })) != null) {
                    if (info.isKeyframe) {
                        tablesInfo.get(i)!!.stss++
                    }
                    if (info.sampleDuration > defaultSampleDuration.get(i)) {
                        defaultSampleDuration.get(i) = info.sampleDuration
                    }
                    tablesInfo.get(i)!!.stsz++
                    if (samplesSize != info.sampleSize) {
                        samplesSize = info.sampleSize
                        sampleSizeChanges++
                    }
                    if (info.hasCompositionTimeOffset) {
                        if (info.sampleCompositionTimeOffset != compositionOffsetLast) {
                            tablesInfo.get(i)!!.ctts++
                            compositionOffsetLast = info.sampleCompositionTimeOffset
                        }
                    }
                    totalSampleSize += info.sampleSize.toLong()
                }
            }
            if (defaultMediaTime.get(i) < 1) {
                defaultMediaTime.get(i) = defaultSampleDuration.get(i)
            }
            readers!!.get(i)!!.rewind()
            if (singleSampleBuffer > 0) {
                initChunkTables(tablesInfo.get(i), singleSampleBuffer, singleSampleBuffer)
            } else {
                initChunkTables(tablesInfo.get(i), SAMPLES_PER_CHUNK_INIT.toInt(), SAMPLES_PER_CHUNK.toInt())
            }
            sampleCount.get(i) = tablesInfo.get(i)!!.stsz
            if (sampleSizeChanges == 1) {
                tablesInfo.get(i)!!.stsz = 0
                tablesInfo.get(i)!!.stszDefault = samplesSize
            } else {
                tablesInfo.get(i)!!.stszDefault = 0
            }
            if (tablesInfo.get(i)!!.stss == tablesInfo.get(i)!!.stsz) {
                tablesInfo.get(i)!!.stss = -1 // for audio tracks (all samples are keyframes)
            }

            // ensure track duration
            if (tracks!!.get(i)!!.trak!!.tkhd!!.duration < 1) {
                tracks!!.get(i)!!.trak!!.tkhd!!.duration = sampleExtra.get(i).toLong() // this never should happen
            }
        }
        val is64: Boolean = read > THRESHOLD_FOR_CO64

        // calculate the moov size
        val auxSize: Int = makeMoov(defaultMediaTime, tablesInfo, is64)
        if (auxSize < THRESHOLD_MOOV_LENGTH) {
            auxBuffer = ByteBuffer.allocate(auxSize) // cache moov in the memory
        }
        moovSimulation = false
        writeOffset = 0
        val ftypSize: Int = makeFtyp()

        // reserve moov space in the output stream
        if (auxSize > 0) {
            var length: Int = auxSize
            val buffer: ByteArray = ByteArray(64 * 1024) // 64 KiB
            while (length > 0) {
                val count: Int = min(length.toDouble(), buffer.size.toDouble()).toInt()
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
            writeEntryArray(tablesInfo.get(i)!!.stts, 2, sampleCount.get(i), defaultSampleDuration.get(i))
            writeEntryArray(tablesInfo.get(i)!!.stsc, tablesInfo.get(i)!!.stscBEntries!!.size,
                    *(tablesInfo.get(i)!!.stscBEntries)!!)
            tablesInfo.get(i)!!.stscBEntries = null
            if (tablesInfo.get(i)!!.ctts > 0) {
                sampleCount.get(i) = 1 // the index is not base zero
                sampleExtra.get(i) = -1
            }
            if (tablesInfo.get(i)!!.sbgp > 0) {
                writeEntryArray(tablesInfo.get(i)!!.sbgp, 1, sampleCount.get(i))
            }
        }
        if (auxBuffer == null) {
            outRestore()
        }
        outWrite(makeMdat(totalSampleSize, is64))
        val sampleIndex: IntArray = IntArray(readers!!.size)
        val sizes: IntArray = IntArray(if (singleSampleBuffer > 0) singleSampleBuffer else SAMPLES_PER_CHUNK)
        val sync: IntArray = IntArray(if (singleSampleBuffer > 0) singleSampleBuffer else SAMPLES_PER_CHUNK)
        var written: Int = readers!!.size
        while (written > 0) {
            written = 0
            for (i in readers!!.indices) {
                if (sampleIndex.get(i) < 0) {
                    continue  // track is done
                }
                val chunkOffset: Long = writeOffset
                var syncCount: Int = 0
                val limit: Int
                if (singleSampleBuffer > 0) {
                    limit = singleSampleBuffer
                } else {
                    limit = (if (sampleIndex.get(i) == 0) SAMPLES_PER_CHUNK_INIT else SAMPLES_PER_CHUNK).toInt()
                }
                var j: Int = 0
                while (j < limit) {
                    val sample: Mp4DashSample? = getNextSample(i)
                    if (sample == null) {
                        if (tablesInfo.get(i)!!.ctts > 0 && sampleExtra.get(i) >= 0) {
                            writeEntryArray(tablesInfo.get(i)!!.ctts, 1, sampleCount.get(i),
                                    sampleExtra.get(i)) // flush last entries
                            outRestore()
                        }
                        sampleIndex.get(i) = -1
                        break
                    }
                    sampleIndex.get(i)++
                    if (tablesInfo.get(i)!!.ctts > 0) {
                        if (sample.info!!.sampleCompositionTimeOffset == sampleExtra.get(i)) {
                            sampleCount.get(i)++
                        } else {
                            if (sampleExtra.get(i) >= 0) {
                                tablesInfo.get(i)!!.ctts = writeEntryArray(tablesInfo.get(i)!!.ctts, 2,
                                        sampleCount.get(i), sampleExtra.get(i))
                                outRestore()
                            }
                            sampleCount.get(i) = 1
                            sampleExtra.get(i) = sample.info!!.sampleCompositionTimeOffset
                        }
                    }
                    if (tablesInfo.get(i)!!.stss > 0 && sample.info!!.isKeyframe) {
                        sync.get(syncCount++) = sampleIndex.get(i)
                    }
                    if (tablesInfo.get(i)!!.stsz > 0) {
                        sizes.get(j) = sample.data.size
                    }
                    outWrite(sample.data, sample.data.size)
                    j++
                }
                if (j > 0) {
                    written++
                    if (tablesInfo.get(i)!!.stsz > 0) {
                        tablesInfo.get(i)!!.stsz = writeEntryArray(tablesInfo.get(i)!!.stsz, j, *sizes)
                    }
                    if (syncCount > 0) {
                        tablesInfo.get(i)!!.stss = writeEntryArray(tablesInfo.get(i)!!.stss, syncCount, *sync)
                    }
                    if (tablesInfo.get(i)!!.stco > 0) {
                        if (is64) {
                            tablesInfo.get(i)!!.stco = writeEntry64(tablesInfo.get(i)!!.stco, chunkOffset)
                        } else {
                            tablesInfo.get(i)!!.stco = writeEntryArray(tablesInfo.get(i)!!.stco, 1, chunkOffset.toInt())
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
    private fun getNextSample(track: Int): Mp4DashSample? {
        if (readersChunks!!.get(track) == null) {
            readersChunks!!.get(track) = readers!!.get(track)!!.getNextChunk(false)
            if (readersChunks!!.get(track) == null) {
                return null // EOF reached
            }
        }
        val sample: Mp4DashSample? = readersChunks!!.get(track)!!.getNextSample()
        if (sample == null) {
            readersChunks!!.get(track) = null
            return getNextSample(track)
        } else {
            return sample
        }
    }

    @Throws(IOException::class)
    private fun writeEntry64(offset: Int, value: Long): Int {
        outBackup()
        auxSeek(offset)
        auxWrite(ByteBuffer.allocate(8).putLong(value).array())
        return offset + 8
    }

    @Throws(IOException::class)
    private fun writeEntryArray(offset: Int, count: Int, vararg values: Int): Int {
        outBackup()
        auxSeek(offset)
        val size: Int = count * 4
        val buffer: ByteBuffer = ByteBuffer.allocate(size)
        for (i in 0 until count) {
            buffer.putInt(values.get(i))
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
    private fun outRestore() {
        if (lastWriteOffset > 0) {
            outSeek(lastWriteOffset)
            lastWriteOffset = -1
        }
    }

    private fun initChunkTables(tables: TablesInfo?, firstCount: Int,
                                successiveCount: Int) {
        // tables.stsz holds amount of samples of the track (total)
        val totalSamples: Int = (tables!!.stsz - firstCount)
        val chunkAmount: Float = totalSamples / successiveCount.toFloat()
        val remainChunkOffset: Int = ceil(chunkAmount.toDouble()).toInt()
        val remain: Boolean = remainChunkOffset != chunkAmount.toInt()
        var index: Int = 0
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
        tables.stscBEntries!!.get(index++) = 1
        tables.stscBEntries!!.get(index++) = firstCount
        tables.stscBEntries!!.get(index++) = 1
        if (firstCount != successiveCount) {
            tables.stscBEntries!!.get(index++) = 2
            tables.stscBEntries!!.get(index++) = successiveCount
            tables.stscBEntries!!.get(index++) = 1
        }
        if (remain) {
            tables.stscBEntries!!.get(index++) = remainChunkOffset + 1
            tables.stscBEntries!!.get(index++) = totalSamples % successiveCount
            tables.stscBEntries!!.get(index) = 1
        }
    }

    @Throws(IOException::class)
    private fun outWrite(buffer: ByteArray?, count: Int = buffer.length) {
        writeOffset += count.toLong()
        outStream!!.write(buffer, 0, count)
    }

    @Throws(IOException::class)
    private fun outSeek(offset: Long) {
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
    private fun outSkip(amount: Long) {
        outStream!!.skip(amount)
        writeOffset += amount
    }

    @Throws(IOException::class)
    private fun lengthFor(offset: Int): Int {
        val size: Int = auxOffset() - offset
        if (moovSimulation) {
            return size
        }
        auxSeek(offset)
        auxWrite(size)
        auxSkip(size - 4)
        return size
    }

    @Throws(IOException::class)
    private fun make(type: Int, extra: Int, columns: Int, rows: Int): Int {
        val base: Byte = 16
        val size: Int = columns * rows * 4
        var total: Int = size + base
        var offset: Int = auxOffset()
        if (extra >= 0) {
            total += 4
        }
        auxWrite(ByteBuffer.allocate(12)
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
    private fun auxWrite(value: Int) {
        auxWrite(ByteBuffer.allocate(4)
                .putInt(value)
                .array()
        )
    }

    @Throws(IOException::class)
    private fun auxWrite(buffer: ByteArray?) {
        if (moovSimulation) {
            writeOffset += buffer!!.size.toLong()
        } else if (auxBuffer == null) {
            outWrite(buffer, buffer!!.size)
        } else {
            auxBuffer!!.put(buffer)
        }
    }

    @Throws(IOException::class)
    private fun auxSeek(offset: Int) {
        if (moovSimulation) {
            writeOffset = offset.toLong()
        } else if (auxBuffer == null) {
            outSeek(offset.toLong())
        } else {
            auxBuffer!!.position(offset)
        }
    }

    @Throws(IOException::class)
    private fun auxSkip(amount: Int) {
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
    private fun makeFtyp(): Int {
        var size: Int = 16 + (compatibleBrands.size * 4)
        if (overrideMainBrand != 0) {
            size += 4
        }
        val buffer: ByteBuffer = ByteBuffer.allocate(size)
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
        for (brand: Int in compatibleBrands) {
            buffer.putInt(brand) // compatible brand
        }
        outWrite(buffer.array())
        return size
    }

    private fun makeMdat(refSize: Long, is64: Boolean): ByteArray {
        var size: Long = refSize
        if (is64) {
            size += 16
        } else {
            size += 8
        }
        val buffer: ByteBuffer = ByteBuffer.allocate(if (is64) 16 else 8)
                .putInt(if (is64) 0x01 else size.toInt())
                .putInt(0x6D646174) // mdat
        if (is64) {
            buffer.putLong(size)
        }
        return buffer.array()
    }

    @Throws(IOException::class)
    private fun makeMvhd(longestTrack: Long) {
        auxWrite(byteArrayOf(
                0x00, 0x00, 0x00, 0x78, 0x6D, 0x76, 0x68, 0x64, 0x01, 0x00, 0x00, 0x00
        ))
        auxWrite(ByteBuffer.allocate(28)
                .putLong(time)
                .putLong(time)
                .putInt(DEFAULT_TIMESCALE.toInt())
                .putLong(longestTrack)
                .array()
        )
        auxWrite(byteArrayOf(
                0x00, 0x01, 0x00, 0x00, 0x01, 0x00,  // default volume and rate
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  // reserved values
                // default matrix
                0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x40, 0x00, 0x00, 0x00
        ))
        auxWrite(ByteArray(24)) // predefined
        auxWrite(ByteBuffer.allocate(4)
                .putInt(tracks!!.size + 1)
                .array()
        )
    }

    @Throws(RuntimeException::class, IOException::class)
    private fun makeMoov(defaultMediaTime: IntArray, tablesInfo: Array<TablesInfo?>,
                         is64: Boolean): Int {
        val start: Int = auxOffset()
        auxWrite(byteArrayOf(
                0x00, 0x00, 0x00, 0x00, 0x6D, 0x6F, 0x6F, 0x76
        ))
        var longestTrack: Long = 0
        val durations: LongArray = LongArray(tracks!!.size)
        for (i in durations.indices) {
            durations.get(i) = ceil((
                    (tracks!!.get(i)!!.trak!!.tkhd!!.duration.toDouble() / tracks!!.get(i)!!.trak!!.mdia!!.mdhdTimeScale)
                            * DEFAULT_TIMESCALE)).toLong()
            if (durations.get(i) > longestTrack) {
                longestTrack = durations.get(i)
            }
        }
        makeMvhd(longestTrack)
        for (i in tracks!!.indices) {
            if (tracks!!.get(i)!!.trak!!.tkhd!!.matrix.size != 36) {
                throw RuntimeException("bad track matrix length (expected 36) in track n°" + i)
            }
            makeTrak(i, durations.get(i), defaultMediaTime.get(i), tablesInfo.get(i), is64)
        }
        return lengthFor(start)
    }

    @Throws(IOException::class)
    private fun makeTrak(index: Int, duration: Long, defaultMediaTime: Int,
                         tables: TablesInfo?, is64: Boolean) {
        val start: Int = auxOffset()
        auxWrite(byteArrayOf( // trak header
                0x00, 0x00, 0x00, 0x00, 0x74, 0x72, 0x61, 0x6B,  // tkhd header
                0x00, 0x00, 0x00, 0x68, 0x74, 0x6B, 0x68, 0x64, 0x01, 0x00, 0x00, 0x03
        ))
        val buffer: ByteBuffer = ByteBuffer.allocate(48)
        buffer.putLong(time)
        buffer.putLong(time)
        buffer.putInt(index + 1)
        buffer.position(24)
        buffer.putLong(duration)
        buffer.position(40)
        buffer.putShort(tracks!!.get(index)!!.trak!!.tkhd!!.bLayer)
        buffer.putShort(tracks!!.get(index)!!.trak!!.tkhd!!.bAlternateGroup)
        buffer.putShort(tracks!!.get(index)!!.trak!!.tkhd!!.bVolume)
        auxWrite(buffer.array())
        auxWrite(tracks!!.get(index)!!.trak!!.tkhd!!.matrix)
        auxWrite(ByteBuffer.allocate(8)
                .putInt(tracks!!.get(index)!!.trak!!.tkhd!!.bWidth)
                .putInt(tracks!!.get(index)!!.trak!!.tkhd!!.bHeight)
                .array()
        )
        auxWrite(byteArrayOf(
                0x00, 0x00, 0x00, 0x24, 0x65, 0x64, 0x74, 0x73,  // edts header
                0x00, 0x00, 0x00, 0x1C, 0x65, 0x6C, 0x73, 0x74,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01 // elst header
        ))
        val bMediaRate: Int
        val mediaTime: Int
        if (tracks!!.get(index)!!.trak!!.edstElst == null) {
            // is a audio track ¿is edst/elst optional for audio tracks?
            mediaTime = 0x00 // ffmpeg set this value as zero, instead of defaultMediaTime
            bMediaRate = 0x00010000
        } else {
            mediaTime = tracks!!.get(index)!!.trak!!.edstElst!!.mediaTime.toInt()
            bMediaRate = tracks!!.get(index)!!.trak!!.edstElst!!.bMediaRate
        }
        auxWrite(ByteBuffer
                .allocate(12)
                .putInt(duration.toInt())
                .putInt(mediaTime)
                .putInt(bMediaRate)
                .array()
        )
        makeMdia(tracks!!.get(index)!!.trak!!.mdia, tables, is64, tracks!!.get(index)!!.kind == Mp4DashReader.TrackKind.Audio)
        lengthFor(start)
    }

    @Throws(IOException::class)
    private fun makeMdia(mdia: Mdia?, tablesInfo: TablesInfo?, is64: Boolean,
                         isAudio: Boolean) {
        val startMdia: Int = auxOffset()
        auxWrite(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x6D, 0x64, 0x69, 0x61)) // mdia
        auxWrite(mdia!!.mdhd)
        auxWrite(makeHdlr(mdia.hdlr))
        val startMinf: Int = auxOffset()
        auxWrite(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x6D, 0x69, 0x6E, 0x66)) // minf
        auxWrite(mdia.minf!!.mhd)
        auxWrite(mdia.minf!!.dinf)
        val startStbl: Int = auxOffset()
        auxWrite(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x73, 0x74, 0x62, 0x6C)) // stbl
        auxWrite(mdia.minf!!.stblStsd)

        //
        // In audio tracks the following tables is not required: ssts ctts
        // And stsz can be empty if has a default sample size
        //
        if (moovSimulation) {
            make(0x73747473, -1, 2, 1) // stts
            if (tablesInfo!!.stss > 0) {
                make(0x73747373, -1, 1, tablesInfo.stss)
            }
            if (tablesInfo.ctts > 0) {
                make(0x63747473, -1, 2, tablesInfo.ctts)
            }
            make(0x73747363, -1, 3, tablesInfo.stsc)
            make(0x7374737A, tablesInfo.stszDefault, 1, tablesInfo.stsz)
            make(if (is64) 0x636F3634 else 0x7374636F, -1, if (is64) 2 else 1, tablesInfo.stco)
        } else {
            tablesInfo!!.stts = make(0x73747473, -1, 2, 1)
            if (tablesInfo.stss > 0) {
                tablesInfo.stss = make(0x73747373, -1, 1, tablesInfo.stss)
            }
            if (tablesInfo.ctts > 0) {
                tablesInfo.ctts = make(0x63747473, -1, 2, tablesInfo.ctts)
            }
            tablesInfo.stsc = make(0x73747363, -1, 3, tablesInfo.stsc)
            tablesInfo.stsz = make(0x7374737A, tablesInfo.stszDefault, 1, tablesInfo.stsz)
            tablesInfo.stco = make(if (is64) 0x636F3634 else 0x7374636F, -1, if (is64) 2 else 1,
                    tablesInfo.stco)
        }
        if (isAudio) {
            auxWrite(makeSgpd())
            tablesInfo.sbgp = makeSbgp() // during simulation the returned offset is ignored
        }
        lengthFor(startStbl)
        lengthFor(startMinf)
        lengthFor(startMdia)
    }

    private fun makeHdlr(hdlr: Hdlr?): ByteArray {
        val buffer: ByteBuffer = ByteBuffer.wrap(byteArrayOf(
                0x00, 0x00, 0x00, 0x21, 0x68, 0x64, 0x6C, 0x72,  // hdlr
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00 // null string character
        ))
        buffer.position(12)
        buffer.putInt(hdlr!!.type)
        buffer.putInt(hdlr.subType)
        buffer.put(hdlr.bReserved) // always is a zero array
        return buffer.array()
    }

    @Throws(IOException::class)
    private fun makeSbgp(): Int {
        val offset: Int = auxOffset()
        auxWrite(byteArrayOf(
                0x00, 0x00, 0x00, 0x1C,  // box size
                0x73, 0x62, 0x67, 0x70,  // "sbpg"
                0x00, 0x00, 0x00, 0x00,  // default box flags
                0x72, 0x6F, 0x6C, 0x6C,  // group type "roll"
                0x00, 0x00, 0x00, 0x01,  // group table size
                0x00, 0x00, 0x00, 0x00,  // group[0] total samples (to be set later)
                0x00, 0x00, 0x00, 0x01 // group[0] description index
        ))
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
        val buffer: ByteBuffer = ByteBuffer.wrap(byteArrayOf(
                0x00, 0x00, 0x00, 0x1A,  // box size
                0x73, 0x67, 0x70, 0x64,  // "sgpd"
                0x01, 0x00, 0x00, 0x00,  // box flags (unknown flag sets)
                0x72, 0x6F, 0x6C, 0x6C,  // ¿¿group type??
                0x00, 0x00, 0x00, 0x02,  // ¿¿??
                0x00, 0x00, 0x00, 0x01, 0xFF.toByte(), 0xFF.toByte()))
        return buffer.array()
    }

    internal class TablesInfo() {
        var stts: Int = 0
        var stsc: Int = 0
        var stscBEntries: IntArray?
        var ctts: Int = 0
        var stsz: Int = 0
        var stszDefault: Int = 0
        var stss: Int = 0
        var stco: Int = 0
        var sbgp: Int = 0
    }

    companion object {
        private val EPOCH_OFFSET: Int = 2082844800
        private val DEFAULT_TIMESCALE: Short = 1000
        private val SAMPLES_PER_CHUNK_INIT: Byte = 2

        // ffmpeg uses 2, basic uses 1 (with 60fps uses 21 or 22). NewPipe will use 6
        private val SAMPLES_PER_CHUNK: Byte = 6

        // near 3.999 GiB
        private val THRESHOLD_FOR_CO64: Long = 0xFFFEFFFFL

        // 2.2 MiB enough for: 1080p 60fps 00h35m00s
        private val THRESHOLD_MOOV_LENGTH: Int = (256 * 1024) + (2048 * 1024)
    }
}
