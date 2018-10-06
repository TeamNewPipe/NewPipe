/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * SubscriptionsImportService.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.local.subscription.services;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.subscription.SubscriptionItem;
import org.schabi.newpipe.local.subscription.ImportExportJsonHelper;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Notification;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.MainActivity.DEBUG;

public class SubscriptionsImportService extends BaseImportExportService {
    public static final int CHANNEL_URL_MODE = 0;
    public static final int INPUT_STREAM_MODE = 1;
    public static final int PREVIOUS_EXPORT_MODE = 2;
    public static final String KEY_MODE = "key_mode";
    public static final String KEY_VALUE = "key_value";

    /**
     * A {@link LocalBroadcastManager local broadcast} will be made with this action when the import is successfully completed.
     */
    public static final String IMPORT_COMPLETE_ACTION = "org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.IMPORT_COMPLETE";

    private Subscription subscription;
    private int currentMode;
    private int currentServiceId;

    @Nullable
    private String channelUrl;
    @Nullable
    private InputStream inputStream;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || subscription != null) return START_NOT_STICKY;

        currentMode = intent.getIntExtra(KEY_MODE, -1);
        currentServiceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, Constants.NO_SERVICE_ID);

        if (currentMode == CHANNEL_URL_MODE) {
            channelUrl = intent.getStringExtra(KEY_VALUE);
        } else {
            final String filePath = intent.getStringExtra(KEY_VALUE);
            if (TextUtils.isEmpty(filePath)) {
                stopAndReportError(new IllegalStateException("Importing from input stream, but file path is empty or null"), "Importing subscriptions");
                return START_NOT_STICKY;
            }

            try {
                inputStream = new FileInputStream(new File(filePath));
            } catch (FileNotFoundException e) {
                handleError(e);
                return START_NOT_STICKY;
            }
        }

        if (currentMode == -1 || currentMode == CHANNEL_URL_MODE && channelUrl == null) {
            final String errorDescription = "Some important field is null or in illegal state: currentMode=[" + currentMode + "], channelUrl=[" + channelUrl + "], inputStream=[" + inputStream + "]";
            stopAndReportError(new IllegalStateException(errorDescription), "Importing subscriptions");
            return START_NOT_STICKY;
        }

        startImport();
        return START_NOT_STICKY;
    }

    @Override
    protected int getNotificationId() {
        return 4568;
    }

    @Override
    public int getTitle() {
        return R.string.import_ongoing;
    }

    @Override
    protected void disposeAll() {
        super.disposeAll();
        if (subscription != null) subscription.cancel();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Imports
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * How many extractions running in parallel.
     */
    public static final int PARALLEL_EXTRACTIONS = 8;

    /**
     * Number of items to buffer to mass-insert in the subscriptions table, this leads to
     * a better performance as we can then use db transactions.
     */
    public static final int BUFFER_COUNT_BEFORE_INSERT = 50;

    private void startImport() {
        showToast(R.string.import_ongoing);

        Flowable<List<SubscriptionItem>> flowable = null;
        switch (currentMode) {
            case CHANNEL_URL_MODE:
                flowable = importFromChannelUrl();
                break;
            case INPUT_STREAM_MODE:
                flowable = importFromInputStream();
                break;
            case PREVIOUS_EXPORT_MODE:
                flowable = importFromPreviousExport();
                break;
        }

        if (flowable == null) {
            final String message = "Flowable given by \"importFrom\" is null (current mode: " + currentMode + ")";
            stopAndReportError(new IllegalStateException(message), "Importing subscriptions");
            return;
        }

        flowable.doOnNext(subscriptionItems -> eventListener.onSizeReceived(subscriptionItems.size()))
                .flatMap(Flowable::fromIterable)

                .parallel(PARALLEL_EXTRACTIONS)
                .runOn(Schedulers.io())
                .map((Function<SubscriptionItem, Notification<ChannelInfo>>) subscriptionItem -> {
                    try {
                        return Notification.createOnNext(ExtractorHelper
                                .getChannelInfo(subscriptionItem.getServiceId(), subscriptionItem.getUrl(), true)
                                .blockingGet());
                    } catch (Throwable e) {
                        return Notification.createOnError(e);
                    }
                })
                .sequential()

                .observeOn(Schedulers.io())
                .doOnNext(getNotificationsConsumer())
                .buffer(BUFFER_COUNT_BEFORE_INSERT)
                .map(upsertBatch())

                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscriber());
    }

    private Subscriber<List<SubscriptionEntity>> getSubscriber() {
        return new Subscriber<List<SubscriptionEntity>>() {

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(List<SubscriptionEntity> successfulInserted) {
                if (DEBUG) Log.d(TAG, "startImport() " + successfulInserted.size() + " items successfully inserted into the database");
            }

            @Override
            public void onError(Throwable error) {
                handleError(error);
            }

            @Override
            public void onComplete() {
                LocalBroadcastManager.getInstance(SubscriptionsImportService.this).sendBroadcast(new Intent(IMPORT_COMPLETE_ACTION));
                showToast(R.string.import_complete_toast);
                stopService();
            }
        };
    }

    private Consumer<Notification<ChannelInfo>> getNotificationsConsumer() {
        return notification -> {
            if (notification.isOnNext()) {
                String name = notification.getValue().getName();
                eventListener.onItemCompleted(!TextUtils.isEmpty(name) ? name : "");
            } else if (notification.isOnError()) {
                final Throwable error = notification.getError();
                final Throwable cause = error.getCause();
                if (error instanceof IOException) {
                    throw (IOException) error;
                } else if (cause != null && cause instanceof IOException) {
                    throw (IOException) cause;
                }

                eventListener.onItemCompleted("");
            }
        };
    }

    private Function<List<Notification<ChannelInfo>>, List<SubscriptionEntity>> upsertBatch() {
        return notificationList -> {
            final List<ChannelInfo> infoList = new ArrayList<>(notificationList.size());
            for (Notification<ChannelInfo> n : notificationList) {
                if (n.isOnNext()) infoList.add(n.getValue());
            }

            return subscriptionService.upsertAll(infoList);
        };
    }

    private Flowable<List<SubscriptionItem>> importFromChannelUrl() {
        return Flowable.fromCallable(() -> NewPipe.getService(currentServiceId)
                .getSubscriptionExtractor()
                .fromChannelUrl(channelUrl));
    }

    private Flowable<List<SubscriptionItem>> importFromInputStream() {
        return Flowable.fromCallable(() -> NewPipe.getService(currentServiceId)
                .getSubscriptionExtractor()
                .fromInputStream(inputStream));
    }

    private Flowable<List<SubscriptionItem>> importFromPreviousExport() {
        return Flowable.fromCallable(() -> ImportExportJsonHelper.readFrom(inputStream, null));
    }

    protected void handleError(@NonNull Throwable error) {
        super.handleError(R.string.subscriptions_import_unsuccessful, error);
    }
}