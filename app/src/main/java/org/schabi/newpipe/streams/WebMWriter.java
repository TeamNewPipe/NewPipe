package org.schabi.newpipe.streams;

import org.schabi.newpipe.streams.WebMReader.Cluster;
import org.schabi.newpipe.streams.WebMReader.Segment;
import org.schabi.newpipe.streams.WebMReader.SimpleBlock;
import org.schabi.newpipe.streams.WebMReader.WebMTrack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.schabi.newpipe.streams.io.SharpStream;

/**
 *
 * @author kapodamy
 */
public class WebMWriter {

    private final static int BUFFER_SIZE = 8 * 1024;
    private final static int DEFAULT_TIMECODE_SCALE = 1000000;
    private final static int INTERV = 100;// 100ms on 1000000us timecode scale
    private final static int DEFAULT_CUES_EACH_MS = 5000;// 100ms on 1000000us timecode scale

    private WebMReader.WebMTrack[] infoTracks;
    private SharpStream[] sourceTracks;

    private WebMReader[] readers;

    private boolean done = false;
    private boolean parsed = false;

    private long written = 0;

    private Segment[] readersSegment;
    private Cluster[] readersCluter;

    private int[] predefinedDurations;

    private byte[] outBuffer;

    public WebMWriter(SharpStream... source) {
        sourceTracks = source;
        readers = new WebMReader[sourceTracks.length];
        infoTracks = new WebMTrack[sourceTracks.length];
        outBuffer = new byte[BUFFER_SIZE];
    }

    public WebMTrack[] getTracksFromSource(int sourceIndex) throws IllegalStateException {
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

    public void selectTracks(int... trackIndex) throws IOException {
        try {
            readersSegment = new Segment[readers.length];
            readersCluter = new Cluster[readers.length];
            predefinedDurations = new int[readers.length];

            for (int i = 0; i < readers.length; i++) {
                infoTracks[i] = readers[i].selectTrack(trackIndex[i]);
                predefinedDurations[i] = -1;
                readersSegment[i] = readers[i].getNextSegment();
            }
        } finally {
            parsed = true;
        }
    }

    public long getBytesWritten() {
        return written;
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
        readersSegment = null;
        readersCluter = null;
        outBuffer = null;
    }

    public void build(SharpStream out) throws IOException, RuntimeException {
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

        long baseSegmentOffset = written + listBuffer.get(0).length;

        /* seek head */
        listBuffer.add(new byte[]{
            0x11, 0x4d, (byte) 0x9b, 0x74, (byte) 0xbe,
            0x4d, (byte) 0xbb, (byte) 0x8b,
            0x53, (byte) 0xab, (byte) 0x84, 0x15, 0x49, (byte) 0xa9, 0x66, 0x53,
            (byte) 0xac, (byte) 0x81, /*info offset*/ 0x43,
            0x4d, (byte) 0xbb, (byte) 0x8b, 0x53, (byte) 0xab,
            (byte) 0x84, 0x16, 0x54, (byte) 0xae, 0x6b, 0x53, (byte) 0xac, (byte) 0x81,
            /*tracks offset*/ 0x6a,
            0x4d, (byte) 0xbb, (byte) 0x8e, 0x53, (byte) 0xab, (byte) 0x84, 0x1f,
            0x43, (byte) 0xb6, 0x75, 0x53, (byte) 0xac, (byte) 0x84, /*cluster offset [2]*/ 0x00, 0x00, 0x00, 0x00,
            0x4d, (byte) 0xbb, (byte) 0x8e, 0x53, (byte) 0xab, (byte) 0x84, 0x1c, 0x53,
            (byte) 0xbb, 0x6b, 0x53, (byte) 0xac, (byte) 0x84, /*cues offset [7]*/ 0x00, 0x00, 0x00, 0x00
        });

        /* info */
        listBuffer.add(new byte[]{
            0x15, 0x49, (byte) 0xa9, 0x66, (byte) 0xa2, 0x2a, (byte) 0xd7, (byte) 0xb1
        });
        listBuffer.add(encode(DEFAULT_TIMECODE_SCALE, true));// this value MUST NOT exceed 4 bytes
        listBuffer.add(new byte[]{0x44, (byte) 0x89, (byte) 0x84,
            0x00, 0x00, 0x00, 0x00,// info.duration
            
            /* MuxingApp */
            0x4d, (byte) 0x80, (byte) 0x87, 0x4E, 
            0x65, 0x77, 0x50, 0x69, 0x70, 0x65, // "NewPipe" binary string
            
            /* WritingApp */ 
            0x57, 0x41, (byte) 0x87, 0x4E, 
            0x65, 0x77, 0x50, 0x69, 0x70, 0x65// "NewPipe" binary string
        });

        /* tracks */
        listBuffer.addAll(makeTracks());

        for (byte[] buff : listBuffer) {
            dump(buff, out);
        }

        // reserve space for Cues element, but is a waste of space (actually is 64 KiB)
        // TODO: better Cue maker
        long cueReservedOffset = written;
        dump(new byte[]{(byte) 0xec, 0x20, (byte) 0xff, (byte) 0xfb}, out);
        int reserved = (1024 * 63) - 4;
        while (reserved > 0) {
            int write = Math.min(reserved, outBuffer.length);
            out.write(outBuffer, 0, write);
            reserved -= write;
            written += write;
        }

        // Select a track for the cue
        int cuesForTrackId = selectTrackForCue();
        long nextCueTime = infoTracks[cuesForTrackId].trackType == 1 ? -1 : 0;
        ArrayList<KeyFrame> keyFrames = new ArrayList<>(32);

        //ArrayList<Block> chunks = new ArrayList<>(readers.length);
        ArrayList<Long> clusterOffsets = new ArrayList<>(32);
        ArrayList<Integer> clusterSizes = new ArrayList<>(32);

        long duration = 0;
        int durationFromTrackId = 0;

        byte[] bTimecode = makeTimecode(0);

        int firstClusterOffset = (int) written;
        long currentClusterOffset = makeCluster(out, bTimecode, 0, clusterOffsets, clusterSizes);

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
                    blockWritten = 1;// fake block
                    newClusterByTrackId = i;
                    i++;
                    continue;
                }

                if (newClusterByTrackId == i) {
                    limitTimecodeByTrackId = i;
                    newClusterByTrackId = -1;
                    baseTimecode = bloq.absoluteTimecode;
                    limitTimecode = baseTimecode + INTERV;
                    bTimecode = makeTimecode(baseTimecode);
                    currentClusterOffset = makeCluster(out, bTimecode, currentClusterOffset, clusterOffsets, clusterSizes);
                }

                if (cuesForTrackId == i) {
                    if ((nextCueTime > -1 && bloq.absoluteTimecode >= nextCueTime) || (nextCueTime < 0 && bloq.isKeyframe())) {
                        if (nextCueTime > -1) {
                            nextCueTime += DEFAULT_CUES_EACH_MS;
                        }
                        keyFrames.add(
                                new KeyFrame(baseSegmentOffset, currentClusterOffset - 7, written, bTimecode.length, bloq.absoluteTimecode)
                        );
                    }
                }

                writeBlock(out, bloq, baseTimecode);
                blockWritten++;

                if (bloq.absoluteTimecode > duration) {
                    duration = bloq.absoluteTimecode;
                    durationFromTrackId = bloq.trackNumber;
                }

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

        makeCluster(out, null, currentClusterOffset, null, clusterSizes);

        long segmentSize = written - offsetSegmentSizeSet - 7;

        // final step write offsets and sizes
        out.rewind();
        written = 0;

        skipTo(out, offsetSegmentSizeSet);
        writeLong(out, segmentSize);

        if (predefinedDurations[durationFromTrackId] > -1) {
            duration += predefinedDurations[durationFromTrackId];// this value is full-filled in makeTrackEntry() method
        }
        skipTo(out, offsetInfoDurationSet);
        writeFloat(out, duration);

        firstClusterOffset -= baseSegmentOffset;
        skipTo(out, offsetClusterSet);
        writeInt(out, firstClusterOffset);

        skipTo(out, cueReservedOffset);

        /* Cue */
        dump(new byte[]{0x1c, 0x53, (byte) 0xbb, 0x6b, 0x20, 0x00, 0x00}, out);

        for (KeyFrame keyFrame : keyFrames) {
            for (byte[] buffer : makeCuePoint(cuesForTrackId, keyFrame)) {
                dump(buffer, out);
                if (written >= (cueReservedOffset + 65535 - 16)) {
                    throw new IOException("Too many Cues");
                }
            }
        }
        short cueSize = (short) (written - cueReservedOffset - 7);

        /*  EBML Void */
        ByteBuffer voidBuffer = ByteBuffer.allocate(4);
        voidBuffer.putShort((short) 0xec20);
        voidBuffer.putShort((short) (firstClusterOffset - written - 4));
        dump(voidBuffer.array(), out);

        out.rewind();
        written = 0;

        skipTo(out, offsetCuesSet);
        writeInt(out, (int) (cueReservedOffset - baseSegmentOffset));

        skipTo(out, cueReservedOffset + 5);
        writeShort(out, cueSize);

        for (int i = 0; i < clusterSizes.size(); i++) {
            skipTo(out, clusterOffsets.get(i));
            byte[] size = ByteBuffer.allocate(4).putInt(clusterSizes.get(i) | 0x200000).array();
            out.write(size, 1, 3);
            written += 3;
        }
    }

    private Block getNextBlockFrom(int internalTrackId) throws IOException {
        if (readersSegment[internalTrackId] == null) {
            readersSegment[internalTrackId] = readers[internalTrackId].getNextSegment();
            if (readersSegment[internalTrackId] == null) {
                return null;// no more blocks in the selected track
            }
        }

        if (readersCluter[internalTrackId] == null) {
            readersCluter[internalTrackId] = readersSegment[internalTrackId].getNextCluster();
            if (readersCluter[internalTrackId] == null) {
                readersSegment[internalTrackId] = null;
                return getNextBlockFrom(internalTrackId);
            }
        }

        SimpleBlock res = readersCluter[internalTrackId].getNextSimpleBlock();
        if (res == null) {
            readersCluter[internalTrackId] = null;
            return new Block();// fake block to indicate the end of the cluster
        }

        Block bloq = new Block();
        bloq.data = res.data;
        bloq.dataSize = (int) res.dataSize;
        bloq.trackNumber = internalTrackId;
        bloq.flags = res.flags;
        bloq.absoluteTimecode = convertTimecode(res.relativeTimeCode, readersSegment[internalTrackId].info.timecodeScale, DEFAULT_TIMECODE_SCALE);
        bloq.absoluteTimecode += readersCluter[internalTrackId].timecode;

        return bloq;
    }

    private short convertTimecode(int time, long oldTimeScale, int newTimeScale) {
        return (short) (time * (newTimeScale / oldTimeScale));
    }

    private void skipTo(SharpStream stream, long absoluteOffset) throws IOException {
        absoluteOffset -= written;
        written += absoluteOffset;
        stream.skip(absoluteOffset);
    }

    private void writeLong(SharpStream stream, long number) throws IOException {
        byte[] buffer = ByteBuffer.allocate(DataReader.LONG_SIZE).putLong(number).array();
        stream.write(buffer, 1, buffer.length - 1);
        written += buffer.length - 1;
    }

    private void writeFloat(SharpStream stream, float number) throws IOException {
        byte[] buffer = ByteBuffer.allocate(DataReader.FLOAT_SIZE).putFloat(number).array();
        dump(buffer, stream);
    }

    private void writeShort(SharpStream stream, short number) throws IOException {
        byte[] buffer = ByteBuffer.allocate(DataReader.SHORT_SIZE).putShort(number).array();
        dump(buffer, stream);
    }

    private void writeInt(SharpStream stream, int number) throws IOException {
        byte[] buffer = ByteBuffer.allocate(DataReader.INTEGER_SIZE).putInt(number).array();
        dump(buffer, stream);
    }

    private void writeBlock(SharpStream stream, Block bloq, long clusterTimecode) throws IOException {
        long relativeTimeCode = bloq.absoluteTimecode - clusterTimecode;

        if (relativeTimeCode < Short.MIN_VALUE || relativeTimeCode > Short.MAX_VALUE) {
            throw new IndexOutOfBoundsException("SimpleBlock timecode overflow.");
        }

        ArrayList<byte[]> listBuffer = new ArrayList<>(5);
        listBuffer.add(new byte[]{(byte) 0xa3});
        listBuffer.add(null);// block size
        listBuffer.add(encode(bloq.trackNumber + 1, false));
        listBuffer.add(ByteBuffer.allocate(DataReader.SHORT_SIZE).putShort((short) relativeTimeCode).array());
        listBuffer.add(new byte[]{bloq.flags});

        int blockSize = bloq.dataSize;
        for (int i = 2; i < listBuffer.size(); i++) {
            blockSize += listBuffer.get(i).length;
        }
        listBuffer.set(1, encode(blockSize, false));

        for (byte[] buff : listBuffer) {
            dump(buff, stream);
        }

        int read;
        while ((read = bloq.data.read(outBuffer)) > 0) {
            stream.write(outBuffer, 0, read);
            written += read;
        }
    }

    private byte[] makeTimecode(long timecode) {
        ByteBuffer buffer = ByteBuffer.allocate(9);
        buffer.put((byte) 0xe7);
        buffer.put(encode(timecode, true));

        byte[] res = new byte[buffer.position()];
        System.arraycopy(buffer.array(), 0, res, 0, res.length);

        return res;
    }

    private long makeCluster(SharpStream stream, byte[] bTimecode, long startOffset, ArrayList<Long> clusterOffsets, ArrayList<Integer> clusterSizes) throws IOException {
        if (startOffset > 0) {
            clusterSizes.add((int) (written - startOffset));// size for last offset
        }

        if (clusterOffsets != null) {
            /* cluster */
            dump(new byte[]{0x1f, 0x43, (byte) 0xb6, 0x75}, stream);
            clusterOffsets.add(written);// warning: max cluster size is 256 MiB
            dump(new byte[]{0x20, 0x00, 0x00}, stream);

            startOffset = written;// size for the this cluster

            dump(bTimecode, stream);

            return startOffset;
        }

        return -1;
    }

    private void makeEBML(SharpStream stream) throws IOException {
        // deafult values
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

    private ArrayList<byte[]> makeTrackEntry(int internalTrackId, WebMTrack track) {
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

        /* type */
        buffer.add(new byte[]{(byte) 0x83});
        buffer.add(encode(track.trackType, true));

        /* default duration */
        if (track.defaultDuration != 0) {
            predefinedDurations[internalTrackId] = (int) Math.ceil(track.defaultDuration / (float) DEFAULT_TIMECODE_SCALE);
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

    private ArrayList<byte[]> makeCuePoint(int internalTrackId, KeyFrame keyFrame) {
        ArrayList<byte[]> buffer = new ArrayList<>(5);

        /* CuePoint */
        buffer.add(new byte[]{(byte) 0xbb});
        buffer.add(null);

        /* CueTime */
        buffer.add(new byte[]{(byte) 0xb3});
        buffer.add(encode(keyFrame.atTimecode, true));

        /* CueTrackPosition */
        buffer.addAll(makeCueTrackPosition(internalTrackId, keyFrame));

        return lengthFor(buffer);
    }

    private ArrayList<byte[]> makeCueTrackPosition(int internalTrackId, KeyFrame keyFrame) {
        ArrayList<byte[]> buffer = new ArrayList<>(8);

        /* CueTrackPositions */
        buffer.add(new byte[]{(byte) 0xb7});
        buffer.add(null);

        /* CueTrack */
        buffer.add(new byte[]{(byte) 0xf7});
        buffer.add(encode(internalTrackId + 1, true));

        /* CueClusterPosition */
        buffer.add(new byte[]{(byte) 0xf1});
        buffer.add(encode(keyFrame.atCluster, true));

        /* CueRelativePosition */
        if (keyFrame.atBlock > 0) {
            buffer.add(new byte[]{(byte) 0xf0});
            buffer.add(encode(keyFrame.atBlock, true));
        }

        return lengthFor(buffer);
    }

    private void dump(byte[] buffer, SharpStream stream) throws IOException {
        stream.write(buffer);
        written += buffer.length;
    }

    private ArrayList<byte[]> lengthFor(ArrayList<byte[]> buffer) {
        long size = 0;
        for (int i = 2; i < buffer.size(); i++) {
            size += buffer.get(i).length;
        }
        buffer.set(1, encode(size, false));
        return buffer;
    }

    private byte[] encode(long number, boolean withLength) {
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
        long marker = (long) Math.floor((length - 1) / 8);

        for (int i = length - 1, mul = 1; i >= 0; i--, mul *= 0x100) {
            long b = (long) Math.floor(number / mul);
            if (!withLength && i == marker) {
                b = b | (0x80 >> (length - 1));
            }
            buffer[offset + i] = (byte) b;
        }

        if (withLength) {
            buffer[0] = (byte) (0x80 | length);
        }

        return buffer;
    }

    private ArrayList<byte[]> encode(String value) {
        byte[] str;
        try {
            str = value.getBytes("utf-8");
        } catch (UnsupportedEncodingException err) {
            str = value.getBytes();
        }

        ArrayList<byte[]> buffer = new ArrayList<>(2);
        buffer.add(encode(str.length, false));
        buffer.add(str);

        return buffer;
    }

    private boolean valid(byte[] buffer) {
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

    class KeyFrame {

        KeyFrame(long segment, long cluster, long block, int bTimecodeLength, long timecode) {
            atCluster = cluster - segment;
            if ((block - bTimecodeLength) > cluster) {
                atBlock = (int) (block - cluster);
            }
            atTimecode = timecode;
        }

        long atCluster;
        int atBlock;
        long atTimecode;
    }

    class Block {

        InputStream data;
        int trackNumber;
        byte flags;
        int dataSize;
        long absoluteTimecode;

        boolean isKeyframe() {
            return (flags & 0x80) == 0x80;
        }

        @Override
        public String toString() {
            return String.format("trackNumber=%s  isKeyFrame=%S  absoluteTimecode=%s", trackNumber, (flags & 0x80) == 0x80, absoluteTimecode);
        }
    }
}
