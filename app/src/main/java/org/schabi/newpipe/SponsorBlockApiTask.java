package org.schabi.newpipe;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.util.VideoSegment;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class SponsorBlockApiTask extends AsyncTask<String, Void, JsonArray> {
    private static final Application APP = App.getApp();
    private String apiUrl;
    private static final String TAG = SponsorBlockApiTask.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    public SponsorBlockApiTask(final String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public VideoSegment[] getYouTubeVideoSegments(final String videoId,
                                                  final boolean includeSponsorCategory,
                                                  final boolean includeIntroCategory,
                                                  final boolean includeOutroCategory,
                                                  final boolean includeInteractionCategory,
                                                  final boolean includeSelfPromoCategory,
                                                  final boolean includeMusicCategory)
            throws ExecutionException, InterruptedException, UnsupportedEncodingException {

        final ArrayList<String> categoryParamList = new ArrayList<>();

        if (includeSponsorCategory) {
            categoryParamList.add("sponsor");
        }
        if (includeIntroCategory) {
            categoryParamList.add("intro");
        }
        if (includeOutroCategory) {
            categoryParamList.add("outro");
        }
        if (includeInteractionCategory) {
            categoryParamList.add("interaction");
        }
        if (includeSelfPromoCategory) {
            categoryParamList.add("selfpromo");
        }
        if (includeMusicCategory) {
            categoryParamList.add("music_offtopic");
        }

        if (categoryParamList.size() == 0) {
            return null;
        }

        String categoryParams = "[\"" + TextUtils.join("\",\"", categoryParamList) + "\"]";
        categoryParams = URLEncoder.encode(categoryParams, "utf-8");

        final String params = "skipSegments?videoID=" + videoId + "&categories=" + categoryParams;

        final JsonArray arrayObj = execute(params).get();

        final ArrayList<VideoSegment> result = new ArrayList<>();

        for (int i = 0; i < arrayObj.size(); i++) {
            final JsonObject obj = (JsonObject) arrayObj.get(i);
            final JsonArray segments = (JsonArray) obj.get("segment");

            final double startTime = segments.getDouble(0) * 1000;
            final double endTime = segments.getDouble(1) * 1000;
            final String category = obj.getString("category");

            final VideoSegment segment = new VideoSegment(startTime, endTime, category);

            result.add(segment);
        }

        return result.toArray(new VideoSegment[0]);
    }

    @Override
    protected JsonArray doInBackground(final String... strings) {
        if (isCancelled() || !isConnected()) {
            return null;
        }

        try {
            final String responseBody =
                    DownloaderImpl
                            .getInstance()
                            .get(apiUrl + strings[0])
                            .responseBody();

            return JsonParser.array().from(responseBody);

        } catch (final Exception ex) {
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(ex));
            }
        }

        return null;
    }

    private boolean isConnected() {
        final ConnectivityManager cm =
                (ConnectivityManager) APP.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null
                && cm.getActiveNetworkInfo().isConnected();
    }
}
