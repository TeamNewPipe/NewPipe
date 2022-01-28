package org.schabi.newpipe.util;

import android.util.Log;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.extractor.stream.StreamInfo;

public final class ReturnYoutubeDislikeUtils {

    private static final String APIURL = "https://returnyoutubedislikeapi.com/votes?videoId=";
    private static final String TAG = ReturnYoutubeDislikeUtils.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    private ReturnYoutubeDislikeUtils() {
    }

    @SuppressWarnings("CheckStyle")
    public static int getDislikes(/*final Context context,*/
            final StreamInfo streamInfo) {
      /*
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean isReturnYoutubeDislikeEnabled = prefs.getBoolean(context
                .getString(R.string.return_youtube_dislikes_enable_key), false);

        if (!isReturnYoutubeDislikeEnabled) {
            return -1;
        }
       */

        if (!streamInfo.getUrl().startsWith("https://www.youtube.com")) {
            return -1;
        }

        JsonObject response = null;

        try {
            final String responseBody =
                    DownloaderImpl
                            .getInstance()
                            .setCustomTimeout(3)
                            .get(APIURL + streamInfo.getId())
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
