package org.schabi.newpipe.streams;

import org.schabi.newpipe.streams.Mp4DashReader.Hdlr;
import org.schabi.newpipe.streams.Mp4DashReader.Mdia;
import org.schabi.newpipe.streams.Mp4DashReader.Mp4DashChunk;
import org.schabi.newpipe.streams.Mp4DashReader.Mp4DashSample;
import org.schabi.newpipe.streams.Mp4DashReader.Mp4Track;
import org.schabi.newpipe.streams.Mp4DashReader.TrackKind;
import org.schabi.newpipe.streams.Mp4DashReader.TrunEntry;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * @author kapodamy
 */
public class Mp4FromDashWriter {
    private static final int EPOCH_OFFSET = 2082844800;
    private static final short DEFAULT_TIMESCALE = 1000;
    private static final byte SAMPLES_PER_CHUNK_INIT = 2;
    // ffmpeg uses 2, basic uses 1 (with 60fps uses 21 or 22). NewPipe will use 6
    private static final byte SAMPLES_PER_CHUNK = 6;
    // near 3.999 GiB
    private static final long THRESHOLD_FOR_CO64 = 0xFFFEFFFFL;
    // 2.2 MiB enough for: 1080p 60fps 00h35m00s
    private static final int THRESHOLD_MOOV_LENGTH = (256 * 1024) + (2048 * 1024);

    private final long time;

    private ByteBuffer auxBuffer;
    private SharpStream outStream;

    private long lastWriteOffset = -1;
    private long writeOffset;

    private boolean moovSimulation = true;

    private boolean done = false;
    private boolean parsed = false;

    private Mp4Track[] tracks;
    private SharpStream[] sourceTracks;

    private Mp4DashReader[] readers;
    private Mp4DashChunk[] readersChunks;

    private int overrideMainBrand = 0x00;

    private final ArrayList<Integer> compatibleBrands = new ArrayList<>(5);

    public Mp4FromDashWriter(final SharpStream... sources) throws IOException {
        for (SharpStream src : sources) {
            if (!src.canRewind() && !src.canRead()) {
                throw new IOException("All sources must be readable and allow rewind");
            }
        }

        sourceTracks = sources;
        readers = new Mp4DashReader[sourceTracks.length];
        readersChunks = new Mp4DashChunk[readers.length];
        time = (System.currentTimeMillis() / 1000L) + EPOCH_OFFSET;

        compatibleBrands.add(0x6D703431); // mp41
        compatibleBrands.add(0x69736F6D); // isom
        compatibleBrands.add(0x69736F32); // iso2
    }

    public Mp4Track[] getTracksFromSource(final int sourceIndex) throws IllegalStateException {
        if (!parsed) {
            throw new IllegalStateException("All sources must be parsed first");
        }

        return readers[sourceIndex].getAvailableTracks();
    }

    public void parseSources() throws IOException, IllegalStateException {
        if (done) {
            throw new IllegalStateException("already done");
        }
        if (parsed) {
            throw new IllegalStateException("already parsed");
        }

        try {
            for (int i = 0; i < readers.length; i++) {
                readers[i] = new Mp4DashReader(sourceTracks[i]);
                readers[i].parse();
            }

        } finally {
            parsed = true;
        }
    }

    public void selectTracks(final int... trackIndex) throws IOException {
        if (done) {
            throw new IOException("already done");
        }
        if (tracks != null) {
            throw new IOException("tracks already selected");
        }

        try {
            tracks = new Mp4Track[readers.length];
            for (int i = 0; i < readers.length; i++) {
                tracks[i] = readers[i].selectTrack(trackIndex[i]);
            }
        } finally {
            parsed = true;
        }
    }

    public void setMainBrand(final int brand) {
        overrideMainBrand = brand;
    }

    public boolean isDone() {
        return done;
    }

    public boolean isParsed() {
        return parsed;
    }

    public void close() throws IOException {
        done = true;
        parsed = true;

        for (SharpStream src : sourceTracks) {
            src.close();
        }

        tracks = null;
        sourceTracks = null;

        readers = null;
        readersChunks = null;

        auxBuffer = null;
        outStream = null;
    }

    public void build(final SharpStream output) throws IOException {
        if (done) {
            throw new RuntimeException("already done");
        }
        if (!output.canWrite()) {
            throw new IOException("the provided output is not writable");
        }

        //
        // WARNING: the muxer requires at least 8 samples of every track
        //          not allowed for very short tracks (less than 0.5 seconds)
        //
        outStream = output;
        long read = 8; // mdat box header size
        long totalSampleSize = 0;
        int[] sampleExtra = new int[readers.length];
        int[] defaultMediaTime = new int[readers.length];
        int[] defaultSampleDuration = new int[readers.length];
        int[] sampleCount = new int[readers.length];

        TablesInfo[] tablesInfo = new TablesInfo[tracks.length];
        for (int i = 0; i < tablesInfo.length; i++) {
            tablesInfo[i] = new TablesInfo();
        }

        int singleSampleBuffer;
        if (tracks.length == 1 && tracks[0].kind == TrackKind.Audio) {
            // near 1 second of audio data per chunk, avoid split the audio stream in large chunks
            singleSampleBuffer = tracks[0].trak.mdia.mdhdTimeScale / 1000;
        } else {
            singleSampleBuffer = -1;
        }


        for (int i = 0; i < readers.length; i++) {
            int samplesSize = 0;
            int sampleSizeChanges = 0;
            int compositionOffsetLast = -1;

            Mp4DashChunk chunk;
            while ((chunk = readers[i].getNextChunk(true)) != null) {

                if (defaultMediaTime[i] < 1 && chunk.moof.traf.tfhd.defaultSampleDuration > 0) {
                    defaultMediaTime[i] = chunk.moof.traf.tfhd.defaultSampleDuration;
                }

                read += chunk.moof.traf.trun.chunkSize;
                sampleExtra[i] += chunk.moof.traf.trun.chunkDuration; // calculate track duration

                TrunEntry info;
                while ((info = chunk.getNextSampleInfo()) != null) {
                    if (info.isKeyframe) {
                        tablesInfo[i].stss++;
                    }

                    if (info.sampleDuration > defaultSampleDuration[i]) {
                        defaultSampleDuration[i] = info.sampleDuration;
                    }

                    tablesInfo[i].stsz++;
                    if (samplesSize != info.sampleSize) {
                        samplesSize = info.sampleSize;
                        sampleSizeChanges++;
                    }

                    if (info.hasCompositionTimeOffset) {
                        if (info.sampleCompositionTimeOffset != compositionOffsetLast) {
                            tablesInfo[i].ctts++;
                            compositionOffsetLast = info.sampleCompositionTimeOffset;
                        }
                    }

                    totalSampleSize += info.sampleSize;
                }
            }

            if (defaultMediaTime[i] < 1) {
                defaultMediaTime[i] = defaultSampleDuration[i];
            }

            readers[i].rewind();

            if (singleSampleBuffer > 0) {
                initChunkTables(tablesInfo[i], singleSampleBuffer, singleSampleBuffer);
            } else {
                initChunkTables(tablesInfo[i], SAMPLES_PER_CHUNK_INIT, SAMPLES_PER_CHUNK);
            }

            sampleCount[i] = tablesInfo[i].stsz;

            if (sampleSizeChanges == 1) {
                tablesInfo[i].stsz = 0;
                tablesInfo[i].stszDefault = samplesSize;
            } else {
                tablesInfo[i].stszDefault = 0;
            }

            if (tablesInfo[i].stss == tablesInfo[i].stsz) {
                tablesInfo[i].stss = -1; // for audio tracks (all samples are keyframes)
            }

            // ensure track duration
            if (tracks[i].trak.tkhd.duration < 1) {
                tracks[i].trak.tkhd.duration = sampleExtra[i]; // this never should happen
            }
        }


        boolean is64 = read > THRESHOLD_FOR_CO64;

        // calculate the moov size
        int auxSize = makeMoov(defaultMediaTime, tablesInfo, is64);

        if (auxSize < THRESHOLD_MOOV_LENGTH) {
            auxBuffer = ByteBuffer.allocate(auxSize); // cache moov in the memory
        }

        moovSimulation = false;
        writeOffset = 0;

        final int ftypSize = makeFtyp();

        // reserve moov space in the output stream
        if (auxSize > 0) {
            int length = auxSize;
            byte[] buffer = new byte[64 * 1024]; // 64 KiB
            while (length > 0) {
                int count = Math.min(length, buffer.length);
                outWrite(buffer, count);
                length -= count;
            }
        }

        if (auxBuffer == null) {
            outSeek(ftypSize);
        }

        // tablesInfo contains row counts
        // and after returning from makeMoov() will contain those table offsets
        makeMoov(defaultMediaTime, tablesInfo, is64);

        // write tables: stts stsc sbgp
        // reset for ctts table: sampleCount sampleExtra
        for (int i = 0; i < readers.length; i++) {
            writeEntryArray(tablesInfo[i].stts, 2, sampleCount[i], defaultSampleDuration[i]);
            writeEntryArray(tablesInfo[i].stsc, tablesInfo[i].stscBEntries.length,
                    tablesInfo[i].stscBEntries);
            tablesInfo[i].stscBEntries = null;
            if (tablesInfo[i].ctts > 0) {
                sampleCount[i] = 1; // the index is not base zero
                sampleExtra[i] = -1;
            }
            if (tablesInfo[i].sbgp > 0) {
                writeEntryArray(tablesInfo[i].sbgp, 1, sampleCount[i]);
            }
        }

        if (auxBuffer == null) {
            outRestore();
        }

        outWrite(makeMdat(totalSampleSize, is64));

        int[] sampleIndex = new int[readers.length];
        int[] sizes = new int[singleSampleBuffer > 0 ? singleSampleBuffer : SAMPLES_PER_CHUNK];
        int[] sync = new int[singleSampleBuffer > 0 ? singleSampleBuffer : SAMPLES_PER_CHUNK];

        int written = readers.length;
        while (written > 0) {
            written = 0;

            for (int i = 0; i < readers.length; i++) {
                if (sampleIndex[i] < 0) {
                    continue; // track is done
                }

                long chunkOffset = writeOffset;
                int syncCount = 0;
                int limit;
                if (singleSampleBuffer > 0) {
                    limit = singleSampleBuffer;
                } else {
                    limit = sampleIndex[i] == 0 ? SAMPLES_PER_CHUNK_INIT : SAMPLES_PER_CHUNK;
                }

                int j = 0;
                for (; j < limit; j++) {
                    Mp4DashSample sample = getNextSample(i);

                    if (sample == null) {
                        if (tablesInfo[i].ctts > 0 && sampleExtra[i] >= 0) {
                            writeEntryArray(tablesInfo[i].ctts, 1, sampleCount[i],
                                    sampleExtra[i]); // flush last entries
                            outRestore();
                        }
                        sampleIndex[i] = -1;
                        break;
                    }

                    sampleIndex[i]++;

                    if (tablesInfo[i].ctts > 0) {
                        if (sample.info.sampleCompositionTimeOffset == sampleExtra[i]) {
                            sampleCount[i]++;
                        } else {
                            if (sampleExtra[i] >= 0) {
                                tablesInfo[i].ctts = writeEntryArray(tablesInfo[i].ctts, 2,
                                        sampleCount[i], sampleExtra[i]);
                                outRestore();
                            }
                            sampleCount[i] = 1;
                            sampleExtra[i] = sample.info.sampleCompositionTimeOffset;
                        }
                    }

                    if (tablesInfo[i].stss > 0 && sample.info.isKeyframe) {
                        sync[syncCount++] = sampleIndex[i];
                    }

                    if (tablesInfo[i].stsz > 0) {
                        sizes[j] = sample.data.length;
                    }

                    outWrite(sample.data, sample.data.length);
                }

                if (j > 0) {
                    written++;

                    if (tablesInfo[i].stsz > 0) {
                        tablesInfo[i].stsz = writeEntryArray(tablesInfo[i].stsz, j, sizes);
                    }

                    if (syncCount > 0) {
                        tablesInfo[i].stss = writeEntryArray(tablesInfo[i].stss, syncCount, sync);
                    }

                    if (tablesInfo[i].stco > 0) {
                        if (is64) {
                            tablesInfo[i].stco = writeEntry64(tablesInfo[i].stco, chunkOffset);
                        } else {
                            tablesInfo[i].stco = writeEntryArray(tablesInfo[i].stco, 1,
                                    (int) chunkOffset);
                        }
                    }

                    outRestore();
                }
            }
        }

        if (auxBuffer != null) {
            // dump moov
            outSeek(ftypSize);
            outStream.write(auxBuffer.array(), 0, auxBuffer.capacity());
            auxBuffer = null;
        }
    }

    private Mp4DashSample getNextSample(final int track) throws IOException {
        if (readersChunks[track] == null) {
            readersChunks[track] = readers[track].getNextChunk(false);
            if (readersChunks[track] == null) {
                return null; // EOF reached
            }
        }

        Mp4DashSample sample = readersChunks[track].getNextSample();
        if (sample == null) {
            readersChunks[track] = null;
            return getNextSample(track);
        } else {
            return sample;
        }
    }


    private int writeEntry64(final int offset, final long value) throws IOException {
        outBackup();

        auxSeek(offset);
        auxWrite(ByteBuffer.allocate(8).putLong(value).array());

        return offset + 8;
    }

    private int writeEntryArray(final int offset, final int count, final int... values)
            throws IOException {
        outBackup();

        auxSeek(offset);

        int size = count * 4;
        ByteBuffer buffer = ByteBuffer.allocate(size);

        for (int i = 0; i < count; i++) {
            buffer.putInt(values[i]);
        }

        auxWrite(buffer.array());

        return offset + size;
    }

    private void outBackup() {
        if (auxBuffer == null && lastWriteOffset < 0) {
            lastWriteOffset = writeOffset;
        }
    }

    /**
     * Restore to the previous position before the first call to writeEntry64()
     * or writeEntryArray() methods.
     */
    private void outRestore() throws IOException {
        if (lastWriteOffset > 0) {
            outSeek(lastWriteOffset);
            lastWriteOffset = -1;
        }
    }

    private void initChunkTables(final TablesInfo tables, final int firstCount,
                                 final int successiveCount) {
        // tables.stsz holds amount of samples of the track (total)
        int totalSamples = (tables.stsz - firstCount);
        float chunkAmount = totalSamples / (float) successiveCount;
        int remainChunkOffset = (int) Math.ceil(chunkAmount);
        boolean remain = remainChunkOffset != (int) chunkAmount;
        int index = 0;

        tables.stsc = 1;
        if (firstCount != successiveCount) {
            tables.stsc++;
        }
        if (remain) {
            tables.stsc++;
        }

        // stsc_table_entry = [first_chunk, samples_per_chunk, sample_description_index]
        tables.stscBEntries = new int[tables.stsc * 3];
        tables.stco = remainChunkOffset + 1; // total entrys in chunk offset box

        tables.stscBEntries[index++] = 1;
        tables.stscBEntries[index++] = firstCount;
        tables.stscBEntries[index++] = 1;

        if (firstCount != successiveCount) {
            tables.stscBEntries[index++] = 2;
            tables.stscBEntries[index++] = successiveCount;
            tables.stscBEntries[index++] = 1;
        }

        if (remain) {
            tables.stscBEntries[index++] = remainChunkOffset + 1;
            tables.stscBEntries[index++] = totalSamples % successiveCount;
            tables.stscBEntries[index] = 1;
        }
    }

    private void outWrite(final byte[] buffer) throws IOException {
        outWrite(buffer, buffer.length);
    }

    private void outWrite(final byte[] buffer, final int count) throws IOException {
        writeOffset += count;
        outStream.write(buffer, 0, count);
    }

    private void outSeek(final long offset) throws IOException {
        if (outStream.canSeek()) {
            outStream.seek(offset);
            writeOffset = offset;
        } else if (outStream.canRewind()) {
            outStream.rewind();
            writeOffset = 0;
            outSkip(offset);
        } else {
            throw new IOException("cannot seek or rewind the output stream");
        }
    }

    private void outSkip(final long amount) throws IOException {
        outStream.skip(amount);
        writeOffset += amount;
    }

    private int lengthFor(final int offset) throws IOException {
        int size = auxOffset() - offset;

        if (moovSimulation) {
            return size;
        }

        auxSeek(offset);
        auxWrite(size);
        auxSkip(size - 4);

        return size;
    }

    private int make(final int type, final int extra, final int columns, final int rows)
            throws IOException {
        final byte base = 16;
        int size = columns * rows * 4;
        int total = size + base;
        int offset = auxOffset();

        if (extra >= 0) {
            total += 4;
        }

        auxWrite(ByteBuffer.allocate(12)
                .putInt(total)
                .putInt(type)
                .putInt(0x00)// default version & flags
                .array()
        );

        if (extra >= 0) {
            offset += 4;
            auxWrite(extra);
        }

        auxWrite(rows);
        auxSkip(size);

        return offset + base;
    }

    private void auxWrite(final int value) throws IOException {
        auxWrite(ByteBuffer.allocate(4)
                .putInt(value)
                .array()
        );
    }

    private void auxWrite(final byte[] buffer) throws IOException {
        if (moovSimulation) {
            writeOffset += buffer.length;
        } else if (auxBuffer == null) {
            outWrite(buffer, buffer.length);
        } else {
            auxBuffer.put(buffer);
        }
    }

    private void auxSeek(final int offset) throws IOException {
        if (moovSimulation) {
            writeOffset = offset;
        } else if (auxBuffer == null) {
            outSeek(offset);
        } else {
            auxBuffer.position(offset);
        }
    }

    private void auxSkip(final int amount) throws IOException {
        if (moovSimulation) {
            writeOffset += amount;
        } else if (auxBuffer == null) {
            outSkip(amount);
        } else {
            auxBuffer.position(auxBuffer.position() + amount);
        }
    }

    private int auxOffset() {
        return auxBuffer == null ? (int) writeOffset : auxBuffer.position();
    }

    private int makeFtyp() throws IOException {
        int size = 16 + (compatibleBrands.size() * 4);
        if (overrideMainBrand != 0) {
            size += 4;
        }

        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putInt(size);
        buffer.putInt(0x66747970); // "ftyp"

        if (overrideMainBrand == 0) {
            buffer.putInt(0x6D703432); // mayor brand "mp42"
            buffer.putInt(512); // default minor version
        } else {
            buffer.putInt(overrideMainBrand);
            buffer.putInt(0);
            buffer.putInt(0x6D703432); // "mp42" compatible brand
        }

        for (Integer brand : compatibleBrands) {
            buffer.putInt(brand); // compatible brand
        }

        outWrite(buffer.array());

        return size;
    }

    private byte[] makeMdat(final long refSize, final boolean is64) {
        long size = refSize;
        if (is64) {
            size += 16;
        } else {
            size += 8;
        }

        ByteBuffer buffer = ByteBuffer.allocate(is64 ? 16 : 8)
                .putInt(is64 ? 0x01 : (int) size)
                .putInt(0x6D646174); // mdat

        if (is64) {
            buffer.putLong(size);
        }

        return buffer.array();
    }

    private void makeMvhd(final long longestTrack) throws IOException {
        auxWrite(new byte[]{
                0x00, 0x00, 0x00, 0x78, 0x6D, 0x76, 0x68, 0x64, 0x01, 0x00, 0x00, 0x00
        });
        auxWrite(ByteBuffer.allocate(28)
                .putLong(time)
                .putLong(time)
                .putInt(DEFAULT_TIMESCALE)
                .putLong(longestTrack)
                .array()
        );

        auxWrite(new byte[]{
                0x00, 0x01, 0x00, 0x00, 0x01, 0x00, // default volume and rate
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // reserved values
                // default matrix
                0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x40, 0x00, 0x00, 0x00
        });
        auxWrite(new byte[24]); // predefined
        auxWrite(ByteBuffer.allocate(4)
                .putInt(tracks.length + 1)
                .array()
        );
    }

    private int makeMoov(final int[] defaultMediaTime, final TablesInfo[] tablesInfo,
                         final boolean is64) throws RuntimeException, IOException {
        int start = auxOffset();

        auxWrite(new byte[]{
                0x00, 0x00, 0x00, 0x00, 0x6D, 0x6F, 0x6F, 0x76
        });

        long longestTrack = 0;
        long[] durations = new long[tracks.length];

        for (int i = 0; i < durations.length; i++) {
            durations[i] = (long) Math.ceil(
                    ((double) tracks[i].trak.tkhd.duration / tracks[i].trak.mdia.mdhdTimeScale)
                            * DEFAULT_TIMESCALE);

            if (durations[i] > longestTrack) {
                longestTrack = durations[i];
            }
        }

        makeMvhd(longestTrack);

        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i].trak.tkhd.matrix.length != 36) {
                throw
                    new RuntimeException("bad track matrix length (expected 36) in track n°" + i);
            }
            makeTrak(i, durations[i], defaultMediaTime[i], tablesInfo[i], is64);
        }

        return lengthFor(start);
    }

    private void makeTrak(final int index, final long duration, final int defaultMediaTime,
                          final TablesInfo tables, final boolean is64) throws IOException {
        int start = auxOffset();

        auxWrite(new byte[]{
                // trak header
                0x00, 0x00, 0x00, 0x00, 0x74, 0x72, 0x61, 0x6B,
                // tkhd header
                0x00, 0x00, 0x00, 0x68, 0x74, 0x6B, 0x68, 0x64, 0x01, 0x00, 0x00, 0x03
        });

        ByteBuffer buffer = ByteBuffer.allocate(48);
        buffer.putLong(time);
        buffer.putLong(time);
        buffer.putInt(index + 1);
        buffer.position(24);
        buffer.putLong(duration);
        buffer.position(40);
        buffer.putShort(tracks[index].trak.tkhd.bLayer);
        buffer.putShort(tracks[index].trak.tkhd.bAlternateGroup);
        buffer.putShort(tracks[index].trak.tkhd.bVolume);
        auxWrite(buffer.array());

        auxWrite(tracks[index].trak.tkhd.matrix);
        auxWrite(ByteBuffer.allocate(8)
                .putInt(tracks[index].trak.tkhd.bWidth)
                .putInt(tracks[index].trak.tkhd.bHeight)
                .array()
        );

        auxWrite(new byte[]{
                0x00, 0x00, 0x00, 0x24, 0x65, 0x64, 0x74, 0x73, // edts header
                0x00, 0x00, 0x00, 0x1C, 0x65, 0x6C, 0x73, 0x74,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01 // elst header
        });

        int bMediaRate;
        int mediaTime;

        if (tracks[index].trak.edstElst == null) {
            // is a audio track ¿is edst/elst optional for audio tracks?
            mediaTime = 0x00; // ffmpeg set this value as zero, instead of defaultMediaTime
            bMediaRate = 0x00010000;
        } else {
            mediaTime = (int) tracks[index].trak.edstElst.mediaTime;
            bMediaRate = tracks[index].trak.edstElst.bMediaRate;
        }

        auxWrite(ByteBuffer
                .allocate(12)
                .putInt((int) duration)
                .putInt(mediaTime)
                .putInt(bMediaRate)
                .array()
        );

        makeMdia(tracks[index].trak.mdia, tables, is64, tracks[index].kind == TrackKind.Audio);

        lengthFor(start);
    }

    private void makeMdia(final Mdia mdia, final TablesInfo tablesInfo, final boolean is64,
                          final boolean isAudio) throws IOException {
        int startMdia = auxOffset();
        auxWrite(new byte[]{0x00, 0x00, 0x00, 0x00, 0x6D, 0x64, 0x69, 0x61}); // mdia
        auxWrite(mdia.mdhd);
        auxWrite(makeHdlr(mdia.hdlr));

        int startMinf = auxOffset();
        auxWrite(new byte[]{0x00, 0x00, 0x00, 0x00, 0x6D, 0x69, 0x6E, 0x66}); // minf
        auxWrite(mdia.minf.mhd);
        auxWrite(mdia.minf.dinf);

        int startStbl = auxOffset();
        auxWrite(new byte[]{0x00, 0x00, 0x00, 0x00, 0x73, 0x74, 0x62, 0x6C}); // stbl
        auxWrite(mdia.minf.stblStsd);

        //
        // In audio tracks the following tables is not required: ssts ctts
        // And stsz can be empty if has a default sample size
        //
        if (moovSimulation) {
            make(0x73747473, -1, 2, 1); // stts
            if (tablesInfo.stss > 0) {
                make(0x73747373, -1, 1, tablesInfo.stss);
            }
            if (tablesInfo.ctts > 0) {
                make(0x63747473, -1, 2, tablesInfo.ctts);
            }
            make(0x73747363, -1, 3, tablesInfo.stsc);
            make(0x7374737A, tablesInfo.stszDefault, 1, tablesInfo.stsz);
            make(is64 ? 0x636F3634 : 0x7374636F, -1, is64 ? 2 : 1, tablesInfo.stco);
        } else {
            tablesInfo.stts = make(0x73747473, -1, 2, 1);
            if (tablesInfo.stss > 0) {
                tablesInfo.stss = make(0x73747373, -1, 1, tablesInfo.stss);
            }
            if (tablesInfo.ctts > 0) {
                tablesInfo.ctts = make(0x63747473, -1, 2, tablesInfo.ctts);
            }
            tablesInfo.stsc = make(0x73747363, -1, 3, tablesInfo.stsc);
            tablesInfo.stsz = make(0x7374737A, tablesInfo.stszDefault, 1, tablesInfo.stsz);
            tablesInfo.stco = make(is64 ? 0x636F3634 : 0x7374636F, -1, is64 ? 2 : 1,
                    tablesInfo.stco);
        }

        if (isAudio) {
            auxWrite(makeSgpd());
            tablesInfo.sbgp = makeSbgp(); // during simulation the returned offset is ignored
        }

        lengthFor(startStbl);
        lengthFor(startMinf);
        lengthFor(startMdia);
    }

    private byte[] makeHdlr(final Hdlr hdlr) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{
                0x00, 0x00, 0x00, 0x21, 0x68, 0x64, 0x6C, 0x72, // hdlr
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00// null string character
        });

        buffer.position(12);
        buffer.putInt(hdlr.type);
        buffer.putInt(hdlr.subType);
        buffer.put(hdlr.bReserved); // always is a zero array

        return buffer.array();
    }

    private int makeSbgp() throws IOException {
        int offset = auxOffset();

        auxWrite(new byte[] {
                0x00, 0x00, 0x00, 0x1C, // box size
                0x73, 0x62, 0x67, 0x70, // "sbpg"
                0x00, 0x00, 0x00, 0x00, // default box flags
                0x72, 0x6F, 0x6C, 0x6C, // group type "roll"
                0x00, 0x00, 0x00, 0x01, // group table size
                0x00, 0x00, 0x00, 0x00, // group[0] total samples (to be set later)
                0x00, 0x00, 0x00, 0x01 // group[0] description index
        });

        return offset + 0x14;
    }

    private byte[] makeSgpd() {
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

        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {
                0x00, 0x00, 0x00, 0x1A, // box size
                0x73, 0x67, 0x70, 0x64, // "sgpd"
                0x01, 0x00, 0x00, 0x00, // box flags (unknown flag sets)
                0x72, 0x6F, 0x6C, 0x6C, // ¿¿group type??
                0x00, 0x00, 0x00, 0x02, // ¿¿??
                0x00, 0x00, 0x00, 0x01, // ¿¿??
                (byte) 0xFF, (byte) 0xFF // ¿¿??
        });

        return buffer.array();
    }

    class TablesInfo {
        int stts;
        int stsc;
        int[] stscBEntries;
        int ctts;
        int stsz;
        int stszDefault;
        int stss;
        int stco;
        int sbgp;
    }
}
