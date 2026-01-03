package org.schabi.newpipe.streams;

import static org.schabi.newpipe.MainActivity.DEBUG;

import android.graphics.Bitmap;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.streams.WebMReader.Cluster;
import org.schabi.newpipe.streams.WebMReader.Segment;
import org.schabi.newpipe.streams.WebMReader.SimpleBlock;
import org.schabi.newpipe.streams.WebMReader.WebMTrack;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author kapodamy
 */
public class OggFromWebMWriter implements Closeable {
    private static final byte FLAG_UNSET = 0x00;
    //private static final byte FLAG_CONTINUED = 0x01;
    private static final byte FLAG_FIRST = 0x02;
    private static final byte FLAG_LAST = 0x04;

    private static final byte HEADER_CHECKSUM_OFFSET = 22;
    private static final byte HEADER_SIZE = 27;

    private static final int TIME_SCALE_NS = 1000000000;

    private boolean done = false;
    private boolean parsed = false;

    private final SharpStream source;
    private final SharpStream output;

    private int sequenceCount = 0;
    private final int streamId;
    private byte packetFlag = FLAG_FIRST;

    private WebMReader webm = null;
    private WebMTrack webmTrack = null;
    private Segment webmSegment = null;
    private Cluster webmCluster = null;
    private SimpleBlock webmBlock = null;

    private long webmBlockLastTimecode = 0;
    private long webmBlockNearDuration = 0;

    private short segmentTableSize = 0;
    private final byte[] segmentTable = new byte[255];
    private long segmentTableNextTimestamp = TIME_SCALE_NS;

    private final int[] crc32Table = new int[256];
    private final StreamInfo streamInfo;
    private final Bitmap thumbnail;

    /**
     * Constructor of OggFromWebMWriter.
     * @param source
     * @param target
     * @param streamInfo the stream info
     * @param thumbnail the thumbnail bitmap used as cover art
     */
    public OggFromWebMWriter(@NonNull final SharpStream source,
                             @NonNull final SharpStream target,
                             @Nullable final StreamInfo streamInfo,
                             @Nullable final Bitmap thumbnail) {
        if (!source.canRead() || !source.canRewind()) {
            throw new IllegalArgumentException("source stream must be readable and allows seeking");
        }
        if (!target.canWrite() || !target.canRewind()) {
            throw new IllegalArgumentException("output stream must be writable and allows seeking");
        }

        this.source = source;
        this.output = target;
        this.streamInfo = streamInfo;
        this.thumbnail = thumbnail;

        this.streamId = (int) System.currentTimeMillis();

        populateCrc32Table();
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
            webmSegment = webm.getNextSegment();
        } finally {
            parsed = true;
        }
    }

    public void selectTrack(final int trackIndex) throws IOException {
        if (!parsed) {
            throw new IllegalStateException("source must be parsed first");
        }
        if (done) {
            throw new IOException("already done");
        }
        if (webmTrack != null) {
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
            webmTrack = webm.selectTrack(trackIndex);
        } finally {
            parsed = true;
        }
    }

    @Override
    public void close() throws IOException {
        done = true;
        parsed = true;

        webmTrack = null;
        webm = null;

        if (!output.isClosed()) {
            output.flush();
        }

        source.close();
        output.close();
    }

    public void build() throws IOException {
        final float resolution;
        SimpleBlock bloq;
        final ByteBuffer header = ByteBuffer.allocate(27 + (255 * 255));
        final ByteBuffer page = ByteBuffer.allocate(64 * 1024);

        header.order(ByteOrder.LITTLE_ENDIAN);

        /* step 1: get the amount of frames per seconds */
        switch (webmTrack.kind) {
            case Audio:
                resolution = getSampleFrequencyFromTrack(webmTrack.bMetadata);
                if (resolution == 0f) {
                    throw new RuntimeException("cannot get the audio sample rate");
                }
                break;
            case Video:
                // WARNING: untested
                if (webmTrack.defaultDuration == 0) {
                    throw new RuntimeException("missing default frame time");
                }
                resolution = 1000f / ((float) webmTrack.defaultDuration
                        / webmSegment.info.timecodeScale);
                break;
            default:
                throw new RuntimeException("not implemented");
        }

        /* step 2: create packet with code init data */
        if (webmTrack.codecPrivate != null) {
            addPacketSegment(webmTrack.codecPrivate.length);
            makePacketheader(0x00, header, webmTrack.codecPrivate);
            write(header);
            output.write(webmTrack.codecPrivate);
        }

        /* step 3: create packet with metadata */
        final byte[] buffer = makeMetadata();
        if (buffer != null) {
            addPacketSegment(buffer.length);
            makePacketheader(0x00, header, buffer);
            write(header);
            output.write(buffer);
        }

        /* step 4: calculate amount of packets */
        while (webmSegment != null) {
            bloq = getNextBlock();

            if (bloq != null && addPacketSegment(bloq)) {
                final int pos = page.position();
                //noinspection ResultOfMethodCallIgnored
                bloq.data.read(page.array(), pos, bloq.dataSize);
                page.position(pos + bloq.dataSize);
                continue;
            }

            // calculate the current packet duration using the next block
            double elapsedNs = webmTrack.codecDelay;

            if (bloq == null) {
                packetFlag = FLAG_LAST; // note: if the flag is FLAG_CONTINUED, is changed
                elapsedNs += webmBlockLastTimecode;

                if (webmTrack.defaultDuration > 0) {
                    elapsedNs += webmTrack.defaultDuration;
                } else {
                    // hardcoded way, guess the sample duration
                    elapsedNs += webmBlockNearDuration;
                }
            } else {
                elapsedNs += bloq.absoluteTimeCodeNs;
            }

            // get the sample count in the page
            elapsedNs = elapsedNs / TIME_SCALE_NS;
            elapsedNs = Math.ceil(elapsedNs * resolution);

            // create header and calculate page checksum
            int checksum = makePacketheader((long) elapsedNs, header, null);
            checksum = calcCrc32(checksum, page.array(), page.position());

            header.putInt(HEADER_CHECKSUM_OFFSET, checksum);

            // dump data
            write(header);
            write(page);

            webmBlock = bloq;
        }
    }

    private int makePacketheader(final long granPos, @NonNull final ByteBuffer buffer,
                                 final byte[] immediatePage) {
        short length = HEADER_SIZE;

        buffer.putInt(0x5367674f); // "OggS" binary string in little-endian
        buffer.put((byte) 0x00); // version
        buffer.put(packetFlag); // type

        buffer.putLong(granPos); // granulate position

        buffer.putInt(streamId); // bitstream serial number
        buffer.putInt(sequenceCount++); // page sequence number

        buffer.putInt(0x00); // page checksum

        buffer.put((byte) segmentTableSize); // segment table
        buffer.put(segmentTable, 0, segmentTableSize); // segment size

        length += segmentTableSize;

        clearSegmentTable(); // clear segment table for next header

        int checksumCrc32 = calcCrc32(0x00, buffer.array(), length);

        if (immediatePage != null) {
            checksumCrc32 = calcCrc32(checksumCrc32, immediatePage, immediatePage.length);
            buffer.putInt(HEADER_CHECKSUM_OFFSET, checksumCrc32);
            segmentTableNextTimestamp -= TIME_SCALE_NS;
        }

        return checksumCrc32;
    }

    @Nullable
    private byte[] makeMetadata() {
        if (DEBUG) {
            Log.d("OggFromWebMWriter", "Downloading media with codec ID " + webmTrack.codecId);
        }

        if ("A_OPUS".equals(webmTrack.codecId)) {
            final var metadata = new ArrayList<Pair<String, String>>();
            if (streamInfo != null) {
                metadata.add(Pair.create("COMMENT", streamInfo.getUrl()));
                metadata.add(Pair.create("GENRE", streamInfo.getCategory()));
                metadata.add(Pair.create("ARTIST", streamInfo.getUploaderName()));
                metadata.add(Pair.create("TITLE", streamInfo.getName()));
                metadata.add(Pair.create("DATE", streamInfo
                        .getUploadDate()
                        .getLocalDateTime()
                        .format(DateTimeFormatter.ISO_DATE)));
                 if (thumbnail != null) {
                     metadata.add(makeOpusPictureTag(thumbnail));
                 }
            }

            if (DEBUG) {
                Log.d("OggFromWebMWriter", "Creating metadata header with this data:");
                metadata.forEach(p -> Log.d("OggFromWebMWriter", p.first + "=" + p.second));
            }

            return makeOpusTagsHeader(metadata);
        } else if ("A_VORBIS".equals(webmTrack.codecId)) {
            return new byte[]{
                    0x03, // ???
                    0x76, 0x6f, 0x72, 0x62, 0x69, 0x73, // "vorbis" binary string
                    0x00, 0x00, 0x00, 0x00, // writing application string size (not present)
                    0x00, 0x00, 0x00, 0x00 // additional tags count (zero means no tags)
            };
        }

        // not implemented for the desired codec
        return null;
    }

    /**
     * This creates a single metadata tag for use in opus metadata headers. It contains the four
     * byte string length field and includes the string as-is. This cannot be used independently,
     * but must follow a proper "OpusTags" header.
     *
     * @param pair A key-value pair in the format "KEY=some value"
     * @return The binary data of the encoded metadata tag
     */
    private static byte[] makeOpusMetadataTag(final Pair<String, String> pair) {
        final var keyValue = pair.first.toUpperCase() + "=" + pair.second.trim();

        final var bytes = keyValue.getBytes();
        final var buf = ByteBuffer.allocate(4 + bytes.length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(bytes.length);
        buf.put(bytes);
        return buf.array();
    }

    /**
     * Adds the {@code METADATA_BLOCK_PICTURE} tag to the Opus metadata,
     * containing the provided bitmap as cover art.
     *
     * <p>
     *     One could also use the COVERART tag instead, but it is not as widely supported
     *     as METADATA_BLOCK_PICTURE.
     * </p>
     *
     * @param bitmap The bitmap to use as cover art
     * @return The key-value pair representing the tag
     */
    private static Pair<String, String> makeOpusPictureTag(final Bitmap bitmap) {
        // FLAC picture block format (big-endian):
        // uint32 picture_type
        // uint32 mime_length, mime_string
        // uint32 desc_length, desc_string
        // uint32 width
        // uint32 height
        // uint32 color_depth
        // uint32 colors_indexed
        // uint32 data_length, data_bytes

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);

        final byte[] imageData = baos.toByteArray();
        final byte[] mimeBytes = "image/jpeg".getBytes(StandardCharsets.UTF_8);
        final byte[] descBytes = new byte[0]; // optional description
        // fixed ints + mime + desc
        final int headerSize = 4 * 8 + mimeBytes.length + descBytes.length;
        final ByteBuffer buf = ByteBuffer.allocate(headerSize + imageData.length);
        buf.putInt(3); // picture type: 3 = Cover (front)
        buf.putInt(mimeBytes.length);
        buf.put(mimeBytes);
        buf.putInt(descBytes.length);
        // no description
        if (descBytes.length > 0) {
            buf.put(descBytes);
        }
        buf.putInt(bitmap.getWidth()); // width (unknown)
        buf.putInt(bitmap.getHeight()); // height (unknown)
        buf.putInt(0); // color depth
        buf.putInt(0); // colors indexed
        buf.putInt(imageData.length);
        buf.put(imageData);
        final String b64 = Base64.getEncoder().encodeToString(buf.array());
        return Pair.create("METADATA_BLOCK_PICTURE", b64);
    }

    /**
     * This returns a complete "OpusTags" header, created from the provided metadata tags.
     * <p>
     * You probably want to use makeOpusMetadata(), which uses this function to create
     * a header with sensible metadata filled in.
     *
     * @param keyValueLines A list of pairs of the tags. This can also be though of as a mapping
     *                      from one key to multiple values.
     * @return The binary header
     */
    private static byte[] makeOpusTagsHeader(final List<Pair<String, String>> keyValueLines) {
        final var tags = keyValueLines
                .stream()
                .filter(p -> !p.second.isBlank())
                .map(OggFromWebMWriter::makeOpusMetadataTag)
                .collect(Collectors.toUnmodifiableList());

        final var tagsBytes = tags.stream().collect(Collectors.summingInt(arr -> arr.length));

        // Fixed header fields + dynamic fields
        final var byteCount = 16 + tagsBytes;

        final var head = ByteBuffer.allocate(byteCount);
        head.order(ByteOrder.LITTLE_ENDIAN);
        head.put(new byte[]{
                0x4F, 0x70, 0x75, 0x73, 0x54, 0x61, 0x67, 0x73, // "OpusTags" binary string
                0x00, 0x00, 0x00, 0x00, // vendor (aka. Encoder) string of length 0
        });
        head.putInt(tags.size()); // 4 bytes for tag count
        tags.forEach(head::put); // dynamic amount of tag bytes

        return head.array();
    }

    private void write(final ByteBuffer buffer) throws IOException {
        output.write(buffer.array(), 0, buffer.position());
        buffer.position(0);
    }

    @Nullable
    private SimpleBlock getNextBlock() throws IOException {
        SimpleBlock res;

        if (webmBlock != null) {
            res = webmBlock;
            webmBlock = null;
            return res;
        }

        if (webmSegment == null) {
            webmSegment = webm.getNextSegment();
            if (webmSegment == null) {
                return null; // no more blocks in the selected track
            }
        }

        if (webmCluster == null) {
            webmCluster = webmSegment.getNextCluster();
            if (webmCluster == null) {
                webmSegment = null;
                return getNextBlock();
            }
        }

        res = webmCluster.getNextSimpleBlock();
        if (res == null) {
            webmCluster = null;
            return getNextBlock();
        }

        webmBlockNearDuration = res.absoluteTimeCodeNs - webmBlockLastTimecode;
        webmBlockLastTimecode = res.absoluteTimeCodeNs;

        return res;
    }

    private float getSampleFrequencyFromTrack(final byte[] bMetadata) {
        // hardcoded way
        final ByteBuffer buffer = ByteBuffer.wrap(bMetadata);

        while (buffer.remaining() >= 6) {
            final int id = buffer.getShort() & 0xFFFF;
            if (id == 0x0000B584) {
                return buffer.getFloat();
            }
        }

        return 0.0f;
    }

    private void clearSegmentTable() {
        segmentTableNextTimestamp += TIME_SCALE_NS;
        packetFlag = FLAG_UNSET;
        segmentTableSize = 0;
    }

    private boolean addPacketSegment(final SimpleBlock block) {
        final long timestamp = block.absoluteTimeCodeNs + webmTrack.codecDelay;

        if (timestamp >= segmentTableNextTimestamp) {
            return false;
        }

        return addPacketSegment(block.dataSize);
    }

    private boolean addPacketSegment(final int size) {
        if (size > 65025) {
            throw new UnsupportedOperationException(
                    String.format("page size is %s but cannot be larger than 65025", size));
        }

        int available = (segmentTable.length - segmentTableSize) * 255;
        final boolean extra = (size % 255) == 0;

        if (extra) {
            // add a zero byte entry in the table
            // required to indicate the sample size is multiple of 255
            available -= 255;
        }

        // check if possible add the segment, without overflow the table
        if (available < size) {
            return false; // not enough space on the page
        }

        for (int seg = size; seg > 0; seg -= 255) {
            segmentTable[segmentTableSize++] = (byte) Math.min(seg, 255);
        }

        if (extra) {
            segmentTable[segmentTableSize++] = 0x00;
        }

        return true;
    }

    private void populateCrc32Table() {
        for (int i = 0; i < 0x100; i++) {
            int crc = i << 24;
            for (int j = 0; j < 8; j++) {
                final long b = crc >>> 31;
                crc <<= 1;
                crc ^= (int) (0x100000000L - b) & 0x04c11db7;
            }
            crc32Table[i] = crc;
        }
    }

    private int calcCrc32(final int initialCrc, final byte[] buffer, final int size) {
        int crc = initialCrc;
        for (int i = 0; i < size; i++) {
            final int reg = (crc >>> 24) & 0xff;
            crc = (crc << 8) ^ crc32Table[reg ^ (buffer[i] & 0xff)];
        }

        return crc;
    }
}
