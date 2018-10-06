/*
 * Copyright 2018 Mauricio Colli <mauriciocolli@outlook.com>
 * BaseImportExportService.java is part of NewPipe
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

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;
import android.widget.Toast;

import org.reactivestreams.Publisher;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.local.subscription.ImportExportEventListener;
import org.schabi.newpipe.local.subscription.SubscriptionService;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.processors.PublishProcessor;

public abstract class BaseImportExportService extends Service {
    protected final String TAG = this.getClass().getSimpleName();

    protected NotificationManagerCompat notificationManager;
    protected NotificationCompat.Builder notificationBuilder;

    protected SubscriptionService subscriptionService;
    protected final CompositeDisposable disposables = new CompositeDisposable();
    protected final PublishProcessor<String> notificationUpdater = PublishProcessor.create();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        subscriptionService = SubscriptionService.getInstance(this);
        setupNotification();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposeAll();
    }

    protected void disposeAll() {
        disposables.clear();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification Impl
    //////////////////////////////////////////////////////////////////////////*/

    private static final int NOTIFICATION_SAMPLING_PERIOD = 2500;

    protected final AtomicInteger currentProgress = new AtomicInteger(-1);
    protected final AtomicInteger maxProgress = new AtomicInteger(-1);
    protected final ImportExportEventListener eventListener = new ImportExportEventListener() {
        @Override
        public void onSizeReceived(int size) {
            maxProgress.set(size);
            currentProgress.set(0);
        }

        @Override
        public void onItemCompleted(String itemName) {
            currentProgress.incrementAndGet();
            notificationUpdater.onNext(itemName);
        }
    };

    protected abstract int getNotificationId();
    @StringRes
    public abstract int getTitle();

    protected void setupNotification() {
        notificationManager = NotificationManagerCompat.from(this);
        notificationBuilder = createNotification();
        startForeground(getNotificationId(), notificationBuilder.build());

        final Function<Flowable<String>, Publisher<String>> throttleAfterFirstEmission = flow -> flow.limit(1)
                .concatWith(flow.skip(1).throttleLast(NOTIFICATION_SAMPLING_PERIOD, TimeUnit.MILLISECONDS));

        disposables.add(notificationUpdater
                .filter(s -> !s.isEmpty())
                .publish(throttleAfterFirstEmission)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateNotification));
    }

    protected void updateNotification(String text) {
        notificationBuilder.setProgress(maxProgress.get(), currentProgress.get(), maxProgress.get() == -1);

        final String progressText = currentProgress + "/" + maxProgress;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!TextUtils.isEmpty(text)) text = text + "  (" + progressText + ")";
        } else {
            notificationBuilder.setContentInfo(progressText);
        }

        if (!TextUtils.isEmpty(text)) notificationBuilder.setContentText(text);
        notificationManager.notify(getNotificationId(), notificationBuilder.build());
    }

    protected void stopService() {
        postErrorResult(null, null);
    }

    protected void stopAndReportError(@Nullable Throwable error, String request) {
        stopService();

        final ErrorActivity.ErrorInfo errorInfo = ErrorActivity.ErrorInfo.make(UserAction.SUBSCRIPTION, "unknown",
                request, R.string.general_error);
        ErrorActivity.reportError(this, error != null ? Collections.singletonList(error) : Collections.emptyList(),
                null, null, errorInfo);
    }

    protected void postErrorResult(String title, String text) {
        disposeAll();
        stopForeground(true);
        stopSelf();

        if (title == null) {
            return;
        }

        text = text == null ? "" : text;
        notificationBuilder = new NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(title)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentText(text);
        notificationManager.notify(getNotificationId(), notificationBuilder.build());
    }

    protected NotificationCompat.Builder createNotification() {
        return new NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setProgress(-1, -1, true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(getString(getTitle()));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Toast
    //////////////////////////////////////////////////////////////////////////*/

    protected Toast toast;

    protected void showToast(@StringRes int message) {
        showToast(getString(message));
    }

    protected void showToast(String message) {
        if (toast != null) toast.cancel();

        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error handling
    //////////////////////////////////////////////////////////////////////////*/

    protected void handleError(@StringRes int errorTitle, @NonNull Throwable error) {
        String message = getErrorMessage(error);

        if (TextUtils.isEmpty(message)) {
            final String errorClassName = error.getClass().getName();
            message = getString(R.string.error_occurred_detail, errorClassName);
        }

        showToast(errorTitle);
        postErrorResult(getString(errorTitle), message);
    }

    protected String getErrorMessage(Throwable error) {
        String message = null;
        if (error instanceof SubscriptionExtractor.InvalidSourceException) {
            message = getString(R.string.invalid_source);
        } else if (error instanceof FileNotFoundException) {
            message = getString(R.string.invalid_file);
        } else if (error instanceof IOException) {
            message = getString(R.string.network_error);
        }
        return message;
    }
}
