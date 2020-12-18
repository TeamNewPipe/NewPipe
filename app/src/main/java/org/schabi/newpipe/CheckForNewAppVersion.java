package org.schabi.newpipe;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.util.Version;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class CheckForNewAppVersion {
    private CheckForNewAppVersion() {
    }

    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final String TAG = CheckForNewAppVersion.class.getSimpleName();
    private static final String API_URL =
            "https://api.github.com/repos/polymorphicshade/NewPipe/releases/latest";

    /**
     * Method to compare the current and latest available app version.
     * If a newer version is available, we show the update notification.
     *
     * @param application    The application
     * @param versionName    Name of new version
     * @param apkLocationUrl Url with the new apk
     */
    private static void compareAppVersionAndShowNotification(@NonNull final Application application,
                                                             final String versionName,
                                                             final String apkLocationUrl) {
        final Version sourceVersion = Version.fromString(BuildConfig.VERSION_NAME);
        final Version targetVersion = Version.fromString(versionName);

        // abort if source version is the same or newer than target version
        if (sourceVersion.compareTo(targetVersion) >= 0) {
            return;
        }

        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkLocationUrl));
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

    private static boolean isConnected(@NonNull final App app) {
        final ConnectivityManager connectivityManager =
                ContextCompat.getSystemService(app, ConnectivityManager.class);
        return connectivityManager != null && connectivityManager.getActiveNetworkInfo() != null
                && connectivityManager.getActiveNetworkInfo().isConnected();
    }

    @Nullable
    public static Disposable checkNewVersion(@NonNull final App app) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);

        // Check if user has enabled/disabled update checking
        // and if the current apk is a github one or not.
        if (!prefs.getBoolean(app.getString(R.string.update_app_key), true)) {
            return null;
        }

        return Maybe
                .fromCallable(() -> {
                    if (!isConnected(app)) {
                        return null;
                    }

                    // Make a network request to get latest NewPipe data.
                    return DownloaderImpl.getInstance().get(API_URL).responseBody();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        response -> {
                            // Parse the json from the response.
                            try {
                                // assuming the first result is the latest one
                                final JsonObject jObj = JsonParser.object().from(response);

                                final String versionName = jObj.getString("tag_name");

                                final String apkLocationUrl = jObj
                                        .getArray("assets")
                                        .getObject(0)
                                        .getString("browser_download_url");

                                compareAppVersionAndShowNotification(app, versionName,
                                        apkLocationUrl);
                            } catch (final JsonParserException e) {
                                // connectivity problems, do not alarm user and fail silently
                                if (DEBUG) {
                                    Log.w(TAG, "Could not get Github API: invalid json", e);
                                }
                            }
                        },
                        e -> {
                            // connectivity problems, do not alarm user and fail silently
                            if (DEBUG) {
                                Log.w(TAG, "Could not get Github API: network problem", e);
                            }
                        });
    }
}
