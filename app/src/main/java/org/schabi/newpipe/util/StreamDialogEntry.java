package org.schabi.newpipe.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class StreamDialogEntry {

    @StringRes
    public final int resource;
    @NonNull
    public final StreamDialogEntryAction action;

    public StreamDialogEntry(@StringRes final int resource,
                             @NonNull final StreamDialogEntryAction action) {
        this.resource = resource;
        this.action = action;
    }

    public String getString(@NonNull final Context context) {
        return context.getString(resource);
    }

    public interface StreamDialogEntryAction {
        void onClick(Fragment fragment, StreamInfoItem infoItem);
    }

    public static void openChannelFragment(@NonNull final Fragment fragment,
                                            @NonNull final StreamInfoItem item,
                                            final String uploaderUrl) {
        // For some reason `getParentFragmentManager()` doesn't work, but this does.
        NavigationHelper.openChannelFragment(
                fragment.requireActivity().getSupportFragmentManager(),
                item.getServiceId(), uploaderUrl, item.getUploaderName());
    }

    /**
     * Fetches a {@link StreamInfoItem} if it is incomplete and executes the callback.
     * <br />
     * This method is required if the info has been fetched
     * via a {@link org.schabi.newpipe.extractor.feed.FeedExtractor}.
     * FeedExtractors provide a fast and lightweight method to fetch info,
     * but the info might be incomplete
     * (see {@link org.schabi.newpipe.local.feed.service.FeedLoadService} for more details).
     * @param context
     * @param item the item which is checked and eventually loaded completely
     * @param callback
     */
    public static void fetchItemInfoIfSparse(@NonNull final Context context,
                                             @NonNull final StreamInfoItem item,
                                             @NonNull final Consumer<SinglePlayQueue> callback) {
        if (!(item.getStreamType() == StreamType.LIVE_STREAM
                || item.getStreamType() == StreamType.AUDIO_LIVE_STREAM)
                && item.getDuration() < 0) {
            // Sparse item: fetched by fast fetch
            ExtractorHelper.getStreamInfo(
                    item.getServiceId(),
                    item.getUrl(),
                    false
            )
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> {
                        final HistoryRecordManager recordManager =
                                new HistoryRecordManager(context);
                        recordManager.saveStreamState(result, 0)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .doOnError(throwable -> ErrorUtil.showSnackbar(
                                        context,
                                        new ErrorInfo(throwable, UserAction.REQUESTED_STREAM,
                                                item.getUrl(), item.getServiceId())))
                                .subscribe();

                        callback.accept(new SinglePlayQueue(result));
                    }, throwable -> ErrorUtil.createNotification(context,
                            new ErrorInfo(throwable, UserAction.REQUESTED_CHANNEL,
                                    "Could not fetch missing stream info")));
        } else {
            callback.accept(new SinglePlayQueue(item));
        }
    }

}
