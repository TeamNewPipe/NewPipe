package org.schabi.newpipe;

import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import org.acra.ACRA;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.ConfigurationBuilder;
import org.acra.sender.ReportSenderFactory;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.report.AcraReportSenderFactory;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.SettingsActivity;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.StateSaver;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;

import io.reactivex.annotations.NonNull;
import io.reactivex.exceptions.CompositeException;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;

/*
 * Copyright (C) Hans-Christoph Steiner 2016 <hans@eds.org>
 * App.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class App extends Application {
    protected static final String TAG = App.class.toString();

    @SuppressWarnings("unchecked")
    private static final Class<? extends ReportSenderFactory>[] reportSenderFactoryClasses = new Class[]{AcraReportSenderFactory.class};

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        initACRA();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize settings first because others inits can use its values
        SettingsActivity.initSettings(this);

        NewPipe.init(Downloader.getInstance());
        NewPipeDatabase.init(this);
        StateSaver.init(this);
        initNotificationChannel();

        // Initialize image loader
        ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(this).build();
        ImageLoader.getInstance().init(config);

        configureRxJavaErrorHandler();
    }

    private void configureRxJavaErrorHandler() {
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable throwable) throws Exception {
                Log.e(TAG, "RxJavaPlugins.ErrorHandler called with -> : throwable = [" + throwable.getClass().getName() + "]");

                if (throwable instanceof UndeliverableException) {
                    // As UndeliverableException is a wrapper, get the cause of it to get the "real" exception
                    throwable = throwable.getCause();
                }

                if (throwable instanceof CompositeException) {
                    for (Throwable element : ((CompositeException) throwable).getExceptions()) {
                        if (checkThrowable(element)) return;
                    }
                }

                if (checkThrowable(throwable)) return;

                // Throw uncaught exception that will trigger the report system
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), throwable);
            }

            private boolean checkThrowable(@NonNull Throwable throwable) {
                // Don't crash the application over a simple network problem
                return ExtractorHelper.hasAssignableCauseThrowable(throwable,
                        IOException.class, SocketException.class, InterruptedException.class, InterruptedIOException.class);
            }
        });
    }

    private void initACRA() {
        try {
            final ACRAConfiguration acraConfig = new ConfigurationBuilder(this)
                    .setReportSenderFactoryClasses(reportSenderFactoryClasses)
                    .setBuildConfigClass(BuildConfig.class)
                    .build();
            ACRA.init(this, acraConfig);
        } catch (ACRAConfigurationException ace) {
            ace.printStackTrace();
            ErrorActivity.reportError(this, ace, null, null, ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                    "Could not initialize ACRA crash report", R.string.app_ui_crash));
        }
    }

    public void initNotificationChannel() {
        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        final String id = getString(R.string.notification_channel_id);
        final CharSequence name = getString(R.string.notification_channel_name);
        final String description = getString(R.string.notification_channel_description);

        // Keep this below DEFAULT to avoid making noise on every notification update
        final int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel mChannel = new NotificationChannel(id, name, importance);
        mChannel.setDescription(description);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(mChannel);
    }

}
