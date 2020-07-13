package org.schabi.newpipe.streams;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 *
 * @author kapodamy
 */
public class WebMReader {
    private static final int ID_EMBL = 0x0A45DFA3;
    private static final int ID_EMBL_READ_VERSION = 0x02F7;
    private static final int ID_EMBL_DOC_TYPE = 0x0282;
    private static final int ID_EMBL_DOC_TYPE_READ_VERSION = 0x0285;

    private static final int ID_SEGMENT = 0x08538067;

    private static final int ID_INFO = 0x0549A966;
    private static final int ID_TIMECODE_SCALE = 0x0AD7B1;
    private static final int ID_DURATION = 0x489;

    private static final int ID_TRACKS = 0x0654AE6B;
    private static final int ID_TRACK_ENTRY = 0x2E;
    private static final int ID_TRACK_NUMBER = 0x57;
    private static final int ID_TRACK_TYPE = 0x03;
    private static final int ID_CODEC_ID = 0x06;
    private static final int ID_CODEC_PRIVATE = 0x23A2;
    private static final int ID_VIDEO = 0x60;
    private static final int ID_AUDIO = 0x61;
    private static final int ID_DEFAULT_DURATION = 0x3E383;
    private static final int ID_FLAG_LACING = 0x1C;
    private static final int ID_CODEC_DELAY = 0x16AA;
    private static final int ID_SEEK_PRE_ROLL = 0x16BB;

    private static final int ID_CLUSTER = 0x0F43B675;
    private static final int ID_TIMECODE = 0x67;
    private static final int ID_SIMPLE_BLOCK = 0x23;
    private static final int ID_BLOCK = 0x21;
    private static final int ID_GROUP_BLOCK = 0x20;


    public enum TrackKind {
        Audio/*2*/, Video/*1*/, Other
    }

    private DataReader stream;
    private Segment segment;
    private WebMTrack[] tracks;
    private int selectedTrack;
    private boolean done;
    private boolean firstSegment;

    public WebMReader(final SharpStream source) {
        this.stream = new DataReader(source);
    }

    public void parse() throws IOException {
        Element elem = readElement(ID_EMBL);
        if (!readEbml(elem, 1, 2)) {
            throw new UnsupportedOperationException("Unsupported EBML data (WebM)");
        }
        ensure(elem);

        elem = untilElement(null, ID_SEGMENT);
        if (elem == null) {
            throw new IOException("Fragment element not found");
        }
        segment = readSegment(elem, 0, true);
        tracks = segment.tracks;
        selectedTrack = -1;
        done = false;
        firstSegment = true;
    }

    public WebMTrack[] getAvailableTracks() {
        return tracks;
    }

    public WebMTrack selectTrack(final int index) {
        selectedTrack = index;
        return tracks[index];
    }

    public Segment getNextSegment() throws IOException {
        if (done) {
            return null;
        }

        if (firstSegment && segment != null) {
            firstSegment = false;
            return segment;
        }

        ensure(segment.ref);
        // WARNING: track cannot be the same or have different index in new segments
        Element elem = untilElement(null, ID_SEGMENT);
        if (elem == null) {
            done = true;
            return null;
        }
        segment = readSegment(elem, 0, false);

        return segment;
    }

    private long readNumber(final Element parent) throws IOException {
        int length = (int) parent.contentSize;
        long value = 0;
        while (length-- > 0) {
            int read = stream.read();
            if (read == -1) {
                throw new EOFException();
            }
            value = (value << 8) | read;
        }
        return value;
    }

    private String readString(final Element parent) throws IOException {
        return new String(readBlob(parent), StandardCharsets.UTF_8); // or use "utf-8"
    }

    private byte[] readBlob(final Element parent) throws IOException {
        long length = parent.contentSize;
        byte[] buffer = new byte[(int) length];
        int read = stream.read(buffer);
        if (read < length) {
            throw new EOFException();
        }
        return buffer;
    }

    private long readEncodedNumber() throws IOException {
        int value = stream.read();

        if (value > 0) {
            byte size = 1;
            int mask = 0x80;

            while (size < 9) {
                if ((value & mask) == mask) {
                    mask = 0xFF;
                    mask >>= size;

                    long number = value & mask;

                    for (int i = 1; i < size; i++) {
                        value = stream.read();
                        number <<= 8;
                        number |= value;
                    }

                    return number;
                }

                mask >>= 1;
                size++;
            }
        }

        throw new IOException("Invalid encoded length");
    }

    private Element readElement() throws IOException {
        Element elem = new Element();
        elem.offset = stream.position();
        elem.type = (int) readEncodedNumber();
        elem.contentSize = readEncodedNumber();
        elem.size = elem.contentSize + stream.position() - elem.offset;

        return elem;
    }

    private Element readElement(final int expected) throws IOException {
        Element elem = readElement();
        if (expected != 0 && elem.type != expected) {
            throw new NoSuchElementException("expected " + elementID(expected)
                    + " found " + elementID(elem.type));
        }

        return elem;
    }

    private Element untilElement(final Element ref, final int... expected) throws IOException {
        Element elem;
        while (ref == null ? stream.available() : (stream.position() < (ref.offset + ref.size))) {
            elem = readElement();
            if (expected.length < 1) {
                return elem;
            }
            for (int type : expected) {
                if (elem.type == type) {
                    return elem;
                }
            }

            ensure(elem);
        }

        return null;
    }

    private String elementID(final long type) {
        return "0x".concat(Long.toHexString(type));
    }

    private void ensure(final Element ref) throws IOException {
        long skip = (ref.offset + ref.size) - stream.position();

        if (skip == 0) {
            return;
        } else if (skip < 0) {
            throw new EOFException(String.format(
                    "parser go beyond limits of the Element. type=%s offset=%s size=%s position=%s",
                    elementID(ref.type), ref.offset, ref.size, stream.position()
            ));
        }

        stream.skipBytes(skip);
    }

    private boolean readEbml(final Element ref, final int minReadVersion,
                             final int minDocTypeVersion) throws IOException {
        Element elem = untilElement(ref, ID_EMBL_READ_VERSION);
        if (elem == null) {
            return false;
        }
        if (readNumber(elem) > minReadVersion) {
            return false;
        }

        elem = untilElement(ref, ID_EMBL_DOC_TYPE);
        if (elem == null) {
            return false;
        }
        if (!readString(elem).equals("webm")) {
            return false;
        }
        elem = untilElement(ref, ID_EMBL_DOC_TYPE_READ_VERSION);

        return elem != null && readNumber(elem) <= minDocTypeVersion;
    }

    private Info readInfo(final Element ref) throws IOException {
        Element elem;
        Info info = new Info();

        while ((elem = untilElement(ref, ID_TIMECODE_SCALE, ID_DURATION)) != null) {
            switch (elem.type) {
                case ID_TIMECODE_SCALE:
                    info.timecodeScale = readNumber(elem);
                    break;
                case ID_DURATION:
                    info.duration = readNumber(elem);
                    break;
            }
            ensure(elem);
        }

        if (info.timecodeScale == 0) {
            throw new NoSuchElementException("Element Timecode not found");
        }

        return info;
    }

    private Segment readSegment(final Element ref, final int trackLacingExpected,
                                final boolean metadataExpected) throws IOException {
        Segment obj = new Segment(ref);
        Element elem;
        while ((elem = untilElement(ref, ID_INFO, ID_TRACKS, ID_CLUSTER)) != null) {
            if (elem.type == ID_CLUSTER) {
                obj.currentCluster = elem;
                break;
            }
            switch (elem.type) {
                case ID_INFO:
                    obj.info = readInfo(elem);
                    break;
                case ID_TRACKS:
                    obj.tracks = readTracks(elem, trackLacingExpected);
                    break;
            }
            ensure(elem);
        }

        if (metadataExpected && (obj.info == null || obj.tracks == null)) {
            throw new RuntimeException(
                    "Cluster element found without Info and/or Tracks element at position "
                            + String.valueOf(ref.offset));
        }

        return obj;
    }

    private WebMTrack[] readTracks(final Element ref, final int lacingExpected) throws IOException {
        ArrayList<WebMTrack> trackEntries = new ArrayList<>(2);
        Element elemTrackEntry;

        while ((elemTrackEntry = untilElement(ref, ID_TRACK_ENTRY)) != null) {
            WebMTrack entry = new WebMTrack();
            boolean drop = false;
            Element elem;
            while ((elem = untilElement(elemTrackEntry)) != null) {
                switch (elem.type) {
                    case ID_TRACK_NUMBER:
                        entry.trackNumber = readNumber(elem);
                        break;
                    case ID_TRACK_TYPE:
                        entry.trackType = (int) readNumber(elem);
                        break;
                    case ID_CODEC_ID:
                        entry.codecId = readString(elem);
                        break;
                    case ID_CODEC_PRIVATE:
                        entry.codecPrivate = readBlob(elem);
                        break;
                    case ID_AUDIO:
                    case ID_VIDEO:
                        entry.bMetadata = readBlob(elem);
                        break;
                    case ID_DEFAULT_DURATION:
                        entry.defaultDuration = readNumber(elem);
                        break;
                    case ID_FLAG_LACING:
                        drop = readNumber(elem) != lacingExpected;
                        break;
                    case ID_CODEC_DELAY:
                        entry.codecDelay = readNumber(elem);
                        break;
                    case ID_SEEK_PRE_ROLL:
                        entry.seekPreRoll = readNumber(elem);
                        break;
                    default:
                        break;
                }
                ensure(elem);
            }
            if (!drop) {
                trackEntries.add(entry);
            }
            ensure(elemTrackEntry);
        }

        WebMTrack[] entries = new WebMTrack[trackEntries.size()];
        trackEntries.toArray(entries);

        for (WebMTrack entry : entries) {
            switch (entry.trackType) {
                case 1:
                    entry.kind = TrackKind.Video;
                    break;
                case 2:
                    entry.kind = TrackKind.Audio;
                    break;
                default:
                    entry.kind = TrackKind.Other;
                    break;
            }
        }

        return entries;
    }

    private SimpleBlock readSimpleBlock(final Element ref) throws IOException {
        SimpleBlock obj = new SimpleBlock(ref);
        obj.trackNumber = readEncodedNumber();
        obj.relativeTimeCode = stream.readShort();
        obj.flags = (byte) stream.read();
        obj.dataSize = (int) ((ref.offset + ref.size) - stream.position());
        obj.createdFromBlock = ref.type == ID_BLOCK;

        // NOTE: lacing is not implemented, and will be mixed with the stream data
        if (obj.dataSize < 0) {
            throw new IOException(String.format(
                    "Unexpected SimpleBlock element size, missing %s bytes", -obj.dataSize));
        }
        return obj;
    }

    private Cluster readCluster(final Element ref) throws IOException {
        Cluster obj = new Cluster(ref);

        Element elem = untilElement(ref, ID_TIMECODE);
        if (elem == null) {
            throw new NoSuchElementException("Cluster at " + String.valueOf(ref.offset)
                    + " without Timecode element");
        }
        obj.timecode = readNumber(elem);

        return obj;
    }

    class Element {
        int type;
        long offset;
        long contentSize;
        long size;
    }

    public class Info {
        public long timecodeScale;
        public long duration;
    }

    public class WebMTrack {
        public long trackNumber;
        protected int trackType;
        public String codecId;
        public byte[] codecPrivate;
        public byte[] bMetadata;
        public TrackKind kind;
        public long defaultDuration = -1;
        public long codecDelay = -1;
        public long seekPreRoll = -1;
    }

    public class Segment {
        Segment(final Element ref) {
            this.ref = ref;
            this.firstClusterInSegment = true;
        }

        public Info info;
        WebMTrack[] tracks;
        private Element currentCluster;
        private final Element ref;
        boolean firstClusterInSegment;

        public Cluster getNextCluster() throws IOException {
            if (done) {
                return null;
            }
            if (firstClusterInSegment && segment.currentCluster != null) {
                firstClusterInSegment = false;
                return readCluster(segment.currentCluster);
            }
            ensure(segment.currentCluster);

            Element elem = untilElement(segment.ref, ID_CLUSTER);
            if (elem == null) {
                return null;
            }

            segment.currentCluster = elem;

            return readCluster(segment.currentCluster);
        }
    }

    public class SimpleBlock {
        public InputStream data;
        public boolean createdFromBlock;

        SimpleBlock(final Element ref) {
            this.ref = ref;
        }

        public long trackNumber;
        public short relativeTimeCode;
        public long absoluteTimeCodeNs;
        public byte flags;
        public int dataSize;
        private final Element ref;

        public boolean isKeyframe() {
            return (flags & 0x80) == 0x80;
        }
    }

    public class Cluster {
        Element ref;
        SimpleBlock currentSimpleBlock = null;
        Element currentBlockGroup = null;
        public long timecode;

        Cluster(final Element ref) {
            this.ref = ref;
        }

        boolean insideClusterBounds() {
            return stream.position() >= (ref.offset + ref.size);
        }

        public SimpleBlock getNextSimpleBlock() throws IOException {
            if (insideClusterBounds()) {
                return null;
            }

            if (currentBlockGroup != null) {
                ensure(currentBlockGroup);
                currentBlockGroup = null;
                currentSimpleBlock = null;
            } else if (currentSimpleBlock != null) {
                ensure(currentSimpleBlock.ref);
            }

            while (!insideClusterBounds()) {
                Element elem = untilElement(ref, ID_SIMPLE_BLOCK, ID_GROUP_BLOCK);
                if (elem == null) {
                    return null;
                }

                if (elem.type == ID_GROUP_BLOCK) {
                    currentBlockGroup = elem;
                    elem = untilElement(currentBlockGroup, ID_BLOCK);

                    if (elem == null) {
                        ensure(currentBlockGroup);
                        currentBlockGroup = null;
                        continue;
                    }
                }

                currentSimpleBlock = readSimpleBlock(elem);
                if (currentSimpleBlock.trackNumber == tracks[selectedTrack].trackNumber) {
                    currentSimpleBlock.data = stream.getView((int) currentSimpleBlock.dataSize);

                    // calculate the timestamp in nanoseconds
                    currentSimpleBlock.absoluteTimeCodeNs = currentSimpleBlock.relativeTimeCode
                            + this.timecode;
                    currentSimpleBlock.absoluteTimeCodeNs *= segment.info.timecodeScale;

                    return currentSimpleBlock;
                }

                ensure(elem);
            }
            return null;
        }
    }
}
