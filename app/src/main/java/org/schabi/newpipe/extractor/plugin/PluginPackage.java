/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Imported from PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Keep this Java SDK's public signatures compatible with precompiled PipePipe plugins.
 */
package org.schabi.newpipe.extractor.plugin;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A plugin package ({@code .ppp}) is a zip file that is at the same time:
 * <ul>
 *     <li>a regular jar: compiled {@code .class} files at the root, loadable by a JVM
 *     {@link java.net.URLClassLoader};</li>
 *     <li>a dex container: {@code classes.dex} at the root, loadable by Android's
 *     {@code DexClassLoader};</li>
 *     <li>{@value PluginManifest#FILE_NAME} at the root.</li>
 * </ul>
 *
 * <p>Integrity: repositories publish the SHA-256 of the package and an Ed25519 signature
 * of that 32 byte digest. The public key is pinned per plugin on first install.</p>
 */
public final class PluginPackage {

    public static final String FILE_EXTENSION = ".ppp";
    public static final String DEX_ENTRY = "classes.dex";
    private static final int MAX_MANIFEST_SIZE = 64 * 1024;
    private static final int MAX_ICON_SIZE = 256 * 1024;

    private PluginPackage() {
    }

    /**
     * Read and validate {@code plugin.json} from a package file.
     */
    @Nonnull
    public static PluginManifest readManifest(@Nonnull final File packageFile)
            throws PluginException {
        try (ZipFile zip = new ZipFile(packageFile)) {
            final ZipEntry entry = zip.getEntry(PluginManifest.FILE_NAME);
            if (entry == null) {
                throw new PluginException("Package has no " + PluginManifest.FILE_NAME);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return PluginManifest.parse(readLimited(in, MAX_MANIFEST_SIZE));
            }
        } catch (final IOException e) {
            throw new PluginException("Cannot read plugin package", e);
        }
    }

    /**
     * Read the icon declared by {@link PluginManifest#getIcon()}.
     *
     * @return the encoded image, or null if the manifest declares no icon
     */
    @Nullable
    public static byte[] readIcon(@Nonnull final File packageFile,
                                  @Nonnull final PluginManifest manifest)
            throws PluginException {
        final String icon = manifest.getIcon();
        if (icon == null) {
            return null;
        }
        try (ZipFile zip = new ZipFile(packageFile)) {
            final ZipEntry entry = zip.getEntry(icon);
            if (entry == null) {
                throw new PluginException("Package has no " + icon);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return readBytesLimited(in, MAX_ICON_SIZE, icon);
            }
        } catch (final IOException e) {
            throw new PluginException("Cannot read plugin package", e);
        }
    }

    public static boolean hasDex(@Nonnull final File packageFile) throws PluginException {
        try (ZipFile zip = new ZipFile(packageFile)) {
            return zip.getEntry(DEX_ENTRY) != null;
        } catch (final IOException e) {
            throw new PluginException("Cannot read plugin package", e);
        }
    }

    @Nonnull
    public static byte[] sha256(@Nonnull final File file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = new FileInputStream(file)) {
            final byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    /**
     * Check that the package matches the hash and signature published by a repository.
     *
     * @param expectedSha256Hex lowercase or uppercase hex SHA-256 of the package
     * @param signatureBase64   Ed25519 signature of the raw 32 byte digest
     * @param publicKeyBase64   raw 32 byte Ed25519 public key
     */
    public static void verify(@Nonnull final File packageFile,
                              @Nonnull final String expectedSha256Hex,
                              @Nonnull final String signatureBase64,
                              @Nonnull final String publicKeyBase64) throws PluginException {
        final byte[] digest;
        try {
            digest = sha256(packageFile);
        } catch (final IOException e) {
            throw new PluginException("Cannot hash plugin package", e);
        }
        if (!Hex.toHexString(digest).equals(expectedSha256Hex.toLowerCase(Locale.ROOT))) {
            throw new PluginException("Plugin package hash mismatch");
        }
        if (!verifySignature(digest, signatureBase64, publicKeyBase64)) {
            throw new PluginException("Invalid plugin package signature");
        }
    }

    public static boolean verifySignature(@Nonnull final byte[] message,
                                          @Nonnull final String signatureBase64,
                                          @Nonnull final String publicKeyBase64) {
        try {
            final byte[] key = Base64.decode(publicKeyBase64);
            final byte[] signature = Base64.decode(signatureBase64);
            if (key.length != Ed25519PublicKeyParameters.KEY_SIZE
                    || signature.length != Ed25519PublicKeyParameters.KEY_SIZE * 2) {
                return false;
            }
            final Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(key, 0));
            verifier.update(message, 0, message.length);
            return verifier.verifySignature(signature);
        } catch (final RuntimeException e) {
            // malformed base64 or key
            return false;
        }
    }

    /**
     * @return whether the string is a well formed raw Ed25519 public key in base64
     */
    public static boolean isValidPublicKey(@Nonnull final String publicKeyBase64) {
        try {
            return Base64.decode(publicKeyBase64).length == Ed25519PublicKeyParameters.KEY_SIZE;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    @Nonnull
    private static String readLimited(@Nonnull final InputStream in, final int limit)
            throws IOException, PluginException {
        return new String(readBytesLimited(in, limit, PluginManifest.FILE_NAME),
                StandardCharsets.UTF_8);
    }

    @Nonnull
    private static byte[] readBytesLimited(@Nonnull final InputStream in, final int limit,
                                           @Nonnull final String name)
            throws IOException, PluginException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > limit) {
                throw new PluginException(name + " is too large");
            }
        }
        return out.toByteArray();
    }
}
