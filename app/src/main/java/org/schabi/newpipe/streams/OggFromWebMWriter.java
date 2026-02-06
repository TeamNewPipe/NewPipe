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
import java.util.Arrays;

/**
 * <p>
 *     This class is used to convert a WebM stream containing Opus or Vorbis audio
 *     into an Ogg stream.
 * </p>
 *
 * <p>
 *     The following specifications are used for the implementation:
 * </p>
 * <ul>
 *     <li>FLAC: <a href="https://www.rfc-editor.org/rfc/rfc9639">RFC 9639</a></li>
 *     <li>Opus: All specs can be found at <a href="https://opus-codec.org/docs/">
 *         https://opus-codec.org/docs/</a>.
 *         <a href="https://datatracker.ietf.org/doc/html/rfc7845.html">RFC7845</a>
 *         defines the Ogg encapsulation for Opus streams, i.e.the container format and metadata.
 *     </li>
 *     <li>Vorbis: <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html">Vorbis I</a></li>
 * </ul>
 *
 * @author kapodamy
 * @author tobigr
 */
public class OggFromWebMWriter implements Closeable {
    private static final String TAG = OggFromWebMWriter.class.getSimpleName();

    /**
     * No flags set.
     */
    private static final byte FLAG_UNSET = 0x00;
    /**
     * The packet is continued from previous the previous page.
     */
    private static final byte FLAG_CONTINUED = 0x01;
    /**
     * BOS (beginning of stream).
     */
    private static final byte FLAG_FIRST = 0x02;
    /**
     * EOS (end of stream).
     */
    private static final byte FLAG_LAST = 0x04;;

    private static final byte HEADER_CHECKSUM_OFFSET = 22;
    private static final byte HEADER_SIZE = 27;

    private static final int TIME_SCALE_NS = 1_000_000_000;

    /**
     * The maximum size of a segment in the Ogg page, in bytes.
     * This is a fixed value defined by the Ogg specification.
     */
    private static final int OGG_SEGMENT_SIZE = 255;

    /**
     * The maximum size of the Opus packet in bytes, to be included in the Ogg page.
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7845.html#section-6">
     *     RFC7845 6. Packet Size Limits</a>
     */
    private static final int OPUS_MAX_PACKETS_PAGE_SIZE = 65_025;

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
    private final byte[] segmentTable = new byte[OGG_SEGMENT_SIZE];
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
            makePacketHeader(0x00, header, webmTrack.codecPrivate);
            write(header);
            output.write(webmTrack.codecPrivate);
        }

        /* step 3: create packet with metadata */
        final byte[] buffer = makeCommentHeader();
        if (buffer != null) {
            // Use the new overloaded addPacketSegment to handle metadata that may be
            // larger than the maximum page size. This method will split the metadata
            // into multiple Ogg pages as needed.
            addPacketSegmentMultiPage(buffer, header);
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
            int checksum = makePacketHeader((long) elapsedNs, header, null);
            checksum = calcCrc32(checksum, page.array(), page.position());

            header.putInt(HEADER_CHECKSUM_OFFSET, checksum);

            // dump data
            write(header);
            write(page);

            webmBlock = bloq;
        }
    }

    private int makePacketHeader(final long granPos, @NonNull final ByteBuffer buffer,
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

    /**
     * Creates the metadata header for the selected codec (Opus or Vorbis).
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7845.html#section-5.2">
     *     RFC7845 5.2. Comment Header</a> for OPUS metadata header format
     * @see <a href="https://xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-610004.2">
     *     Vorbis I 4.2. Header decode and decode setup</a> and
     *     <a href="https://xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-820005">
     *     Vorbis 5. comment field and header specification</a>
     *     for VORBIS metadata header format
     *
     * @return the metadata header as a byte array, or null if the codec is not supported
     * for metadata generation
     */
    @Nullable
    private byte[] makeCommentHeader() {
        if (DEBUG) {
            Log.d(TAG, "Downloading media with codec ID " + webmTrack.codecId);
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
                     metadata.add(makeFlacPictureTag(thumbnail));
                 }
            }

            if (DEBUG) {
                Log.d(TAG, "Creating metadata header with this data:");
                metadata.forEach(p -> Log.d(TAG, p.first + "=" + p.second));
            }

            return makeOpusTagsHeader(metadata);
        } else if ("A_VORBIS".equals(webmTrack.codecId)) {
            // See https://xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-610004.2
            // for the Vorbis comment header format
            // TODO: add Vorbis metadata: same as Opus, but with the Vorbis comment header format
            return new byte[]{
                    0x03, // packet type for Vorbis comment header
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
     * Generates a FLAC picture block for the provided bitmap.
     *
     * <p>
     *     The {@code METADATA_BLOCK_PICTURE} tag is defined in the FLAC specification (RFC 9639)
     *     and is supported by Opus and Vorbis metadata headers.
     *     The picture block contains the image data which is converted to JPEG
     *     and associated metadata such as picture type, dimensions, and color depth.
     *     The image data is Base64-encoded as per specification.
     * </p>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9639.html#section-8.8">
     *     RFC 9639 8.8 Picture</a>
     *
     * @param bitmap The bitmap to use for the picture block
     * @return The key-value pair representing the tag.
     * The key is {@code METADATA_BLOCK_PICTURE}
     * and the value is the Base64-encoded FLAC picture block.
     */
    private static Pair<String, String> makeFlacPictureTag(final Bitmap bitmap) {
        // FLAC picture block format (big-endian):
        // uint32 picture_type
        // uint32 mime_length,
        //        mime_string
        // uint32 desc_length,
        //        desc_string
        // uint32 width
        // uint32 height
        // uint32 color_depth
        // uint32 colors_indexed
        // uint32 data_length,
        //        data_bytes

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);

        final byte[] imageData = baos.toByteArray();
        final byte[] mimeBytes = "image/jpeg".getBytes(StandardCharsets.UTF_8);
        final byte[] descBytes = new byte[0]; // optional description
        // fixed ints + mime + desc
        final int headerSize = 4 * 8 + mimeBytes.length + descBytes.length;
        final ByteBuffer buf = ByteBuffer.allocate(headerSize + imageData.length);
        // See https://www.rfc-editor.org/rfc/rfc9639.html#table-13 for the complete list
        // of picture types
        // TODO: allow specifying other picture types, i.e. cover (front) for music albums;
        //       but this info needs to be provided by the extractor first.
        buf.putInt(3); // picture type: 0 = Other, 2 = Cover (front)
        buf.putInt(mimeBytes.length);
        buf.put(mimeBytes);
        buf.putInt(descBytes.length);
        // no description
        if (descBytes.length > 0) {
            buf.put(descBytes);
        }
        buf.putInt(bitmap.getWidth());
        buf.putInt(bitmap.getHeight());
        buf.putInt(24); // color depth for JPEG and PNG is usually 24 bits
        buf.putInt(0); // colors indexed (0 for non-indexed images like JPEG)
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
     * @ImplNote See <a href="https://datatracker.ietf.org/doc/html/rfc7845.html#section-5.2">
     *     RFC7845 5.2</a>
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
        // See RFC7845 5.2: https://datatracker.ietf.org/doc/html/rfc7845.html#section-5.2
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
        if (size > OPUS_MAX_PACKETS_PAGE_SIZE) {
            throw new UnsupportedOperationException(String.format(
                    "page size is %s but cannot be larger than %s",
                    size, OPUS_MAX_PACKETS_PAGE_SIZE));
        }

        int available = (segmentTable.length - segmentTableSize) * OGG_SEGMENT_SIZE;
        final boolean extra = (size % OGG_SEGMENT_SIZE) == 0;

        if (extra) {
            // add a zero byte entry in the table
            // required to indicate the sample size is multiple of OGG_SEGMENT_SIZE
            available -= OGG_SEGMENT_SIZE;
        }

        // check if possible add the segment, without overflow the table
        if (available < size) {
            return false; // not enough space on the page
        }

        for (int seg = size; seg > 0; seg -= OGG_SEGMENT_SIZE) {
            segmentTable[segmentTableSize++] = (byte) Math.min(seg, OGG_SEGMENT_SIZE);
        }

        if (extra) {
            segmentTable[segmentTableSize++] = 0x00;
        }

        return true;
    }

    /**
     * Overloaded addPacketSegment for large metadata blobs: splits the provided data into
     * multiple pages if necessary and writes them immediately (header + data).
     * This method is intended to be used only for metadata (e.g. large thumbnails).
     *
     * @param data the metadata to add as a packet segment
     * @param header a reusable ByteBuffer for writing page headers; this method will write
     *               the header for each page as needed
     */
    private void addPacketSegmentMultiPage(@NonNull final byte[] data,
                                           @NonNull final ByteBuffer header) throws IOException {
        int offset = 0;
        boolean first = true;

        while (offset < data.length) {
            final int remaining = data.length - offset;
            final boolean finalChunkCandidate = remaining <= OPUS_MAX_PACKETS_PAGE_SIZE;
            final int chunkSize;
            if (finalChunkCandidate) {
                chunkSize = remaining; // final chunk can be any size
            } else {
                // For intermediate (non-final) chunks, make the chunk size a multiple
                // of OGG_SEGMENT_SIZE so that the last lacing value is 255 and the
                // decoder won't treat the packet as finished on that page.
                final int maxFullSegments = OPUS_MAX_PACKETS_PAGE_SIZE / OGG_SEGMENT_SIZE;
                chunkSize = maxFullSegments * OGG_SEGMENT_SIZE;
            }

            final boolean isFinalChunk = (offset + chunkSize) >= data.length;

            // We must reserve appropriate number of lacing values in the segment table.
            // For chunks that are exact multiples of OGG_SEGMENT_SIZE and are the final
            // chunk of the packet, a trailing 0 lacing entry is required to indicate
            // the packet ends exactly on a segment boundary. For intermediate chunks
            // (continued across pages) we MUST NOT write that trailing 0 because then
            // the packet would appear complete on that page. Instead intermediate
            // chunks should end with only 255-valued lacing entries (no trailing 0).
            final int fullSegments = chunkSize / OGG_SEGMENT_SIZE; // may be 0
            final int lastSegSize = chunkSize % OGG_SEGMENT_SIZE; // 0..254
            final boolean chunkIsMultiple = (lastSegSize == 0);

            int requiredEntries = fullSegments + (lastSegSize > 0 ? 1 : 0);
            if (chunkIsMultiple && isFinalChunk) {
                // need an extra zero entry to mark packet end
                requiredEntries += 1;
            }

            // If the segment table doesn't have enough room, flush the current page
            // by writing a header without immediate data. This clears the segment table.
            if (requiredEntries > (segmentTable.length - segmentTableSize)) {
                // flush current page
                int checksum = makePacketHeader(0x00, header, null);
                checksum = calcCrc32(checksum, new byte[0], 0);
                header.putInt(HEADER_CHECKSUM_OFFSET, checksum);
                write(header);
            }

            // After ensuring space, if still not enough (edge case), throw
            if (requiredEntries > (segmentTable.length - segmentTableSize)) {
                throw new IOException("Unable to reserve segment table entries for metadata chunk");
            }

            // Fill the segment table entries for this chunk. For intermediate chunks
            // that are an exact multiple of OGG_SEGMENT_SIZE we must NOT append a
            // trailing zero entry (that would incorrectly signal packet end).
            final int remainingToAssign = chunkSize;
            for (int seg = remainingToAssign; seg > 0; seg -= OGG_SEGMENT_SIZE) {
                segmentTable[segmentTableSize++] = (byte) Math.min(seg, OGG_SEGMENT_SIZE);
            }

            if (chunkIsMultiple && isFinalChunk) {
                // Only append the zero terminator for a final chunk that has an exact
                // multiple of OGG_SEGMENT_SIZE bytes.
                segmentTable[segmentTableSize++] = 0x00;
            }

            // For continuation pages (after the first), mark the page as continued.
            if (!first) {
                packetFlag = FLAG_CONTINUED;
            }

            final byte[] chunk = Arrays.copyOfRange(data, offset, offset + chunkSize);

            // Now create header (which will consume and clear the segment table) and write
            // header + chunk data. makePacketHeader will compute checksum including chunk
            // when an immediatePage is provided.
            makePacketHeader(0x00, header, chunk);
            write(header);
            output.write(chunk);

            offset += chunkSize;
            first = false;
        }
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
