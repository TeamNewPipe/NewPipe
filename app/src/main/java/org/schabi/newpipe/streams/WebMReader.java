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

    //<editor-fold defaultState="collapsed" desc="constants">
    private final static int ID_EMBL = 0x0A45DFA3;
    private final static int ID_EMBLReadVersion = 0x02F7;
    private final static int ID_EMBLDocType = 0x0282;
    private final static int ID_EMBLDocTypeReadVersion = 0x0285;

    private final static int ID_Segment = 0x08538067;

    private final static int ID_Info = 0x0549A966;
    private final static int ID_TimecodeScale = 0x0AD7B1;
    private final static int ID_Duration = 0x489;

    private final static int ID_Tracks = 0x0654AE6B;
    private final static int ID_TrackEntry = 0x2E;
    private final static int ID_TrackNumber = 0x57;
    private final static int ID_TrackType = 0x03;
    private final static int ID_CodecID = 0x06;
    private final static int ID_CodecPrivate = 0x23A2;
    private final static int ID_Video = 0x60;
    private final static int ID_Audio = 0x61;
    private final static int ID_DefaultDuration = 0x3E383;
    private final static int ID_FlagLacing = 0x1C;

    private final static int ID_Cluster = 0x0F43B675;
    private final static int ID_Timecode = 0x67;
    private final static int ID_SimpleBlock = 0x23;
//</editor-fold>

    public enum TrackKind {
        Audio/*2*/, Video/*1*/, Other
    }

    private DataReader stream;
    private Segment segment;
    private WebMTrack[] tracks;
    private int selectedTrack;
    private boolean done;
    private boolean firstSegment;

    public WebMReader(SharpStream source) {
        this.stream = new DataReader(source);
    }

    public void parse() throws IOException {
        Element elem = readElement(ID_EMBL);
        if (!readEbml(elem, 1, 2)) {
            throw new UnsupportedOperationException("Unsupported EBML data (WebM)");
        }
        ensure(elem);

        elem = untilElement(null, ID_Segment);
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

    public WebMTrack selectTrack(int index) {
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

        Element elem = untilElement(null, ID_Segment);
        if (elem == null) {
            done = true;
            return null;
        }
        segment = readSegment(elem, 0, false);

        return segment;
    }

    //<editor-fold defaultstate="collapsed" desc="utils">
    private long readNumber(Element parent) throws IOException {
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

    private String readString(Element parent) throws IOException {
        return new String(readBlob(parent), StandardCharsets.UTF_8);// or use "utf-8"
    }

    private byte[] readBlob(Element parent) throws IOException {
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

    private Element readElement(int expected) throws IOException {
        Element elem = readElement();
        if (expected != 0 && elem.type != expected) {
            throw new NoSuchElementException("expected " + elementID(expected) + " found " + elementID(elem.type));
        }

        return elem;
    }

    private Element untilElement(Element ref, int... expected) throws IOException {
        Element elem;
        while (ref == null ? stream.available() : (stream.position() < (ref.offset + ref.size))) {
            elem = readElement();
            for (int type : expected) {
                if (elem.type == type) {
                    return elem;
                }
            }

            ensure(elem);
        }

        return null;
    }

    private String elementID(long type) {
        return "0x".concat(Long.toHexString(type));
    }

    private void ensure(Element ref) throws IOException {
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
//</editor-fold>

    //<editor-fold defaultState="collapsed" desc="elements readers">
    private boolean readEbml(Element ref, int minReadVersion, int minDocTypeVersion) throws IOException {
        Element elem = untilElement(ref, ID_EMBLReadVersion);
        if (elem == null) {
            return false;
        }
        if (readNumber(elem) > minReadVersion) {
            return false;
        }

        elem = untilElement(ref, ID_EMBLDocType);
        if (elem == null) {
            return false;
        }
        if (!readString(elem).equals("webm")) {
            return false;
        }
        elem = untilElement(ref, ID_EMBLDocTypeReadVersion);

        return elem != null && readNumber(elem) <= minDocTypeVersion;
    }

    private Info readInfo(Element ref) throws IOException {
        Element elem;
        Info info = new Info();

        while ((elem = untilElement(ref, ID_TimecodeScale, ID_Duration)) != null) {
            switch (elem.type) {
                case ID_TimecodeScale:
                    info.timecodeScale = readNumber(elem);
                    break;
                case ID_Duration:
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

    private Segment readSegment(Element ref, int trackLacingExpected, boolean metadataExpected) throws IOException {
        Segment obj = new Segment(ref);
        Element elem;
        while ((elem = untilElement(ref, ID_Info, ID_Tracks, ID_Cluster)) != null) {
            if (elem.type == ID_Cluster) {
                obj.currentCluster = elem;
                break;
            }
            switch (elem.type) {
                case ID_Info:
                    obj.info = readInfo(elem);
                    break;
                case ID_Tracks:
                    obj.tracks = readTracks(elem, trackLacingExpected);
                    break;
            }
            ensure(elem);
        }

        if (metadataExpected && (obj.info == null || obj.tracks == null)) {
            throw new RuntimeException("Cluster element found without Info and/or Tracks element at position " + String.valueOf(ref.offset));
        }

        return obj;
    }

    private WebMTrack[] readTracks(Element ref, int lacingExpected) throws IOException {
        ArrayList<WebMTrack> trackEntries = new ArrayList<>(2);
        Element elem_trackEntry;

        while ((elem_trackEntry = untilElement(ref, ID_TrackEntry)) != null) {
            WebMTrack entry = new WebMTrack();
            boolean drop = false;
            Element elem;
            while ((elem = untilElement(elem_trackEntry,
                    ID_TrackNumber, ID_TrackType, ID_CodecID, ID_CodecPrivate, ID_FlagLacing, ID_DefaultDuration, ID_Audio, ID_Video
            )) != null) {
                switch (elem.type) {
                    case ID_TrackNumber:
                        entry.trackNumber = readNumber(elem);
                        break;
                    case ID_TrackType:
                        entry.trackType = (int) readNumber(elem);
                        break;
                    case ID_CodecID:
                        entry.codecId = readString(elem);
                        break;
                    case ID_CodecPrivate:
                        entry.codecPrivate = readBlob(elem);
                        break;
                    case ID_Audio:
                    case ID_Video:
                        entry.bMetadata = readBlob(elem);
                        break;
                    case ID_DefaultDuration:
                        entry.defaultDuration = readNumber(elem);
                        break;
                    case ID_FlagLacing:
                        drop = readNumber(elem) != lacingExpected;
                        break;
                    default:
                        System.out.println();
                        break;
                }
                ensure(elem);
            }
            if (!drop) {
                trackEntries.add(entry);
            }
            ensure(elem_trackEntry);
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

    private SimpleBlock readSimpleBlock(Element ref) throws IOException {
        SimpleBlock obj = new SimpleBlock(ref);
        obj.dataSize = stream.position();
        obj.trackNumber = readEncodedNumber();
        obj.relativeTimeCode = stream.readShort();
        obj.flags = (byte) stream.read();
        obj.dataSize = (ref.offset + ref.size) - stream.position();

        if (obj.dataSize < 0) {
            throw new IOException(String.format("Unexpected SimpleBlock element size, missing %s bytes", -obj.dataSize));
        }
        return obj;
    }

    private Cluster readCluster(Element ref) throws IOException {
        Cluster obj = new Cluster(ref);

        Element elem = untilElement(ref, ID_Timecode);
        if (elem == null) {
            throw new NoSuchElementException("Cluster at " + String.valueOf(ref.offset) + " without Timecode element");
        }
        obj.timecode = readNumber(elem);

        return obj;
    }
//</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="class helpers">
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
        public long defaultDuration;
    }

    public class Segment {

        Segment(Element ref) {
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

            Element elem = untilElement(segment.ref, ID_Cluster);
            if (elem == null) {
                return null;
            }

            segment.currentCluster = elem;

            return readCluster(segment.currentCluster);
        }
    }

    public class SimpleBlock {

        public InputStream data;

        SimpleBlock(Element ref) {
            this.ref = ref;
        }

        public long trackNumber;
        public short relativeTimeCode;
        public byte flags;
        public long dataSize;
        private final Element ref;

        public boolean isKeyframe() {
            return (flags & 0x80) == 0x80;
        }
    }

    public class Cluster {

        Element ref;
        SimpleBlock currentSimpleBlock = null;
        public long timecode;

        Cluster(Element ref) {
            this.ref = ref;
        }

        boolean check() {
            return stream.position() >= (ref.offset + ref.size);
        }

        public SimpleBlock getNextSimpleBlock() throws IOException {
            if (check()) {
                return null;
            }
            if (currentSimpleBlock != null) {
                ensure(currentSimpleBlock.ref);
            }

            while (!check()) {
                Element elem = untilElement(ref, ID_SimpleBlock);
                if (elem == null) {
                    return null;
                }

                currentSimpleBlock = readSimpleBlock(elem);
                if (currentSimpleBlock.trackNumber == tracks[selectedTrack].trackNumber) {
                    currentSimpleBlock.data = stream.getView((int) currentSimpleBlock.dataSize);
                    return currentSimpleBlock;
                }

                ensure(elem);
            }

            return null;
        }

    }
//</editor-fold>
}
