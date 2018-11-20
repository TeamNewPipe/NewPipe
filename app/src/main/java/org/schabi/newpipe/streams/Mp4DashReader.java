package org.schabi.newpipe.streams;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import org.schabi.newpipe.streams.io.SharpStream;

/**
 * @author kapodamy
 */
public class Mp4DashReader {

    // <editor-fold defaultState="collapsed" desc="Constants">
    private static final int ATOM_MOOF = 0x6D6F6F66;
    private static final int ATOM_MFHD = 0x6D666864;
    private static final int ATOM_TRAF = 0x74726166;
    private static final int ATOM_TFHD = 0x74666864;
    private static final int ATOM_TFDT = 0x74666474;
    private static final int ATOM_TRUN = 0x7472756E;
    private static final int ATOM_MDIA = 0x6D646961;
    private static final int ATOM_FTYP = 0x66747970;
    private static final int ATOM_SIDX = 0x73696478;
    private static final int ATOM_MOOV = 0x6D6F6F76;
    private static final int ATOM_MDAT = 0x6D646174;
    private static final int ATOM_MVHD = 0x6D766864;
    private static final int ATOM_TRAK = 0x7472616B;
    private static final int ATOM_MVEX = 0x6D766578;
    private static final int ATOM_TREX = 0x74726578;
    private static final int ATOM_TKHD = 0x746B6864;
    private static final int ATOM_MFRA = 0x6D667261;
    private static final int ATOM_TFRA = 0x74667261;
    private static final int ATOM_MDHD = 0x6D646864;
    private static final int BRAND_DASH = 0x64617368;
    // </editor-fold>

    private final DataReader stream;

    private Mp4Track[] tracks = null;

    private Box box;
    private Moof moof;

    private boolean chunkZero = false;

    private int selectedTrack = -1;

    public enum TrackKind {
        Audio, Video, Other
    }

    public Mp4DashReader(SharpStream source) {
        this.stream = new DataReader(source);
    }

    public void parse() throws IOException, NoSuchElementException {
        if (selectedTrack > -1) {
            return;
        }

        box = readBox(ATOM_FTYP);
        if (parse_ftyp() != BRAND_DASH) {
            throw new NoSuchElementException("Main Brand is not dash");
        }

        Moov moov = null;
        int i;

        while (box.type != ATOM_MOOF) {
            ensure(box);
            box = readBox();

            switch (box.type) {
                case ATOM_MOOV:
                    moov = parse_moov(box);
                    break;
                case ATOM_SIDX:
                    break;
                case ATOM_MFRA:
                    break;
                case ATOM_MDAT:
                    throw new IOException("Expected moof, found mdat");
            }
        }

        if (moov == null) {
            throw new IOException("The provided Mp4 doesn't have the 'moov' box");
        }

        tracks = new Mp4Track[moov.trak.length];

        for (i = 0; i < tracks.length; i++) {
            tracks[i] = new Mp4Track();
            tracks[i].trak = moov.trak[i];

            if (moov.mvex_trex != null) {
                for (Trex mvex_trex : moov.mvex_trex) {
                    if (tracks[i].trak.tkhd.trackId == mvex_trex.trackId) {
                        tracks[i].trex = mvex_trex;
                    }
                }
            }

            if (moov.trak[i].tkhd.bHeight == 0 && moov.trak[i].tkhd.bWidth == 0) {
                tracks[i].kind = moov.trak[i].tkhd.bVolume == 0 ? TrackKind.Other : TrackKind.Audio;
            } else {
                tracks[i].kind = TrackKind.Video;
            }
        }
    }

    public Mp4Track selectTrack(int index) {
        selectedTrack = index;
        return tracks[index];
    }

    /**
     * Count all fragments present. This operation requires a seekable stream
     *
     * @return list with a basic info
     * @throws IOException if the source stream is not seekeable
     */
    public int getFragmentsCount() throws IOException {
        if (selectedTrack < 0) {
            throw new IllegalStateException("track no selected");
        }
        if (!stream.canRewind()) {
            throw new IOException("The provided stream doesn't allow seek");
        }

        Box tmp;
        int count = 0;
        long orig_offset = stream.position();

        if (box.type == ATOM_MOOF) {
            tmp = box;
        } else {
            ensure(box);
            tmp = readBox();
        }

        do {
            if (tmp.type == ATOM_MOOF) {
                ensure(readBox(ATOM_MFHD));
                Box traf;
                while ((traf = untilBox(tmp, ATOM_TRAF)) != null) {
                    Box tfhd = readBox(ATOM_TFHD);
                    if (parse_tfhd(tracks[selectedTrack].trak.tkhd.trackId) != null) {
                        count++;
                        break;
                    }
                    ensure(tfhd);
                    ensure(traf);
                }
            }
            ensure(tmp);
        } while (stream.available() && (tmp = readBox()) != null);

        stream.rewind();
        stream.skipBytes((int) orig_offset);

        return count;
    }

    public Mp4Track[] getAvailableTracks() {
        return tracks;
    }

    public Mp4TrackChunk getNextChunk() throws IOException {
        Mp4Track track = tracks[selectedTrack];

        while (stream.available()) {

            if (chunkZero) {
                ensure(box);
                if (!stream.available()) {
                    break;
                }
                box = readBox();
            } else {
                chunkZero = true;
            }

            switch (box.type) {
                case ATOM_MOOF:
                    if (moof != null) {
                        throw new IOException("moof found without mdat");
                    }

                    moof = parse_moof(box, track.trak.tkhd.trackId);

                    if (moof.traf != null) {

                        if (hasFlag(moof.traf.trun.bFlags, 0x0001)) {
                            moof.traf.trun.dataOffset -= box.size + 8;
                            if (moof.traf.trun.dataOffset < 0) {
                                throw new IOException("trun box has wrong data offset, points outside of concurrent mdat box");
                            }
                        }

                        if (moof.traf.trun.chunkSize < 1) {
                            if (hasFlag(moof.traf.tfhd.bFlags, 0x10)) {
                                moof.traf.trun.chunkSize = moof.traf.tfhd.defaultSampleSize * moof.traf.trun.entryCount;
                            } else {
                                moof.traf.trun.chunkSize = box.size - 8;
                            }
                        }
                        if (!hasFlag(moof.traf.trun.bFlags, 0x900) && moof.traf.trun.chunkDuration == 0) {
                            if (hasFlag(moof.traf.tfhd.bFlags, 0x20)) {
                                moof.traf.trun.chunkDuration = moof.traf.tfhd.defaultSampleDuration * moof.traf.trun.entryCount;
                            }
                        }
                    }
                    break;
                case ATOM_MDAT:
                    if (moof == null) {
                        throw new IOException("mdat found without moof");
                    }

                    if (moof.traf == null) {
                        moof = null;
                        continue;// find another chunk
                    }

                    Mp4TrackChunk chunk = new Mp4TrackChunk();
                    chunk.moof = moof;
                    chunk.data = new TrackDataChunk(stream, moof.traf.trun.chunkSize);
                    moof = null;

                    stream.skipBytes(chunk.moof.traf.trun.dataOffset);
                    return chunk;
                default:
            }
        }

        return null;
    }

    // <editor-fold defaultState="collapsed" desc="Utils">
    private long readUint() throws IOException {
        return stream.readInt() & 0xffffffffL;
    }

    public static boolean hasFlag(int flags, int mask) {
        return (flags & mask) == mask;
    }

    private String boxName(Box ref) {
        return boxName(ref.type);
    }

    private String boxName(int type) {
        try {
            return new String(ByteBuffer.allocate(4).putInt(type).array(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "0x" + Integer.toHexString(type);
        }
    }

    private Box readBox() throws IOException {
        Box b = new Box();
        b.offset = stream.position();
        b.size = stream.readInt();
        b.type = stream.readInt();

        return b;
    }

    private Box readBox(int expected) throws IOException {
        Box b = readBox();
        if (b.type != expected) {
            throw new NoSuchElementException("expected " + boxName(expected) + " found " + boxName(b));
        }
        return b;
    }

    private void ensure(Box ref) throws IOException {
        long skip = ref.offset + ref.size - stream.position();

        if (skip == 0) {
            return;
        } else if (skip < 0) {
            throw new EOFException(String.format(
                    "parser go beyond limits of the box. type=%s offset=%s size=%s position=%s",
                    boxName(ref), ref.offset, ref.size, stream.position()
            ));
        }

        stream.skipBytes((int) skip);
    }

    private Box untilBox(Box ref, int... expected) throws IOException {
        Box b;
        while (stream.position() < (ref.offset + ref.size)) {
            b = readBox();
            for (int type : expected) {
                if (b.type == type) {
                    return b;
                }
            }
            ensure(b);
        }

        return null;
    }

    // </editor-fold>

    // <editor-fold defaultState="collapsed" desc="Box readers">

    private Moof parse_moof(Box ref, int trackId) throws IOException {
        Moof obj = new Moof();

        Box b = readBox(ATOM_MFHD);
        obj.mfhd_SequenceNumber = parse_mfhd();
        ensure(b);

        while ((b = untilBox(ref, ATOM_TRAF)) != null) {
            obj.traf = parse_traf(b, trackId);
            ensure(b);

            if (obj.traf != null) {
                return obj;
            }
        }
        
        return obj;
    }

    private int parse_mfhd() throws IOException {
        // version
        // flags
        stream.skipBytes(4);

        return stream.readInt();
    }

    private Traf parse_traf(Box ref, int trackId) throws IOException {
        Traf traf = new Traf();

        Box b = readBox(ATOM_TFHD);
        traf.tfhd = parse_tfhd(trackId);
        ensure(b);

        if (traf.tfhd == null) {
            return null;
        }

        b = untilBox(ref, ATOM_TRUN, ATOM_TFDT);

        if (b.type == ATOM_TFDT) {
            traf.tfdt = parse_tfdt();
            ensure(b);
            b = readBox(ATOM_TRUN);
        }

        traf.trun = parse_trun();
        ensure(b);

        return traf;
    }

    private Tfhd parse_tfhd(int trackId) throws IOException {
        Tfhd obj = new Tfhd();

        obj.bFlags = stream.readInt();
        obj.trackId = stream.readInt();

        if (trackId != -1 && obj.trackId != trackId) {
            return null;
        }

        if (hasFlag(obj.bFlags, 0x01)) {
            stream.skipBytes(8);
        }
        if (hasFlag(obj.bFlags, 0x02)) {
            stream.skipBytes(4);
        }
        if (hasFlag(obj.bFlags, 0x08)) {
            obj.defaultSampleDuration = stream.readInt();
        }
        if (hasFlag(obj.bFlags, 0x10)) {
            obj.defaultSampleSize = stream.readInt();
        }
        if (hasFlag(obj.bFlags, 0x20)) {
            obj.defaultSampleFlags = stream.readInt();
        }

        return obj;
    }

    private long parse_tfdt() throws IOException {
        int version = stream.read();
        stream.skipBytes(3);// flags     
        return version == 0 ? readUint() : stream.readLong();
    }

    private Trun parse_trun() throws IOException {
        Trun obj = new Trun();
        obj.bFlags = stream.readInt();
        obj.entryCount = stream.readInt();// unsigned int    

        obj.entries_rowSize = 0;
        if (hasFlag(obj.bFlags, 0x0100)) {
            obj.entries_rowSize += 4;
        }
        if (hasFlag(obj.bFlags, 0x0200)) {
            obj.entries_rowSize += 4;
        }
        if (hasFlag(obj.bFlags, 0x0400)) {
            obj.entries_rowSize += 4;
        }
        if (hasFlag(obj.bFlags, 0x0800)) {
            obj.entries_rowSize += 4;
        }
        obj.bEntries = new byte[obj.entries_rowSize * obj.entryCount];

        if (hasFlag(obj.bFlags, 0x0001)) {
            obj.dataOffset = stream.readInt();
        }
        if (hasFlag(obj.bFlags, 0x0004)) {
            obj.bFirstSampleFlags = stream.readInt();
        }

        stream.read(obj.bEntries);

        for (int i = 0; i < obj.entryCount; i++) {
            TrunEntry entry = obj.getEntry(i);
            if (hasFlag(obj.bFlags, 0x0100)) {
                obj.chunkDuration += entry.sampleDuration;
            }
            if (hasFlag(obj.bFlags, 0x0200)) {
                obj.chunkSize += entry.sampleSize;
            }
            if (hasFlag(obj.bFlags, 0x0800)) {
                if (!hasFlag(obj.bFlags, 0x0100)) {
                    obj.chunkDuration += entry.sampleCompositionTimeOffset;
                }
            }
        }

        return obj;
    }

    private int parse_ftyp() throws IOException {
        int brand = stream.readInt();
        stream.skipBytes(4);// minor version

        return brand;
    }

    private Mvhd parse_mvhd() throws IOException {
        int version = stream.read();
        stream.skipBytes(3);// flags

        // creation entries_time
        // modification entries_time
        stream.skipBytes(2 * (version == 0 ? 4 : 8));

        Mvhd obj = new Mvhd();
        obj.timeScale = readUint();

        // chunkDuration
        stream.skipBytes(version == 0 ? 4 : 8);

        // rate
        // volume
        // reserved
        // matrix array
        // predefined
        stream.skipBytes(76);

        obj.nextTrackId = readUint();

        return obj;
    }

    private Tkhd parse_tkhd() throws IOException {
        int version = stream.read();

        Tkhd obj = new Tkhd();

        // flags
        // creation entries_time
        // modification entries_time
        stream.skipBytes(3 + (2 * (version == 0 ? 4 : 8)));

        obj.trackId = stream.readInt();

        stream.skipBytes(4);// reserved

        obj.duration = version == 0 ? readUint() : stream.readLong();

        stream.skipBytes(2 * 4);// reserved

        obj.bLayer = stream.readShort();
        obj.bAlternateGroup = stream.readShort();
        obj.bVolume = stream.readShort();

        stream.skipBytes(2);// reserved

        obj.matrix = new byte[9 * 4];
        stream.read(obj.matrix);

        obj.bWidth = stream.readInt();
        obj.bHeight = stream.readInt();

        return obj;
    }

    private Trak parse_trak(Box ref) throws IOException {
        Trak trak = new Trak();

        Box b = readBox(ATOM_TKHD);
        trak.tkhd = parse_tkhd();
        ensure(b);

        b = untilBox(ref, ATOM_MDIA);
        trak.mdia = new byte[b.size];

        ByteBuffer buffer = ByteBuffer.wrap(trak.mdia);
        buffer.putInt(b.size);
        buffer.putInt(ATOM_MDIA);
        stream.read(trak.mdia, 8, b.size - 8);

        trak.mdia_mdhd_timeScale = parse_mdia(buffer);

        return trak;
    }

    private int parse_mdia(ByteBuffer data) {
        while (data.hasRemaining()) {
            int end = data.position() + data.getInt();
            if (data.getInt() == ATOM_MDHD) {
                byte version = data.get();
                data.position(data.position() + 3 + ((version == 0 ? 4 : 8) * 2));
                return data.getInt();
            }

            data.position(end);
        }

        return 0;// this NEVER should happen
    }

    private Moov parse_moov(Box ref) throws IOException {
        Box b = readBox(ATOM_MVHD);
        Moov moov = new Moov();
        moov.mvhd = parse_mvhd();
        ensure(b);

        ArrayList<Trak> tmp = new ArrayList<>((int) moov.mvhd.nextTrackId);
        while ((b = untilBox(ref, ATOM_TRAK, ATOM_MVEX)) != null) {

            switch (b.type) {
                case ATOM_TRAK:
                    tmp.add(parse_trak(b));
                    break;
                case ATOM_MVEX:
                    moov.mvex_trex = parse_mvex(b, (int) moov.mvhd.nextTrackId);
                    break;
            }

            ensure(b);
        }

        moov.trak = tmp.toArray(new Trak[tmp.size()]);

        return moov;
    }

    private Trex[] parse_mvex(Box ref, int possibleTrackCount) throws IOException {
        ArrayList<Trex> tmp = new ArrayList<>(possibleTrackCount);

        Box b;
        while ((b = untilBox(ref, ATOM_TREX)) != null) {
            tmp.add(parse_trex());
            ensure(b);
        }

        return tmp.toArray(new Trex[tmp.size()]);
    }

    private Trex parse_trex() throws IOException {
        // version
        // flags
        stream.skipBytes(4);

        Trex obj = new Trex();
        obj.trackId = stream.readInt();
        obj.defaultSampleDescriptionIndex = stream.readInt();
        obj.defaultSampleDuration = stream.readInt();
        obj.defaultSampleSize = stream.readInt();
        obj.defaultSampleFlags = stream.readInt();

        return obj;
    }

    private Tfra parse_tfra() throws IOException {
        int version = stream.read();

        stream.skipBytes(3);// flags

        Tfra tfra = new Tfra();
        tfra.trackId = stream.readInt();

        stream.skipBytes(3);// reserved
        int bFlags = stream.read();
        int size_tts = ((bFlags >> 4) & 3) + ((bFlags >> 2) & 3) + (bFlags & 3);

        tfra.entries_time = new int[stream.readInt()];

        for (int i = 0; i < tfra.entries_time.length; i++) {
            tfra.entries_time[i] = version == 0 ? stream.readInt() : (int) stream.readLong();
            stream.skipBytes(size_tts + (version == 0 ? 4 : 8));
        }

        return tfra;
    }

    private Sidx parse_sidx() throws IOException {
        int version = stream.read();

        stream.skipBytes(3);// flags

        Sidx obj = new Sidx();
        obj.referenceId = stream.readInt();
        obj.timescale = stream.readInt();

        // earliest presentation entries_time
        // first offset
        // reserved
        stream.skipBytes((2 * (version == 0 ? 4 : 8)) + 2);

        obj.entries_subsegmentDuration = new int[stream.readShort()];

        for (int i = 0; i < obj.entries_subsegmentDuration.length; i++) {
            // reference type
            // referenced size
            stream.skipBytes(4);
            obj.entries_subsegmentDuration[i] = stream.readInt();// unsigned int

            // starts with SAP
            // SAP type
            // SAP delta entries_time
            stream.skipBytes(4);
        }

        return obj;
    }

    private Tfra[] parse_mfra(Box ref, int trackCount) throws IOException {
        ArrayList<Tfra> tmp = new ArrayList<>(trackCount);
        long limit = ref.offset + ref.size;

        while (stream.position() < limit) {
            box = readBox();

            if (box.type == ATOM_TFRA) {
                tmp.add(parse_tfra());
            }

            ensure(box);
        }

        return tmp.toArray(new Tfra[tmp.size()]);
    }

    // </editor-fold>

    // <editor-fold defaultState="collapsed" desc="Helper classes">
    class Box {

        int type;
        long offset;
        int size;
    }

    class Sidx {

        int timescale;
        int referenceId;
        int[] entries_subsegmentDuration;
    }

    public class Moof {

        int mfhd_SequenceNumber;
        public Traf traf;
    }

    public class Traf {

        public Tfhd tfhd;
        long tfdt;
        public Trun trun;
    }

    public class Tfhd {

        int bFlags;
        public int trackId;
        int defaultSampleDuration;
        int defaultSampleSize;
        int defaultSampleFlags;
    }

    public class TrunEntry {

        public int sampleDuration;
        public int sampleSize;
        public int sampleFlags;
        public int sampleCompositionTimeOffset;
    }

    public class Trun {

        public int chunkDuration;
        public int chunkSize;

        public int bFlags;
        int bFirstSampleFlags;
        int dataOffset;

        public int entryCount;
        byte[] bEntries;
        int entries_rowSize;

        public TrunEntry getEntry(int i) {
            ByteBuffer buffer = ByteBuffer.wrap(bEntries, i * entries_rowSize, entries_rowSize);
            TrunEntry entry = new TrunEntry();

            if (hasFlag(bFlags, 0x0100)) {
                entry.sampleDuration = buffer.getInt();
            }
            if (hasFlag(bFlags, 0x0200)) {
                entry.sampleSize = buffer.getInt();
            }
            if (hasFlag(bFlags, 0x0400)) {
                entry.sampleFlags = buffer.getInt();
            }
            if (hasFlag(bFlags, 0x0800)) {
                entry.sampleCompositionTimeOffset = buffer.getInt();
            }

            return entry;
        }
    }

    public class Tkhd {

        int trackId;
        long duration;
        short bVolume;
        int bWidth;
        int bHeight;
        byte[] matrix;
        short bLayer;
        short bAlternateGroup;
    }

    public class Trak {

        public Tkhd tkhd;
        public int mdia_mdhd_timeScale;

        byte[] mdia;
    }

    class Mvhd {

        long timeScale;
        long nextTrackId;
    }

    class Moov {

        Mvhd mvhd;
        Trak[] trak;
        Trex[] mvex_trex;
    }

    class Tfra {

        int trackId;
        int[] entries_time;
    }

    public class Trex {

        private int trackId;
        int defaultSampleDescriptionIndex;
        int defaultSampleDuration;
        int defaultSampleSize;
        int defaultSampleFlags;
    }

    public class Mp4Track {

        public TrackKind kind;
        public Trak trak;
        public Trex trex;
    }

    public class Mp4TrackChunk {

        public InputStream data;
        public Moof moof;
    }
//</editor-fold>
}
