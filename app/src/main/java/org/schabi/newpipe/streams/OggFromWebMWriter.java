package org.schabi.newpipe.streams;

import android.support.annotation.NonNull;

import org.schabi.newpipe.streams.WebMReader.Cluster;
import org.schabi.newpipe.streams.WebMReader.Segment;
import org.schabi.newpipe.streams.WebMReader.SimpleBlock;
import org.schabi.newpipe.streams.WebMReader.WebMTrack;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Random;

import javax.annotation.Nullable;

/**
 * @author kapodamy
 */
public class OggFromWebMWriter implements Closeable {

    private static final byte FLAG_UNSET = 0x00;
    //private static final byte FLAG_CONTINUED = 0x01;
    private static final byte FLAG_FIRST = 0x02;
    private static final byte FLAG_LAST = 0x04;

    private final static byte SEGMENTS_PER_PACKET = 50;// used in ffmpeg, which is near 1 second at 48kHz
    private final static byte HEADER_CHECKSUM_OFFSET = 22;

    private boolean done = false;
    private boolean parsed = false;

    private SharpStream source;
    private SharpStream output;

    private int sequence_count = 0;
    private final int STREAM_ID;

    private WebMReader webm = null;
    private WebMTrack webm_track = null;
    private int track_index = 0;

    public OggFromWebMWriter(@NonNull SharpStream source, @NonNull SharpStream target) {
        if (!source.canRead() || !source.canRewind()) {
            throw new IllegalArgumentException("source stream must be readable and allows seeking");
        }
        if (!target.canWrite() || !target.canRewind()) {
            throw new IllegalArgumentException("output stream must be writable and allows seeking");
        }

        this.source = source;
        this.output = target;

        this.STREAM_ID = (new Random(System.currentTimeMillis())).nextInt();

        populate_crc32_table();
    }

    public boolean isDone() {
        return done;
    }

    public boolean isParsed() {
        return parsed;
    }

    public WebMTrack[] getTracksFromSource() throws IllegalStateException {
        if (!parsed) {
            throw new IllegalStateException("source must be parsed first");
        }

        return webm.getAvailableTracks();
    }

    public void parseSource() throws IOException, IllegalStateException {
        if (done) {
            throw new IllegalStateException("already done");
        }
        if (parsed) {
            throw new IllegalStateException("already parsed");
        }

        try {
            webm = new WebMReader(source);
            webm.parse();
            webm_segment = webm.getNextSegment();
        } finally {
            parsed = true;
        }
    }

    public void selectTrack(int trackIndex) throws IOException {
        if (!parsed) {
            throw new IllegalStateException("source must be parsed first");
        }
        if (done) {
            throw new IOException("already done");
        }
        if (webm_track != null) {
            throw new IOException("tracks already selected");
        }

        switch (webm.getAvailableTracks()[trackIndex].kind) {
            case Audio:
            case Video:
                break;
            default:
                throw new UnsupportedOperationException("the track must an audio or video stream");
        }

        try {
            webm_track = webm.selectTrack(trackIndex);
            track_index = trackIndex;
        } finally {
            parsed = true;
        }
    }

    @Override
    public void close() throws IOException {
        done = true;
        parsed = true;

        webm_track = null;
        webm = null;

        if (!output.isClosed()) {
            output.flush();
        }

        source.close();
        output.close();
    }

    public void build() throws IOException {
        float resolution;
        int read;
        byte[] buffer;
        int checksum;
        byte flag = FLAG_FIRST;// obligatory

        switch (webm_track.kind) {
            case Audio:
                resolution = getSampleFrequencyFromTrack(webm_track.bMetadata);
                if (resolution == 0f) {
                    throw new RuntimeException("cannot get the audio sample rate");
                }
                break;
            case Video:
                // WARNING: untested
                if (webm_track.defaultDuration == 0) {
                    throw new RuntimeException("missing default frame time");
                }
                resolution = 1000f / ((float) webm_track.defaultDuration / webm_segment.info.timecodeScale);
                break;
            default:
                throw new RuntimeException("not implemented");
        }

        /* step 1.1: write codec init data, in most cases must be present */
        if (webm_track.codecPrivate != null) {
            addPacketSegment(webm_track.codecPrivate.length);
            dump_packetHeader(flag, 0x00, webm_track.codecPrivate);
            flag = FLAG_UNSET;
        }

        /* step 1.2: write metadata */
        buffer = make_metadata();
        if (buffer != null) {
            addPacketSegment(buffer.length);
            dump_packetHeader(flag, 0x00, buffer);
            flag = FLAG_UNSET;
        }

        buffer = new byte[8 * 1024];

        /* step 1.3: write headers */
        long approx_packets = webm_segment.info.duration / webm_segment.info.timecodeScale;
        approx_packets = approx_packets / (approx_packets / SEGMENTS_PER_PACKET);

        ArrayList<Long> pending_offsets = new ArrayList<>((int) approx_packets);
        ArrayList<Integer> pending_checksums = new ArrayList<>((int) approx_packets);
        ArrayList<Short> data_offsets = new ArrayList<>((int) approx_packets);

        int page_size = 0;
        SimpleBlock bloq;

        while (webm_segment != null) {
            bloq = getNextBlock();

            if (bloq != null && addPacketSegment(bloq.dataSize)) {
                page_size += bloq.dataSize;

                if (segment_table_size < SEGMENTS_PER_PACKET) {
                    continue;
                }

                // calculate the current packet duration using the next block
                bloq = getNextBlock();
            }

            double elapsed_ns = webm_track.codecDelay;

            if (bloq == null) {
                flag = FLAG_LAST;
                elapsed_ns += webm_block_last_timecode;

                if (webm_track.defaultDuration > 0) {
                    elapsed_ns += webm_track.defaultDuration;
                } else {
                    // hardcoded way, guess the sample duration
                    elapsed_ns += webm_block_near_duration;
                }
            } else {
                elapsed_ns += bloq.absoluteTimeCodeNs;
            }

            // get the sample count in the page
            elapsed_ns = (elapsed_ns / 1000000000d) * resolution;
            elapsed_ns = Math.ceil(elapsed_ns);

            long offset = output_offset + HEADER_CHECKSUM_OFFSET;
            pending_offsets.add(offset);

            checksum = dump_packetHeader(flag, (long) elapsed_ns, null);
            pending_checksums.add(checksum);

            data_offsets.add((short) (output_offset - offset));

            // reserve space in the page
            while (page_size > 0) {
                int write = Math.min(page_size, buffer.length);
                out_write(buffer, write);
                page_size -= write;
            }

            webm_block = bloq;
        }

        /* step 2.1: write stream data */
        output.rewind();
        output_offset = 0;

        source.rewind();

        webm = new WebMReader(source);
        webm.parse();
        webm_track = webm.selectTrack(track_index);

        for (int i = 0; i < pending_offsets.size(); i++) {
            checksum = pending_checksums.get(i);
            segment_table_size = 0;

            out_seek(pending_offsets.get(i) + data_offsets.get(i));

            while (segment_table_size < SEGMENTS_PER_PACKET) {
                bloq = getNextBlock();

                if (bloq == null || !addPacketSegment(bloq.dataSize)) {
                    webm_block = bloq;// use this block later (if not null)
                    break;
                }

                // NOTE: calling bloq.data.close() is unnecessary
                while ((read = bloq.data.read(buffer)) != -1) {
                    out_write(buffer, read);
                    checksum = calc_crc32(checksum, buffer, read);
                }
            }

            pending_checksums.set(i, checksum);
        }

        /* step 2.2: write every checksum */
        output.rewind();
        output_offset = 0;
        buffer = new byte[4];

        ByteBuffer buff = ByteBuffer.wrap(buffer);
        buff.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < pending_checksums.size(); i++) {
            out_seek(pending_offsets.get(i));
            buff.putInt(0, pending_checksums.get(i));
            out_write(buffer);
        }
    }

    private int dump_packetHeader(byte flag, long gran_pos, byte[] immediate_page) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(27 + segment_table_size);

        buffer.putInt(0x4F676753);// "OggS" binary string
        buffer.put((byte) 0x00);// version
        buffer.put(flag);// type

        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putLong(gran_pos);// granulate position

        buffer.putInt(STREAM_ID);// bitstream serial number
        buffer.putInt(sequence_count++);// page sequence number

        buffer.putInt(0x00);// page checksum

        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.put((byte) segment_table_size);// segment table
        buffer.put(segment_table, 0, segment_table_size);// segment size

        segment_table_size = 0;// clear segment table for next header

        byte[] buff = buffer.array();
        int checksum_crc32 = calc_crc32(0x00, buff, buff.length);

        if (immediate_page != null) {
            checksum_crc32 = calc_crc32(checksum_crc32, immediate_page, immediate_page.length);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(HEADER_CHECKSUM_OFFSET, checksum_crc32);

            out_write(buff);
            out_write(immediate_page);
            return 0;
        }

        out_write(buff);
        return checksum_crc32;
    }

    @Nullable
    private byte[] make_metadata() {
        if ("A_OPUS".equals(webm_track.codecId)) {
            return new byte[]{
                    0x4F, 0x70, 0x75, 0x73, 0x54, 0x61, 0x67, 0x73,// "OpusTags" binary string
                    0x07, 0x00, 0x00, 0x00,// writting application string size
                    0x4E, 0x65, 0x77, 0x50, 0x69, 0x70, 0x65,// "NewPipe" binary string
                    0x00, 0x00, 0x00, 0x00// additional tags count (zero means no tags)
            };
        } else if ("A_VORBIS".equals(webm_track.codecId)) {
            return new byte[]{
                    0x03,// ????????
                    0x76, 0x6f, 0x72, 0x62, 0x69, 0x73,// "vorbis" binary string
                    0x07, 0x00, 0x00, 0x00,// writting application string size
                    0x4E, 0x65, 0x77, 0x50, 0x69, 0x70, 0x65,// "NewPipe" binary string
                    0x01, 0x00, 0x00, 0x00,// additional tags count (zero means no tags)

                    /*
                    // whole file duration (not implemented)
                    0x44,// tag string size
                    0x55, 0x52, 0x41, 0x54, 0x49, 0x4F, 0x4E, 0x3D, 0x30, 0x30, 0x3A, 0x30, 0x30, 0x3A, 0x30,
                    0x30, 0x2E, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30
                     */
                    0x0F,// tag string size
                    0x00, 0x00, 0x00, 0x45, 0x4E, 0x43, 0x4F, 0x44, 0x45, 0x52, 0x3D,// "ENCODER=" binary string
                    0x4E, 0x65, 0x77, 0x50, 0x69, 0x70, 0x65,// "NewPipe" binary string
                    0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00// ????????
            };
        }

        // not implemented for the desired codec
        return null;
    }

    //<editor-fold defaultstate="collapsed" desc="WebM track handling">
    private Segment webm_segment = null;
    private Cluster webm_cluter = null;
    private SimpleBlock webm_block = null;
    private long webm_block_last_timecode = 0;
    private long webm_block_near_duration = 0;

    private SimpleBlock getNextBlock() throws IOException {
        SimpleBlock res;

        if (webm_block != null) {
            res = webm_block;
            webm_block = null;
            return res;
        }

        if (webm_segment == null) {
            webm_segment = webm.getNextSegment();
            if (webm_segment == null) {
                return null;// no more blocks in the selected track
            }
        }

        if (webm_cluter == null) {
            webm_cluter = webm_segment.getNextCluster();
            if (webm_cluter == null) {
                webm_segment = null;
                return getNextBlock();
            }
        }

        res = webm_cluter.getNextSimpleBlock();
        if (res == null) {
            webm_cluter = null;
            return getNextBlock();
        }

        webm_block_near_duration = res.absoluteTimeCodeNs - webm_block_last_timecode;
        webm_block_last_timecode = res.absoluteTimeCodeNs;

        return res;
    }

    private float getSampleFrequencyFromTrack(byte[] bMetadata) {
        // hardcoded way
        ByteBuffer buffer = ByteBuffer.wrap(bMetadata);

        while (buffer.remaining() >= 6) {
            int id = buffer.getShort() & 0xFFFF;
            if (id == 0x0000B584) {
                return buffer.getFloat();
            }
        }

        return 0f;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Segment table store">
    private int segment_table_size = 0;
    private final byte[] segment_table = new byte[255];

    private boolean addPacketSegment(long size) {
        // check if possible add the segment, without overflow the table
        int available = (segment_table.length - segment_table_size) * 255;
        if (available < size) {
            return false;// not enough space on the page
        }

        while (size > 0) {
            segment_table[segment_table_size++] = (byte) Math.min(size, 255);
            size -= 255;
        }

        return true;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Output handling">
    private long output_offset = 0;

    private void out_write(byte[] buffer) throws IOException {
        output.write(buffer);
        output_offset += buffer.length;
    }

    private void out_write(byte[] buffer, int size) throws IOException {
        output.write(buffer, 0, size);
        output_offset += size;
    }

    private void out_seek(long offset) throws IOException {
        //if (output.canSeek()) { output.seek(offset); }
        output.skip(offset - output_offset);
        output_offset = offset;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Checksum CRC32">
    private final int[] crc32_table = new int[256];

    private void populate_crc32_table() {
        for (int i = 0; i < 0x100; i++) {
            int crc = i << 24;
            for (int j = 0; j < 8; j++) {
                long b = crc >>> 31;
                crc <<= 1;
                crc ^= (int) (0x100000000L - b) & 0x04c11db7;
            }
            crc32_table[i] = crc;
        }
    }

    private int calc_crc32(int initial_crc, byte[] buffer, int size) {
        for (int i = 0; i < size; i++) {
            int reg = (initial_crc >>> 24) & 0xff;
            initial_crc = (initial_crc << 8) ^ crc32_table[reg ^ (buffer[i] & 0xff)];
        }

        return initial_crc;
    }
    //</editor-fold>
}
