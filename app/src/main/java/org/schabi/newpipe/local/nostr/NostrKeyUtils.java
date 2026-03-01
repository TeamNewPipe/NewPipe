package org.schabi.newpipe.local.nostr;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.engines.ChaCha7539Engine;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.math.ec.ECPoint;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NostrKeyUtils {
    private static final String BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int[] BECH32_GENERATOR = {
            0x3b6a57b2,
            0x26508e6d,
            0x1ea119fa,
            0x3d4233dd,
            0x2a1462b3
    };
    private static final Pattern NSEC_PATTERN = Pattern.compile(
            "(nsec1[" + BECH32_CHARSET + "]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]+$");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final QRCodeWriter QR_CODE_WRITER = new QRCodeWriter();
    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final BigInteger CURVE_ORDER =
            CURVE_PARAMS == null ? BigInteger.ZERO : CURVE_PARAMS.getN();

    private NostrKeyUtils() {
    }

    @NonNull
    static NostrIdentity generateIdentity() {
        final byte[] privateKey = generatePrivateKeyBytes();
        final byte[] publicKey = deriveXOnlyPublicKey(privateKey);

        final String nsec = encodeBech32("nsec", convertBits(privateKey, 8, 5, true));
        final String npub = encodeBech32("npub", convertBits(publicKey, 8, 5, true));
        return new NostrIdentity(nsec, npub);
    }

    @NonNull
    static NostrIdentity fromScannedNsec(@Nullable final String rawNsec) {
        if (rawNsec == null || rawNsec.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing nsec value");
        }

        final String candidate = extractNsecCandidate(rawNsec);
        final DecodedBech32 decoded = decodeBech32(candidate);
        if (!"nsec".equals(decoded.hrp)) {
            throw new IllegalArgumentException("QR code does not contain nsec");
        }

        final byte[] privateKey = convertBits(decoded.data, 5, 8, false);
        validatePrivateKey(privateKey);
        final byte[] publicKey = deriveXOnlyPublicKey(privateKey);

        final String nsec = encodeBech32("nsec", convertBits(privateKey, 8, 5, true));
        final String npub = encodeBech32("npub", convertBits(publicKey, 8, 5, true));
        return new NostrIdentity(nsec, npub);
    }

    @NonNull
    static String toNpub(@Nullable final String rawPublicKey) {
        if (rawPublicKey == null || rawPublicKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing public key value");
        }

        String candidate = rawPublicKey.trim();
        if (candidate.toLowerCase(Locale.US).startsWith("nostr:")) {
            candidate = candidate.substring("nostr:".length());
        }
        final String normalized = candidate.toLowerCase(Locale.US);

        if (normalized.startsWith("npub1")) {
            final DecodedBech32 decoded = decodeBech32(normalized);
            if (!"npub".equals(decoded.hrp)) {
                throw new IllegalArgumentException("Expected npub key");
            }
            final byte[] publicKey = convertBits(decoded.data, 5, 8, false);
            if (publicKey.length != 32) {
                throw new IllegalArgumentException("Invalid public key length");
            }
            return encodeBech32("npub", convertBits(publicKey, 8, 5, true));
        }

        final byte[] publicKey = hexToBytes(normalized);
        if (publicKey.length != 32) {
            throw new IllegalArgumentException("Invalid public key length");
        }
        return encodeBech32("npub", convertBits(publicKey, 8, 5, true));
    }

    @NonNull
    static String toPublicKeyHex(@Nullable final String rawPublicKey) {
        if (rawPublicKey == null || rawPublicKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing public key value");
        }

        String candidate = rawPublicKey.trim();
        if (candidate.toLowerCase(Locale.US).startsWith("nostr:")) {
            candidate = candidate.substring("nostr:".length());
        }
        final String normalized = candidate.toLowerCase(Locale.US);

        if (normalized.startsWith("npub1")) {
            final DecodedBech32 decoded = decodeBech32(normalized);
            if (!"npub".equals(decoded.hrp)) {
                throw new IllegalArgumentException("Expected npub key");
            }
            final byte[] publicKey = convertBits(decoded.data, 5, 8, false);
            if (publicKey.length != 32) {
                throw new IllegalArgumentException("Invalid public key length");
            }
            return bytesToHex(publicKey);
        }

        final byte[] publicKey = hexToBytes(normalized);
        if (publicKey.length != 32) {
            throw new IllegalArgumentException("Invalid public key length");
        }
        return bytesToHex(publicKey);
    }

    @NonNull
    static byte[] decodeNsecPrivateKey(@Nullable final String nsec) {
        if (nsec == null || nsec.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing nsec value");
        }
        final String candidate = extractNsecCandidate(nsec);
        final DecodedBech32 decoded = decodeBech32(candidate);
        if (!"nsec".equals(decoded.hrp)) {
            throw new IllegalArgumentException("Expected nsec key");
        }

        final byte[] privateKey = convertBits(decoded.data, 5, 8, false);
        validatePrivateKey(privateKey);
        return privateKey;
    }

    @NonNull
    static String derivePublicKeyHexFromNsec(@NonNull final String nsec) {
        final byte[] privateKey = decodeNsecPrivateKey(nsec);
        final byte[] publicKey = deriveXOnlyPublicKey(privateKey);
        return bytesToHex(publicKey);
    }

    @NonNull
    static String signEventId(@NonNull final String nsec, @NonNull final byte[] messageHash) {
        if (messageHash.length != 32) {
            throw new IllegalArgumentException("Event hash must be 32 bytes");
        }
        final byte[] privateKey = decodeNsecPrivateKey(nsec);
        return signSchnorrBip340(privateKey, messageHash);
    }

    @NonNull
    static String encryptNip44(@NonNull final String nsec,
                               @NonNull final String recipientPubKeyHex,
                               @NonNull final String plainText) {
        final byte[] privateKey = decodeNsecPrivateKey(nsec);
        final byte[] sharedSecret = deriveSharedSecret(privateKey, recipientPubKeyHex);
        final byte[] conversationKey = hkdfExtract(
                sharedSecret,
                "nip44-v2".getBytes(StandardCharsets.UTF_8)
        );
        final byte[] nonce = new byte[32];
        SECURE_RANDOM.nextBytes(nonce);

        final MessageKeys keys = deriveMessageKeys(conversationKey, nonce);
        final byte[] padded = padNip44(plainText.getBytes(StandardCharsets.UTF_8));
        final byte[] cipherText = chacha20(keys.chachaKey, keys.chachaNonce, padded);
        final byte[] mac = hmacSha256(keys.hmacKey, concatenate(nonce, cipherText));

        final byte[] payload = concatenate(
                new byte[]{0x02},
                nonce,
                cipherText,
                mac
        );
        return Base64.encodeToString(payload, Base64.NO_WRAP);
    }

    @NonNull
    static String decryptNip44(@NonNull final String nsec,
                               @NonNull final String senderPubKeyHex,
                               @NonNull final String payload) {
        if (payload.isEmpty() || payload.charAt(0) == '#') {
            throw new IllegalArgumentException("Unsupported NIP-44 payload version");
        }
        if (payload.length() < 132 || payload.length() > 87472) {
            throw new IllegalArgumentException("Invalid NIP-44 payload length");
        }

        final byte[] decoded = Base64.decode(payload, Base64.DEFAULT);
        if (decoded.length < 99 || decoded.length > 65603) {
            throw new IllegalArgumentException("Invalid NIP-44 decoded payload length");
        }
        if ((decoded[0] & 0xff) != 2) {
            throw new IllegalArgumentException("Unsupported NIP-44 payload version");
        }

        final byte[] nonce = Arrays.copyOfRange(decoded, 1, 33);
        final byte[] cipherText = Arrays.copyOfRange(decoded, 33, decoded.length - 32);
        final byte[] receivedMac = Arrays.copyOfRange(decoded, decoded.length - 32, decoded.length);

        final byte[] privateKey = decodeNsecPrivateKey(nsec);
        final byte[] sharedSecret = deriveSharedSecret(privateKey, senderPubKeyHex);
        final byte[] conversationKey = hkdfExtract(
                sharedSecret,
                "nip44-v2".getBytes(StandardCharsets.UTF_8)
        );
        final MessageKeys keys = deriveMessageKeys(conversationKey, nonce);
        final byte[] expectedMac = hmacSha256(keys.hmacKey, concatenate(nonce, cipherText));
        if (!MessageDigest.isEqual(expectedMac, receivedMac)) {
            throw new IllegalArgumentException("Invalid NIP-44 MAC");
        }

        final byte[] padded = chacha20(keys.chachaKey, keys.chachaNonce, cipherText);
        final byte[] unpadded = unpadNip44(padded);
        return new String(unpadded, StandardCharsets.UTF_8);
    }

    @NonNull
    static Bitmap generateQrCode(@NonNull final String text, final int sizePx) {
        try {
            final BitMatrix matrix = QR_CODE_WRITER.encode(
                    text, BarcodeFormat.QR_CODE, sizePx, sizePx
            );
            final Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565);
            for (int y = 0; y < sizePx; y++) {
                for (int x = 0; x < sizePx; x++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (final WriterException e) {
            throw new IllegalArgumentException("Could not generate QR code", e);
        }
    }

    private static void validatePrivateKey(@NonNull final byte[] privateKeyBytes) {
        ensureCurveAvailable();
        if (privateKeyBytes.length != 32) {
            throw new IllegalArgumentException("Invalid nsec key length");
        }
        final BigInteger privateKey = new BigInteger(1, privateKeyBytes);
        if (privateKey.signum() == 0 || privateKey.compareTo(CURVE_ORDER) >= 0) {
            throw new IllegalArgumentException("Invalid nsec key value");
        }
    }

    @NonNull
    private static byte[] generatePrivateKeyBytes() {
        ensureCurveAvailable();
        final byte[] candidate = new byte[32];
        while (true) {
            SECURE_RANDOM.nextBytes(candidate);
            final BigInteger value = new BigInteger(1, candidate);
            if (value.signum() > 0 && value.compareTo(CURVE_ORDER) < 0) {
                return candidate;
            }
        }
    }

    @NonNull
    private static byte[] deriveXOnlyPublicKey(@NonNull final byte[] privateKeyBytes) {
        ensureCurveAvailable();

        final BigInteger privateKey = new BigInteger(1, privateKeyBytes);
        final ECPoint publicPoint = CURVE_PARAMS.getG().multiply(privateKey).normalize();
        return toFixedSizeBytes(publicPoint.getAffineXCoord().toBigInteger(), 32);
    }

    @NonNull
    private static String signSchnorrBip340(@NonNull final byte[] privateKeyBytes,
                                            @NonNull final byte[] messageHash) {
        ensureCurveAvailable();
        validatePrivateKey(privateKeyBytes);
        if (messageHash.length != 32) {
            throw new IllegalArgumentException("Schnorr message must be 32 bytes");
        }

        final BigInteger d0 = new BigInteger(1, privateKeyBytes);
        final ECPoint publicPoint = CURVE_PARAMS.getG().multiply(d0).normalize();
        final BigInteger d = publicPoint.getAffineYCoord().toBigInteger().testBit(0)
                ? CURVE_ORDER.subtract(d0)
                : d0;
        final byte[] publicKeyX = toFixedSizeBytes(
                publicPoint.getAffineXCoord().toBigInteger(),
                32
        );

        final byte[] auxRand = new byte[32];
        SECURE_RANDOM.nextBytes(auxRand);
        final byte[] dBytes = toFixedSizeBytes(d, 32);
        final byte[] auxHash = taggedHash("BIP0340/aux", auxRand);
        final byte[] t = xor(dBytes, auxHash);

        final byte[] nonceInput = concatenate(t, publicKeyX, messageHash);
        final BigInteger k0 = new BigInteger(1, taggedHash("BIP0340/nonce", nonceInput))
                .mod(CURVE_ORDER);
        if (k0.signum() == 0) {
            throw new IllegalStateException("Invalid Schnorr nonce");
        }

        final ECPoint noncePoint = CURVE_PARAMS.getG().multiply(k0).normalize();
        final BigInteger k = noncePoint.getAffineYCoord().toBigInteger().testBit(0)
                ? CURVE_ORDER.subtract(k0)
                : k0;
        final byte[] nonceX = toFixedSizeBytes(noncePoint.getAffineXCoord().toBigInteger(), 32);

        final byte[] challengeInput = concatenate(nonceX, publicKeyX, messageHash);
        final BigInteger challenge = new BigInteger(
                1, taggedHash("BIP0340/challenge", challengeInput)
        ).mod(CURVE_ORDER);

        final BigInteger s = k.add(challenge.multiply(d)).mod(CURVE_ORDER);
        final byte[] signature = concatenate(nonceX, toFixedSizeBytes(s, 32));
        return bytesToHex(signature);
    }

    @NonNull
    private static byte[] deriveSharedSecret(@NonNull final byte[] privateKeyBytes,
                                             @NonNull final String pubKeyHex) {
        ensureCurveAvailable();
        validatePrivateKey(privateKeyBytes);
        final ECPoint publicPoint = decodePublicPoint(pubKeyHex);
        final BigInteger privateKey = new BigInteger(1, privateKeyBytes);
        final ECPoint sharedPoint = publicPoint.multiply(privateKey).normalize();
        if (sharedPoint.isInfinity()) {
            throw new IllegalArgumentException("Invalid shared point");
        }
        return toFixedSizeBytes(sharedPoint.getAffineXCoord().toBigInteger(), 32);
    }

    @NonNull
    private static MessageKeys deriveMessageKeys(@NonNull final byte[] conversationKey,
                                                 @NonNull final byte[] nonce) {
        if (conversationKey.length != 32) {
            throw new IllegalArgumentException("Invalid NIP-44 conversation key length");
        }
        if (nonce.length != 32) {
            throw new IllegalArgumentException("Invalid NIP-44 nonce length");
        }

        final byte[] expanded = hkdfExpand(conversationKey, nonce, 76);
        final byte[] chachaKey = Arrays.copyOfRange(expanded, 0, 32);
        final byte[] chachaNonce = Arrays.copyOfRange(expanded, 32, 44);
        final byte[] hmacKey = Arrays.copyOfRange(expanded, 44, 76);
        return new MessageKeys(chachaKey, chachaNonce, hmacKey);
    }

    @NonNull
    private static byte[] hkdfExtract(@NonNull final byte[] ikm, @NonNull final byte[] salt) {
        return hmacSha256(salt, ikm);
    }

    @NonNull
    private static byte[] hkdfExpand(@NonNull final byte[] prk,
                                     @NonNull final byte[] info,
                                     final int outputLength) {
        if (outputLength <= 0 || outputLength > 255 * 32) {
            throw new IllegalArgumentException("Invalid HKDF output length");
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream(outputLength);
        byte[] previous = new byte[0];
        int counter = 1;
        while (output.size() < outputLength) {
            final byte[] blockInput = concatenate(previous, info, new byte[]{(byte) counter});
            previous = hmacSha256(prk, blockInput);
            final int remaining = outputLength - output.size();
            output.write(previous, 0, Math.min(previous.length, remaining));
            counter++;
        }
        return output.toByteArray();
    }

    @NonNull
    private static byte[] hmacSha256(@NonNull final byte[] key, @NonNull final byte[] message) {
        final HMac hMac = new HMac(new SHA256Digest());
        hMac.init(new KeyParameter(key));
        hMac.update(message, 0, message.length);
        final byte[] output = new byte[hMac.getMacSize()];
        hMac.doFinal(output, 0);
        return output;
    }

    @NonNull
    private static byte[] chacha20(@NonNull final byte[] key,
                                   @NonNull final byte[] nonce,
                                   @NonNull final byte[] input) {
        if (key.length != 32 || nonce.length != 12) {
            throw new IllegalArgumentException("Invalid ChaCha20 key or nonce length");
        }
        final ChaCha7539Engine engine = new ChaCha7539Engine();
        engine.init(true, new ParametersWithIV(new KeyParameter(key), nonce));

        final byte[] output = new byte[input.length];
        engine.processBytes(input, 0, input.length, output, 0);
        return output;
    }

    @NonNull
    private static byte[] padNip44(@NonNull final byte[] plaintext) {
        final int unpaddedLength = plaintext.length;
        if (unpaddedLength < 1 || unpaddedLength > 65535) {
            throw new IllegalArgumentException("Invalid NIP-44 plaintext length");
        }

        final int paddedLength = calculatePaddedLength(unpaddedLength);
        final byte[] output = new byte[2 + paddedLength];
        output[0] = (byte) ((unpaddedLength >>> 8) & 0xff);
        output[1] = (byte) (unpaddedLength & 0xff);
        System.arraycopy(plaintext, 0, output, 2, unpaddedLength);
        return output;
    }

    @NonNull
    private static byte[] unpadNip44(@NonNull final byte[] padded) {
        if (padded.length < 34) {
            throw new IllegalArgumentException("Invalid NIP-44 padded payload");
        }
        final int unpaddedLength = ((padded[0] & 0xff) << 8) | (padded[1] & 0xff);
        if (unpaddedLength < 1) {
            throw new IllegalArgumentException("Invalid NIP-44 unpadded length");
        }
        final int expectedLength = 2 + calculatePaddedLength(unpaddedLength);
        if (padded.length != expectedLength || 2 + unpaddedLength > padded.length) {
            throw new IllegalArgumentException("Invalid NIP-44 padding size");
        }
        return Arrays.copyOfRange(padded, 2, 2 + unpaddedLength);
    }

    private static int calculatePaddedLength(final int unpaddedLength) {
        if (unpaddedLength <= 32) {
            return 32;
        }

        final int nextPower = 1 << (32 - Integer.numberOfLeadingZeros(unpaddedLength - 1));
        final int chunk = nextPower <= 256 ? 32 : nextPower / 8;
        return chunk * ((unpaddedLength - 1) / chunk + 1);
    }

    @NonNull
    private static ECPoint decodePublicPoint(@NonNull final String rawPubKeyHex) {
        ensureCurveAvailable();
        final byte[] pubKeyBytes = hexToBytes(rawPubKeyHex);
        if (pubKeyBytes.length == 32) {
            final byte[] compressed = new byte[33];
            compressed[0] = 0x02;
            System.arraycopy(pubKeyBytes, 0, compressed, 1, 32);
            return CURVE_PARAMS.getCurve().decodePoint(compressed).normalize();
        }
        if (pubKeyBytes.length == 33 || pubKeyBytes.length == 65) {
            return CURVE_PARAMS.getCurve().decodePoint(pubKeyBytes).normalize();
        }
        throw new IllegalArgumentException("Invalid public key format");
    }

    private static void ensureCurveAvailable() {
        if (CURVE_PARAMS == null || CURVE_ORDER.equals(BigInteger.ZERO)) {
            throw new IllegalStateException("secp256k1 curve unavailable");
        }
    }

    @NonNull
    private static byte[] toFixedSizeBytes(@NonNull final BigInteger value, final int size) {
        final byte[] raw = value.toByteArray();
        if (raw.length == size) {
            return raw;
        }

        final byte[] output = new byte[size];
        if (raw.length > size) {
            System.arraycopy(raw, raw.length - size, output, 0, size);
        } else {
            System.arraycopy(raw, 0, output, size - raw.length, raw.length);
        }
        return output;
    }

    @NonNull
    private static String extractNsecCandidate(@NonNull final String input) {
        final String normalized = input.trim().toLowerCase(Locale.US);
        final String withoutPrefix = normalized.startsWith("nostr:")
                ? normalized.substring("nostr:".length())
                : normalized;

        if (withoutPrefix.startsWith("nsec1")) {
            return withoutPrefix;
        }

        final Matcher matcher = NSEC_PATTERN.matcher(withoutPrefix);
        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new IllegalArgumentException("No nsec key found in QR code");
    }

    @NonNull
    private static byte[] hexToBytes(@NonNull final String rawHex) {
        final String hex = rawHex.startsWith("0x") ? rawHex.substring(2) : rawHex;
        if (hex.isEmpty() || (hex.length() & 1) != 0 || !HEX_PATTERN.matcher(hex).matches()) {
            throw new IllegalArgumentException("Invalid hex public key");
        }

        final byte[] output = new byte[hex.length() / 2];
        for (int i = 0; i < output.length; i++) {
            final int high = Character.digit(hex.charAt(i * 2), 16);
            final int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            output[i] = (byte) ((high << 4) | low);
        }
        return output;
    }

    @NonNull
    private static String bytesToHex(@NonNull final byte[] bytes) {
        final char[] hexChars = new char[bytes.length * 2];
        final char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            final int value = bytes[i] & 0xff;
            hexChars[i * 2] = alphabet[value >>> 4];
            hexChars[i * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(hexChars);
    }

    @NonNull
    private static byte[] taggedHash(@NonNull final String tag, @NonNull final byte[] data) {
        final byte[] tagHash = sha256(tag.getBytes(StandardCharsets.UTF_8));
        return sha256(concatenate(tagHash, tagHash, data));
    }

    @NonNull
    private static byte[] sha256(@NonNull final byte[] data) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @NonNull
    private static byte[] xor(@NonNull final byte[] left, @NonNull final byte[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Mismatched xor input lengths");
        }
        final byte[] output = new byte[left.length];
        for (int i = 0; i < left.length; i++) {
            output[i] = (byte) (left[i] ^ right[i]);
        }
        return output;
    }

    @NonNull
    private static String encodeBech32(@NonNull final String hrp, @NonNull final byte[] data) {
        final String lowerHrp = hrp.toLowerCase(Locale.US);
        final byte[] checksum = createChecksum(lowerHrp, data);
        final StringBuilder builder = new StringBuilder(
                lowerHrp.length() + 1 + data.length + checksum.length
        );
        builder.append(lowerHrp).append('1');

        for (final byte value : data) {
            builder.append(BECH32_CHARSET.charAt(value));
        }
        for (final byte value : checksum) {
            builder.append(BECH32_CHARSET.charAt(value));
        }
        return builder.toString();
    }

    @NonNull
    private static DecodedBech32 decodeBech32(@NonNull final String value) {
        final String bech32 = value.trim().toLowerCase(Locale.US);
        final int separatorIndex = bech32.lastIndexOf('1');
        if (separatorIndex < 1 || separatorIndex + 7 > bech32.length()) {
            throw new IllegalArgumentException("Invalid bech32 value");
        }

        final String hrp = bech32.substring(0, separatorIndex);
        final byte[] data = new byte[bech32.length() - separatorIndex - 1];
        for (int i = 0; i < data.length; i++) {
            final int charIndex = BECH32_CHARSET.indexOf(bech32.charAt(separatorIndex + 1 + i));
            if (charIndex < 0) {
                throw new IllegalArgumentException("Invalid bech32 characters");
            }
            data[i] = (byte) charIndex;
        }

        if (!verifyChecksum(hrp, data)) {
            throw new IllegalArgumentException("Invalid bech32 checksum");
        }

        return new DecodedBech32(hrp, Arrays.copyOf(data, data.length - 6));
    }

    @NonNull
    private static byte[] createChecksum(@NonNull final String hrp, @NonNull final byte[] data) {
        final byte[] values = concatenate(hrpExpand(hrp), data, new byte[6]);
        final int polymod = bech32Polymod(values) ^ 1;

        final byte[] checksum = new byte[6];
        for (int i = 0; i < 6; i++) {
            checksum[i] = (byte) ((polymod >> (5 * (5 - i))) & 0x1f);
        }
        return checksum;
    }

    private static boolean verifyChecksum(@NonNull final String hrp, @NonNull final byte[] data) {
        return bech32Polymod(concatenate(hrpExpand(hrp), data)) == 1;
    }

    @NonNull
    private static byte[] hrpExpand(@NonNull final String hrp) {
        final byte[] output = new byte[hrp.length() * 2 + 1];
        for (int i = 0; i < hrp.length(); i++) {
            output[i] = (byte) (hrp.charAt(i) >> 5);
            output[i + hrp.length() + 1] = (byte) (hrp.charAt(i) & 0x1f);
        }
        output[hrp.length()] = 0;
        return output;
    }

    private static int bech32Polymod(@NonNull final byte[] values) {
        int checksum = 1;
        for (final byte value : values) {
            final int top = checksum >>> 25;
            checksum = (checksum & 0x1ffffff) << 5 ^ (value & 0xff);
            for (int i = 0; i < 5; i++) {
                if (((top >>> i) & 1) != 0) {
                    checksum ^= BECH32_GENERATOR[i];
                }
            }
        }
        return checksum;
    }

    @NonNull
    private static byte[] concatenate(@NonNull final byte[]... arrays) {
        int totalLength = 0;
        for (final byte[] array : arrays) {
            totalLength += array.length;
        }

        final byte[] output = new byte[totalLength];
        int position = 0;
        for (final byte[] array : arrays) {
            System.arraycopy(array, 0, output, position, array.length);
            position += array.length;
        }
        return output;
    }

    @NonNull
    private static byte[] convertBits(@NonNull final byte[] data,
                                      final int fromBits,
                                      final int toBits,
                                      final boolean pad) {
        final int maxValue = (1 << toBits) - 1;
        int accumulator = 0;
        int bits = 0;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        for (final byte value : data) {
            final int intValue = value & 0xff;
            if ((intValue >>> fromBits) != 0) {
                throw new IllegalArgumentException("Input value exceeds bit size");
            }

            accumulator = (accumulator << fromBits) | intValue;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                output.write((accumulator >>> bits) & maxValue);
            }
        }

        if (pad) {
            if (bits > 0) {
                output.write((accumulator << (toBits - bits)) & maxValue);
            }
        } else if (bits >= fromBits || ((accumulator << (toBits - bits)) & maxValue) != 0) {
            throw new IllegalArgumentException("Invalid padding in bech32 value");
        }

        return output.toByteArray();
    }

    private static final class MessageKeys {
        @NonNull
        final byte[] chachaKey;
        @NonNull
        final byte[] chachaNonce;
        @NonNull
        final byte[] hmacKey;

        MessageKeys(@NonNull final byte[] chachaKey,
                    @NonNull final byte[] chachaNonce,
                    @NonNull final byte[] hmacKey) {
            this.chachaKey = chachaKey;
            this.chachaNonce = chachaNonce;
            this.hmacKey = hmacKey;
        }
    }

    static final class NostrIdentity {
        @NonNull
        final String nsec;
        @NonNull
        final String npub;

        NostrIdentity(@NonNull final String nsec, @NonNull final String npub) {
            this.nsec = nsec;
            this.npub = npub;
        }
    }

    private static final class DecodedBech32 {
        @NonNull
        final String hrp;
        @NonNull
        final byte[] data;

        DecodedBech32(@NonNull final String hrp, @NonNull final byte[] data) {
            this.hrp = hrp;
            this.data = data;
        }
    }
}
