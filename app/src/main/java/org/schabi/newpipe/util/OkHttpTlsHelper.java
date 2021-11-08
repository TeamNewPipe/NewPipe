package org.schabi.newpipe.util;

import android.util.Log;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import info.guardianproject.netcipher.NetCipher;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

public final class OkHttpTlsHelper {
    private static final String TAG = OkHttpTlsHelper.class.getSimpleName();

    private OkHttpTlsHelper() {
    }

    /**
     * Enable modern TLS 1.1 and 1.2 through NetCipher.
     * This is especially useful on Android KitKat where TLS 1.1 and 1.2 are
     * available but not enabled by default. Furthermore NetCipher will
     * enable/disable ciphers and TLS versions according to best practice.
     * <p>
     * Parts of this function are taken from the documentation of
     * OkHttpClient.Builder.sslSocketFactory(_,_).
     * <p>
     * If there is an error, the function will safely fall back to doing nothing
     * and printing the error to the console.
     * </p>
     *
     * @param builder The HTTPClient Builder on which TLS is enabled on (will be modified in-place)
     * @return the same builder that was supplied. So the method can be chained.
     */
    public static OkHttpClient.Builder enableModernTLS(final OkHttpClient.Builder builder) {
        try {
            // get the default TrustManager
            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            final TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:"
                        + Arrays.toString(trustManagers));
            }
            final X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

            // insert NetCiphers TLSSocketFactory
            final SSLSocketFactory sslSocketFactory = NetCipher.getTlsOnlySocketFactory();

            builder.sslSocketFactory(sslSocketFactory, trustManager);

            // Let NetCipher sort out the TlsVersions and CipherSuites that should not be used
            final ConnectionSpec connectionSpec =
                    new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .allEnabledTlsVersions()
                            .allEnabledCipherSuites()
                            .build();
            builder.connectionSpecs(Collections.singletonList(connectionSpec));
        } catch (final NoSuchAlgorithmException | KeyStoreException e) {
            Log.w(TAG, "Could not setup X509TrustManager for NetCipher", e);
        }

        return builder;
    }
}
