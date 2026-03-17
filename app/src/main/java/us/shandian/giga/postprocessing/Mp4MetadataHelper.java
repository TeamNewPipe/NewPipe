/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.postprocessing;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.util.StreamInfoMetadataHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

public final class Mp4MetadataHelper {

    @Nullable
    final StreamInfo streamInfo;
    @Nullable final Bitmap thumbnail;
    @Nonnull final Supplier<Integer> auxOffset;
    @Nonnull final Consumer<byte[]> auxWriteBytes;
    @Nonnull final Function<Integer, Integer> lengthFor;
    public Mp4MetadataHelper(@Nonnull Supplier<Integer> auxOffset,
                             @Nonnull Consumer<byte[]> auxWriteBytes,
                             @Nonnull Function<Integer, Integer> lengthFor,
                             @Nullable final StreamInfo streamInfo,
                             @Nullable final Bitmap thumbnail) {
        this.auxOffset = auxOffset;
        this.auxWriteBytes = auxWriteBytes;
        this.lengthFor = lengthFor;
        this.streamInfo = streamInfo;
        this.thumbnail = thumbnail;
    }

    /**
     * Create the 'udta' box with metadata fields.
     * {@code udta} is a user data box that can contain various types of metadata,
     * including title, artist, date, and cover art.
     * @see <a href="https://developer.apple.com/documentation/quicktime-file-format/
     * user_data_atoms">Apple Quick Time Format Specification for user data atoms</a>
     * @see <a href="https://wiki.multimedia.cx/index.php?title=FFmpeg_Metadata
     * #QuickTime/MOV/MP4/M4A/et_al.">Multimedia Wiki FFmpeg Metadata</a>
     * @see <a href="https://atomicparsley.sourceforge.net/mpeg-4files.html">atomicparsley docs</a>
     * for a short and understandable reference about metadata keys and values
     * @throws IOException
     */
    public void makeUdta() throws IOException {
        if (streamInfo == null) {
            return;
        }

        // udta
        final int startUdta = auxOffset.get();
        auxWriteBytes.accept(ByteBuffer.allocate(8).putInt(0).putInt(0x75647461).array()); // "udta"

        // meta (full box: type + version/flags)
        final int startMeta = auxOffset.get();
        auxWriteBytes.accept(ByteBuffer.allocate(8).putInt(0).putInt(0x6D657461).array()); // "meta"
        auxWriteBytes.accept(ByteBuffer.allocate(4).putInt(0).array()); // version & flags = 0

        // hdlr inside meta
        auxWriteBytes.accept(makeMetaHdlr());

        // ilst container
        final int startIlst = auxOffset.get();
        auxWriteBytes.accept(ByteBuffer.allocate(8).putInt(0).putInt(0x696C7374).array()); // "ilst"

        // write metadata items

        final var metaHelper = new StreamInfoMetadataHelper(streamInfo);
        final String title = metaHelper.getTitle();
        final String artist = metaHelper.getArtist();
        final String date = metaHelper.getReleaseDate().getLocalDateTime()
                .toLocalDate().toString();
        @Nullable
        final String recordLabel = metaHelper.getRecordLabel();
        @Nullable
        final String copyright = metaHelper.getCopyright();

        writeMetaItem("©nam", title);
        writeMetaItem("©ART", artist);
        // this means 'year' in mp4 metadata, who the hell thought that?
        writeMetaItem("©day", date);

        if (recordLabel != null && !recordLabel.isEmpty()) {
            writeMetaItem("©lab", recordLabel);
        }
        if (copyright != null && !copyright.isEmpty()) {
            writeMetaItem("©cpy", copyright);
        }

        if (thumbnail != null) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            thumbnail.compress(Bitmap.CompressFormat.PNG, 100, baos);
            final byte[] imgBytes = baos.toByteArray();
            baos.close();
            // 0x0000000E = PNG type indicator for 'data' box (0x0D = JPEG)
            writeMetaCover(imgBytes, 0x0000000E);

        }

        // fix lengths
        lengthFor.apply(startIlst);
        lengthFor.apply(startMeta);
        lengthFor.apply(startUdta);

    }

    /**
     * Helper to write a metadata item inside the 'ilst' box.
     *
     * <pre>
     *     [size][key] [data_box]
     *     data_box = [size]["data"][type(4bytes)=1][locale(4bytes)=0][payload]
     * </pre>
     *
     * @param keyStr 4-char metadata key
     * @param value the metadata value
     * @throws IOException
     */
    private void writeMetaItem(final String keyStr, final String value) throws IOException {
        final byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);
        final byte[] keyBytes = keyStr.getBytes(StandardCharsets.ISO_8859_1);

        final int dataBoxSize = 16 + valBytes.length; // 4(size)+4("data")+4(type/locale)+payload
        final int itemBoxSize = 8 + dataBoxSize; // 4(size)+4(key)+dataBox

        final ByteBuffer buf = ByteBuffer.allocate(itemBoxSize);
        buf.putInt(itemBoxSize);
        // key (4 bytes)
        if (keyBytes.length == 4) {
            buf.put(keyBytes);
        } else {
            // fallback: pad or truncate
            final byte[] kb = new byte[4];
            System.arraycopy(keyBytes, 0, kb, 0, Math.min(keyBytes.length, 4));
            buf.put(kb);
        }

        // data box
        buf.putInt(dataBoxSize);
        buf.putInt(0x64617461); // "data"
        buf.putInt(0x00000001); // well-known type indicator (UTF-8)
        buf.putInt(0x00000000); // locale
        buf.put(valBytes);

        auxWriteBytes.accept(buf.array());
    }

    /**
     * Create a minimal hdlr box for the meta container.
     * The boxsize is fixed (33 bytes) as no name is provided.
     * @return byte array with the hdlr box
     */
    private byte[] makeMetaHdlr() {
        final ByteBuffer buf = ByteBuffer.allocate(33);
        buf.putInt(33);
        buf.putInt(0x68646C72); // "hdlr"
        buf.putInt(0x00000000); // pre-defined
        buf.putInt(0x6D646972); // "mdir" handler_type (metadata directory)
        buf.putInt(0x00000000); // subtype / reserved
        buf.put(new byte[12]);  // reserved
        buf.put((byte) 0x00);   // name (empty, null-terminated)
        return buf.array();
    }

    /**
     * Helper to add cover image inside the 'udta' box.
     * <p>
     * This method writes the 'covr' metadata item which contains the cover image.
     * The cover image is displayed as thumbnail in many media players and file managers.
     * </p>
     * <pre>
     *     [size][key] [data_box]
     *     data_box = [size]["data"][type(4bytes)][locale(4bytes)=0][payload]
     * </pre>
     *
     * @param imageData image byte data
     * @param dataType  type indicator: 0x0000000E = PNG, 0x0000000D = JPEG
     * @throws IOException
     */
    private void writeMetaCover(final byte[] imageData, final int dataType) throws IOException {
        if (imageData == null || imageData.length == 0) {
            return;
        }

        final byte[] keyBytes = "covr".getBytes(StandardCharsets.ISO_8859_1);

        // data box: 4(size) + 4("data") + 4(type) + 4(locale) + payload
        final int dataBoxSize = 16 + imageData.length;
        final int itemBoxSize = 8 + dataBoxSize;

        final ByteBuffer buf = ByteBuffer.allocate(itemBoxSize);
        buf.putInt(itemBoxSize);

        // key (4 chars)
        if (keyBytes.length == 4) {
            buf.put(keyBytes);
        } else {
            final byte[] kb = new byte[4];
            System.arraycopy(keyBytes, 0, kb, 0, Math.min(keyBytes.length, 4));
            buf.put(kb);
        }

        // data box
        buf.putInt(dataBoxSize);
        buf.putInt(0x64617461); // "data"
        buf.putInt(dataType);   // type indicator: 0x0000000E = PNG, 0x0000000D = JPEG
        buf.putInt(0x00000000); // locale
        buf.put(imageData);

        auxWriteBytes.accept(buf.array());
    }


}
