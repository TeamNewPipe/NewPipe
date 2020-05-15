package org.schabi.newpipe;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.util.SponsorTimeInfo;
import org.schabi.newpipe.util.TimeFrame;

import java.util.concurrent.ExecutionException;

public class SponsorBlockApiTask extends AsyncTask<String, Void, JSONObject> {
    private static final Application APP = App.getApp();
    private static final String SPONSOR_BLOCK_API_URL = "https://sponsor.ajay.app/api/";
    private static final String TAG = SponsorBlockApiTask.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    public SponsorTimeInfo getYouTubeVideoSponsorTimes(final String videoId)
            throws ExecutionException, InterruptedException, JSONException {

        JSONObject obj = execute("getVideoSponsorTimes?videoID=" + videoId).get();
        JSONArray arrayObj = obj.getJSONArray("sponsorTimes");

        SponsorTimeInfo result = new SponsorTimeInfo();

        for (int i = 0; i < arrayObj.length(); i++) {
            JSONArray subArrayObj = arrayObj.getJSONArray(i);

            double startTime = subArrayObj.getDouble(0) * 1000;
            double endTime = subArrayObj.getDouble(1) * 1000;

            TimeFrame timeFrame = new TimeFrame(startTime, endTime);

            result.timeFrames.add(timeFrame);
        }

        return result;
    }

    @Override
    protected JSONObject doInBackground(final String... strings) {
        if (isCancelled() || !isConnected()) {
            return null;
        }

        try {
            String responseBody =
                    DownloaderImpl
                            .getInstance()
                            .get(SPONSOR_BLOCK_API_URL + strings[0])
                            .responseBody();

            return new JSONObject(responseBody);

        } catch (Exception ex) {
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(ex));
            }
        }

        return null;
    }

    private boolean isConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) APP.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null
                && cm.getActiveNetworkInfo().isConnected();
    }
}
