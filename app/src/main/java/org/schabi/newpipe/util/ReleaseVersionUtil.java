package org.schabi.newpipe.util;

import android.content.pm.PackageManager;
import android.content.pm.Signature;

import androidx.annotation.NonNull;
import androidx.core.content.pm.PackageInfoCompat;

import org.schabi.newpipe.App;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

public class ReleaseVersionUtil {
    // Public key of the certificate that is used in NewPipe release versions
    private static final String RELEASE_CERT_PUBLIC_KEY_SHA1
            = "B0:2E:90:7C:1C:D6:FC:57:C3:35:F0:88:D0:8F:50:5F:94:E4:D2:15";

    public static boolean isReleaseApk() {
        return getCertificateSHA1Fingerprint().equals(RELEASE_CERT_PUBLIC_KEY_SHA1);
    }

    /**
     * Method to get the APK's SHA1 key. See https://stackoverflow.com/questions/9293019/#22506133.
     *
     * @return String with the APK's SHA1 fingerprint in hexadecimal
     */
    @NonNull
    private static String getCertificateSHA1Fingerprint() {
        final App app = App.getApp();
        final List<Signature> signatures;
        try {
            signatures = PackageInfoCompat.getSignatures(app.getPackageManager(),
                    app.getPackageName());
        } catch (final PackageManager.NameNotFoundException e) {
            ErrorUtil.createNotification(app, new ErrorInfo(e,
                    UserAction.CHECK_FOR_NEW_APP_VERSION, "Could not find package info"));
            return "";
        }
        if (signatures.isEmpty()) {
            return "";
        }

        final X509Certificate c;
        try {
            final byte[] cert = signatures.get(0).toByteArray();
            final InputStream input = new ByteArrayInputStream(cert);
            final CertificateFactory cf = CertificateFactory.getInstance("X509");
            c = (X509Certificate) cf.generateCertificate(input);
        } catch (final CertificateException e) {
            ErrorUtil.createNotification(app, new ErrorInfo(e,
                    UserAction.CHECK_FOR_NEW_APP_VERSION, "Certificate error"));
            return "";
        }

        try {
            final MessageDigest md = MessageDigest.getInstance("SHA1");
            final byte[] publicKey = md.digest(c.getEncoded());
            return byte2HexFormatted(publicKey);
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            ErrorUtil.createNotification(app, new ErrorInfo(e,
                    UserAction.CHECK_FOR_NEW_APP_VERSION, "Could not retrieve SHA1 key"));
            return "";
        }
    }

    private static String byte2HexFormatted(final byte[] arr) {
        final StringBuilder str = new StringBuilder(arr.length * 2);

        for (int i = 0; i < arr.length; i++) {
            String h = Integer.toHexString(arr[i]);
            final int l = h.length();
            if (l == 1) {
                h = "0" + h;
            }
            if (l > 2) {
                h = h.substring(l - 2, l);
            }
            str.append(h.toUpperCase());
            if (i < (arr.length - 1)) {
                str.append(':');
            }
        }
        return str.toString();
    }
}
