package org.schabi.newpipe.util;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Utility class for putting the uploader url into the database - when required.
 */
public final class SaveUploaderUrlHelper {
    private SaveUploaderUrlHelper() {
    }

    // Public functions which call the function that does
    // the actual work with the correct parameters
    public static void saveUploaderUrlIfNeeded(@NonNull final Fragment fragment,
                                               @NonNull final StreamInfoItem infoItem,
                                               @NonNull final SaveUploaderUrlCallback callback) {
        saveUploaderUrlIfNeeded(fragment.requireContext(),
                infoItem.getServiceId(),
                infoItem.getUrl(),
                infoItem.getUploaderUrl(),
                callback);
    }
    public static void saveUploaderUrlIfNeeded(@NonNull final Context context,
                                               @NonNull final PlayQueueItem queueItem,
                                               @NonNull final SaveUploaderUrlCallback callback) {
        saveUploaderUrlIfNeeded(context,
                queueItem.getServiceId(),
                queueItem.getUrl(),
                queueItem.getUploaderUrl(),
                callback);
    }

    /**
     * Fetches and saves the uploaderUrl if it is empty (meaning that it does
     * not exist in the video item). The callback is called with either the
     * fetched uploaderUrl, or the already saved uploaderUrl, but it is always
     * called with a valid uploaderUrl that can be used to show channel details.
     *
     * @param context       Context
     * @param serviceId     The serviceId of the item
     * @param url           The item url
     * @param uploaderUrl   The uploaderUrl of the item, if null or empty, it
     *                      will be fetched using the item url.
     * @param callback      The callback that returns the fetched or existing
     *                      uploaderUrl
     */
    private static void saveUploaderUrlIfNeeded(@NonNull final Context context,
                                         final int serviceId,
                                         @NonNull final String url,
                                         // Only used if not null or empty
                                         @Nullable final String uploaderUrl,
                                         @NonNull final SaveUploaderUrlCallback callback) {
        if (isNullOrEmpty(uploaderUrl)) {
            Toast.makeText(context, R.string.loading_channel_details,
                    Toast.LENGTH_SHORT).show();
            ExtractorHelper.getStreamInfo(serviceId, url, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> {
                        NewPipeDatabase.getInstance(context).streamDAO()
                                .setUploaderUrl(serviceId, url, result.getUploaderUrl())
                                .subscribeOn(Schedulers.io()).subscribe();
                        callback.onCallback(result.getUploaderUrl());
                    }, throwable -> ErrorUtil.createNotification(context,
                            new ErrorInfo(throwable, UserAction.REQUESTED_CHANNEL,
                                    "Could not load channel details")
                    ));
        } else {
            callback.onCallback(uploaderUrl);
        }
    }

    public interface SaveUploaderUrlCallback {
        void onCallback(@NonNull String uploaderUrl);
    }
}
