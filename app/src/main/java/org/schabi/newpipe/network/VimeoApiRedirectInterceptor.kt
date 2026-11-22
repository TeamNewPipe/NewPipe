/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.network

import java.io.IOException
import java.net.ProtocolException
import okhttp3.Interceptor
import okhttp3.Response

/** Runs on each network response before either redirect implementation can follow it. */
class VimeoApiRedirectInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.url.isHttps && request.url.host == "api.vimeo.com" &&
            request.header("Authorization") != null && response.code in 300..399
        ) {
            response.close()
            throw ProtocolException("Vimeo API redirects are not permitted")
        }
        return response
    }
}
