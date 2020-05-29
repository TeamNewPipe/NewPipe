package org.schabi.newpipe

import android.content.Context
import androidx.multidex.MultiDex
import androidx.preference.PreferenceManager
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader

class DebugApp : App() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        initStetho()

        // Give each object 10 seconds to be GC'ed, before LeakCanary gets nosy on it
        AppWatcher.config = AppWatcher.config.copy(watchDurationMillis = 10000)
        LeakCanary.config = LeakCanary.config.copy(dumpHeap = PreferenceManager
                .getDefaultSharedPreferences(this).getBoolean(getString(
                        R.string.allow_heap_dumping_key), false))
    }

    override fun getDownloader(): Downloader {
        val downloader = DownloaderImpl.init(OkHttpClient.Builder()
                .addNetworkInterceptor(StethoInterceptor()))
        setCookiesToDownloader(downloader)
        return downloader
    }

    private fun initStetho() {
        // Create an InitializerBuilder
        val initializerBuilder = Stetho.newInitializerBuilder(this)

        // Enable Chrome DevTools
        initializerBuilder.enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))

        // Enable command line interface
        initializerBuilder.enableDumpapp(
                Stetho.defaultDumperPluginsProvider(applicationContext))

        // Use the InitializerBuilder to generate an Initializer
        val initializer = initializerBuilder.build()

        // Initialize Stetho with the Initializer
        Stetho.initialize(initializer)
    }

    override fun isDisposedRxExceptionsReported(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.allow_disposed_exceptions_key), false)
    }
}
