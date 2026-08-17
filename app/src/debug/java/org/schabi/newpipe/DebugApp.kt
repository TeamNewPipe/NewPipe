package org.schabi.newpipe

import androidx.preference.PreferenceManager
import leakcanary.LeakCanary
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader

class DebugApp : App() {
    override fun onCreate() {
        super.onCreate()

        LeakCanary.config = LeakCanary.config.copy(
            dumpHeap = PreferenceManager
                .getDefaultSharedPreferences(this).getBoolean(
                    getString(
                        R.string.allow_heap_dumping_key
                    ),
                    false
                )
        )
    }

    override fun getDownloader(): Downloader {
        val downloader = DownloaderImpl.init(
            OkHttpClient.Builder()
        )
        setCookiesToDownloader(downloader)
        return downloader
    }
}
