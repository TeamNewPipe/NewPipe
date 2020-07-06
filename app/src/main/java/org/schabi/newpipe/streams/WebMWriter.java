package org.schabi.newpipe.streams;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.WebMReader.Cluster;
import org.schabi.newpipe.streams.WebMReader.Segment;
import org.schabi.newpipe.streams.WebMReader.SimpleBlock;
import org.schabi.newpipe.streams.WebMReader.WebMTrack;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * @author kapodamy
 */
public class WebMWriter implements Closeable {
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final int DEFAULT_TIMECODE_SCALE = 1000000;
    private static final int INTERV = 100; // 100ms on 1000000us timecode scale
    private static final int DEFAULT_CUES_EACH_MS = 5000; // 5000ms on 1000000us timecode scale
    private static final byte CLUSTER_HEADER_SIZE = 8;
    private static final int CUE_RESERVE_SIZE = 65535;
    private static final byte MINIMUM_EBML_VOID_SIZE = 4;

    private WebMReader.WebMTrack[] infoTracks;
    private SharpStream[] sourceTracks;

    private WebMReader[] readers;

    private boolean done = false;
    private boolean parsed = false;

    private long written = 0;

    private Segment[] readersSegment;
    private Cluster[] readersCluster;

    private ArrayList<ClusterInfo> clustersOffsetsSizes;

    private byte[] outBuffer;
    private ByteBuffer outByteBuffer;

    public WebMWriter(final SharpStream... source) {
        sourceTracks = source;
        readers = new WebMReader[sourceTracks.length];
        infoTracks = new WebMTrack[sourceTracks.length];
        outBuffer = new byte[BUFFER_SIZE];
        outByteBuffer = ByteBuffer.wrap(outBuffer);
        clustersOffsetsSizes = new ArrayList<>(256);
    }

    public WebMTrack[] getTracksFromSource(final int sourceIndex) throws IllegalStateException {
        if (done) {
            throw new IllegalStateException("already done");
        }
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
                readers[i] = new WebMReader(sourceTracks[i]);
                readers[i].parse();
            }

        } finally {
            parsed = true;
        }
    }

    public void selectTracks(final int... trackIndex) throws IOException {
        try {
            readersSegment = new Segment[readers.length];
            readersCluster = new Cluster[readers.length];

            for (int i = 0; i < readers.length; i++) {
                infoTracks[i] = readers[i].selectTrack(trackIndex[i]);
                readersSegment[i] = readers[i].getNextSegment();
            }
        } finally {
            parsed = true;
        }
    }

    public boolean isDone() {
        return done;
    }

    @Override
    public void close() {
        done = true;
        parsed = true;

        for (SharpStream src : sourceTracks) {
            src.close();
        }

        sourceTracks = null;
        readers = null;
        infoTracks = null;
        readersSegment = null;
        readersCluster = null;
        outBuffer = null;
        outByteBuffer = null;
        clustersOffsetsSizes = null;
    }

    public void build(final SharpStream out) throws IOException, RuntimeException {
        if (!out.canRewind()) {
            throw new IOException("The output stream must be allow seek");
        }

        makeEBML(out);

        long offsetSegmentSizeSet = written + 5;
        long offsetInfoDurationSet = written + 94;
        long offsetClusterSet = written + 58;
        long offsetCuesSet = written + 75;

        ArrayList<byte[]> listBuffer = new ArrayList<>(4);

        /* segment */
        listBuffer.add(new byte[]{
                0x18, 0x53, (byte) 0x80, 0x67, 0x01,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00// segment content size
        });

        long segmentOffset = written + listBuffer.get(0).length;

        /* seek head */
        listBuffer.add(new byte[]{
                0x11, 0x4d, (byte) 0x9b, 0x74, (byte) 0xbe,
                0x4d, (byte) 0xbb, (byte) 0x8b,
                0x53, (byte) 0xab, (byte) 0x84, 0x15, 0x49, (byte) 0xa9, 0x66, 0x53,
                (byte) 0xac, (byte) 0x81,
                /*info offset*/ 0x43,
                0x4d, (byte) 0xbb, (byte) 0x8b, 0x53, (byte) 0xab,
                (byte) 0x84, 0x16, 0x54, (byte) 0xae, 0x6b, 0x53, (byte) 0xac, (byte) 0x81,
                /*tracks offset*/ 0x56,
                0x4d, (byte) 0xbb, (byte) 0x8e, 0x53, (byte) 0xab, (byte) 0x84, 0x1f,
                0x43, (byte) 0xb6, 0x75, 0x53, (byte) 0xac, (byte) 0x84,
                /*cluster offset [2]*/ 0x00, 0x00, 0x00, 0x00,
                0x4d, (byte) 0xbb, (byte) 0x8e, 0x53, (byte) 0xab, (byte) 0x84, 0x1c, 0x53,
                (byte) 0xbb, 0x6b, 0x53, (byte) 0xac, (byte) 0x84,
                /*cues offset [7]*/ 0x00, 0x00, 0x00, 0x00
        });

        /* info */
        listBuffer.add(new byte[]{
                0x15, 0x49, (byte) 0xa9, 0x66, (byte) 0x8e, 0x2a, (byte) 0xd7, (byte) 0xb1
        });
        // the segment duration MUST NOT exceed 4 bytes
        listBuffer.add(encode(DEFAULT_TIMECODE_SCALE, true));
        listBuffer.add(new byte[]{0x44, (byte) 0x89, (byte) 0x84,
                0x00, 0x00, 0x00, 0x00, // info.duration
        });

        /* tracks */
        listBuffer.addAll(makeTracks());

        dump(listBuffer, out);

        // reserve space for Cues element
        long cueOffset = written;
        makeEbmlVoid(out, CUE_RESERVE_SIZE, true);

        int[] defaultSampleDuration = new int[infoTracks.length];
        long[] duration = new long[infoTracks.length];

        for (int i = 0; i < infoTracks.length; i++) {
            if (infoTracks[i].defaultDuration < 0) {
                defaultSampleDuration[i] = -1; // not available
            } else {
                defaultSampleDuration[i] = (int) Math.ceil(infoTracks[i].defaultDuration
                        / (float) DEFAULT_TIMECODE_SCALE);
            }
            duration[i] = -1;
        }

        // Select a track for the cue
        int cuesForTrackId = selectTrackForCue();
        long nextCueTime = infoTracks[cuesForTrackId].trackType == 1 ? -1 : 0;
        ArrayList<KeyFrame> keyFrames = new ArrayList<>(32);

        int firstClusterOffset = (int) written;
        long currentClusterOffset = makeCluster(out, 0, 0, true);

        long baseTimecode = 0;
        long limitTimecode = -1;
        int limitTimecodeByTrackId = cuesForTrackId;

        int blockWritten = Integer.MAX_VALUE;

        int newClusterByTrackId = -1;

        while (blockWritten > 0) {
            blockWritten = 0;
            int i = 0;
            while (i < readers.length) {
                Block bloq = getNextBlockFrom(i);
                if (bloq == null) {
                    i++;
                    continue;
                }

                if (bloq.data == null) {
                    blockWritten = 1; // fake block
                    newClusterByTrackId = i;
                    i++;
                    continue;
                }

                if (newClusterByTrackId == i) {
                    limitTimecodeByTrackId = i;
                    newClusterByTrackId = -1;
                    baseTimecode = bloq.absoluteTimecode;
                    limitTimecode = baseTimecode + INTERV;
                    currentClusterOffset = makeCluster(out, baseTimecode, currentClusterOffset,
                            true);
                }

                if (cuesForTrackId == i) {
                    if ((nextCueTime > -1 && bloq.absoluteTimecode >= nextCueTime)
                            || (nextCueTime < 0 && bloq.isKeyframe())) {
                        if (nextCueTime > -1) {
                            nextCueTime += DEFAULT_CUES_EACH_MS;
                        }
                        keyFrames.add(new KeyFrame(segmentOffset, currentClusterOffset, written,
                                bloq.absoluteTimecode));
                    }
                }

                writeBlock(out, bloq, baseTimecode);
                blockWritten++;

                if (defaultSampleDuration[i] < 0 && duration[i] >= 0) {
                    // if the sample duration in unknown,
                    // calculate using current_duration - previous_duration
                    defaultSampleDuration[i] = (int) (bloq.absoluteTimecode - duration[i]);
                }
                duration[i] = bloq.absoluteTimecode;

                if (limitTimecode < 0) {
                    limitTimecode = bloq.absoluteTimecode + INTERV;
                    continue;
                }

                if (bloq.absoluteTimecode >= limitTimecode) {
                    if (limitTimecodeByTrackId != i) {
                        limitTimecode += INTERV - (bloq.absoluteTimecode - limitTimecode);
                    }
                    i++;
                }
            }
        }

        makeCluster(out, -1, currentClusterOffset, false);

        long segmentSize = written - offsetSegmentSizeSet - 7;

        /* Segment size */
        seekTo(out, offsetSegmentSizeSet);
        outByteBuffer.putLong(0, segmentSize);
        out.write(outBuffer, 1, DataReader.LONG_SIZE - 1);

        /* Segment duration */
        long longestDuration = 0;
        for (int i = 0; i < duration.length; i++) {
            if (defaultSampleDuration[i] > 0) {
                duration[i] += defaultSampleDuration[i];
            }
            if (duration[i] > longestDuration) {
                longestDuration = duration[i];
            }
        }
        seekTo(out, offsetInfoDurationSet);
        outByteBuffer.putFloat(0, longestDuration);
        dump(outBuffer, DataReader.FLOAT_SIZE, out);

        /* first Cluster offset */
        firstClusterOffset -= segmentOffset;
        writeInt(out, offsetClusterSet, firstClusterOffset);

        seekTo(out, cueOffset);

        /* Cue */
        short cueSize = 0;
        dump(new byte[]{0x1c, 0x53, (byte) 0xbb, 0x6b, 0x20, 0x00, 0x00}, out); // header size is 7

        for (KeyFrame keyFrame : keyFrames) {
            int size = makeCuePoint(cuesForTrackId, keyFrame, outBuffer);

            if ((cueSize + size + 7 + MINIMUM_EBML_VOID_SIZE) > CUE_RESERVE_SIZE) {
                break; // no space left
            }

            cueSize += size;
            dump(outBuffer, size, out);
        }

        makeEbmlVoid(out, CUE_RESERVE_SIZE - cueSize - 7, false);

        seekTo(out, cueOffset + 5);
        outByteBuffer.putShort(0, cueSize);
        dump(outBuffer, DataReader.SHORT_SIZE, out);

        /* seek head, seek for cues element */
        writeInt(out, offsetCuesSet, (int) (cueOffset - segmentOffset));

        for (ClusterInfo cluster : clustersOffsetsSizes) {
            writeInt(out, cluster.offset, cluster.size | 0x10000000);
        }
    }

    private Block getNextBlockFrom(final int internalTrackId) throws IOException {
        if (readersSegment[internalTrackId] == null) {
            readersSegment[internalTrackId] = readers[internalTrackId].getNextSegment();
            if (readersSegment[internalTrackId] == null) {
                return null; // no more blocks in the selected track
            }
        }

        if (readersCluster[internalTrackId] == null) {
            readersCluster[internalTrackId] = readersSegment[internalTrackId].getNextCluster();
            if (readersCluster[internalTrackId] == null) {
                readersSegment[internalTrackId] = null;
                return getNextBlockFrom(internalTrackId);
            }
        }

        SimpleBlock res = readersCluster[internalTrackId].getNextSimpleBlock();
        if (res == null) {
            readersCluster[internalTrackId] = null;
            return new Block(); // fake block to indicate the end of the cluster
        }

        Block bloq = new Block();
        bloq.data = res.data;
        bloq.dataSize = res.dataSize;
        bloq.trackNumber = internalTrackId;
        bloq.flags = res.flags;
        bloq.absoluteTimecode = res.absoluteTimeCodeNs / DEFAULT_TIMECODE_SCALE;

        return bloq;
    }

    private void seekTo(final SharpStream stream, final long offset) throws IOException {
        if (stream.canSeek()) {
            stream.seek(offset);
        } else {
            if (offset > written) {
                stream.skip(offset - written);
            } else {
                stream.rewind();
                stream.skip(offset);
            }
        }

        written = offset;
    }

    private void writeInt(final SharpStream stream, final long offset, final int number)
            throws IOException {
        seekTo(stream, offset);
        outByteBuffer.putInt(0, number);
        dump(outBuffer, DataReader.INTEGER_SIZE, stream);
    }

    private void writeBlock(final SharpStream stream, final Block bloq, final long clusterTimecode)
            throws IOException {
        long relativeTimeCode = bloq.absoluteTimecode - clusterTimecode;

        if (relativeTimeCode < Short.MIN_VALUE || relativeTimeCode > Short.MAX_VALUE) {
            throw new IndexOutOfBoundsException("SimpleBlock timecode overflow.");
        }

        ArrayList<byte[]> listBuffer = new ArrayList<>(5);
        listBuffer.add(new byte[]{(byte) 0xa3});
        listBuffer.add(null); // block size
        listBuffer.add(encode(bloq.trackNumber + 1, false));
        listBuffer.add(ByteBuffer.allocate(DataReader.SHORT_SIZE).putShort((short) relativeTimeCode)
                .array());
        listBuffer.add(new byte[]{bloq.flags});

        int blockSize = bloq.dataSize;
        for (int i = 2; i < listBuffer.size(); i++) {
            blockSize += listBuffer.get(i).length;
        }
        listBuffer.set(1, encode(blockSize, false));

        dump(listBuffer, stream);

        int read;
        while ((read = bloq.data.read(outBuffer)) > 0) {
            dump(outBuffer, read, stream);
        }
    }

    private long makeCluster(final SharpStream stream, final long timecode, final long offsetStart,
                             final boolean create) throws IOException {
        ClusterInfo cluster;
        long offset = offsetStart;

        if (offset > 0) {
            // save the size of the previous cluster (maximum 256 MiB)
            cluster = clustersOffsetsSizes.get(clustersOffsetsSizes.size() - 1);
            cluster.size = (int) (written - offset - CLUSTER_HEADER_SIZE);
        }

        offset = written;

        if (create) {
            /* cluster */
            dump(new byte[]{0x1f, 0x43, (byte) 0xb6, 0x75}, stream);

            cluster = new ClusterInfo();
            cluster.offset = written;
            clustersOffsetsSizes.add(cluster);

            dump(new byte[]{
                    0x10, 0x00, 0x00, 0x00,
                    /* timestamp */
                    (byte) 0xe7
            }, stream);

            dump(encode(timecode, true), stream);
        }

        return offset;
    }

    private void makeEBML(final SharpStream stream) throws IOException {
        // default values
        dump(new byte[]{
                0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x1F, 0x42, (byte) 0x86, (byte) 0x81, 0x01,
                0x42, (byte) 0xF7, (byte) 0x81, 0x01, 0x42, (byte) 0xF2, (byte) 0x81, 0x04,
                0x42, (byte) 0xF3, (byte) 0x81, 0x08, 0x42, (byte) 0x82, (byte) 0x84, 0x77,
                0x65, 0x62, 0x6D, 0x42, (byte) 0x87, (byte) 0x81, 0x02,
                0x42, (byte) 0x85, (byte) 0x81, 0x02
        }, stream);
    }

    private ArrayList<byte[]> makeTracks() {
        ArrayList<byte[]> buffer = new ArrayList<>(1);
        buffer.add(new byte[]{0x16, 0x54, (byte) 0xae, 0x6b});
        buffer.add(null);

        for (int i = 0; i < infoTracks.length; i++) {
            buffer.addAll(makeTrackEntry(i, infoTracks[i]));
        }

        return lengthFor(buffer);
    }

    private ArrayList<byte[]> makeTrackEntry(final int internalTrackId, final WebMTrack track) {
        byte[] id = encode(internalTrackId + 1, true);
        ArrayList<byte[]> buffer = new ArrayList<>(12);

        /* track */
        buffer.add(new byte[]{(byte) 0xae});
        buffer.add(null);

        /* track number */
        buffer.add(new byte[]{(byte) 0xd7});
        buffer.add(id);

        /* track uid */
        buffer.add(new byte[]{0x73, (byte) 0xc5});
        buffer.add(id);

        /* flag lacing */
        buffer.add(new byte[]{(byte) 0x9c, (byte) 0x81, 0x00});

        /* lang */
        buffer.add(new byte[]{0x22, (byte) 0xb5, (byte) 0x9c, (byte) 0x83, 0x75, 0x6e, 0x64});

        /* codec id */
        buffer.add(new byte[]{(byte) 0x86});
        buffer.addAll(encode(track.codecId));

        /* codec delay*/
        if (track.codecDelay >= 0) {
            buffer.add(new byte[]{0x56, (byte) 0xAA});
            buffer.add(encode(track.codecDelay, true));
        }

        /* codec seek pre-roll*/
        if (track.seekPreRoll >= 0) {
            buffer.add(new byte[]{0x56, (byte) 0xBB});
            buffer.add(encode(track.seekPreRoll, true));
        }

        /* type */
        buffer.add(new byte[]{(byte) 0x83});
        buffer.add(encode(track.trackType, true));

        /* default duration */
        if (track.defaultDuration >= 0) {
            buffer.add(new byte[]{0x23, (byte) 0xe3, (byte) 0x83});
            buffer.add(encode(track.defaultDuration, true));
        }

        /* audio/video */
        if ((track.trackType == 1 || track.trackType == 2) && valid(track.bMetadata)) {
            buffer.add(new byte[]{(byte) (track.trackType == 1 ? 0xe0 : 0xe1)});
            buffer.add(encode(track.bMetadata.length, false));
            buffer.add(track.bMetadata);
        }

        /* codec private*/
        if (valid(track.codecPrivate)) {
            buffer.add(new byte[]{0x63, (byte) 0xa2});
            buffer.add(encode(track.codecPrivate.length, false));
            buffer.add(track.codecPrivate);
        }

        return lengthFor(buffer);
    }

    private int makeCuePoint(final int internalTrackId, final KeyFrame keyFrame,
                             final byte[] buffer) {
        ArrayList<byte[]> cue = new ArrayList<>(5);

        /* CuePoint */
        cue.add(new byte[]{(byte) 0xbb});
        cue.add(null);

        /* CueTime */
        cue.add(new byte[]{(byte) 0xb3});
        cue.add(encode(keyFrame.duration, true));

        /* CueTrackPosition */
        cue.addAll(makeCueTrackPosition(internalTrackId, keyFrame));

        int size = 0;
        lengthFor(cue);

        for (byte[] buff : cue) {
            System.arraycopy(buff, 0, buffer, size, buff.length);
            size += buff.length;
        }

        return size;
    }

    private ArrayList<byte[]> makeCueTrackPosition(final int internalTrackId,
                                                   final KeyFrame keyFrame) {
        ArrayList<byte[]> buffer = new ArrayList<>(8);

        /* CueTrackPositions */
        buffer.add(new byte[]{(byte) 0xb7});
        buffer.add(null);

        /* CueTrack */
        buffer.add(new byte[]{(byte) 0xf7});
        buffer.add(encode(internalTrackId + 1, true));

        /* CueClusterPosition */
        buffer.add(new byte[]{(byte) 0xf1});
        buffer.add(encode(keyFrame.clusterPosition, true));

        /* CueRelativePosition */
        if (keyFrame.relativePosition > 0) {
            buffer.add(new byte[]{(byte) 0xf0});
            buffer.add(encode(keyFrame.relativePosition, true));
        }

        return lengthFor(buffer);
    }

    private void makeEbmlVoid(final SharpStream out, final int amount, final boolean wipe)
            throws IOException {
        int size = amount;

        /* ebml void */
        outByteBuffer.putShort(0, (short) 0xec20);
        outByteBuffer.putShort(2, (short) (size - 4));

        dump(outBuffer, 4, out);

        if (wipe) {
            size -= 4;
            while (size > 0) {
                int write = Math.min(size, outBuffer.length);
                dump(outBuffer, write, out);
                size -= write;
            }
        }
    }

    private void dump(final byte[] buffer, final SharpStream stream) throws IOException {
        dump(buffer, buffer.length, stream);
    }

    private void dump(final byte[] buffer, final int count, final SharpStream stream)
            throws IOException {
        stream.write(buffer, 0, count);
        written += count;
    }

    private void dump(final ArrayList<byte[]> buffers, final SharpStream stream)
            throws IOException {
        for (byte[] buffer : buffers) {
            stream.write(buffer);
            written += buffer.length;
        }
    }

    private ArrayList<byte[]> lengthFor(final ArrayList<byte[]> buffer) {
        long size = 0;
        for (int i = 2; i < buffer.size(); i++) {
            size += buffer.get(i).length;
        }
        buffer.set(1, encode(size, false));
        return buffer;
    }

    private byte[] encode(final long number, final boolean withLength) {
        int length = -1;
        for (int i = 1; i <= 7; i++) {
            if (number < Math.pow(2, 7 * i)) {
                length = i;
                break;
            }
        }

        if (length < 1) {
            throw new ArithmeticException("Can't encode a number of bigger than 7 bytes");
        }

        if (number == (Math.pow(2, 7 * length)) - 1) {
            length++;
        }

        int offset = withLength ? 1 : 0;
        byte[] buffer = new byte[offset + length];
        long marker = (long) Math.floor((length - 1f) / 8f);

        int shift = 0;
        for (int i = length - 1; i >= 0; i--, shift += 8) {
            long b = number >>> shift;
            if (!withLength && i == marker) {
                b = b | (0x80 >>> (length - 1));
            }
            buffer[offset + i] = (byte) b;
        }

        if (withLength) {
            buffer[0] = (byte) (0x80 | length);
        }

        return buffer;
    }

    private ArrayList<byte[]> encode(final String value) {
        byte[] str;
        str = value.getBytes(StandardCharsets.UTF_8); // or use "utf-8"

        ArrayList<byte[]> buffer = new ArrayList<>(2);
        buffer.add(encode(str.length, false));
        buffer.add(str);

        return buffer;
    }

    private boolean valid(final byte[] buffer) {
        return buffer != null && buffer.length > 0;
    }

    private int selectTrackForCue() {
        int i = 0;
        int videoTracks = 0;
        int audioTracks = 0;

        for (; i < infoTracks.length; i++) {
            switch (infoTracks[i].trackType) {
                case 1:
                    videoTracks++;
                    break;
                case 2:
                    audioTracks++;
                    break;
            }
        }

        int kind;
        if (audioTracks == infoTracks.length) {
            kind = 2;
        } else if (videoTracks == infoTracks.length) {
            kind = 1;
        } else if (videoTracks > 0) {
            kind = 1;
        } else if (audioTracks > 0) {
            kind = 2;
        } else {
            return 0;
        }

        // TODO: in the adove code, find and select the shortest track for the desired kind
        for (i = 0; i < infoTracks.length; i++) {
            if (kind == infoTracks[i].trackType) {
                return i;
            }
        }

        return 0;
    }

    static class KeyFrame {
        KeyFrame(final long segment, final long cluster, final long block, final long timecode) {
            clusterPosition = cluster - segment;
            relativePosition = (int) (block - cluster - CLUSTER_HEADER_SIZE);
            duration = timecode;
        }

        final long clusterPosition;
        final int relativePosition;
        final long duration;
    }

    static class Block {
        InputStream data;
        int trackNumber;
        byte flags;
        int dataSize;
        long absoluteTimecode;

        boolean isKeyframe() {
            return (flags & 0x80) == 0x80;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format("trackNumber=%s  isKeyFrame=%S  absoluteTimecode=%s", trackNumber,
                    isKeyframe(), absoluteTimecode);
        }
    }

    static class ClusterInfo {
        long offset;
        int size;
    }
}
