/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.postprocessing;

import static java.time.ZoneOffset.UTC;

import android.graphics.Bitmap;

import org.schabi.newpipe.streams.io.SharpInputStream;
import org.schabi.newpipe.streams.io.SharpStream;
import org.schabi.newpipe.util.StreamInfoMetadataHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * Adds Metadata to an MP3 file by writing ID3v2.4 frames, i.e. metadata tags,
 * at the start of the file.
 * @see <a href="https://id3.org/id3v2.4.0-structure">ID3v2.4 specification</a>
 * @see <a href="https://id3.org/id3v2.4.0-frames">ID3v2.4 frames</a>
 */
public class Mp3Metadata extends Postprocessing {
    /**
     * ID3v2 tags are stored at the start of the MP3 file and consist of a 10-byte header
     * followed by a sequence of frames.
     * <br>
     * The header contains the
     * <ul>
     * <li>tag identifier (3 bytes),</li>
     * <li>version (1 byte),</li>
     * <li>revision (1 byte),</li>
     * <li>flags (1 byte),</li>
     * <li>and the size of the tag (excluding the header) as a synchsafe integer (4 bytes).</li>
     * </ul>
     */
    private static final int ID3_HEADER_SIZE = 10;

    Mp3Metadata() {
        super(true, true, ALGORITHM_MP3_METADATA);
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        if (sources == null || sources.length == 0 || sources[0] == null) {
            // nothing to do
            return OK_RESULT;
        }

        // MP3 metadata is stored in ID3v2 tags at the start of the file,
        // so we need to build the tag in memory first and then write it
        // before copying the rest of the file.

        final ByteArrayOutputStream frames = new ByteArrayOutputStream();
        final FrameWriter fw = new FrameWriter(frames);

        makeMetadata(fw);
        makePictureFrame(fw);

        byte[] framesBytes = frames.toByteArray();
        final int tagSize = framesBytes.length; // size excluding 10-byte header

        out.write(new byte[]{
                'I', 'D', '3',
                0x04, // version 2.4
                0x00, // revision
                0x00, // flags
        });
        out.write(toSynchsafe(tagSize));
        out.write(framesBytes);

        // copy the rest of the file, skipping any existing ID3v2 tag if present
        try (InputStream sIn = new SharpInputStream(sources[0])) {
            copyStreamSkippingId3(sIn, out);
        }
        out.flush();

        return OK_RESULT;
    }

    /**
     * Write metadata frames based on the StreamInfo's metadata.
     * @see <a href="https://id3.org/id3v2.4.0-frames">ID3v2.4 frames</a> for a list of frame types
     * and their identifiers.
     * @param fw the FrameWriter to write frames to
     * @throws IOException if an I/O error occurs while writing frames
     */
    private void makeMetadata(@Nonnull final FrameWriter fw) throws IOException {
        var metadata = new StreamInfoMetadataHelper(this.streamInfo);

        fw.writeTextFrame("TIT2", metadata.getTitle());
        fw.writeTextFrame("TPE1", metadata.getArtist());
        fw.writeTextFrame("TCOM", metadata.getComposer());
        fw.writeTextFrame("TIPL", metadata.getPerformer());
        fw.writeTextFrame("TCON", metadata.getGenre());
        fw.writeTextFrame("TALB", metadata.getAlbum());

        final LocalDateTime releaseDate = metadata.getReleaseDate().getLocalDateTime(UTC);
        // determine precision by checking that lower-order fields are at their "zero"/start values
        final boolean isOnlyMonth = releaseDate.getDayOfMonth() == 1
                && releaseDate.getHour() == 0
                && releaseDate.getMinute() == 0
                && releaseDate.getSecond() == 0
                && releaseDate.getNano() == 0;
        final boolean isOnlyYear = releaseDate.getMonthValue() == 1
                && isOnlyMonth;
        // see https://id3.org/id3v2.4.0-structure > 4. ID3v2 frame overview
        // for date formats in TDRC frame
        final String datePattern;
        if (isOnlyYear) {
            datePattern = "yyyy";
        } else if (isOnlyMonth) {
            datePattern = "yyyy-MM";
        } else {
            datePattern = "yyyy-MM-dd";
        }
        fw.writeTextFrame("TDRC",
                releaseDate.format(DateTimeFormatter.ofPattern(datePattern)));


        if (metadata.getTrackNumber() != null) {
            fw.writeTextFrame("TRCK", String.valueOf(metadata.getTrackNumber()));
        }

        fw.writeTextFrame("TPUB", metadata.getRecordLabel());
        fw.writeTextFrame("TCOP", metadata.getCopyright());

        // WXXX is a user defined URL link frame, we can use it to store the URL of the stream
        // However, since it's user defined, so not all players support it.
        // Using the comment frame (COMM) as fallback
        fw.writeTextFrame("WXXX", streamInfo.getUrl());
        fw.writeCommentFrame("eng", streamInfo.getUrl());
    }

    /**
     * Write a picture frame (APIC) with the thumbnail image if available.
     * @param fw the FrameWriter to write the picture frame to
     * @throws IOException if an I/O error occurs while writing the frame
     */
    private void makePictureFrame(FrameWriter fw) throws IOException {
        if (thumbnail != null) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            thumbnail.compress(Bitmap.CompressFormat.PNG, 100, baos);
            final byte[] imgBytes = baos.toByteArray();
            baos.close();
            fw.writePictureFrame("image/png", imgBytes);
        }
    }

    /**
     * Copy the input stream to the output stream, but if the input stream starts with an ID3v2 tag,
     * skip the tag and only copy the audio data.
     * @param in the input stream to read from (should be at the start of the MP3 file)
     * @param out the output stream to write to
     * @throws IOException if an I/O error occurs while reading or writing
     */
    private static void copyStreamSkippingId3(@Nonnull final InputStream in,
                                              @Nonnull final SharpStream out) throws IOException {
        PushbackInputStream pin = (in instanceof PushbackInputStream pis)
                ? pis : new PushbackInputStream(in, ID3_HEADER_SIZE);
        // This assumes that the ID3 tag is at the very start of the file, which is the case
        // for ID3v2 tags.
        // IDv1 tags are at the end of the file, but ID3v1 is very old and not commonly used
        // (IDv2 has been around since 1998 and is more widely supported),
        // so we can ignore it for simplicity.
        byte[] header = new byte[ID3_HEADER_SIZE];
        int hr = pin.read(header);
        if (hr == ID3_HEADER_SIZE && header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
            // bytes 3 and 4 are version and revision and byte 5 is flags
            // the size is stored as synchsafe at bytes 6..9
            int size = fromSynchsafe(header, 6);
            long remaining = size;
            // consume exactly 'size' bytes, i.e. the rest of the metadata frames, from the stream
            byte[] skipBuf = new byte[8192];
            while (remaining > 0) {
                int toRead = (int) Math.min(skipBuf.length, remaining);
                int r = pin.read(skipBuf, 0, toRead);
                if (r <= 0) break;
                remaining -= r;
            }
        } else {
            // push header bytes back so copy will include them
            if (hr > 0) pin.unread(header, 0, hr);
        }

        // copy rest
        byte[] buf = new byte[8192];
        int r;
        while ((r = pin.read(buf)) > 0) out.write(buf, 0, r);
    }

    /**
     * Create a 4-byte synchsafe integer from a regular integer value.
     * @see <a href="https://id3.org/id3v2.4.0-structure">ID3v2.4 specification</a> section
     * <i>6.2. Synchsafe integers</i>
     * @param value the integer value to convert (should be non-negative and less than 2^28)
     * @return the synchsafe byte array
     */
    private static byte[] toSynchsafe(int value) {
        byte[] b = new byte[4];
        b[0] = (byte) ((value >> 21) & 0x7F);
        b[1] = (byte) ((value >> 14) & 0x7F);
        b[2] = (byte) ((value >> 7) & 0x7F);
        b[3] = (byte) (value & 0x7F);
        return b;
    }

    /**
     * Get a regular integer from a 4-byte synchsafe byte array.
     * @see <a href="https://id3.org/id3v2.4.0-structure">ID3v2.4 specification</a> section
     * <i>6.2. Synchsafe integers</i>
     * @param b the byte array containing the synchsafe integer
     *          (should be at least 4 bytes + offset long)
     * @param offset the offset in the byte array where the synchsafe integer starts
     * @return the regular integer value
     */
    private static int fromSynchsafe(byte[] b, int offset) {
        return ((b[offset] & 0x7F) << 21)
                | ((b[offset + 1] & 0x7F) << 14)
                | ((b[offset + 2] & 0x7F) << 7)
                | (b[offset + 3] & 0x7F);
    }

    /**
     * Helper class to write ID3v2.4 frames to a ByteArrayOutputStream.
     */
    private static class FrameWriter {

        /**
         * This separator is used to separate multiple entries in a list of an ID3v2 text frame.
         * @see <a href="https://id3.org/id3v2.4.0-frames">ID3v2.4 frames</a> section
         * <i>4.2. Text information frames</i>
         */
        private static final Character TEXT_LIST_SEPARATOR = 0x00;
        private static final byte UTF8_ENCODING_BYTE = 0x03;

        private final ByteArrayOutputStream out;

        FrameWriter(ByteArrayOutputStream out) {
            this.out = out;
        }

        /**
         * Write a text frame with the given identifier and text content.
         * @param id the 4 character long frame identifier
         * @param text the text content to write. If null or blank, no frame is written.
         * @throws IOException if an I/O error occurs while writing the frame
         */
        void writeTextFrame(String id, String text) throws IOException {
            if (text == null || text.isBlank()) return;
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(UTF8_ENCODING_BYTE);
            frame.write(data);
            writeFrame(id, frame.toByteArray());
        }

        /**
         * Write a text frame that can contain multiple entries separated by the
         * {@link #TEXT_LIST_SEPARATOR}.
         * @param id the 4 character long frame identifier
         * @param texts the list of text entries to write. If null or empty, no frame is written.
         *              Blank or null entries are skipped.
         * @throws IOException if an I/O error occurs while writing the frame
         */
        void writeTextFrame(String id, List<String> texts) throws IOException {
            if (texts == null || texts.isEmpty()) return;
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(UTF8_ENCODING_BYTE);
            for (int i = 0; i < texts.size(); i++) {
                String text = texts.get(i);
                if (text != null && !text.isBlank()) {
                    byte[] data = text.getBytes(StandardCharsets.UTF_8);
                    frame.write(data);
                    if (i < texts.size() - 1) {
                        frame.write(TEXT_LIST_SEPARATOR);
                    }
                }
            }
            writeFrame(id, frame.toByteArray());
        }

        /**
         * Write a picture frame (APIC) with the given MIME type and image data.
         * @see <a href="https://id3.org/id3v2.4.0-frames">ID3v2.4 frames</a> section
         * <i>4.14. Attached picture</i>
         * @param mimeType the MIME type of the image (e.g. "image/png" or "image/jpeg").
         * @param imageData the binary data of the image. If empty, no frame is written.
         * @throws IOException
         */
        void writePictureFrame(@Nonnull String mimeType, @Nonnull byte[] imageData)
                throws IOException {
            if (imageData.length == 0) return;
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(UTF8_ENCODING_BYTE);
            frame.write(mimeType.getBytes(StandardCharsets.US_ASCII));
            frame.write(0x00);
            frame.write(0x03); // picture type: 3 = cover(front)
            frame.write(0x00); // empty description terminator (UTF-8 empty string)
            // Then the picture bytes
            frame.write(imageData);
            writeFrame("APIC", frame.toByteArray());
        }

        /**
         * Write a comment frame (COMM) with the given language and comment text.
         * @param lang a 3-character ISO-639-2 language code (e.g. "eng" for English).
         *             If null or invalid, defaults to "eng".
         * @param comment the comment text to write. If null, no frame is written.
         * @throws IOException
         */
        void writeCommentFrame(String lang, String comment) throws IOException {
            if (comment == null) return;
            if (lang == null || lang.length() != 3) lang = "eng";
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(UTF8_ENCODING_BYTE);
            frame.write(lang.getBytes(StandardCharsets.US_ASCII));
            frame.write(0x00); // short content descriptor (empty) terminator
            frame.write(comment.getBytes(StandardCharsets.UTF_8));
            writeFrame("COMM", frame.toByteArray());
        }

        private void writeFrame(String id, byte[] data) throws IOException {
            if (data == null || data.length == 0) return;
            // frame header: id(4) size(4 synchsafe) flags(2)
            out.write(id.getBytes(StandardCharsets.US_ASCII));
            out.write(toSynchsafe(data.length));
            out.write(new byte[]{0x00, 0x00});
            out.write(data);
        }
    }
}
