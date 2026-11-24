/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import org.schabi.newpipe.extractor.downloader.Downloader

/** Preserves the callback default when an API 24 plugin is loaded by this API 23 host. */
object PluginDownloaderAdapters {
    @JvmStatic
    fun asyncCallback(callback: Downloader.AsyncCallback): Downloader.AsyncCallback = if (callback.javaClass.classLoader === Downloader.AsyncCallback::class.java.classLoader) {
        callback
    } else {
        AsyncCallbackAdapter(callback)
    }

    private class AsyncCallbackAdapter(private val delegate: Downloader.AsyncCallback) : Downloader.AsyncCallback by delegate {
        override fun onError(error: Exception) {
            try {
                delegate.onError(error)
            } catch (failure: AbstractMethodError) {
                val declaration = try {
                    delegate.javaClass.getMethod("onError", Exception::class.java)
                } catch (_: NoSuchMethodException) {
                    null
                }
                // A concrete plugin method can fail internally; retain that failure.
                if (declaration != null && !declaration.declaringClass.isInterface) throw failure
                super<Downloader.AsyncCallback>.onError(error)
            }
        }
    }
}
