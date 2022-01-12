package org.schabi.newpipe;

import android.app.Application;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

public final class CheckForNewAppVersion extends IntentService {
    public CheckForNewAppVersion() {
        super("CheckForNewAppVersion");
    }

    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final String TAG = CheckForNewAppVersion.class.getSimpleName();

    // Public key of the certificate that is used in NewPipe release versions
    private static final String RELEASE_CERT_PUBLIC_KEY_SHA1
            = "B0:2E:90:7C:1C:D6:FC:57:C3:35:F0:88:D0:8F:50:5F:94:E4:D2:15";
    private static final String NEWPIPE_API_URL = "https://newpipe.net/api/data.json";

    /**
     * Method to get the APK's SHA1 key. See https://stackoverflow.com/questions/9293019/#22506133.
     *
     * @param application The application
     * @return String with the APK's SHA1 fingerprint in hexadecimal
     */
    @NonNull
    private static String getCertificateSHA1Fingerprint(@NonNull final Application application) {
        final List<Signature> signatures;
        try {
            signatures = PackageInfoCompat.getSignatures(application.getPackageManager(),
                    application.getPackageName());
        } catch (final PackageManager.NameNotFoundException e) {
            ErrorUtil.createNotification(application, new ErrorInfo(e,
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
            ErrorUtil.createNotification(application, new ErrorInfo(e,
                    UserAction.CHECK_FOR_NEW_APP_VERSION, "Certificate error"));
            return "";
        }

        try {
            final MessageDigest md = MessageDigest.getInstance("SHA1");
            final byte[] publicKey = md.digest(c.getEncoded());
            return byte2HexFormatted(publicKey);
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            ErrorUtil.createNotification(application, new ErrorInfo(e,
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

    /**
     * Method to compare the current and latest available app version.
     * If a newer version is available, we show the update notification.
     *
     * @param application    The application
     * @param versionName    Name of new version
     * @param apkLocationUrl Url with the new apk
     * @param versionCode    Code of new version
     */
    private static void compareAppVersionAndShowNotification(@NonNull final Application application,
                                                             final String versionName,
                                                             final String apkLocationUrl,
                                                             final int versionCode) {
        if (BuildConfig.VERSION_CODE >= versionCode) {
            return;
        }

        // A pending intent to open the apk location url in the browser.
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkLocationUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent pendingIntent
                = PendingIntent.getActivity(application, 0, intent, 0);

        final String channelId = application
                .getString(R.string.app_update_notification_channel_id);
        final NotificationCompat.Builder notificationBuilder
                = new NotificationCompat.Builder(application, channelId)
                .setSmallIcon(R.drawable.ic_newpipe_update)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setContentTitle(application
                        .getString(R.string.app_update_notification_content_title))
                .setContentText(application
                        .getString(R.string.app_update_notification_content_text)
                        + " " + versionName);

        final NotificationManagerCompat notificationManager
                = NotificationManagerCompat.from(application);
        notificationManager.notify(2000, notificationBuilder.build());
    }

    public static boolean isReleaseApk(@NonNull final App app) {
        return getCertificateSHA1Fingerprint(app).equals(RELEASE_CERT_PUBLIC_KEY_SHA1);
    }

    private void checkNewVersion() throws IOException, ReCaptchaException {
        final App app = App.getApp();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        final NewVersionManager manager = new NewVersionManager();

        // Check if the current apk is a github one or not.
        if (!isReleaseApk(app)) {
            return;
        }

        // Check if the last request has happened a certain time ago
        // to reduce the number of API requests.
        final long expiry = prefs.getLong(app.getString(R.string.update_expiry_key), 0);
        if (!manager.isExpired(expiry)) {
            return;
        }

        // Make a network request to get latest NewPipe data.
        final Response response = DownloaderImpl.getInstance().get(NEWPIPE_API_URL);
        handleResponse(response, manager, prefs, app);
    }

    private void handleResponse(@NonNull final Response response,
                                @NonNull final NewVersionManager manager,
                                @NonNull final SharedPreferences prefs,
                                @NonNull final App app) {
        try {
            // Store a timestamp which needs to be exceeded,
            // before a new request to the API is made.
            final long newExpiry = manager
                    .coerceExpiry(response.getHeader("expires"));
            prefs.edit()
                    .putLong(app.getString(R.string.update_expiry_key), newExpiry)
                    .apply();
        } catch (final Exception e) {
            if (DEBUG) {
                Log.w(TAG, "Could not extract and save new expiry date", e);
            }
        }

        // Parse the json from the response.
        try {

            final JsonObject githubStableObject = JsonParser.object()
                    .from(response.responseBody()).getObject("flavors")
                    .getObject("github").getObject("stable");

            final String versionName = githubStableObject
                    .getString("version");
            final int versionCode = githubStableObject
                    .getInt("version_code");
            final String apkLocationUrl = githubStableObject
                    .getString("apk");

            compareAppVersionAndShowNotification(app, versionName,
                    apkLocationUrl, versionCode);
        } catch (final JsonParserException e) {
            // Most likely something is wrong in data received from NEWPIPE_API_URL.
            // Do not alarm user and fail silently.
            if (DEBUG) {
                Log.w(TAG, "Could not get NewPipe API: invalid json", e);
            }
        }
    }

    /**
     * Start a new service which
     * checks if all conditions for performing a version check are met,
     * fetches the API endpoint {@link #NEWPIPE_API_URL} containing info
     * about the latest NewPipe version
     * and displays a notification about ana available update.
     * <br>
     * Following conditions need to be met, before data is request from the server:
     * <ul>
     * <li> The app is signed with the correct signing key (by TeamNewPipe / schabi).
     * If the signing key differs from the one used upstream, the update cannot be installed.</li>
     * <li>The user enabled searching for and notifying about updates in the settings.</li>
     * <li>The app did not recently check for updates.
     * We do not want to make unnecessary connections and DOS our servers.</li>
     * </ul>
     * <b>Must not be executed</b> when the app is in background.
     */
    public static void startNewVersionCheckService() {
        final Intent intent = new Intent(App.getApp().getApplicationContext(),
                CheckForNewAppVersion.class);
        App.getApp().startService(intent);
    }

    @Override
    protected void onHandleIntent(@Nullable final Intent intent) {
        try {
            checkNewVersion();
        } catch (final IOException e) {
            Log.w(TAG, "Could not fetch NewPipe API: probably network problem", e);
        } catch (final ReCaptchaException e) {
            Log.e(TAG, "ReCaptchaException should never happen here.", e);
        }

    }
}
