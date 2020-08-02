package org.schabi.newpipe;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.nostra13.universalimageloader.cache.memory.impl.LRULimitedMemoryCache;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import org.acra.ACRA;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.CoreConfiguration;
import org.acra.config.CoreConfigurationBuilder;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.settings.extensions.ManageExtensionsFragment;
import org.schabi.newpipe.util.ExceptionUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.StateSaver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

import dalvik.system.PathClassLoader;
import io.reactivex.exceptions.CompositeException;
import io.reactivex.exceptions.MissingBackpressureException;
import io.reactivex.exceptions.OnErrorNotImplementedException;
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
    private static App app;

    public static App getApp() {
        return app;
    }

    @Override
    protected void attachBaseContext(final Context base) {
        super.attachBaseContext(base);
        initACRA();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        app = this;

        // Initialize settings first because others inits can use its values
        NewPipeSettings.initSettings(this);

        NewPipe.init(getDownloader(),
                Localization.getPreferredLocalization(this),
                Localization.getPreferredContentCountry(this));

        initExtensions();

        Localization.init(getApplicationContext());

        StateSaver.init(this);
        initNotificationChannel();

        ServiceHelper.initServices(this);

        // Initialize image loader
        ImageLoader.getInstance().init(getImageLoaderConfigurations(10, 50));

        configureRxJavaErrorHandler();

        // Check for new version
        new CheckForNewAppVersionTask().execute();
    }

    private void initExtensions() {
        final String path = getApplicationInfo().dataDir + "/extensions/";
        final File dir = new File(path);
        if (!dir.exists()) {
            return;
        }
        for (final String extension : dir.list()) {
            try {
                // Delete incomplete extensions
                final File extensionDir = new File(path + extension);
                boolean hasAbout = false;
                boolean hasClasses = false;
                boolean hasIcon = false;
                for (final String file : extensionDir.list()) {
                    if (file.equals("about.json")) {
                        hasAbout = true;
                    } else if (file.equals("classes.dex")) {
                        hasClasses = true;
                    } else if (file.equals("icon.png")) {
                        hasIcon = true;
                    }
                }
                if (!hasAbout || !hasClasses || !hasIcon) {
                    ManageExtensionsFragment.removeExtension(path + extension);
                }

                final FileInputStream aboutStream = new FileInputStream(new File(
                        path + extension + "/about.json"));
                final JsonObject about = JsonParser.object().from(aboutStream);
                final String className = about.getString("class");
                final String version = about.getString("version");

                // Delete extensions for different NewPipe versions
                if (!version.equals(BuildConfig.VERSION_NAME)) {
                    ManageExtensionsFragment.removeExtension(path + extension);
                    continue;
                }

                final String dexPath = path + extension + "/classes.dex";
                final PathClassLoader pathClassLoader = new PathClassLoader(dexPath,
                        getClassLoader());
                final Class<StreamingService> serviceClass
                        = (Class<StreamingService>) pathClassLoader.loadClass(className);

                if (about.has("replaces")) {
                    ServiceList.replaceService(serviceClass, about.getInt("replaces"));
                } else {
                    ServiceList.addService(serviceClass);
                }
            } catch (Exception ignored) { }
        }
    }

    protected Downloader getDownloader() {
        DownloaderImpl downloader = DownloaderImpl.init(null);
        setCookiesToDownloader(downloader);
        return downloader;
    }

    protected void setCookiesToDownloader(final DownloaderImpl downloader) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        final String key = getApplicationContext().getString(R.string.recaptcha_cookies_key);
        downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, prefs.getString(key, ""));
        downloader.updateYoutubeRestrictedModeCookies(getApplicationContext());
    }

    private void configureRxJavaErrorHandler() {
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull final Throwable throwable) {
                Log.e(TAG, "RxJavaPlugins.ErrorHandler called with -> : "
                        + "throwable = [" + throwable.getClass().getName() + "]");

                final Throwable actualThrowable;
                if (throwable instanceof UndeliverableException) {
                    // As UndeliverableException is a wrapper,
                    // get the cause of it to get the "real" exception
                    actualThrowable = throwable.getCause();
                } else {
                    actualThrowable = throwable;
                }

                final List<Throwable> errors;
                if (actualThrowable instanceof CompositeException) {
                    errors = ((CompositeException) actualThrowable).getExceptions();
                } else {
                    errors = Collections.singletonList(actualThrowable);
                }

                for (final Throwable error : errors) {
                    if (isThrowableIgnored(error)) {
                        return;
                    }
                    if (isThrowableCritical(error)) {
                        reportException(error);
                        return;
                    }
                }

                // Out-of-lifecycle exceptions should only be reported if a debug user wishes so,
                // When exception is not reported, log it
                if (isDisposedRxExceptionsReported()) {
                    reportException(actualThrowable);
                } else {
                    Log.e(TAG, "RxJavaPlugin: Undeliverable Exception received: ", actualThrowable);
                }
            }

            private boolean isThrowableIgnored(@NonNull final Throwable throwable) {
                // Don't crash the application over a simple network problem
                return ExceptionUtils.hasAssignableCause(throwable,
                        // network api cancellation
                        IOException.class, SocketException.class,
                        // blocking code disposed
                        InterruptedException.class, InterruptedIOException.class);
            }

            private boolean isThrowableCritical(@NonNull final Throwable throwable) {
                // Though these exceptions cannot be ignored
                return ExceptionUtils.hasAssignableCause(throwable,
                        NullPointerException.class, IllegalArgumentException.class, // bug in app
                        OnErrorNotImplementedException.class, MissingBackpressureException.class,
                        IllegalStateException.class); // bug in operator
            }

            private void reportException(@NonNull final Throwable throwable) {
                // Throw uncaught exception that will trigger the report system
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), throwable);
            }
        });
    }

    private ImageLoaderConfiguration getImageLoaderConfigurations(final int memoryCacheSizeMb,
                                                                  final int diskCacheSizeMb) {
        return new ImageLoaderConfiguration.Builder(this)
                .memoryCache(new LRULimitedMemoryCache(memoryCacheSizeMb * 1024 * 1024))
                .diskCacheSize(diskCacheSizeMb * 1024 * 1024)
                .imageDownloader(new ImageDownloader(getApplicationContext()))
                .build();
    }

    /**
     * Called in {@link #attachBaseContext(Context)} after calling the {@code super} method.
     * Should be overridden if MultiDex is enabled, since it has to be initialized before ACRA.
     */
    protected void initACRA() {
        if (ACRA.isACRASenderServiceProcess()) {
            return;
        }

        try {
            final CoreConfiguration acraConfig = new CoreConfigurationBuilder(this)
                    .setBuildConfigClass(BuildConfig.class)
                    .build();
            ACRA.init(this, acraConfig);
        } catch (ACRAConfigurationException ace) {
            ace.printStackTrace();
            ErrorActivity.reportError(this,
                    ace,
                    null,
                    null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
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

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(mChannel);

        setUpUpdateNotificationChannel(importance);
    }

    /**
     * Set up notification channel for app update.
     *
     * @param importance
     */
    @TargetApi(Build.VERSION_CODES.O)
    private void setUpUpdateNotificationChannel(final int importance) {
        final String appUpdateId
                = getString(R.string.app_update_notification_channel_id);
        final CharSequence appUpdateName
                = getString(R.string.app_update_notification_channel_name);
        final String appUpdateDescription
                = getString(R.string.app_update_notification_channel_description);

        NotificationChannel appUpdateChannel
                = new NotificationChannel(appUpdateId, appUpdateName, importance);
        appUpdateChannel.setDescription(appUpdateDescription);

        NotificationManager appUpdateNotificationManager
                = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        appUpdateNotificationManager.createNotificationChannel(appUpdateChannel);
    }

    protected boolean isDisposedRxExceptionsReported() {
        return false;
    }
}
