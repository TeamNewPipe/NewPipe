package com.kt.apps.video.data.api

import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import org.schabi.newpipe.BuildConfig
import timber.log.Timber

class LoggerImpl : Logger {
    private val analytics by lazy { Firebase.analytics }
    override fun init() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun logEvent(event: String, params: Map<String, String>?) {
        val bundle = params?.run {
            Bundle().apply {
                params.forEach { item ->
                    putString(item.key, item.value)
                }
            }
        }
        analytics.logEvent(event, bundle)
        Timber.tag("logEvent").d("$event, params: $params")
    }
}
