package org.schabi.newpipe;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AsyncTask to check if there is a newer version of the NewPipe github apk available or not.
 * If there is a newer version we show a notification, informing the user. On tapping
 * the notification, the user will be directed to the download link.
 */
public class CheckForNewAppVersionTask extends AsyncTask<Void, Void, String> {

    private static final Application app = App.getApp();
    private static final String GITHUB_APK_SHA1 = "B0:2E:90:7C:1C:D6:FC:57:C3:35:F0:88:D0:8F:50:5F:94:E4:D2:15";
    private static final String newPipeApiUrl = "https://newpipe.schabi.org/api/data.json";
    private static final int timeoutPeriod = 30;

    private SharedPreferences mPrefs;
    private OkHttpClient client;

    @Override
    protected void onPreExecute() {

        mPrefs = PreferenceManager.getDefaultSharedPreferences(app);

        // Check if user has enabled/ disabled update checking
        // and if the current apk is a github one or not.
        if (!mPrefs.getBoolean(app.getString(R.string.update_app_key), true)
                || !isGithubApk()) {
            this.cancel(true);
        }
    }

    @Override
    protected String doInBackground(Void... voids) {

        // Make a network request to get latest NewPipe data.
        if (client == null) {

            client = new OkHttpClient
                    .Builder()
                    .readTimeout(timeoutPeriod, TimeUnit.SECONDS)
                    .build();
        }

        Request request = new Request.Builder()
                .url(newPipeApiUrl)
                .build();

        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
        } catch (IOException ex) {
            ErrorActivity.reportError(app, ex, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "app update API fail", R.string.app_ui_crash));
        }

        return null;
    }

    @Override
    protected void onPostExecute(String response) {

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
                ErrorActivity.reportError(app, ex, null, null,
                        ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                        "could not parse app update JSON data", R.string.app_ui_crash));
            }
        }
    }

    /**
     * Method to compare the current and latest available app version.
     * If a newer version is available, we show the update notification.
     * @param versionName
     * @param apkLocationUrl
     */
    private void compareAppVersionAndShowNotification(String versionName,
                                                      String apkLocationUrl,
                                                      String versionCode) {

        int NOTIFICATION_ID = 2000;

        if (BuildConfig.VERSION_CODE < Integer.valueOf(versionCode)) {

            // A pending intent to open the apk location url in the browser.
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkLocationUrl));
            PendingIntent pendingIntent
                    = PendingIntent.getActivity(app, 0, intent, 0);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat
                    .Builder(app, app.getString(R.string.app_update_notification_channel_id))
                    .setSmallIcon(R.drawable.ic_newpipe_update)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setContentTitle(app.getString(R.string.app_update_notification_content_title))
                    .setContentText(app.getString(R.string.app_update_notification_content_text)
                            + " " + versionName);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(app);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    /**
     * Method to get the apk's SHA1 key.
     * https://stackoverflow.com/questions/9293019/get-certificate-fingerprint-from-android-app#22506133
     */
    private static String getCertificateSHA1Fingerprint() {

        PackageManager pm = app.getPackageManager();
        String packageName = app.getPackageName();
        int flags = PackageManager.GET_SIGNATURES;
        PackageInfo packageInfo = null;

        try {
            packageInfo = pm.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException ex) {
            ErrorActivity.reportError(app, ex, null, null,
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
            ErrorActivity.reportError(app, ex, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Certificate error", R.string.app_ui_crash));
        }

        String hexString = null;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] publicKey = md.digest(c.getEncoded());
            hexString = byte2HexFormatted(publicKey);
        } catch (NoSuchAlgorithmException ex1) {
            ErrorActivity.reportError(app, ex1, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Could not retrieve SHA1 key", R.string.app_ui_crash));
        } catch (CertificateEncodingException ex2) {
            ErrorActivity.reportError(app, ex2, null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Could not retrieve SHA1 key", R.string.app_ui_crash));
        }

        return hexString;
    }

    private static String byte2HexFormatted(byte[] arr) {

        StringBuilder str = new StringBuilder(arr.length * 2);

        for (int i = 0; i < arr.length; i++) {
            String h = Integer.toHexString(arr[i]);
            int l = h.length();
            if (l == 1) h = "0" + h;
            if (l > 2) h = h.substring(l - 2, l);
            str.append(h.toUpperCase());
            if (i < (arr.length - 1)) str.append(':');
        }
        return str.toString();
    }

    public static boolean isGithubApk() {

        return getCertificateSHA1Fingerprint().equals(GITHUB_APK_SHA1);
    }
}
