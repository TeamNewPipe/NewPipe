package org.schabi.newpipe.streams;

import org.schabi.newpipe.streams.io.SharpStream;

import org.schabi.newpipe.streams.Mp4DashReader.Mp4Track;
import org.schabi.newpipe.streams.Mp4DashReader.Mp4TrackChunk;
import org.schabi.newpipe.streams.Mp4DashReader.Trak;
import org.schabi.newpipe.streams.Mp4DashReader.Trex;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.schabi.newpipe.streams.Mp4DashReader.hasFlag;

/**
 *
 * @author kapodamy
 */
public class Mp4DashWriter {

    private final static byte DIMENSIONAL_FIVE = 5;
    private final static byte DIMENSIONAL_TWO = 2;
    private final static short DEFAULT_TIMESCALE = 1000;
    private final static int BUFFER_SIZE = 8 * 1024;
    private final static byte DEFAULT_TREX_SIZE = 32;
    private final static byte[] TFRA_TTS_DEFAULT = new byte[]{0x01, 0x01, 0x01};
    private final static int EPOCH_OFFSET = 2082844800;

    private Mp4Track[] infoTracks;
    private SharpStream[] sourceTracks;

    private Mp4DashReader[] readers;
    private final long time;

    private boolean done = false;
    private boolean parsed = false;

    private long written = 0;
    private ArrayList<ArrayList<Integer>> chunkTimes;
    private ArrayList<Long> moofOffsets;
    private ArrayList<Integer> fragSizes;

    public Mp4DashWriter(SharpStream... source) {
        sourceTracks = source;
        readers = new Mp4DashReader[sourceTracks.length];
        infoTracks = new Mp4Track[sourceTracks.length];
        time = (System.currentTimeMillis() / 1000L) + EPOCH_OFFSET;
    }

    public Mp4Track[] getTracksFromSource(int sourceIndex) throws IllegalStateException {
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

    public void selectTracks(int... trackIndex) throws IOException {
        if (done) {
            throw new IOException("already done");
        }
        if (chunkTimes != null) {
            throw new IOException("tracks already selected");
        }

        try {
            chunkTimes = new ArrayList<>(readers.length);
            moofOffsets = new ArrayList<>(32);
            fragSizes = new ArrayList<>(32);

            for (int i = 0; i < readers.length; i++) {
                infoTracks[i] = readers[i].selectTrack(trackIndex[i]);

                chunkTimes.add(new ArrayList<Integer>(32));
            }

        } finally {
            parsed = true;
        }
    }

    public long getBytesWritten() {
        return written;
    }

    public void build(SharpStream out) throws IOException, RuntimeException {
        if (done) {
            throw new RuntimeException("already done");
        }
        if (!out.canWrite()) {
            throw new IOException("the provided output is not writable");
        }

        long sidxOffsets = -1;
        int maxFrags = 0;

        for (SharpStream stream : sourceTracks) {
            if (!stream.canRewind()) {
                sidxOffsets = -2;// sidx not available
            }
        }

        try {
            dump(make_ftyp(), out);
            dump(make_moov(), out);

            if (sidxOffsets == -1 && out.canRewind()) {
                //<editor-fold defaultstate="collapsed" desc="calculate sidx">
                int reserved = 0;
                for (Mp4DashReader reader : readers) {
                    int count = reader.getFragmentsCount();
                    if (count > maxFrags) {
                        maxFrags = count;
                    }
                    reserved += 12 + calcSidxBodySize(count);
                }
                if (maxFrags > 0xFFFF) {
                    sidxOffsets = -3;// TODO: to many fragments, needs a multi-sidx implementation
                } else {
                    sidxOffsets = written;
                    dump(make_free(reserved), out);
                }
                //</editor-fold>
            }
            ArrayList<Mp4TrackChunk> chunks = new ArrayList<>(readers.length);
            chunks.add(null);

            int read;
            byte[] buffer = new byte[BUFFER_SIZE];
            int sequenceNumber = 1;

            while (true) {
                chunks.clear();

                for (int i = 0; i < readers.length; i++) {
                    Mp4TrackChunk chunk = readers[i].getNextChunk();
                    if (chunk == null || chunk.moof.traf.trun.chunkSize < 1) {
                        continue;
                    }
                    chunk.moof.traf.tfhd.trackId = i + 1;
                    chunks.add(chunk);

                    if (sequenceNumber == 1) {
                        if (chunk.moof.traf.trun.entryCount > 0 && hasFlag(chunk.moof.traf.trun.bFlags, 0x0800)) {
                            chunkTimes.get(i).add(chunk.moof.traf.trun.getEntry(0).sampleCompositionTimeOffset);
                        } else {
                            chunkTimes.get(i).add(0);
                        }
                    }

                    chunkTimes.get(i).add(chunk.moof.traf.trun.chunkDuration);
                }

                if (chunks.size() < 1) {
                    break;
                }

                long offset = written;
                moofOffsets.add(offset);

                dump(make_moof(sequenceNumber++, chunks, offset), out);
                dump(make_mdat(chunks), out);

                for (Mp4TrackChunk chunk : chunks) {
                    while ((read = chunk.data.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        written += read;
                    }
                }

                fragSizes.add((int) (written - offset));
            }

            dump(make_mfra(), out);

            if (sidxOffsets > 0 && moofOffsets.size() == maxFrags) {
                long len = written;

                out.rewind();
                out.skip(sidxOffsets);

                written = sidxOffsets;
                sidxOffsets = moofOffsets.get(0);

                for (int i = 0; i < readers.length; i++) {
                    dump(make_sidx(i, sidxOffsets - written), out);
                }

                written = len;
            }
        } finally {
            done = true;
        }
    }

    public boolean isDone() {
        return done;
    }

    public boolean isParsed() {
        return parsed;
    }

    public void close() {
        done = true;
        parsed = true;

        for (SharpStream src : sourceTracks) {
            src.dispose();
        }

        sourceTracks = null;
        readers = null;
        infoTracks = null;
        moofOffsets = null;
        chunkTimes = null;
    }

    // <editor-fold defaultstate="collapsed" desc="Utils">
    private void dump(byte[][] buffer, SharpStream stream) throws IOException {
        for (byte[] buff : buffer) {
            stream.write(buff);
            written += buff.length;
        }
    }

    private byte[][] lengthFor(byte[][] buffer) {
        int length = 0;
        for (byte[] buff : buffer) {
            length += buff.length;
        }

        ByteBuffer.wrap(buffer[0]).putInt(length);

        return buffer;
    }

    private int calcSidxBodySize(int entryCount) {
        return 4 + 4 + 8 + 8 + 4 + (entryCount * 12);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Box makers">
    private byte[][] make_moof(int sequence, ArrayList<Mp4TrackChunk> chunks, long referenceOffset) {
        int pos = 2;
        TrunExtra[] extra = new TrunExtra[chunks.size()];

        byte[][] buffer = new byte[pos + (extra.length * DIMENSIONAL_FIVE)][];
        buffer[0] = new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x6D, 0x6F, 0x6F, 0x66,// info header
            0x00, 0x00, 0x00, 0x10, 0x6D, 0x66, 0x68, 0x64, 0x00, 0x00, 0x00, 0x00//mfhd
        };
        buffer[1] = new byte[4];
        ByteBuffer.wrap(buffer[1]).putInt(sequence);

        for (int i = 0; i < extra.length; i++) {
            extra[i] = new TrunExtra();
            for (byte[] buff : make_traf(chunks.get(i), extra[i], referenceOffset)) {
                buffer[pos++] = buff;
            }
        }

        lengthFor(buffer);

        int offset = 8 + ByteBuffer.wrap(buffer[0]).getInt();

        for (int i = 0; i < extra.length; i++) {
            extra[i].byteBuffer.putInt(offset);
            offset += chunks.get(i).moof.traf.trun.chunkSize;
        }

        return buffer;
    }

    private byte[][] make_traf(Mp4TrackChunk chunk, TrunExtra extra, long moofOffset) {
        byte[][] buffer = new byte[DIMENSIONAL_FIVE][];
        buffer[0] = new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x74, 0x72, 0x61, 0x66,
            0x00, 0x00, 0x00, 0x00, 0x74, 0x66, 0x68, 0x64
        };

        int flags = (chunk.moof.traf.tfhd.bFlags & 0x38) | 0x01;
        byte tfhdBodySize = 8 + 8;
        if (hasFlag(flags, 0x08)) {
            tfhdBodySize += 4;
        }
        if (hasFlag(flags, 0x10)) {
            tfhdBodySize += 4;
        }
        if (hasFlag(flags, 0x20)) {
            tfhdBodySize += 4;
        }
        buffer[1] = new byte[tfhdBodySize];
        ByteBuffer set = ByteBuffer.wrap(buffer[1]);
        set.position(4);
        set.putInt(chunk.moof.traf.tfhd.trackId);
        set.putLong(moofOffset);
        if (hasFlag(flags, 0x08)) {
            set.putInt(chunk.moof.traf.tfhd.defaultSampleDuration);
        }
        if (hasFlag(flags, 0x10)) {
            set.putInt(chunk.moof.traf.tfhd.defaultSampleSize);
        }
        if (hasFlag(flags, 0x20)) {
            set.putInt(chunk.moof.traf.tfhd.defaultSampleFlags);
        }
        set.putInt(0, flags);
        ByteBuffer.wrap(buffer[0]).putInt(8, 8 + tfhdBodySize);

        buffer[2] = new byte[]{
            0x00, 0x00, 0x00, 0x14,
            0x74, 0x66, 0x64, 0x74,
            0x01, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        };

        ByteBuffer.wrap(buffer[2]).putLong(12, chunk.moof.traf.tfdt);

        buffer[3] = new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x74, 0x72, 0x75, 0x6E,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        };

        buffer[4] = chunk.moof.traf.trun.bEntries;

        lengthFor(buffer);

        set = ByteBuffer.wrap(buffer[3]);
        set.putInt(buffer[3].length + buffer[4].length);
        set.position(8);
        set.putInt((chunk.moof.traf.trun.bFlags | 0x01) & 0x0F01);
        set.putInt(chunk.moof.traf.trun.entryCount);
        extra.byteBuffer = set;

        return buffer;
    }

    private byte[][] make_mdat(ArrayList<Mp4TrackChunk> chunks) {
        byte[][] buffer = new byte[][]{
            {
                0x00, 0x00, 0x00, 0x00, 0x6D, 0x64, 0x61, 0x74
            }
        };

        int length = 0;

        for (Mp4TrackChunk chunk : chunks) {
            length += chunk.moof.traf.trun.chunkSize;
        }

        ByteBuffer.wrap(buffer[0]).putInt(length + 8);

        return buffer;
    }

    private byte[][] make_ftyp() {
        return new byte[][]{
            {
                0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70, 0x64, 0x61, 0x73, 0x68, 0x00, 0x00, 0x00, 0x00,
                0x6D, 0x70, 0x34, 0x31, 0x69, 0x73, 0x6F, 0x6D, 0x69, 0x73, 0x6F, 0x36, 0x69, 0x73, 0x6F, 0x32
            }
        };
    }

    private byte[][] make_mvhd() {
        byte[][] buffer = new byte[DIMENSIONAL_FIVE][];

        buffer[0] = new byte[]{
            0x00, 0x00, 0x00, 0x78, 0x6D, 0x76, 0x68, 0x64, 0x01, 0x00, 0x00, 0x00
        };
        buffer[1] = new byte[28];
        buffer[2] = new byte[]{
            0x00, 0x01, 0x00, 0x00, 0x01, 0x00,// default volume and rate
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,// reserved values
            // default matrix
            0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x40, 0x00, 0x00, 0x00
        };
        buffer[3] = new byte[24];// predefined
        buffer[4] = ByteBuffer.allocate(4).putInt(infoTracks.length + 1).array();

        long longestTrack = 0;

        for (Mp4Track track : infoTracks) {
            long tmp = (long) ((track.trak.tkhd.duration / (double) track.trak.mdia_mdhd_timeScale) * DEFAULT_TIMESCALE);
            if (tmp > longestTrack) {
                longestTrack = tmp;
            }
        }

        ByteBuffer.wrap(buffer[1])
                .putLong(time)
                .putLong(time)
                .putInt(DEFAULT_TIMESCALE)
                .putLong(longestTrack);

        return buffer;
    }

    private byte[][] make_trak(int trackId, Trak trak) throws RuntimeException {
        if (trak.tkhd.matrix.length != 36) {
            throw new RuntimeException("bad track matrix length (expected 36)");
        }

        byte[][] buffer = new byte[DIMENSIONAL_FIVE][];

        buffer[0] = new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x74, 0x72, 0x61, 0x6B,// trak header
            0x00, 0x00, 0x00, 0x68, 0x74, 0x6B, 0x68, 0x64, 0x01, 0x00, 0x00, 0x03 // tkhd header
        };
        buffer[1] = new byte[48];
        buffer[2] = trak.tkhd.matrix;
        buffer[3] = new byte[8];
        buffer[4] = trak.mdia;

        ByteBuffer set = ByteBuffer.wrap(buffer[1]);
        set.putLong(time);
        set.putLong(time);
        set.putInt(trackId);
        set.position(24);
        set.putLong(trak.tkhd.duration);
        set.position(40);
        set.putShort(trak.tkhd.bLayer);
        set.putShort(trak.tkhd.bAlternateGroup);
        set.putShort(trak.tkhd.bVolume);

        ByteBuffer.wrap(buffer[3])
                .putInt(trak.tkhd.bWidth)
                .putInt(trak.tkhd.bHeight);

        return lengthFor(buffer);
    }

    private byte[][] make_moov() throws RuntimeException {
        int pos = 1;
        byte[][] buffer = new byte[2 + (DIMENSIONAL_TWO * infoTracks.length) + (DIMENSIONAL_FIVE * infoTracks.length) + DIMENSIONAL_FIVE + 1][];

        buffer[0] = new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x6D, 0x6F, 0x6F, 0x76
        };

        for (byte[] buff : make_mvhd()) {
            buffer[pos++] = buff;
        }

        for (int i = 0; i < infoTracks.length; i++) {
            for (byte[] buff : make_trak(i + 1, infoTracks[i].trak)) {
                buffer[pos++] = buff;
            }
        }

        buffer[pos] = new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x6D, 0x76, 0x65, 0x78
        };

        ByteBuffer.wrap(buffer[pos++]).putInt((infoTracks.length * DEFAULT_TREX_SIZE) + 8);

        for (int i = 0; i < infoTracks.length; i++) {
            for (byte[] buff : make_trex(i + 1, infoTracks[i].trex)) {
                buffer[pos++] = buff;
            }
        }

        // default udta
        buffer[pos] = new byte[]{
            0x00, 0x00, 0x00, 0x5C, 0x75, 0x64, 0x74, 0x61, 0x00, 0x00, 0x00, 0x54, 0x6D, 0x65, 0x74, 0x61,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x21, 0x68, 0x64, 0x6C, 0x72, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x6D, 0x64, 0x69, 0x72, 0x61, 0x70, 0x70, 0x6C, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x27, 0x69, 0x6C, 0x73, 0x74, 0x00, 0x00, 0x00,
            0x1F, (byte) 0xA9, 0x63, 0x6D, 0x74, 0x00, 0x00, 0x00, 0x17, 0x64, 0x61, 0x74, 0x61, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00, 0x00,
            0x4E, 0x65, 0x77, 0x50, 0x69, 0x70, 0x65// "NewPipe" binary string
        };

        return lengthFor(buffer);
    }

    private byte[][] make_trex(int trackId, Trex trex) {
        byte[][] buffer = new byte[][]{
            {
                0x00, 0x00, 0x00, 0x20, 0x74, 0x72, 0x65, 0x78, 0x00, 0x00, 0x00, 0x00
            },
            new byte[20]
        };

        ByteBuffer.wrap(buffer[1])
                .putInt(trackId)
                .putInt(trex.defaultSampleDescriptionIndex)
                .putInt(trex.defaultSampleDuration)
                .putInt(trex.defaultSampleSize)
                .putInt(trex.defaultSampleFlags);

        return buffer;
    }

    private byte[][] make_tfra(int trackId, List<Integer> times, List<Long> moofOffsets) {
        int entryCount = times.size() - 1;
        byte[][] buffer = new byte[DIMENSIONAL_TWO][];
        buffer[0] = new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x74, 0x66, 0x72, 0x61, 0x01, 0x00, 0x00, 0x00
        };
        buffer[1] = new byte[12 + ((16 + TFRA_TTS_DEFAULT.length) * entryCount)];

        ByteBuffer set = ByteBuffer.wrap(buffer[1]);
        set.putInt(trackId);
        set.position(8);
        set.putInt(entryCount);

        long decodeTime = 0;

        for (int i = 0; i < entryCount; i++) {
            decodeTime += times.get(i);
            set.putLong(decodeTime);
            set.putLong(moofOffsets.get(i));
            set.put(TFRA_TTS_DEFAULT);// default values: traf number/trun number/sample number
        }

        return lengthFor(buffer);
    }

    private byte[][] make_mfra() {
        byte[][] buffer = new byte[2 + (DIMENSIONAL_TWO * infoTracks.length)][];
        buffer[0] = new byte[]{
            0x00, 0x00, 0x00, 0x00, 0x6D, 0x66, 0x72, 0x61
        };
        int pos = 1;

        for (int i = 0; i < infoTracks.length; i++) {
            for (byte[] buff : make_tfra(i + 1, chunkTimes.get(i), moofOffsets)) {
                buffer[pos++] = buff;
            }
        }

        buffer[pos] = new byte[]{// mfro
            0x00, 0x00, 0x00, 0x10, 0x6D, 0x66, 0x72, 0x6F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        lengthFor(buffer);

        ByteBuffer set = ByteBuffer.wrap(buffer[pos]);
        set.position(12);
        set.put(buffer[0], 0, 4);

        return buffer;

    }

    private byte[][] make_sidx(int internalTrackId, long firstOffset) {
        List<Integer> times = chunkTimes.get(internalTrackId);
        int count = times.size() - 1;// the first item is ignored (composition time)

        if (count > 65535) {
            throw new OutOfMemoryError("to many fragments. sidx limit is 65535, found " + String.valueOf(count));
        }

        byte[][] buffer = new byte[][]{
            new byte[]{
                0x00, 0x00, 0x00, 0x00, 0x73, 0x69, 0x64, 0x78, 0x01, 0x00, 0x00, 0x00
            },
            new byte[calcSidxBodySize(count)]
        };

        lengthFor(buffer);

        ByteBuffer set = ByteBuffer.wrap(buffer[1]);
        set.putInt(internalTrackId + 1);
        set.putInt(infoTracks[internalTrackId].trak.mdia_mdhd_timeScale);
        set.putLong(0);
        set.putLong(firstOffset - ByteBuffer.wrap(buffer[0]).getInt());
        set.putInt(0xFFFF & count);// unsigned

        int i = 0;
        while (i < count) {
            set.putInt(fragSizes.get(i) & 0x7fffffff);// default reference type is 0
            set.putInt(times.get(i + 1));
            set.putInt(0x90000000);// default SAP settings
            i++;
        }

        return buffer;
    }

    private byte[][] make_free(int totalSize) {
        return lengthFor(new byte[][]{
            new byte[]{0x00, 0x00, 0x00, 0x00, 0x66, 0x72, 0x65, 0x65},
            new byte[totalSize - 8]// this is waste of RAM
        });

    }

//</editor-fold>

    class TrunExtra {

        ByteBuffer byteBuffer;
    }
}
