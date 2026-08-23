package org.schabi.newpipe.util;

import static org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM;
import static org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import android.content.Context;
import android.content.res.Resources;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.reactivex.rxjava3.core.Single;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Utility class for fetching additional data for stream items when needed.
 */
public final class SparseItemUtil {
    private SparseItemUtil() {
    }

    /**
     * Use this to certainly obtain an single play queue with all of the data filled in when the
     * stream info item you are handling might be sparse, e.g. because it was fetched via a {@link
     * org.schabi.newpipe.extractor.feed.FeedExtractor}. FeedExtractors provide a fast and
     * lightweight method to fetch info, but the info might be incomplete (see
     * {@link org.schabi.newpipe.local.feed.service.FeedLoadService} for more details).
     *
     * @param context  Android context
     * @param item     item which is checked and eventually loaded completely
     * @param callback callback to call with the single play queue built from the original item if
     *                 all info was available, otherwise from the fetched {@link
     *                 org.schabi.newpipe.extractor.stream.StreamInfo}
     */
    public static void fetchItemInfoIfSparse(@NonNull final Context context,
                                             @NonNull final StreamInfoItem item,
                                             @NonNull final Consumer<SinglePlayQueue> callback) {
        if (((item.getStreamType() == LIVE_STREAM || item.getStreamType() == AUDIO_LIVE_STREAM)
                || item.getDuration() >= 0) && !isNullOrEmpty(item.getUploaderUrl())) {
            // if the duration is >= 0 (provided that the item is not a livestream) and there is an
            // uploader url, probably all info is already there, so there is no need to fetch it
            callback.accept(new SinglePlayQueue(item));
            return;
        }

        // either the duration or the uploader url are not available, so fetch more info
        fetchStreamInfoAndSaveToDatabase(context, item.getServiceId(), item.getUrl(),
                streamInfo -> callback.accept(new SinglePlayQueue(streamInfo)));
    }

    /**
     * Use this to certainly obtain an uploader url when the stream info item or play queue item you
     * are handling might not have the uploader url (e.g. because it was fetched with {@link
     * org.schabi.newpipe.extractor.feed.FeedExtractor}). A toast is shown if loading details is
     * required.
     *
     * @param context     Android context
     * @param serviceId   serviceId of the item
     * @param url         item url
     * @param uploaderUrl uploaderUrl of the item; if null or empty will be fetched
     * @param callback    callback to be called with either the original uploaderUrl, if it was a
     *                    valid url, otherwise with the uploader url obtained by fetching the {@link
     *                    org.schabi.newpipe.extractor.stream.StreamInfo} corresponding to the item
     */
    public static void fetchUploaderUrlIfSparse(@NonNull final Context context,
                                                final int serviceId,
                                                @NonNull final String url,
                                                @Nullable final String uploaderUrl,
                                                @NonNull final Consumer<String> callback) {
        if (!isNullOrEmpty(uploaderUrl)) {
            callback.accept(uploaderUrl);
            return;
        }
        fetchStreamInfoAndSaveToDatabase(context, serviceId, url,
                streamInfo -> callback.accept(streamInfo.getUploaderUrl()));
    }

    /**
     * Loads the stream info corresponding to the given data on an I/O thread, stores the result in
     * the database and calls the callback on the main thread with the result. A toast will be shown
     * to the user about loading stream details, so this needs to be called on the main thread.
     *
     * @param context   Android context
     * @param serviceId service id of the stream to load
     * @param url       url of the stream to load
     * @param callback  callback to be called with the result
     */
    public static void fetchStreamInfoAndSaveToDatabase(@NonNull final Context context,
                                                        final int serviceId,
                                                        @NonNull final String url,
                                                        final Consumer<StreamInfo> callback) {

        Toast.makeText(context, R.string.loading_stream_details, Toast.LENGTH_SHORT).show();

        ExtractorHelper.getStreamInfo(serviceId, url, false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    // save to database in the background (not on main thread)
                    Completable.fromAction(() -> NewPipeDatabase.getInstance(context)
                            .streamDAO().upsert(new StreamEntity(result)))
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .doOnError(throwable ->
                                    ErrorUtil.createNotification(context,
                                            new ErrorInfo(throwable, UserAction.REQUESTED_STREAM,
                                                    "Saving stream info to database", result)))
                            .subscribe();

                    // call callback on main thread with the obtained result
                    callback.accept(result);
                }, throwable -> ErrorUtil.createNotification(context,
                        new ErrorInfo(throwable, UserAction.REQUESTED_STREAM,
                                "Loading stream info: " + url, serviceId)
                ));
    }

    public static Single<StreamInfo> fetchStreamInfoAndSaveToDatabaseRx(
            @NonNull final Context context,
            final int serviceId,
            @NonNull final String url) {

        return ExtractorHelper.getStreamInfoWithoutException(serviceId, url, false)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .flatMap(result -> {
                    if (result.getServiceId() == -1) {
                        return Single.just(result);
                    }
                    // save to database in the background
                    return Completable.fromAction(() -> NewPipeDatabase.getInstance(context)
                                    .streamDAO().upsert(new StreamEntity(result)))
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .doOnError(throwable ->
                                    ErrorUtil.createNotification(context,
                                            new ErrorInfo(throwable, UserAction.REQUESTED_STREAM,
                                                    "Saving stream info to database", result)))
                            .andThen(Single.just(result)); // Emit the result after saving
                })
                .doOnError(throwable ->
                        System.err.println("Error processing stream item: " + url + " - " + throwable.getMessage())
                );
    }

}
