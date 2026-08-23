package org.schabi.newpipe.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.preference.PreferenceManager;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import us.shandian.giga.get.DirectDownloader;

import static org.schabi.newpipe.util.FilenameUtils.createFilename;
import static org.schabi.newpipe.util.SparseItemUtil.fetchStreamInfoAndSaveToDatabaseRx;

public class StreamProcessor {

    public interface ProcessingCallback {
        void onProcessItem(StreamInfo streamInfo) throws Exception;
    }

    private static final String NOTIFICATION_CHANNEL_ID = "stream_processing_channel";
    private static final int NOTIFICATION_ID = 1100;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<StreamInfoItem> failedStreamItems = new ArrayList<>();
    private Context appContext;
    Resources res;

    public void processStreamsSequentiallyWithProgress(
            @NonNull Context context,
            @NonNull List<StreamInfoItem> streams,
            DirectDownloader.DownloadType downloadType) {
        processStreamsSequentiallyWithProgress(context, streams, downloadType, null);
    }

    public void processStreamsSequentiallyWithProgress(
            @NonNull Context context,
            @NonNull List<StreamInfoItem> streams,
            @Nullable DirectDownloader.DownloadType downloadType,
            @Nullable ProcessingCallback customCallback) {

        if (streams.isEmpty()) {
            return;
        }

        this.appContext = context.getApplicationContext();
        this.res = appContext.getResources();
        failedStreamItems.clear();

        notificationManager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel(appContext);

        final int totalStreams = streams.size();
        final AtomicInteger processedCount = new AtomicInteger(0);
        final AtomicInteger failedCount = new AtomicInteger(0);
        final AtomicInteger skippedCount = new AtomicInteger(0);

        notificationBuilder = new NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(res.getString(R.string.stream_processing_notification_title))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        updateProgressNotification(res.getString(R.string.stream_processing_starting), 0, totalStreams, true, null);

        Disposable disposable = Flowable.fromIterable(streams)
                .concatMap(infoItem -> {
                    if (downloadType != null) {
                        String uri;
                        if (downloadType == DirectDownloader.DownloadType.AUDIO) {
                            uri = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.download_path_audio_key), "");
                        } else {
                            uri = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.download_path_video_key), "");
                        }
                        StoredDirectoryHelper storage = new StoredDirectoryHelper(context, Uri.parse(uri), null);
                        if (storage.findFileWithoutExtension(createFilename(context, infoItem.getName()))) {
                            skippedCount.incrementAndGet();
                            processedCount.incrementAndGet();
                            return Flowable.empty();
                        }
                    }
                    return fetchStreamInfoAndSaveToDatabaseRx(
                            appContext,
                            infoItem.getServiceId(),
                            infoItem.getUrl()
                    )
                            .toFlowable()
                            .doOnNext(streamInfo -> {
                                if (streamInfo == null || streamInfo.getServiceId() == -1) {
                                    throw new Exception("Fetched stream info is invalid for: " + infoItem.getUrl());
                                }
                                if (customCallback != null) {
                                    customCallback.onProcessItem(streamInfo);
                                } else if (downloadType != null) {
                                    new DirectDownloader(appContext, streamInfo, downloadType);
                                }
                            })
                            .doOnError(throwable -> {
                                System.err.println("Error processing stream item: " + infoItem.getUrl() + " - " + throwable.getMessage());
                                failedStreamItems.add(infoItem);
                                failedCount.incrementAndGet();
                            })
                            .onErrorResumeNext(throwable -> Flowable.empty())
                            .doOnComplete(() -> {
                                int currentAttempted = processedCount.incrementAndGet();
                                String progressText = res.getString(R.string.stream_processing_progress, currentAttempted, totalStreams);
                                if (failedCount.get() > 0) {
                                    progressText += " " + res.getString(R.string.stream_processing_failed_count, failedCount.get());
                                }
                                updateProgressNotification(progressText, currentAttempted, totalStreams, true, null);
                            });
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(() -> {
                    int successfulProcessedCount = processedCount.get() - failedCount.get() - skippedCount.get();
                    String completionMessage = res.getString(R.string.stream_processing_complete, successfulProcessedCount, failedCount.get(), skippedCount.get());

                    PendingIntent resultPendingIntent = null;
                    if (failedCount.get() > 0 && !failedStreamItems.isEmpty()) {
                        Intent resultIntent = new Intent(appContext, StreamFailureDetailActivity.class);
                        ArrayList<String> failedDetails = new ArrayList<>();
                        for (StreamInfoItem item : failedStreamItems) {
                            failedDetails.add(item.getName() + " (" + item.getUrl() + ")");
                        }
                        resultIntent.putStringArrayListExtra("failed_stream_details", failedDetails);
                        resultIntent.putExtra("notification_id_to_cancel", NOTIFICATION_ID);

                        resultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
                        }
                        resultPendingIntent = PendingIntent.getActivity(
                                appContext,
                                NOTIFICATION_ID,
                                resultIntent,
                                pendingIntentFlags
                        );
                    }

                    notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download_done);
                    updateProgressNotification(completionMessage, totalStreams, totalStreams, false, resultPendingIntent);
                })
                .doOnError(throwable -> {
                    System.err.println("Error in stream processing flow: " + throwable.getMessage());
                    notificationBuilder.setSmallIcon(android.R.drawable.ic_dialog_alert);
                    updateProgressNotification(res.getString(R.string.stream_processing_failed), processedCount.get(), totalStreams, false, null);
                })
                .subscribe(
                        ignored -> { },
                        throwable -> System.err.println("Overall flow error (already handled by doOnError): " + throwable.getMessage()),
                        () -> System.out.println("Stream processing sequence complete.")
                );
        disposables.add(disposable);
    }

    private void updateProgressNotification(String contentText, int progress, int maxProgress, boolean isOngoing, @Nullable PendingIntent clickIntent) {
        if (notificationBuilder == null) {
            notificationBuilder = new NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(res.getString(R.string.stream_processing_notification_title))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true);
            if (isOngoing) {
                notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
            } else {
                if (notificationBuilder.mActions.isEmpty()) {
                    notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download_done);
                }
            }
        }

        notificationBuilder.setContentText(contentText)
                .setOngoing(isOngoing)
                .setContentIntent(clickIntent);

        if (isOngoing) {
            notificationBuilder.setProgress(maxProgress, progress, (progress == maxProgress && contentText.equals(res.getString(R.string.stream_processing_starting))));
        } else {
            notificationBuilder.setProgress(0, 0, false);
        }

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Stream Processing Channel";
            String description = "Notifications for stream processing progress";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void dispose() {
        disposables.clear();
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        failedStreamItems.clear();
    }
}
