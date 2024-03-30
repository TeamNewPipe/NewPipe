package org.schabi.newpipe

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.jakewharton.processphoenix.ProcessPhoenix
import io.reactivex.rxjava3.exceptions.CompositeException
import io.reactivex.rxjava3.exceptions.MissingBackpressureException
import io.reactivex.rxjava3.exceptions.OnErrorNotImplementedException
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import org.acra.ACRA.init
import org.acra.ACRA.isACRASenderServiceProcess
import org.acra.config.CoreConfigurationBuilder
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.ktx.hasAssignableCause
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.StateSaver
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PicassoHelper
import org.schabi.newpipe.util.image.PreferredImageQuality
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.util.Objects

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
open class App() : Application() {
    private var isFirstRun: Boolean = false
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initACRA()
    }

    public override fun onCreate() {
        super.onCreate()
        app = this
        if (ProcessPhoenix.isPhoenixProcess(this)) {
            Log.i(TAG, ("This is a phoenix process! "
                    + "Aborting initialization of App[onCreate]"))
            return
        }

        // check if the last used preference version is set
        // to determine whether this is the first app run
        val lastUsedPrefVersion: Int = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt(getString(R.string.last_used_preferences_version), -1)
        isFirstRun = lastUsedPrefVersion == -1

        // Initialize settings first because other initializations can use its values
        NewPipeSettings.initSettings(this)
        NewPipe.init(getDownloader(),
                Localization.getPreferredLocalization(this),
                Localization.getPreferredContentCountry(this))
        Localization.initPrettyTime(Localization.resolvePrettyTime(getApplicationContext()))
        StateSaver.init(this)
        initNotificationChannels()
        ServiceHelper.initServices(this)

        // Initialize image loader
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        PicassoHelper.init(this)
        ImageStrategy.setPreferredImageQuality(PreferredImageQuality.Companion.fromPreferenceKey(this,
                prefs.getString(getString(R.string.image_quality_key),
                        getString(R.string.image_quality_default))))
        PicassoHelper.setIndicatorsEnabled((MainActivity.Companion.DEBUG
                && prefs.getBoolean(getString(R.string.show_image_indicators_key), false)))
        configureRxJavaErrorHandler()
    }

    public override fun onTerminate() {
        super.onTerminate()
        PicassoHelper.terminate()
    }

    protected open fun getDownloader(): Downloader? {
        val downloader: DownloaderImpl? = DownloaderImpl.Companion.init(null)
        setCookiesToDownloader(downloader)
        return downloader
    }

    protected fun setCookiesToDownloader(downloader: DownloaderImpl?) {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext())
        val key: String = getApplicationContext().getString(R.string.recaptcha_cookies_key)
        downloader!!.setCookie(ReCaptchaActivity.Companion.RECAPTCHA_COOKIES_KEY, prefs.getString(key, null))
        downloader.updateYoutubeRestrictedModeCookies(getApplicationContext())
    }

    private fun configureRxJavaErrorHandler() {
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler(object : Consumer<Throwable> {
            public override fun accept(throwable: Throwable) {
                Log.e(TAG, ("RxJavaPlugins.ErrorHandler called with -> : "
                        + "throwable = [" + throwable.javaClass.getName() + "]"))
                val actualThrowable: Throwable
                if (throwable is UndeliverableException) {
                    // As UndeliverableException is a wrapper,
                    // get the cause of it to get the "real" exception
                    actualThrowable = Objects.requireNonNull(throwable.cause)
                } else {
                    actualThrowable = throwable
                }
                val errors: List<Throwable>
                if (actualThrowable is CompositeException) {
                    errors = actualThrowable.getExceptions()
                } else {
                    errors = java.util.List.of(actualThrowable)
                }
                for (error: Throwable in errors) {
                    if (isThrowableIgnored(error)) {
                        return
                    }
                    if (isThrowableCritical(error)) {
                        reportException(error)
                        return
                    }
                }

                // Out-of-lifecycle exceptions should only be reported if a debug user wishes so,
                // When exception is not reported, log it
                if (isDisposedRxExceptionsReported()) {
                    reportException(actualThrowable)
                } else {
                    Log.e(TAG, "RxJavaPlugin: Undeliverable Exception received: ", actualThrowable)
                }
            }

            private fun isThrowableIgnored(throwable: Throwable): Boolean {
                // Don't crash the application over a simple network problem
                return throwable.hasAssignableCause( // network api cancellation
                        IOException::class.java, SocketException::class.java,  // blocking code disposed
                        InterruptedException::class.java, InterruptedIOException::class.java)
            }

            private fun isThrowableCritical(throwable: Throwable): Boolean {
                // Though these exceptions cannot be ignored
                return throwable.hasAssignableCause(NullPointerException::class.java, IllegalArgumentException::class.java,  // bug in app
                        OnErrorNotImplementedException::class.java, MissingBackpressureException::class.java, IllegalStateException::class.java) // bug in operator
            }

            private fun reportException(throwable: Throwable) {
                // Throw uncaught exception that will trigger the report system
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), throwable)
            }
        })
    }

    /**
     * Called in [.attachBaseContext] after calling the `super` method.
     * Should be overridden if MultiDex is enabled, since it has to be initialized before ACRA.
     */
    protected fun initACRA() {
        if (isACRASenderServiceProcess()) {
            return
        }
        val acraConfig: CoreConfigurationBuilder = CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig::class.java)
        init(this, acraConfig)
    }

    private fun initNotificationChannels() {
        // Keep the importance below DEFAULT to avoid making noise on every notification update for
        // the main and update channels
        val notificationChannelCompats: List<NotificationChannelCompat> = java.util.List.of(
                NotificationChannelCompat.Builder(getString(R.string.notification_channel_id),
                        NotificationManagerCompat.IMPORTANCE_LOW)
                        .setName(getString(R.string.notification_channel_name))
                        .setDescription(getString(R.string.notification_channel_description))
                        .build(),
                NotificationChannelCompat.Builder(getString(R.string.app_update_notification_channel_id),
                        NotificationManagerCompat.IMPORTANCE_LOW)
                        .setName(getString(R.string.app_update_notification_channel_name))
                        .setDescription(
                                getString(R.string.app_update_notification_channel_description))
                        .build(),
                NotificationChannelCompat.Builder(getString(R.string.hash_channel_id),
                        NotificationManagerCompat.IMPORTANCE_HIGH)
                        .setName(getString(R.string.hash_channel_name))
                        .setDescription(getString(R.string.hash_channel_description))
                        .build(),
                NotificationChannelCompat.Builder(getString(R.string.error_report_channel_id),
                        NotificationManagerCompat.IMPORTANCE_LOW)
                        .setName(getString(R.string.error_report_channel_name))
                        .setDescription(getString(R.string.error_report_channel_description))
                        .build(),
                NotificationChannelCompat.Builder(getString(R.string.streams_notification_channel_id),
                        NotificationManagerCompat.IMPORTANCE_DEFAULT)
                        .setName(getString(R.string.streams_notification_channel_name))
                        .setDescription(
                                getString(R.string.streams_notification_channel_description))
                        .build()
        )
        val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannelsCompat(notificationChannelCompats)
    }

    protected open fun isDisposedRxExceptionsReported(): Boolean {
        return false
    }

    fun isFirstRun(): Boolean {
        return isFirstRun
    }

    companion object {
        val PACKAGE_NAME: String = BuildConfig.APPLICATION_ID
        private val TAG: String = App::class.java.toString()
        private var app: App? = null
        fun getApp(): App {
            return (app)!!
        }
    }
}
