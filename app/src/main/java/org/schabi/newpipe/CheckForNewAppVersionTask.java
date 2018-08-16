package org.schabi.newpipe;

import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * AsyncTask to check if there is a newer version of the NewPipe github apk available or not.
 * If there is a newer version we show a notification, informing the user. On tapping
 * the notification, the user will be directed to the download link.
 */
public class CheckForNewAppVersionTask extends AsyncTask<Void, Void, String> {

    private Application app = App.getContext();

    private String newPipeApiUrl = "https://newpipe.schabi.org/api/data.json";
    private int timeoutPeriod = 10000;

    @Override
    protected void onPreExecute() {
        // Continue with version check only if the build variant is of type "github".
        if (!BuildConfig.FLAVOR.equals(app.getString(R.string.app_flavor_github))) {
            this.cancel(true);
        }
    }

    @Override
    protected String doInBackground(Void... voids) {

        // Make a network request to get latest NewPipe data.

        String response;
        HttpURLConnection connection = null;

        try {

            URL url = new URL(newPipeApiUrl);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutPeriod);
            connection.setReadTimeout(timeoutPeriod);
            connection.setRequestProperty("Content-length", "0");
            connection.setUseCaches(false);
            connection.setAllowUserInteraction(false);
            connection.connect();

            int responseStatus = connection.getResponseCode();

            switch (responseStatus) {

                case 200:
                case 201:
                    BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));

                    StringBuilder stringBuilder = new StringBuilder();

                    String line;

                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                        stringBuilder.append("\n");
                    }

                    bufferedReader.close();
                    response = stringBuilder.toString();

                    return response;
            }
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        return null;
    }

    @Override
    protected void onPostExecute(String response) {

        Log.i("Response--", response);

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
                ex.printStackTrace();
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
}
