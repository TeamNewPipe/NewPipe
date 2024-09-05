package com.kt.apps.video.data.api

interface Logger {
    fun init()
    fun logEvent(event: String, params: Map<String, String>? = null)
}
