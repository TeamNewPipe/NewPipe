package org.schabi.newpipe.settings.extensions;

import android.content.Context;
import android.preference.PreferenceManager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

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
import java.util.jar.JarInputStream;

import dalvik.system.PathClassLoader;
import kotlin.collections.SetsKt;
import wb9688.simple_dex_parser.SimpleDexParser;

public final class ExtensionManager {
    private static final List<String> ALLOWED_PACKAGES = Arrays.asList("Ldalvik/annotation/",
            "Ljavax/annotation/", "Ljava/lang/", "Ljava/util/", "Ljava/text/",
            "Lcom/grack/nanojson/", "Lorg/schabi/newpipe/extractor/");
    private static final List<String> ALLOWED_CLASSES = Arrays.asList("Ljava/io/IOException;",
            "Ljava/io/UnsupportedEncodingException;", "Ljava/io/InputStream;", "Ljava/net/URL;",
            "Ljava/net/MalformedURLException;", "Ljava/net/URLEncoder;", "Ljava/net/URLDecoder;",
            "Ljava/net/URI;", "Ljava/net/URISyntaxException;", "Lorg/jsoup/Jsoup;",
            "Lorg/jsoup/nodes/Document;", "Lorg/jsoup/nodes/Element;", "Lorg/jsoup/parser/Parser;",
            "Lorg/jsoup/select/Elements;");
    private static final List<String> DISALLOWED_PACKAGES = Arrays.asList("Ljava/lang/reflect/",
            "Lorg/schabi/newpipe/extractor/services/");
    private static final List<String> DISALLOWED_CLASSES = Arrays.asList("Ljava/lang/Class;",
            "Ljava/lang/ClassLoader;", "Lorg/schabi/newpipe/extractor/ServiceList;");

    private ExtensionManager() { }

    static ExtensionInfo checkExtension(final Context context, final StoredFileHelper file)
            throws IOException, UnknownSignatureException, InvalidSignatureException,
            VersionMismatchException, InvalidReplacementException, InvalidExtensionException,
            SignatureMismatchException, CaseMismatchException, DexParsingException,
            InsecureDexException {
        String name = null;
        String author = null;
        String cclass = null;
        int replaces = -1;
        String fingerprint = null;
        String path = null;
        boolean upgrade = false;
        try (JarInputStream jarInputStream = new JarInputStream(new BufferedInputStream(
                new SharpInputStream(file.getStream())))) {
            boolean hasDex = false;
            boolean hasIcon = false;
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                switch (jarEntry.getName()) {
                    case "about.json":
                        final JsonObject about;
                        try {
                            about = JsonParser.object().from(jarInputStream);
                        } catch (JsonParserException e) {
                            throw new IOException(e);
                        }
                        if (about.getInt("major_version") != NewPipe.MAJOR_VERSION
                                || about.getInt("minor_version") > NewPipe.MINOR_VERSION) {
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

                        if (replaces == -1) {
                            for (int i = 0; i < ServiceList.builtinServices; i++) {
                                if (ServiceList.all().get(i).getServiceInfo().getName()
                                        .equals(name)) {
                                    throw new InvalidExtensionException();
                                }
                            }
                        }

                        if (!cclass
                                .startsWith("org.schabi.newpipe.extractor.extensions.services.")) {
                            throw new InvalidExtensionException();
                        }

                        final String[] installedExtensions = new File(
                                context.getApplicationInfo().dataDir + "/extensions/").list();
                        if (installedExtensions != null) {
                            for (final String installedExtension : installedExtensions) {
                                if (name.equalsIgnoreCase(installedExtension)
                                        && !name.equals(installedExtension)) {
                                    throw new CaseMismatchException();
                                }
                            }
                        }

                        final X509Certificate certificate = getSigningCertFromJar(jarEntry);
                        try {
                            fingerprint = calcFingerprint(certificate.getEncoded());
                        } catch (CertificateEncodingException e) {
                            throw new InvalidSignatureException();
                        }

                        if (!PreferenceManager.getDefaultSharedPreferences(context).getStringSet(
                                context.getString(R.string.fingerprints_key), SetsKt.hashSetOf(
                                        "CB84069BD68116BAFAE5EE4EE5B08A567AA6D898404E7CB12F9E756DF5"
                                                + "CF5CAB")).contains(fingerprint)) {
                            throw new UnknownSignatureException();
                        }

                        path = getPathForExtensionName(context, name);
                        upgrade = new File(path + "about.json").exists()
                                && new File(path + "classes.dex").exists()
                                && new File(path + "icon.png").exists();

                        if (upgrade) {
                            final BufferedReader reader = new BufferedReader(new FileReader(
                                    getFingerprintFileForExtensionName(context, name)));
                            final String prevFingerprint = reader.readLine();
                            reader.close();
                            verifySigningCertificate(certificate, prevFingerprint);
                        }
                        break;
                    case "classes.dex":
                        if (!checkDex(new BufferedInputStream(jarInputStream))) {
                            throw new InsecureDexException();
                        }
                        hasDex = true;
                        break;
                    case "icon.png":
                        hasIcon = true;
                        break;
                }
                jarInputStream.closeEntry();
            }
            if (!hasDex || !hasIcon || name == null || author == null || cclass == null) {
                throw new InvalidExtensionException();
            }

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
        }

        return new ExtensionInfo(name, author, fingerprint, cclass, replaces, upgrade);
    }

    static void addExtension(final ClassLoader classLoader, final Context context,
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
        if (!extensionDir.exists()) {
            return;
        }
        for (final File file : extensionDir.listFiles()) {
            file.delete();
        }

        extensionDir.delete();
    }

    private static boolean checkDex(final InputStream inputStream) throws DexParsingException {
        final SimpleDexParser simpleDexParser;
        try {
            simpleDexParser = new SimpleDexParser(inputStream);
        } catch (IOException e) {
            throw new DexParsingException();
        }
        types:
        for (String type : simpleDexParser.getTypes()) {
            while (type.startsWith("[")) {
                type = type.substring(1);
            }

            if (!type.startsWith("L")) {
                continue;
            }

            if (DISALLOWED_CLASSES.contains(type)) {
                return false;
            }

            for (final String disallowedPackage : DISALLOWED_PACKAGES) {
                if (type.startsWith(disallowedPackage)) {
                    return false;
                }
            }

            if (ALLOWED_CLASSES.contains(type)) {
                continue;
            }

            for (final String allowedPackage : ALLOWED_PACKAGES) {
                if (type.startsWith(allowedPackage)) {
                    continue types;
                }
            }

            return false;
        }

        return true;
    }

    private static X509Certificate getSigningCertFromJar(final JarEntry jarEntry)
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

    private static String calcFingerprint(final byte[] key) throws InvalidSignatureException {
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

    static String getPathForExtensionName(final Context context, final String name) {
        return context.getApplicationInfo().dataDir + "/extensions/" + name + "/";
    }

    static File getTmpFileForExtensionName(final Context context, final String name) {
        return new File(getPathForExtensionName(context, name) + "tmp.jar");
    }

    private static File getFingerprintFileForExtensionName(final Context context,
                                                           final String name) {
        return new File(getPathForExtensionName(context, name) + "fingerprint.txt");
    }

    static class ExtensionInfo {
        final String name;
        final String author;
        final String fingerprint;
        final String cclass;
        final int replaces;
        final boolean upgrade;

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

    static class NameMismatchException extends Exception {
        NameMismatchException() {
            super();
        }
    }

    static class InvalidSignatureException extends Exception {
        InvalidSignatureException() {
            super();
        }
    }

    static class SignatureMismatchException extends Exception {
        SignatureMismatchException() {
            super();
        }
    }

    static class UnknownSignatureException extends Exception {
        UnknownSignatureException() {
            super();
        }
    }

    static class VersionMismatchException extends Exception {
        VersionMismatchException() {
            super();
        }
    }

    static class InvalidReplacementException extends Exception {
        InvalidReplacementException() {
            super();
        }
    }

    static class CaseMismatchException extends Exception {
        CaseMismatchException() {
            super();
        }
    }

    static class DexParsingException extends Exception {
        DexParsingException() {
            super();
        }
    }

    static class InsecureDexException extends Exception {
        InsecureDexException() {
            super();
        }
    }

    static class InvalidExtensionException extends Exception {
        InvalidExtensionException() {
            super();
        }
    }
}
