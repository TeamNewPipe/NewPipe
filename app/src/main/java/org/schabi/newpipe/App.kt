package org.schabi.newpipe

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.util.DebugLogger
import okio.Path.Companion.toOkioPath
import com.jakewharton.processphoenix.ProcessPhoenix
import org.acra.ACRA.init
import org.acra.ACRA.isACRASenderServiceProcess
import org.acra.config.CoreConfigurationBuilder
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.util.BridgeStateSaverInitializer
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.StateSaver
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality
import org.schabi.newpipe.util.potoken.PoTokenProviderImpl
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import net.newpipe.app.di.KoinApp
import net.newpipe.app.navigation.navModule
import org.schabi.newpipe.ui.navigation.appNavModule
import org.koin.dsl.module

/*
 * Copyright (C) Hans-Christoph Steiner 2016 <hans@eds.org>
 * App.kt is part of NewPipe.
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
open class App :
    Application(),
    SingletonImageLoader.Factory {
    var isFirstRun = false
        private set
    var notificationsRequested = false
        private set

    fun setNotificationsRequested() {
        notificationsRequested = true
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        initACRA()
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        if (ProcessPhoenix.isPhoenixProcess(this)) {
            Log.i(TAG, "This is a phoenix process! Aborting initialization of App[onCreate]")
            return
        }

        startKoin {
            androidContext(this@App)
            modules(
                module {
                    includes(navModule(), appNavModule(onCloseRequest = {}))
                }
            )
        }

        // check if the last used preference version is set
        // to determine whether this is the first app run
        val lastUsedPrefVersion =
            PreferenceManager
                .getDefaultSharedPreferences(this)
                .getInt(getString(R.string.last_used_preferences_version), -1)
        isFirstRun = lastUsedPrefVersion == -1

        // Initialize settings first because other initializations can use its values
        NewPipeSettings.initSettings(this)

        NewPipe.init(
            getDownloader(),
            Localization.getPreferredLocalization(this),
            Localization.getPreferredContentCountry(this)
        )
        Localization.initPrettyTime(Localization.resolvePrettyTime())

        BridgeStateSaverInitializer.init(this)
        StateSaver.init(this)
        initNotificationChannels()

        ServiceHelper.initServices(this)

        // Initialize image loader
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        ImageStrategy.setPreferredImageQuality(
            PreferredImageQuality.fromPreferenceKey(
                this,
                prefs.getString(
                    getString(R.string.image_quality_key),
                    getString(R.string.image_quality_default)
                )
            )
        )

        YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl)
    }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader
        .Builder(this)
        .logger(if (BuildConfig.DEBUG) DebugLogger() else null)
        .allowRgb565(getSystemService<ActivityManager>()!!.isLowRamDevice)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(this, 0.25)
                .strongReferencesEnabled(true)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(100L * 1024 * 1024)
                .build()
        }
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = DownloaderImpl.instance!!.client))
        }.build()

    protected open fun getDownloader(): Downloader {
        val downloader = DownloaderImpl.init(null)
        setCookiesToDownloader(downloader)
        return downloader
    }

    protected fun setCookiesToDownloader(downloader: DownloaderImpl) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val key = getString(R.string.recaptcha_cookies_key)
        prefs.getString(key, null)?.let {
            downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, it)
        }
        downloader.updateYoutubeRestrictedModeCookies(this)
    }

    /**
     * Called in [.attachBaseContext] after calling the `super` method.
     * Should be overridden if MultiDex is enabled, since it has to be initialized before ACRA.
     */
    protected fun initACRA() {
        if (isACRASenderServiceProcess()) {
            return
        }

        val acraConfig =
            CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig::class.java)
        init(this, acraConfig)
    }

    private fun initNotificationChannels() {
        // Keep the importance below DEFAULT to avoid making noise on every notification update for
        // the main and update channels
        val mainChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.notification_channel_id),
                    NotificationManagerCompat.IMPORTANCE_LOW
                ).setName(getString(R.string.notification_channel_name))
                .setDescription(getString(R.string.notification_channel_description))
                .build()
        val appUpdateChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.app_update_notification_channel_id),
                    NotificationManagerCompat.IMPORTANCE_LOW
                ).setName(getString(R.string.app_update_notification_channel_name))
                .setDescription(getString(R.string.app_update_notification_channel_description))
                .build()
        val hashChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.hash_channel_id),
                    NotificationManagerCompat.IMPORTANCE_HIGH
                ).setName(getString(R.string.hash_channel_name))
                .setDescription(getString(R.string.hash_channel_description))
                .build()
        val errorReportChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.error_report_channel_id),
                    NotificationManagerCompat.IMPORTANCE_LOW
                ).setName(getString(R.string.error_report_channel_name))
                .setDescription(getString(R.string.error_report_channel_description))
                .build()
        val newStreamChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.streams_notification_channel_id),
                    NotificationManagerCompat.IMPORTANCE_DEFAULT
                ).setName(getString(R.string.streams_notification_channel_name))
                .setDescription(getString(R.string.streams_notification_channel_description))
                .build()

        val channels = listOf(mainChannel, appUpdateChannel, hashChannel, errorReportChannel, newStreamChannel)

        NotificationManagerCompat.from(this).createNotificationChannelsCompat(channels)
    }

    companion object {
        const val PACKAGE_NAME: String = BuildConfig.APPLICATION_ID
        private val TAG = App::class.java.toString()

        @JvmStatic
        lateinit var instance: App
            private set
    }
}
