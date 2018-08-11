package org.schabi.newpipe;

import android.os.AsyncTask;
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
 * AsyncTask to check if there is a newer version of the github apk available or not.
 * If there is a newer version we show a notification, informing the user. On tapping
 * the notification, the user will be directed to download link.
 */
public class FetchAppVersionTask extends AsyncTask<Void, Void, String> {

    private String newPipeApiUrl = "https://newpipe.schabi.org/api/data.json";
    private int timeoutPeriod = 10000;

    @Override
    protected String doInBackground(Void... voids) {

        String output;

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
                    BufferedReader bufferedReader
                            = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));

                    StringBuilder stringBuilder = new StringBuilder();

                    String line;

                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line + "\n");
                    }

                    bufferedReader.close();
                    output = stringBuilder.toString();

                    return output;
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
    protected void onPostExecute(String output) {

        if (output != null) {

            Log.i("output---", output);

            try {
                JSONObject mainObject = new JSONObject(output);
                JSONObject flavoursObject = mainObject.getJSONObject("flavors");
                JSONObject githubObject = flavoursObject.getJSONObject("github");
                JSONObject githubStableObject = githubObject.getJSONObject("stable");

                String versionName = githubStableObject.getString("version");
                // String versionCode = githubStableObject.getString("version_code");
                String apkLocationUrl = githubStableObject.getString("apk");

                compareAppVersionAndShowNotification(versionName, apkLocationUrl);

            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void compareAppVersionAndShowNotification(String versionName, String apkLocationUrl) {

        if (!BuildConfig.VERSION_NAME.equals(versionName)) {


        }
    }
}
