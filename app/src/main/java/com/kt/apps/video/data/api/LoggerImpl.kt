package com.kt.apps.video.data.api

import org.schabi.newpipe.BuildConfig
import timber.log.Timber

class LoggerImpl : Logger {
    override fun init() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
