/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.postprocessing;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Postprocessing algorithm to insert metadata into an existing MP4 file
 * by modifying/adding the 'udta' box inside 'moov'.
 *
 * @see <a href="https://atomicparsley.sourceforge.net/mpeg-4files.html">
 *     https://atomicparsley.sourceforge.net/mpeg-4files.html</a> for a quick summary on
 *     the MP4 file format and its specification.
 * @see <a href="https://developer.apple.com/documentation/quicktime-file-format/">
 *     Apple Quick Time Format Specification</a> which is the basis for MP4 file format
 *     and contains detailed information about the structure of MP4 files.
 *  @see <a href="https://developer.apple.com/documentation/quicktime-file-format/
 *      * user_data_atoms">Apple Quick Time Format Specification for user data atoms (udta)</a>
 */
public class Mp4Metadata extends Postprocessing {

    Mp4Metadata() {
        super(false, true, ALGORITHM_MP4_METADATA);
    }

    @Override
    boolean test(SharpStream... sources) throws IOException {
        // nothing to do if metadata should not be embedded
        if (!embedMetadata) return false;

        // quick check: ensure there's at least one source and it looks like an MP4,
        // i.e. the file has a 'moov' box near the beginning.
        // THe 'udta' box is inserted inside 'moov', so if there's no 'moov' we can't do anything.
        if (sources == null || sources.length == 0 || sources[0] == null) return false;

        final SharpStream src = sources[0];
        try {
            src.rewind();

            // scan first few boxes until we find moov or reach a reasonable limit
            final int MAX_SCAN = 8 * 1024 * 1024; // 8 MiB
            int scanned = 0;

            while (scanned < MAX_SCAN) {
                // read header
                byte[] header = new byte[8];
                int r = readFully(src, header, 0, 8);
                if (r < 8) break;

                final int boxSize = ByteBuffer.wrap(header, 0, 4).getInt();
                final int boxType = ByteBuffer.wrap(header, 4, 4).getInt();

                if (boxType == 0x6D6F6F76) { // "moov"
                    return true;
                }

                long skip = (boxSize > 8) ? (boxSize - 8) : 0;
                // boxSize == 0 means extends to EOF -> stop scanning
                if (boxSize == 0) break;

                // attempt skip
                long skipped = src.skip(skip);
                if (skipped < skip) break;

                scanned += 8 + (int) skip;
            }

            return false;
        } finally {
            // best-effort rewind; ignore problems here
            try {
                src.rewind();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        if (sources == null || sources.length == 0) return OK_RESULT;

        final SharpStream src = sources[0];
        src.rewind();

        // helper buffer for copy
        final byte[] buf = new byte[64 * 1024];

        // copy until moov
        while (true) {
            // read header
            byte[] header = new byte[8];
            int h = readFully(src, header, 0, 8);
            if (h < 8) {
                // no more data, nothing to do
                return OK_RESULT;
            }

            final int boxSize = ByteBuffer.wrap(header, 0, 4).getInt();
            final int boxType = ByteBuffer.wrap(header, 4, 4).getInt();

            if (boxType != 0x6D6F6F76) { // not "moov" -> copy whole box
                // write header
                out.write(header);

                long remaining = (boxSize > 8) ? (boxSize - 8) : 0;
                if (boxSize == 0) {
                    // box extends to EOF: copy rest and return
                    int r;
                    while ((r = src.read(buf)) > 0) {
                        out.write(buf, 0, r);
                    }
                    return OK_RESULT;
                }

                while (remaining > 0) {
                    int read = src.read(buf, 0, (int) Math.min(buf.length, remaining));
                    if (read <= 0) break;
                    out.write(buf, 0, read);
                    remaining -= read;
                }

                continue;
            }

            // found moov. read full moov box into memory
            long moovSize = boxSize;
            boolean hasLargeSize = false;
            if (moovSize == 1) {
                // extended size: read 8 bytes
                byte[] ext = new byte[8];
                readFully(src, ext, 0, 8);
                moovSize = ByteBuffer.wrap(ext).getLong();
                hasLargeSize = true;
            }

            if (moovSize < 8) {
                // malformed
                return OK_RESULT;
            }

            final int toRead = (int) (moovSize - (hasLargeSize ? 16 : 8));
            final byte[] moovPayload = new byte[toRead];
            readFully(src, moovPayload, 0, toRead);

            // search for udta inside moov
            int udtaIndex = indexOfBox(moovPayload, 0x75647461); // "udta"

            if (udtaIndex < 0) {
                // no udta: build udta using helper and insert before first 'trak' atom
                byte[] udtaBytes = buildUdta();

                int insertPos = indexOfBox(moovPayload, 0x7472616B); // "trak"
                if (insertPos < 0) insertPos = moovPayload.length;

                byte[] newPayload = new byte[moovPayload.length + udtaBytes.length];
                System.arraycopy(moovPayload, 0, newPayload, 0, insertPos);
                System.arraycopy(udtaBytes, 0, newPayload, insertPos, udtaBytes.length);
                System.arraycopy(moovPayload, insertPos, newPayload, insertPos + udtaBytes.length,
                        moovPayload.length - insertPos);

                long newMoovSize = moovSize + udtaBytes.length;
                long delta = newMoovSize - moovSize;

                // adjust chunk offsets in the new payload so stco/co64 entries point to correct mdat offsets
                adjustChunkOffsetsRecursive(newPayload, 0, newPayload.length, delta);

                // write updated moov header
                if (hasLargeSize) {
                    out.write(intToBytes(1));
                    out.write(intToBytes(0x6D6F6F76)); // "moov"
                    out.write(longToBytes(newMoovSize));
                } else {
                    out.write(intToBytes((int) newMoovSize));
                    out.write(intToBytes(0x6D6F6F76)); // "moov"
                }

                out.write(newPayload);

            } else {
                // udta exists: replace the existing udta box with newly built udta
                // determine old udta size (support extended size and size==0 -> till end of moov)
                if (udtaIndex + 8 > moovPayload.length) {
                    // malformed; just write original and continue
                    if (hasLargeSize) {
                        out.write(intToBytes(1));
                        out.write(intToBytes(0x6D6F6F76)); // "moov"
                        out.write(longToBytes(moovSize));
                    } else {
                        out.write(intToBytes((int) moovSize));
                        out.write(intToBytes(0x6D6F6F76)); // "moov"
                    }
                    out.write(moovPayload);
                } else {
                    int sizeField = readUInt32(moovPayload, udtaIndex);
                    long oldUdtaSize;
                    if (sizeField == 1) {
                        // extended
                        if (udtaIndex + 16 > moovPayload.length) {
                            oldUdtaSize = ((long) moovPayload.length) - udtaIndex; // fallback
                        } else {
                            oldUdtaSize = readUInt64(moovPayload, udtaIndex + 8);
                        }
                    } else if (sizeField == 0) {
                        // until end of file/moov
                        oldUdtaSize = ((long) moovPayload.length) - udtaIndex;
                    } else {
                        oldUdtaSize = sizeField & 0xFFFFFFFFL;
                    }

                    // compute the integer length (bounded by remaining payload)
                    int oldUdtaIntLen = (int) Math.min(oldUdtaSize, (moovPayload.length - udtaIndex));

                    // build new udta
                    byte[] newUdta = buildUdta();

                    // If new udta fits into old udta area, overwrite in place and keep moov size unchanged
                    if (newUdta.length <= oldUdtaIntLen) {
                        byte[] newPayload = new byte[moovPayload.length];
                        // copy prefix
                        System.arraycopy(moovPayload, 0, newPayload, 0, udtaIndex);
                        // copy new udta
                        System.arraycopy(newUdta, 0, newPayload, udtaIndex, newUdta.length);
                        // pad remaining old udta space with zeros
                        int padStart = udtaIndex + newUdta.length;
                        int padLen = oldUdtaIntLen - newUdta.length;
                        if (padLen > 0) {
                            Arrays.fill(newPayload, padStart, padStart + padLen, (byte) 0);
                        }
                        // copy suffix
                        int suffixStart = udtaIndex + oldUdtaIntLen;
                        System.arraycopy(moovPayload, suffixStart, newPayload, udtaIndex + oldUdtaIntLen,
                                moovPayload.length - suffixStart);

                        // moovSize unchanged
                        if (hasLargeSize) {
                            out.write(intToBytes(1));
                            out.write(intToBytes(0x6D6F6F76));
                            out.write(longToBytes(moovSize));
                        } else {
                            out.write(intToBytes((int) moovSize));
                            out.write(intToBytes(0x6D6F6F76));
                        }
                        out.write(newPayload);

                    } else {
                        // construct new moov payload by replacing the old udta region (previous behavior)
                        int newPayloadLen = moovPayload.length - oldUdtaIntLen + newUdta.length;
                        byte[] newPayload = new byte[newPayloadLen];

                        // copy prefix
                        System.arraycopy(moovPayload, 0, newPayload, 0, udtaIndex);
                        // copy new udta
                        System.arraycopy(newUdta, 0, newPayload, udtaIndex, newUdta.length);
                        // copy suffix
                        int suffixStart = udtaIndex + oldUdtaIntLen;
                        System.arraycopy(moovPayload, suffixStart, newPayload, udtaIndex + newUdta.length,
                                moovPayload.length - suffixStart);

                        long newMoovSize = moovSize - oldUdtaSize + newUdta.length;
                        long delta = newMoovSize - moovSize;

                        // adjust chunk offsets in the new payload so stco/co64 entries point to correct mdat offsets
                        adjustChunkOffsetsRecursive(newPayload, 0, newPayload.length, delta);

                        // write updated moov header
                        if (hasLargeSize) {
                            out.write(intToBytes(1));
                            out.write(intToBytes(0x6D6F6F76)); // "moov"
                            out.write(longToBytes(newMoovSize));
                        } else {
                            out.write(intToBytes((int) newMoovSize));
                            out.write(intToBytes(0x6D6F6F76)); // "moov"
                        }

                        out.write(newPayload);
                    }
                }
            }

            // copy rest of file
            int r;
            while ((r = src.read(buf)) > 0) {
                out.write(buf, 0, r);
            }

            return OK_RESULT;
        }
    }

    private void adjustChunkOffsetsRecursive(byte[] payload, int start,
                                             int length, long delta) throws IOException {
        int idx = start;
        final int end = start + length;
        while (idx + 8 <= end) {
            int boxSize = readUInt32(payload, idx);
            int boxType = readUInt32(payload, idx + 4);

            if (boxSize == 0) {
                // box extends to end of parent
                boxSize = end - idx;
            } else if (boxSize < 0) {
                break;
            }

            int headerLen = 8;
            long declaredSize = ((long) boxSize) & 0xFFFFFFFFL;
            if (boxSize == 1) {
                // extended size
                if (idx + 16 > end) break;
                declaredSize = readUInt64(payload, idx + 8);
                headerLen = 16;
            }

            int contentStart = idx + headerLen;
            int contentLen = (int) (declaredSize - headerLen);
            if (contentLen < 0 || contentStart + contentLen > end) {
                // invalid, stop
                break;
            }

            if (boxType == 0x7374636F) { // 'stco'
                // version/flags(4) entry_count(4) entries
                int entryCountOff = contentStart + 4;
                if (entryCountOff + 4 > end) return;
                int count = readUInt32(payload, entryCountOff);
                int entriesStart = entryCountOff + 4;
                for (int i = 0; i < count; i++) {
                    int entryOff = entriesStart + i * 4;
                    if (entryOff + 4 > end) break;
                    long val = ((long) readUInt32(payload, entryOff)) & 0xFFFFFFFFL;
                    long newVal = val + delta;
                    if (newVal < 0 || newVal > 0xFFFFFFFFL) {
                        throw new IOException("stco entry overflow after applying delta");
                    }
                    putUInt32(payload, entryOff, (int) newVal);
                }
            } else if (boxType == 0x636F3634) { // 'co64'
                int entryCountOff = contentStart + 4;
                if (entryCountOff + 4 > end) return;
                int count = readUInt32(payload, entryCountOff);
                int entriesStart = entryCountOff + 4;
                for (int i = 0; i < count; i++) {
                    int entryOff = entriesStart + i * 8;
                    if (entryOff + 8 > end) break;
                    long val = readUInt64(payload, entryOff);
                    long newVal = val + delta;
                    putUInt64(payload, entryOff, newVal);
                }
            } else {
                // recurse into container boxes
                if (contentLen >= 8) {
                    adjustChunkOffsetsRecursive(payload, contentStart, contentLen, delta);
                }
            }

            idx += (int) declaredSize;
        }
    }

    private static int readUInt32(byte[] buf, int off) {
        return ((buf[off] & 0xFF) << 24) | ((buf[off + 1] & 0xFF) << 16)
                | ((buf[off + 2] & 0xFF) << 8) | (buf[off + 3] & 0xFF);
    }

    private static long readUInt64(byte[] buf, int off) {
        return ((long) readUInt32(buf, off) << 32) | ((long) readUInt32(buf, off + 4) & 0xFFFFFFFFL);
    }

    private static void putUInt32(byte[] buf, int off, int v) {
        buf[off] = (byte) ((v >>> 24) & 0xFF);
        buf[off + 1] = (byte) ((v >>> 16) & 0xFF);
        buf[off + 2] = (byte) ((v >>> 8) & 0xFF);
        buf[off + 3] = (byte) (v & 0xFF);
    }

    private static void putUInt64(byte[] buf, int off, long v) {
        putUInt32(buf, off, (int) ((v >>> 32) & 0xFFFFFFFFL));
        putUInt32(buf, off + 4, (int) (v & 0xFFFFFFFFL));
    }

    private static int readFully(SharpStream in, byte[] buf, int off, int len) throws IOException {
        int readTotal = 0;
        while (readTotal < len) {
            int r = in.read(buf, off + readTotal, len - readTotal);
            if (r <= 0) break;
            readTotal += r;
        }
        return readTotal;
    }

    private static int indexOfBox(byte[] payload, int boxType) {
        int idx = 0;
        while (idx + 8 <= payload.length) {
            int size = readUInt32(payload, idx);
            int type = readUInt32(payload, idx + 4);
            if (type == boxType) return idx;
            if (size <= 0) break;
            idx += size;
        }
        return -1;
    }

    private static byte[] intToBytes(int v) {
        return ByteBuffer.allocate(4).putInt(v).array();
    }

    private static byte[] longToBytes(long v) {
        return ByteBuffer.allocate(8).putLong(v).array();
    }

    /**
     * Build udta bytes using {@link Mp4MetadataHelper}.
     */
    private byte[] buildUdta() throws IOException {
        final GrowableByteArray aux = new GrowableByteArray(Math.max(64 * 1024, 256 * 1024));

        final Mp4MetadataHelper helper = new Mp4MetadataHelper(
                aux::position,
                aux::put,
                offset -> {
                    int size = aux.position() - offset;
                    aux.putInt(offset, size);
                    return size;
                },
                streamInfo,
                thumbnail
        );

        helper.makeUdta();

        return aux.toByteArray();
    }

    /**
     * Small growable byte array helper with minimal random-access putInt support
     */
    private static final class GrowableByteArray {
        private byte[] buf;
        private int pos = 0;

        GrowableByteArray(int initial) {
            buf = new byte[initial];
        }

        int position() { return pos; }

        void put(byte[] data) {
            ensureCapacity(pos + data.length);
            System.arraycopy(data, 0, buf, pos, data.length);
            pos += data.length;
        }

        void putInt(int offset, int value) {
            ensureCapacity(offset + 4);
            buf[offset] = (byte) ((value >>> 24) & 0xff);
            buf[offset + 1] = (byte) ((value >>> 16) & 0xff);
            buf[offset + 2] = (byte) ((value >>> 8) & 0xff);
            buf[offset + 3] = (byte) (value & 0xff);
        }

        private void ensureCapacity(int min) {
            if (min <= buf.length) return;
            int newCap = buf.length * 2;
            while (newCap < min) newCap *= 2;
            buf = Arrays.copyOf(buf, newCap);
        }

        byte[] toByteArray() {
            return Arrays.copyOf(buf, pos);
        }
    }

}
