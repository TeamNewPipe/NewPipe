package org.schabi.newpipe

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.nostra13.universalimageloader.cache.memory.impl.LRULimitedMemoryCache
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import io.reactivex.exceptions.CompositeException
import io.reactivex.exceptions.MissingBackpressureException
import io.reactivex.exceptions.OnErrorNotImplementedException
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.functions.Consumer
import io.reactivex.plugins.RxJavaPlugins
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.acra.ACRA
import org.acra.config.ACRAConfigurationException
import org.acra.config.CoreConfigurationBuilder
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.report.ErrorActivity
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.util.ExceptionUtils.Companion.hasAssignableCause
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.StateSaver

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
open class App : Application() {
    protected open val isDisposedRxExceptionsReported: Boolean
        get() = false

    protected open val downloader: Downloader
        get() {
            val downloader = DownloaderImpl.init(null)
            setCookiesToDownloader(downloader)
            return downloader
        }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initACRA()
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        // Initialize settings first because others inits can use its values
        SettingsActivity.initSettings(this)
        NewPipe.init(downloader,
                Localization.getPreferredLocalization(this),
                Localization.getPreferredContentCountry(this))
        Localization.init(applicationContext)
        StateSaver.init(this)
        initNotificationChannels()
        ServiceHelper.initServices(this)

        // Initialize image loader
        ImageLoader.getInstance().init(getImageLoaderConfigurations(10, 50))
        configureRxJavaErrorHandler()

        // Check for new version
        GlobalScope.launch { checkNewVersion() }
    }

    protected fun setCookiesToDownloader(downloader: DownloaderImpl) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val key = applicationContext.getString(R.string.recaptcha_cookies_key)
        downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, prefs.getString(key, ""))
        downloader.updateYoutubeRestrictedModeCookies(applicationContext)
    }

    private fun configureRxJavaErrorHandler() {
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler(object : Consumer<Throwable> {
            override fun accept(throwable: Throwable) {
                Log.e(TAG, "RxJavaPlugins.ErrorHandler called with -> : " +
                        "throwable = [" + throwable.javaClass.name + "]")
                val actualThrowable = if (throwable is UndeliverableException) {
                    // As UndeliverableException is a wrapper,
                    // get the cause of it to get the "real" exception
                    throwable.cause
                } else {
                    throwable
                }
                val errors = if (actualThrowable is CompositeException) {
                    actualThrowable.exceptions
                } else {
                    listOf(actualThrowable)
                }.filterNotNull()
                for (error in errors) {
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
                if (isDisposedRxExceptionsReported) {
                    reportException(actualThrowable!!)
                } else {
                    Log.e(TAG, "RxJavaPlugin: Undeliverable Exception received: ", actualThrowable)
                }
            }

            private fun isThrowableIgnored(throwable: Throwable): Boolean {
                // Don't crash the application over a simple network problem
                return hasAssignableCause(throwable, // network api cancellation
                        IOException::class.java, SocketException::class.java, // blocking code disposed
                        InterruptedException::class.java, InterruptedIOException::class.java)
            }

            private fun isThrowableCritical(throwable: Throwable): Boolean {
                // Though these exceptions cannot be ignored
                return hasAssignableCause(throwable,
                        NullPointerException::class.java, IllegalArgumentException::class.java, // bug in app
                        OnErrorNotImplementedException::class.java, MissingBackpressureException::class.java,
                        IllegalStateException::class.java) // bug in operator
            }

            private fun reportException(throwable: Throwable) {
                // Throw uncaught exception that will trigger the report system
                Thread.currentThread().uncaughtExceptionHandler
                        .uncaughtException(Thread.currentThread(), throwable)
            }
        })
    }

    private fun getImageLoaderConfigurations(memoryCacheSizeMb: Int, diskCacheSizeMb: Int): ImageLoaderConfiguration {
        return ImageLoaderConfiguration.Builder(this)
                .memoryCache(LRULimitedMemoryCache(memoryCacheSizeMb * 1024 * 1024))
                .diskCacheSize(diskCacheSizeMb * 1024 * 1024)
                .imageDownloader(ImageDownloader(applicationContext))
                .build()
    }

    /**
     * Called in [.attachBaseContext] after calling the `super` method.
     * Should be overridden if MultiDex is enabled, since it has to be initialized before ACRA.
     */
    protected open fun initACRA() {
        if (ACRA.isACRASenderServiceProcess()) {
            return
        }
        try {
            val acraConfig = CoreConfigurationBuilder(this)
                    .setBuildConfigClass(BuildConfig::class.java)
                    .build()
            ACRA.init(this, acraConfig)
        } catch (ace: ACRAConfigurationException) {
            ace.printStackTrace()
            ErrorActivity.reportError(this,
                    ace,
                    null,
                    null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Could not initialize ACRA crash report", R.string.app_ui_crash))
        }
    }

    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        var id = getString(R.string.notification_channel_id)
        var name = getString(R.string.notification_channel_name)
        var description = getString(R.string.notification_channel_description)

        // Keep this below DEFAULT to avoid making noise on every notification update
        val importance = NotificationManager.IMPORTANCE_LOW
        val mainChannel = NotificationChannel(id, name, importance)
        mainChannel.description = description
        id = getString(R.string.app_update_notification_channel_id)
        name = getString(R.string.app_update_notification_channel_name)
        description = getString(R.string.app_update_notification_channel_description)
        val appUpdateChannel = NotificationChannel(id, name, importance)
        appUpdateChannel.description = description
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannels(listOf(mainChannel, appUpdateChannel))
    }

    companion object {
        protected val TAG = App::class.java.toString()
        @JvmStatic
        lateinit var app: App
            private set
    }
}
