package org.schabi.newpipe.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public class MobileDataLiveStreamHelper {

    public static boolean shouldDisplayWarningForMobileData(PlayQueue queue, Context context) {
        boolean showWarningPreference = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.show_warning_live_stream_on_mobile), true);

        return showWarningPreference && containsLiveStream(queue) && isMobileDataActive(context);
    }

    private static boolean containsLiveStream(PlayQueue queue) {
        for (PlayQueueItem stream : queue.getStreams()) {
            boolean containsLiveStream = stream.getStreamType().equals(StreamType.LIVE_STREAM);
            if (containsLiveStream) {
                return true;
            }
        }

        return false;
    }

    private static boolean isMobileDataActive(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isMobileData = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isMobileData = cm.getNetworkCapabilities(cm.getActiveNetwork()).hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        } else {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnected()) {
                isMobileData = activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE;
            }
        }

        return isMobileData;
    }

}
