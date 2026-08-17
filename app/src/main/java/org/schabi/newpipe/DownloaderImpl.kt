package org.schabi.newpipe

import android.content.Context
import androidx.preference.PreferenceManager
import java.io.IOException
import java.util.Arrays
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.Stream
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.util.InfoCache

class DownloaderImpl private constructor(builder: OkHttpClient.Builder) : Downloader() {
    val client: OkHttpClient = builder
        .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 20
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val mCookies: MutableMap<String, String> = HashMap()

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        const val YOUTUBE_RESTRICTED_MODE_COOKIE_KEY = "youtube_restricted_mode_key"
        const val YOUTUBE_RESTRICTED_MODE_COOKIE = "PREF=f2=8000000"
        const val YOUTUBE_DOMAIN = "youtube.com"

        @JvmStatic
        var instance: DownloaderImpl? = null
            private set

        @JvmStatic
        fun init(builder: OkHttpClient.Builder?): DownloaderImpl {
            val newInstance = DownloaderImpl(builder ?: OkHttpClient.Builder())
            instance = newInstance
            return newInstance
        }
    }

    fun getCookies(url: String): String {
        val youtubeCookie = if (url.contains(YOUTUBE_DOMAIN)) {
            getCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY)
        } else {
            null
        }

        // Recaptcha cookie is always added TODO: not sure if this is necessary
        return Stream.of(youtubeCookie, getCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY))
            .filter { it != null }
            .flatMap { cookies -> Arrays.stream(cookies!!.split("; *".toRegex()).toTypedArray()) }
            .distinct()
            .collect(Collectors.joining("; "))
    }

    fun getCookie(key: String): String? {
        return mCookies[key]
    }

    fun setCookie(key: String, cookie: String) {
        mCookies[key] = cookie
    }

    fun removeCookie(key: String) {
        mCookies.remove(key)
    }

    fun updateYoutubeRestrictedModeCookies(context: Context) {
        val restrictedModeEnabledKey = context.getString(R.string.youtube_restricted_mode_enabled)
        val restrictedModeEnabled = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(restrictedModeEnabledKey, false)
        updateYoutubeRestrictedModeCookies(restrictedModeEnabled)
    }

    fun updateYoutubeRestrictedModeCookies(youtubeRestrictedModeEnabled: Boolean) {
        if (youtubeRestrictedModeEnabled) {
            setCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY, YOUTUBE_RESTRICTED_MODE_COOKIE)
        } else {
            removeCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY)
        }
        InfoCache.clearCache()
    }

    @Throws(IOException::class)
    fun getContentLength(url: String): Long {
        return try {
            val response = head(url)
            response.getHeader("Content-Length")?.toLong() ?: throw IOException("Invalid content length")
        } catch (e: NumberFormatException) {
            throw IOException("Invalid content length", e)
        } catch (e: ReCaptchaException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = dataToSend?.let { RequestBody.create(null, it) }

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        val cookies = getCookies(url)
        if (cookies.isNotEmpty()) {
            requestBuilder.addHeader("Cookie", cookies)
        }

        headers.forEach { (headerName, headerValueList) ->
            requestBuilder.removeHeader(headerName)
            headerValueList.forEach { headerValue ->
                requestBuilder.addHeader(headerName, headerValue)
            }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }

            val responseBodyToReturn = response.body?.string()

            val latestUrl = response.request.url.toString()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBodyToReturn,
                latestUrl
            )
        }
    }
}
