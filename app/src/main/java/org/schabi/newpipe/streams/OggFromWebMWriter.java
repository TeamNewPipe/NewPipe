package org.schabi.newpipe.streams;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.WebMReader.Cluster;
import org.schabi.newpipe.streams.WebMReader.Segment;
import org.schabi.newpipe.streams.WebMReader.SimpleBlock;
import org.schabi.newpipe.streams.WebMReader.WebMTrack;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.annotation.Nullable;

/**
 * @author kapodamy
 */
public class OggFromWebMWriter implements Closeable {

    private static final byte FLAG_UNSET = 0x00;
    //private static final byte FLAG_CONTINUED = 0x01;
    private static final byte FLAG_FIRST = 0x02;
    private static final byte FLAG_LAST = 0x04;

    private final static byte HEADER_CHECKSUM_OFFSET = 22;
    private final static byte HEADER_SIZE = 27;

    private final static int TIME_SCALE_NS = 1000000000;

    private boolean done = false;
    private boolean parsed = false;

    private SharpStream source;
    private SharpStream output;

    private int sequence_count = 0;
    private final int STREAM_ID;
    private byte packet_flag = FLAG_FIRST;

    private WebMReader webm = null;
    private WebMTrack webm_track = null;
    private Segment webm_segment = null;
    private Cluster webm_cluster = null;
    private SimpleBlock webm_block = null;

    private long webm_block_last_timecode = 0;
    private long webm_block_near_duration = 0;

    private short segment_table_size = 0;
    private final byte[] segment_table = new byte[255];
    private long segment_table_next_timestamp = TIME_SCALE_NS;

    private final int[] crc32_table = new int[256];

    public OggFromWebMWriter(@NonNull SharpStream source, @NonNull SharpStream target) {
        if (!source.canRead() || !source.canRewind()) {
            throw new IllegalArgumentException("source stream must be readable and allows seeking");
        }
        if (!target.canWrite() || !target.canRewind()) {
            throw new IllegalArgumentException("output stream must be writable and allows seeking");
        }

        this.source = source;
        this.output = target;

        this.STREAM_ID = (int) System.currentTimeMillis();

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
        SimpleBlock bloq;
        ByteBuffer header = ByteBuffer.allocate(27 + (255 * 255));
        ByteBuffer page = ByteBuffer.allocate(64 * 1024);

        header.order(ByteOrder.LITTLE_ENDIAN);

        /* step 1: get the amount of frames per seconds */
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

        /* step 2: create packet with code init data */
        if (webm_track.codecPrivate != null) {
            addPacketSegment(webm_track.codecPrivate.length);
            make_packetHeader(0x00, header, webm_track.codecPrivate);
            write(header);
            output.write(webm_track.codecPrivate);
        }

        /* step 3: create packet with metadata */
        byte[] buffer = make_metadata();
        if (buffer != null) {
            addPacketSegment(buffer.length);
            make_packetHeader(0x00, header, buffer);
            write(header);
            output.write(buffer);
        }

        /* step 4: calculate amount of packets */
        while (webm_segment != null) {
            bloq = getNextBlock();

            if (bloq != null && addPacketSegment(bloq)) {
                int pos = page.position();
                //noinspection ResultOfMethodCallIgnored
                bloq.data.read(page.array(), pos, bloq.dataSize);
                page.position(pos + bloq.dataSize);
                continue;
            }

            // calculate the current packet duration using the next block
            double elapsed_ns = webm_track.codecDelay;

            if (bloq == null) {
                packet_flag = FLAG_LAST;// note: if the flag is FLAG_CONTINUED, is changed
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
            elapsed_ns = elapsed_ns / TIME_SCALE_NS;
            elapsed_ns = Math.ceil(elapsed_ns * resolution);

            // create header and calculate page checksum
            int checksum = make_packetHeader((long) elapsed_ns, header, null);
            checksum = calc_crc32(checksum, page.array(), page.position());

            header.putInt(HEADER_CHECKSUM_OFFSET, checksum);

            // dump data
            write(header);
            write(page);

            webm_block = bloq;
        }
    }

    private int make_packetHeader(long gran_pos, @NonNull ByteBuffer buffer, byte[] immediate_page) {
        short length = HEADER_SIZE;

        buffer.putInt(0x5367674f);// "OggS" binary string in little-endian
        buffer.put((byte) 0x00);// version
        buffer.put(packet_flag);// type

        buffer.putLong(gran_pos);// granulate position

        buffer.putInt(STREAM_ID);// bitstream serial number
        buffer.putInt(sequence_count++);// page sequence number

        buffer.putInt(0x00);// page checksum

        buffer.put((byte) segment_table_size);// segment table
        buffer.put(segment_table, 0, segment_table_size);// segment size

        length += segment_table_size;

        clearSegmentTable();// clear segment table for next header

        int checksum_crc32 = calc_crc32(0x00, buffer.array(), length);

        if (immediate_page != null) {
            checksum_crc32 = calc_crc32(checksum_crc32, immediate_page, immediate_page.length);
            buffer.putInt(HEADER_CHECKSUM_OFFSET, checksum_crc32);
            segment_table_next_timestamp -= TIME_SCALE_NS;
        }

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

    private void write(ByteBuffer buffer) throws IOException {
        output.write(buffer.array(), 0, buffer.position());
        buffer.position(0);
    }



    @Nullable
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

        if (webm_cluster == null) {
            webm_cluster = webm_segment.getNextCluster();
            if (webm_cluster == null) {
                webm_segment = null;
                return getNextBlock();
            }
        }

        res = webm_cluster.getNextSimpleBlock();
        if (res == null) {
            webm_cluster = null;
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

    private void clearSegmentTable() {
        segment_table_next_timestamp += TIME_SCALE_NS;
        packet_flag = FLAG_UNSET;
        segment_table_size = 0;
    }

    private boolean addPacketSegment(SimpleBlock block) {
        long timestamp = block.absoluteTimeCodeNs + webm_track.codecDelay;

        if (timestamp >= segment_table_next_timestamp) {
            return false;
        }

        return addPacketSegment(block.dataSize);
    }

    private boolean addPacketSegment(int size) {
        if (size > 65025) {
            throw new UnsupportedOperationException("page size cannot be larger than 65025");
        }

        int available = (segment_table.length - segment_table_size) * 255;
        boolean extra = (size % 255) == 0;

        if (extra) {
            // add a zero byte entry in the table
            // required to indicate the sample size is multiple of 255
            available -= 255;
        }

        // check if possible add the segment, without overflow the table
        if (available < size) {
            return false;// not enough space on the page
        }

        for (; size > 0; size -= 255) {
            segment_table[segment_table_size++] = (byte) Math.min(size, 255);
        }

        if (extra) {
            segment_table[segment_table_size++] = 0x00;
        }

        return true;
    }

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

}
