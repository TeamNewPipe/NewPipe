package org.schabi.newpipe.settings.extensions;

import android.content.Context;
import android.preference.PreferenceManager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.streams.io.SharpInputStream;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ZipHelper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.PathClassLoader;
import kotlin.collections.SetsKt;

public final class ExtensionManager {
    private ExtensionManager() { }

    public static class ExtensionInfo {
        public final String name;
        public final String author;
        public final String fingerprint;
        public final String cclass;
        public final int replaces;
        public final boolean upgrade;

        ExtensionInfo(final String name, final String author, final String fingerprint,
                      final String cclass, final int replaces, final boolean upgrade) {
            this.name = name;
            this.author = author;
            this.fingerprint = fingerprint;
            this.cclass = cclass;
            this.replaces = replaces;
            this.upgrade = upgrade;
        }
    }

    static String getPathForExtensionName(final Context context, final String name) {
        return context.getApplicationInfo().dataDir + "/extensions/" + name + "/";
    }

    static File getTmpFileForExtensionName(final Context context, final String name) {
        return new File(getPathForExtensionName(context, name) + "tmp.jar");
    }

    static File getFingerprintFileForExtensionName(final Context context, final String name) {
        return new File(getPathForExtensionName(context, name) + "fingerprint.txt");
    }

    public static ExtensionInfo checkExtension(final Context context, final StoredFileHelper file)
            throws IOException, UnknownSignatureException, InvalidSignatureException,
            VersionMismatchException, InvalidReplacementException, InvalidExtensionException,
            SignatureMismatchException {
        String name = null;
        String author = null;
        String cclass = null;
        int replaces = -1;
        final String fingerprint;
        final boolean upgrade;
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(
                new SharpInputStream(file.getStream())))) {
            boolean hasDex = false;
            boolean hasIcon = false;
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                switch (zipEntry.getName()) {
                    case "about.json":
                        final JsonObject about;
                        try {
                            about = JsonParser.object().from(zipInputStream);
                        } catch (JsonParserException e) {
                            throw new IOException(e);
                        }
                        if (!about.getString("version").equals(BuildConfig.VERSION_NAME)) {
                            throw new VersionMismatchException();
                        }
                        if (about.has("replaces")
                                && about.getInt("replaces") >= ServiceList.builtinServices) {
                            throw new InvalidReplacementException();
                        }
                        name = about.getString("name");
                        author = about.getString("author");
                        cclass = about.getString("class");
                        replaces = about.getInt("replaces", -1);
                        break;
                    case "classes.dex":
                        hasDex = true;
                        break;
                    case "icon.png":
                        hasIcon = true;
                        break;
                }
                zipInputStream.closeEntry();
            }
            if (!hasDex || !hasIcon || name == null || author == null || cclass == null) {
                throw new InvalidExtensionException();
            }

            final String path = getPathForExtensionName(context, name);
            upgrade = new File(path + "about.json").exists()
                    && new File(path + "classes.dex").exists()
                    && new File(path + "icon.png").exists();

            final File dir = new File(path);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            final File tmpFile = getTmpFileForExtensionName(context, name);
            tmpFile.createNewFile();

            final InputStream inFile = new SharpInputStream(file.getStream());
            final FileOutputStream outFile = new FileOutputStream(tmpFile);
            final byte[] buf = new byte[2048];
            int count;
            while ((count = inFile.read(buf)) != -1) {
                outFile.write(buf, 0, count);
            }
            outFile.close();

            final JarFile jarFile = new JarFile(tmpFile);

            final JarEntry jarEntry = jarFile.getJarEntry("about.json");
            final InputStream entryStream = jarFile.getInputStream(jarEntry);
            while (entryStream.read(buf) != -1) {
                // Stream needs to be fully read or else the signing certificate will be null
                continue;
            }
            final X509Certificate certificate = getSigningCertFromJar(jarEntry);
            try {
                fingerprint = calcFingerprint(certificate.getEncoded());
            } catch (CertificateEncodingException e) {
                throw new InvalidSignatureException();
            }

            if (!PreferenceManager.getDefaultSharedPreferences(context).getStringSet(
                    context.getString(R.string.fingerprints_key), SetsKt.hashSetOf(
                            "CB84069BD68116BAFAE5EE4EE5B08A567AA6D898404E7CB12F9E756DF5CF5CAB"))
                    .contains(fingerprint)) {
                throw new UnknownSignatureException();
            }

            if (upgrade) {
                final BufferedReader reader = new BufferedReader(new FileReader(
                        getFingerprintFileForExtensionName(context, name)));
                final String prevFingerprint = reader.readLine();
                reader.close();
                verifySigningCertificate(certificate, prevFingerprint);
            }
        }

        return new ExtensionInfo(name, author, fingerprint, cclass, replaces, upgrade);
    }

    public static void addExtension(final ClassLoader classLoader, final Context context,
                                    final ExtensionInfo extension)
            throws IOException, NameMismatchException, InvalidSignatureException,
            SignatureMismatchException, InvalidExtensionException {
        final File tmpFile = getTmpFileForExtensionName(context, extension.name);
        final JarFile jarFile = new JarFile(tmpFile);

        final byte[] buf = new byte[2048];
        for (final String filename : new String[] {"about.json", "classes.dex", "icon.png"}) {
            final JarEntry jarEntry = jarFile.getJarEntry(filename);
            final InputStream entryStream = jarFile.getInputStream(jarEntry);
            while (entryStream.read(buf) != -1) {
                // Stream needs to be fully read or else the signing certificate will be null
                continue;
            }
            final X509Certificate certificate = getSigningCertFromJar(jarEntry);

            final File fingerprintFile
                    = getFingerprintFileForExtensionName(context, extension.name);
            if (fingerprintFile.exists()) {
                final BufferedReader reader
                        = new BufferedReader(new FileReader(fingerprintFile));
                final String prevFingerprint = reader.readLine();
                reader.close();
                verifySigningCertificate(certificate, prevFingerprint);
            } else {
                fingerprintFile.createNewFile();
                try (BufferedWriter writer
                             = new BufferedWriter(new FileWriter(fingerprintFile))) {
                    writer.write(calcFingerprint(certificate.getEncoded()));
                } catch (CertificateEncodingException e) {
                    throw new InvalidSignatureException();
                }
            }
        }

        final String path = getPathForExtensionName(context, extension.name);
        final InputStream tmpFileStream = new FileInputStream(tmpFile);
        if (!ZipHelper.extractFilesFromZip(tmpFileStream, Arrays.asList(path + "about.json",
                path + "classes.dex", path + "icon.png"), Arrays.asList("about.json",
                "classes.dex", "icon.png"))) {
            throw new IOException("Not all files have been extracted successfully");
        }
        tmpFile.delete();

        final String dexPath = path + "classes.dex";
        final PathClassLoader pathClassLoader = new PathClassLoader(dexPath, classLoader);
        final StreamingService service;
        try {
            final Class<?> serviceClass = pathClassLoader.loadClass(extension.cclass);
            if (!StreamingService.class.isAssignableFrom(serviceClass)) {
                throw new InvalidExtensionException();
            }

            service = ((Class<StreamingService>) serviceClass)
                    .getConstructor(new Class[] {int.class}).newInstance(-1);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException
                | InvocationTargetException | ClassNotFoundException e) {
            throw new InvalidExtensionException();
        }

        if (!service.getServiceInfo().getName().equals(extension.name)) {
            throw new NameMismatchException();
        }
        if (extension.replaces != -1
                && !NewPipe.getNameOfService(extension.replaces).equals(extension.name)) {
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

    public static class UnknownSignatureException extends Exception {
        UnknownSignatureException() {
            super();
        }
    }

    public static class VersionMismatchException extends Exception {
        VersionMismatchException() {
            super();
        }
    }

    public static class InvalidReplacementException extends Exception {
        InvalidReplacementException() {
            super();
        }
    }

    public static class InvalidExtensionException extends Exception {
        InvalidExtensionException() {
            super();
        }
    }
}
