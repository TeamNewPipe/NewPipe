/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings

import android.content.Context
import org.schabi.newpipe.extractor.services.vimeo.VimeoPublicApi
import org.schabi.newpipe.network.VimeoApiTokenStore
import org.schabi.newpipe.util.InfoCache

object VimeoApiSettings {
    @Volatile private var token: String? = null

    fun configure(context: Context) {
        token = VimeoApiTokenStore(context.applicationContext).read()
        VimeoPublicApi.setTokenProvider { token }
    }

    /** Call only after the official API has verified that the token has the public scope alone. */
    @Synchronized
    fun saveVerified(context: Context, value: String): Boolean {
        if (!VimeoApiTokenStore(context.applicationContext).save(value)) return false
        token = value
        VimeoPublicApi.setTokenProvider { token }
        InfoCache.getInstance().clearCache()
        return true
    }

    @Synchronized
    fun clear(context: Context): Boolean {
        if (!VimeoApiTokenStore(context.applicationContext).clear()) return false
        token = null
        VimeoPublicApi.setTokenProvider { token }
        InfoCache.getInstance().clearCache()
        return true
    }
}
