package org.schabi.newpipe;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.util.ReleaseVersionUtil;

import java.io.IOException;

public final class NewVersionWorker extends Worker {

    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final String TAG = NewVersionWorker.class.getSimpleName();
    private static final String NEWPIPE_API_URL = "https://newpipe.net/api/data.json";

    public NewVersionWorker(@NonNull final Context context,
                            @NonNull final WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Method to compare the current and latest available app version.
     * If a newer version is available, we show the update notification.
     *
     * @param versionName    Name of new version
     * @param apkLocationUrl Url with the new apk
     * @param versionCode    Code of new version
     */
    private static void compareAppVersionAndShowNotification(final String versionName,
                                                            final String apkLocationUrl,
                                                            final int versionCode) {
        if (BuildConfig.VERSION_CODE >= versionCode) {
            return;
        }

        final App app = App.getApp();
        // A pending intent to open the apk location url in the browser.
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkLocationUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent pendingIntent = PendingIntent.getActivity(app, 0, intent, 0);

        final String channelId = app.getString(R.string.app_update_notification_channel_id);
        final NotificationCompat.Builder notificationBuilder
                = new NotificationCompat.Builder(app, channelId)
                .setSmallIcon(R.drawable.ic_newpipe_update)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setContentTitle(app.getString(R.string.app_update_notification_content_title))
                .setContentText(app.getString(R.string.app_update_notification_content_text)
                        + " " + versionName);

        final NotificationManagerCompat notificationManager
                = NotificationManagerCompat.from(app);
        notificationManager.notify(2000, notificationBuilder.build());
    }

    private void checkNewVersion() throws IOException, ReCaptchaException {
        final App app = App.getApp();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);

        // Check if the current apk is a github one or not.
        if (!ReleaseVersionUtil.isReleaseApk()) {
            return;
        }

        // Check if the last request has happened a certain time ago
        // to reduce the number of API requests.
        final long expiry = prefs.getLong(app.getString(R.string.update_expiry_key), 0);
        if (!ReleaseVersionUtil.isLastUpdateCheckExpired(expiry)) {
            return;
        }

        // Make a network request to get latest NewPipe data.
        final Response response = DownloaderImpl.getInstance().get(NEWPIPE_API_URL);
        handleResponse(response);
    }

    private void handleResponse(@NonNull final Response response) {
        final App app = App.getApp();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        try {
            // Store a timestamp which needs to be exceeded,
            // before a new request to the API is made.
            final long newExpiry = ReleaseVersionUtil
                    .coerceUpdateCheckExpiry(response.getHeader("expires"));
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

            final String versionName = githubStableObject.getString("version");
            final int versionCode = githubStableObject.getInt("version_code");
            final String apkLocationUrl = githubStableObject.getString("apk");

            compareAppVersionAndShowNotification(versionName,
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
     * Start a new worker which
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
     */
    public static void enqueueNewVersionCheckingWork(final Context context) {
        final WorkRequest workRequest =
                new OneTimeWorkRequest.Builder(NewVersionWorker.class).build();
        WorkManager.getInstance(context).enqueue(workRequest);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            checkNewVersion();
        } catch (final IOException e) {
            Log.w(TAG, "Could not fetch NewPipe API: probably network problem", e);
            return Result.failure();
        } catch (final ReCaptchaException e) {
            Log.e(TAG, "ReCaptchaException should never happen here.", e);
            return Result.failure();
        }
        return Result.success();
    }
}
