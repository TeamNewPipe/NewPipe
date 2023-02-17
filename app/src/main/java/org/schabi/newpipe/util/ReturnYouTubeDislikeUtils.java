package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;

public final class ReturnYouTubeDislikeUtils {

    private static final String API_URL = "https://returnyoutubedislikeapi.com/votes?videoId=";
    private static final String TAG = ReturnYouTubeDislikeUtils.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    private ReturnYouTubeDislikeUtils() {
    }

    @SuppressWarnings("CheckStyle")
    public static int getDislikes(final Context context,
            final StreamInfo streamInfo) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean isReturnYouTubeDislikeEnabled = prefs.getBoolean(context
                .getString(R.string.enable_return_youtube_dislike_key), false);

        if (!isReturnYouTubeDislikeEnabled) {
            return -1;
        }

        if (streamInfo.getServiceId() != ServiceList.YouTube.getServiceId()) {
            return -1;
        }

        JsonObject response = null;

        try {
            final String responseBody =
                    DownloaderImpl
                            .getInstance()
                            .setCustomTimeout(3)
                            .get(API_URL + streamInfo.getId())
                            .responseBody();

            response = JsonParser.object().from(responseBody);

        } catch (final Exception ex) {
            if (DEBUG) {
                Log.w(TAG, Log.getStackTraceString(ex));
            }
        }

        if (response == null) {
            return -1;
        }

        if (response.has("dislikes")) {
            return response.getInt("dislikes", 0);
        }

        return -1;
    }
}
