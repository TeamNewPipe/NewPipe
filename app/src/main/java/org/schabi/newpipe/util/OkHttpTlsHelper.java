package org.schabi.newpipe.util;

import android.os.Build;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

import static org.schabi.newpipe.MainActivity.DEBUG;

public final class OkHttpTlsHelper {

    private OkHttpTlsHelper() {
    }

    /**
     * Enable TLS 1.2 and 1.1 on Android Kitkat. This function is mostly taken
     * from the documentation of OkHttpClient.Builder.sslSocketFactory(_,_).
     * <p>
     * If there is an error, the function will safely fall back to doing nothing
     * and printing the error to the console.
     * </p>
     *
     * @param builder The HTTPClient Builder on which TLS is enabled on (will be modified in-place)
     */
    public static void enableModernTLS(final OkHttpClient.Builder builder) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
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

                // insert our own TLSSocketFactory
                final SSLSocketFactory sslSocketFactory = TLSSocketFactoryCompat.getInstance();

                builder.sslSocketFactory(sslSocketFactory, trustManager);

                // This will try to enable all modern CipherSuites(+2 more)
                // that are supported on the device.
                // Necessary because some servers (e.g. Framatube.org)
                // don't support the old cipher suites.
                // https://github.com/square/okhttp/issues/4053#issuecomment-402579554
                final List<CipherSuite> cipherSuites =
                        new ArrayList<>(ConnectionSpec.MODERN_TLS.cipherSuites());
                cipherSuites.add(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA);
                cipherSuites.add(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA);
                final ConnectionSpec legacyTLS =
                        new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                                .cipherSuites(cipherSuites.toArray(new CipherSuite[0]))
                                .build();

                builder.connectionSpecs(Arrays.asList(legacyTLS, ConnectionSpec.CLEARTEXT));
            } catch (final KeyManagementException | NoSuchAlgorithmException
                    | KeyStoreException e) {
                if (DEBUG) {
                    e.printStackTrace();
                }
            }
        }
    }
}
