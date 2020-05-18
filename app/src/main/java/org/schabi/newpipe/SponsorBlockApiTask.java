package org.schabi.newpipe;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.util.Log;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.util.SponsorTimeInfo;
import org.schabi.newpipe.util.TimeFrame;

import java.util.concurrent.ExecutionException;

public class SponsorBlockApiTask extends AsyncTask<String, Void, JsonObject> {
    private static final Application APP = App.getApp();
    private String apiUrl;
    private static final String TAG = SponsorBlockApiTask.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    public SponsorBlockApiTask(final String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public SponsorTimeInfo getYouTubeVideoSponsorTimes(final String videoId)
            throws ExecutionException, InterruptedException {

        JsonObject obj = execute("getVideoSponsorTimes?videoID=" + videoId).get();
        JsonArray arrayObj = obj.getArray("sponsorTimes");

        SponsorTimeInfo result = new SponsorTimeInfo();

        for (int i = 0; i < arrayObj.size(); i++) {
            JsonArray subArrayObj = arrayObj.getArray(i);

            double startTime = subArrayObj.getDouble(0) * 1000;
            double endTime = subArrayObj.getDouble(1) * 1000;

            TimeFrame timeFrame = new TimeFrame(startTime, endTime);

            result.timeFrames.add(timeFrame);
        }

        return result;
    }

    @Override
    protected JsonObject doInBackground(final String... strings) {
        if (isCancelled() || !isConnected()) {
            return null;
        }

        try {
            String responseBody =
                    DownloaderImpl
                            .getInstance()
                            .get(apiUrl + strings[0])
                            .responseBody();

            return JsonParser.object().from(responseBody);

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
