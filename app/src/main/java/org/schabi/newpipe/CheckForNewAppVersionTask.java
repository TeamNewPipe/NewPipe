package org.schabi.newpipe;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * AsyncTask to check if there is a newer version of the NewPipe github apk available or not.
 * If there is a newer version we show a notification, informing the user. On tapping
 * the notification, the user will be directed to the download link.
 */
public class CheckForNewAppVersionTask extends AsyncTask<Void, Void, String> {
    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final String TAG = CheckForNewAppVersionTask.class.getSimpleName();
    private static final Application APP = App.getApp();
    private static final String GITHUB_APK_SHA1
            = "B0:2E:90:7C:1C:D6:FC:57:C3:35:F0:88:D0:8F:50:5F:94:E4:D2:15";
    private static final String NEWPIPE_API_URL = "https://newpipe.schabi.org/api/data.json";
    private static final int TIMEOUT_PERIOD = 30;

    private SharedPreferences mPrefs;
    private OkHttpClient client;

    /**
     * Method to get the apk's SHA1 key. See https://stackoverflow.com/questions/9293019/#22506133.
     *
     * @return String with the apk's SHA1 fingeprint in hexadecimal
     */
    private static String getCertificateSHA1Fingerprint() {
        PackageManager pm = APP.getPackageManager();
        String packageName = APP.getPackageName();
        int flags = PackageManager.GET_SIGNATURES;
        PackageInfo packageInfo = null;

        try {
            packageInfo = pm.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException ex) {
            ErrorActivity.reportError(APP, ex, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Could not find package info", R.string.app_ui_crash));
        }

        Signature[] signatures = packageInfo.signatures;
        byte[] cert = signatures[0].toByteArray();
        InputStream input = new ByteArrayInputStream(cert);

        CertificateFactory cf = null;
        X509Certificate c = null;

        try {
            cf = CertificateFactory.getInstance("X509");
            c = (X509Certificate) cf.generateCertificate(input);
        } catch (CertificateException ex) {
            ErrorActivity.reportError(APP, ex, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Certificate error", R.string.app_ui_crash));
        }

        String hexString = null;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] publicKey = md.digest(c.getEncoded());
            hexString = byte2HexFormatted(publicKey);
        } catch (NoSuchAlgorithmException ex1) {
            ErrorActivity.reportError(APP, ex1, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Could not retrieve SHA1 key", R.string.app_ui_crash));
        } catch (CertificateEncodingException ex2) {
            ErrorActivity.reportError(APP, ex2, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Could not retrieve SHA1 key", R.string.app_ui_crash));
        }

        return hexString;
    }

    private static String byte2HexFormatted(final byte[] arr) {
        StringBuilder str = new StringBuilder(arr.length * 2);

        for (int i = 0; i < arr.length; i++) {
            String h = Integer.toHexString(arr[i]);
            int l = h.length();
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

    public static boolean isGithubApk() {
        return getCertificateSHA1Fingerprint().equals(GITHUB_APK_SHA1);
    }

    @Override
    protected void onPreExecute() {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(APP);

        // Check if user has enabled/ disabled update checking
        // and if the current apk is a github one or not.
        if (!mPrefs.getBoolean(APP.getString(R.string.update_app_key), true) || !isGithubApk()) {
            this.cancel(true);
        }
    }

    @Override
    protected String doInBackground(final Void... voids) {
        if (isCancelled() || !isConnected()) {
            return null;
        }

        // Make a network request to get latest NewPipe data.
        // FIXME: Use DownloaderImp
        if (client == null) {

            client = new OkHttpClient.Builder()
                    .readTimeout(TIMEOUT_PERIOD, TimeUnit.SECONDS).build();
        }

        Request request = new Request.Builder().url(NEWPIPE_API_URL).build();

        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
        } catch (IOException ex) {
            // connectivity problems, do not alarm user and fail silently
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(ex));
            }
        }

        return null;
    }

    @Override
    protected void onPostExecute(final String response) {
        // Parse the json from the response.
        if (response != null) {

            try {
                JSONObject mainObject = new JSONObject(response);
                JSONObject flavoursObject = mainObject.getJSONObject("flavors");
                JSONObject githubObject = flavoursObject.getJSONObject("github");
                JSONObject githubStableObject = githubObject.getJSONObject("stable");

                String versionName = githubStableObject.getString("version");
                String versionCode = githubStableObject.getString("version_code");
                String apkLocationUrl = githubStableObject.getString("apk");

                compareAppVersionAndShowNotification(versionName, apkLocationUrl, versionCode);

            } catch (JSONException ex) {
                // connectivity problems, do not alarm user and fail silently
                if (DEBUG) {
                    Log.w(TAG, Log.getStackTraceString(ex));
                }
            }
        }
    }

    /**
     * Method to compare the current and latest available app version.
     * If a newer version is available, we show the update notification.
     *
     * @param versionName    Name of new version
     * @param apkLocationUrl Url with the new apk
     * @param versionCode    V
     */
    private void compareAppVersionAndShowNotification(final String versionName,
                                                      final String apkLocationUrl,
                                                      final String versionCode) {
        int notificationId = 2000;

        if (BuildConfig.VERSION_CODE < Integer.valueOf(versionCode)) {

            // A pending intent to open the apk location url in the browser.
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkLocationUrl));
            PendingIntent pendingIntent
                    = PendingIntent.getActivity(APP, 0, intent, 0);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat
                    .Builder(APP, APP.getString(R.string.app_update_notification_channel_id))
                    .setSmallIcon(R.drawable.ic_newpipe_update)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setContentTitle(APP.getString(R.string.app_update_notification_content_title))
                    .setContentText(APP.getString(R.string.app_update_notification_content_text)
                            + " " + versionName);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(APP);
            notificationManager.notify(notificationId, notificationBuilder.build());
        }
    }

    private boolean isConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) APP.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null
                && cm.getActiveNetworkInfo().isConnected();
    }
}
