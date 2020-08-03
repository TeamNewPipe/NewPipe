package org.schabi.newpipe.settings.extensions;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.util.ZipHelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import dalvik.system.PathClassLoader;

public final class ExtensionManager {
    private ExtensionManager() { }

    public static void addExtension(final ClassLoader classLoader, final String path,
                                    final File tmpFile, final JsonObject about)
            throws IOException, NoStreamingServiceClassException, NameMismatchException,
            InvalidSignatureException, SignatureMismatchException {
        final JarFile jarFile = new JarFile(tmpFile);

        final byte[] buf = new byte[2048];
        for (final String filename : new String[] {"about.json", "classes.dex", "icon.png"}) {
            final JarEntry jarEntry = jarFile.getJarEntry(filename);
            final InputStream entryStream = jarFile.getInputStream(jarEntry);
            while (entryStream.read(buf) != -1) {
                // Stream needs to be fully read or else the signing certificate will be null
                continue;
            }
            final X509Certificate certificate
                    = ExtensionManager.getSigningCertFromJar(jarEntry);

            final File fingerprintFile = new File(path + "fingerprint.txt");
            if (fingerprintFile.exists()) {
                final BufferedReader reader
                        = new BufferedReader(new FileReader(fingerprintFile));
                final String prevFingerprint = reader.readLine();
                reader.close();
                ExtensionManager.verifySigningCertificate(certificate, prevFingerprint);
            } else {
                fingerprintFile.createNewFile();
                try (BufferedWriter writer
                             = new BufferedWriter(new FileWriter(fingerprintFile))) {
                    writer.write(ExtensionManager.calcFingerprint(certificate.getEncoded()));
                } catch (CertificateEncodingException e) {
                    throw new InvalidSignatureException();
                }
            }
        }

        final InputStream tmpFileStream = new FileInputStream(tmpFile);
        if (!ZipHelper.extractFilesFromZip(tmpFileStream, Arrays.asList(path + "about.json",
                path + "classes.dex", path + "icon.png"), Arrays.asList("about.json",
                "classes.dex", "icon.png"))) {
            throw new IOException("Not all files have been extracted successfully");
        }
        tmpFile.delete();

        final String dexPath = path + "classes.dex";
        final PathClassLoader pathClassLoader = new PathClassLoader(dexPath, classLoader);
        if (!about.has("class")) {
            throw new NoStreamingServiceClassException();
        }
        final StreamingService service;
        try {
            final Class<?> serviceClass = pathClassLoader.loadClass(about.getString("class"));
            if (!StreamingService.class.isAssignableFrom(serviceClass)) {
                throw new NoStreamingServiceClassException();
            }

            service = ((Class<StreamingService>) serviceClass)
                    .getConstructor(new Class[] {int.class}).newInstance(-1);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException
                | InvocationTargetException | ClassNotFoundException e) {
            throw new NoStreamingServiceClassException();
        }

        if (!service.getServiceInfo().getName().equals(about.getString("name"))) {
            throw new NameMismatchException();
        }
        if (about.has("replaces") && !NewPipe.getNameOfService(about.getInt("replaces"))
                .equals(about.getString("name"))) {
            throw new NameMismatchException();
        }
    }

    public static void removeExtension(final String path) {
        final File extensionDir = new File(path);
        for (final File file : extensionDir.listFiles()) {
            file.delete();
        }

        extensionDir.delete();
    }

    static X509Certificate getSigningCertFromJar(final JarEntry jarEntry)
            throws InvalidSignatureException {
        final CodeSigner[] codeSigners = jarEntry.getCodeSigners();
        if (codeSigners == null || codeSigners.length == 0) {
            throw new InvalidSignatureException();
        }
        // We could in theory support more than 1, but as of now we do not
        if (codeSigners.length > 1) {
            throw new InvalidSignatureException();
        }
        final List<? extends Certificate> certs
                = codeSigners[0].getSignerCertPath().getCertificates();
        if (certs.size() != 1) {
            throw new InvalidSignatureException();
        }
        return (X509Certificate) certs.get(0);
    }

    private static void verifySigningCertificate(final X509Certificate rawCertFromJar,
                                                 final String previousFingerprint)
            throws InvalidSignatureException, SignatureMismatchException {
        final byte[] encodedCert;
        try {
            encodedCert = rawCertFromJar.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new InvalidSignatureException();
        }
        if (encodedCert == null || encodedCert.length == 0) {
            throw new InvalidSignatureException();
        }

        final String fingerprintFromJar = calcFingerprint(encodedCert);
        if (!previousFingerprint.equalsIgnoreCase(fingerprintFromJar)) {
            throw new SignatureMismatchException();
        }
    }

    static String calcFingerprint(final byte[] key) throws InvalidSignatureException {
        if (key == null) {
            throw new InvalidSignatureException();
        }
        if (key.length < 256) {
            throw new InvalidSignatureException();
        }
        try {
            // keytool -list -v gives you the SHA-256 fingerprint
            final MessageDigest digest = MessageDigest.getInstance("sha256");
            digest.update(key);
            final byte[] fingerprint = digest.digest();
            final Formatter formatter = new Formatter(new StringBuilder());
            for (final byte aFingerprint : fingerprint) {
                formatter.format("%02X", aFingerprint);
            }
            final String ret = formatter.toString();
            formatter.close();
            return ret;
        } catch (NoSuchAlgorithmException e) {
            throw new InvalidSignatureException();
        }
    }

    public static class NameMismatchException extends Exception {
        NameMismatchException() {
            super();
        }
    }

    public static class NoStreamingServiceClassException extends Exception {
        NoStreamingServiceClassException() {
            super();
        }
    }

    public static class InvalidSignatureException extends Exception {
        InvalidSignatureException() {
            super();
        }
    }

    public static class SignatureMismatchException extends Exception {
        SignatureMismatchException() {
            super();
        }
    }
}
