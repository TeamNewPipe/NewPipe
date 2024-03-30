package org.schabi.newpipe

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room.RoomDatabase.Builder.build
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder.build
import okhttp3.OkHttpClient.Builder.readTimeout
import okhttp3.Request.Builder.addHeader
import okhttp3.Request.Builder.build
import okhttp3.Request.Builder.header
import okhttp3.Request.Builder.method
import okhttp3.Request.Builder.removeHeader
import okhttp3.Request.Builder.url
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.schabi.newpipe.database.stream.model.StreamEntity.url
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.util.InfoCache
import java.io.IOException
import java.util.Arrays
import java.util.Objects
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.Stream

class DownloaderImpl private constructor(builder: Builder) : Downloader() {
    private val mCookies: MutableMap<String, String?>
    private val client: OkHttpClient

    init {
        client = builder
                .readTimeout(30, TimeUnit.SECONDS) //                .cache(new Cache(new File(context.getExternalCacheDir(), "okhttp"),
                //                        16 * 1024 * 1024))
                .build()
        mCookies = HashMap()
    }

    fun getCookies(url: String): String {
        val youtubeCookie: String? = if (url.contains(YOUTUBE_DOMAIN)) getCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY) else null

        // Recaptcha cookie is always added TODO: not sure if this is necessary
        return Stream.of<String?>(youtubeCookie, getCookie(ReCaptchaActivity.Companion.RECAPTCHA_COOKIES_KEY))
                .filter(Predicate<String?>({ obj: String? -> Objects.nonNull(obj) }))
                .flatMap<String?>(Function<String?, Stream<out String?>>({ cookies: String? -> Arrays.stream<String?>(cookies!!.split("; *".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()) }))
                .distinct()
                .collect(Collectors.joining("; "))
    }

    fun getCookie(key: String): String? {
        return mCookies.get(key)
    }

    fun setCookie(key: String, cookie: String?) {
        mCookies.put(key, cookie)
    }

    fun removeCookie(key: String) {
        mCookies.remove(key)
    }

    fun updateYoutubeRestrictedModeCookies(context: Context) {
        val restrictedModeEnabledKey: String = context.getString(R.string.youtube_restricted_mode_enabled)
        val restrictedModeEnabled: Boolean = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(restrictedModeEnabledKey, false)
        updateYoutubeRestrictedModeCookies(restrictedModeEnabled)
    }

    fun updateYoutubeRestrictedModeCookies(youtubeRestrictedModeEnabled: Boolean) {
        if (youtubeRestrictedModeEnabled) {
            setCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY,
                    YOUTUBE_RESTRICTED_MODE_COOKIE)
        } else {
            removeCookie(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY)
        }
        InfoCache.Companion.getInstance().clearCache()
    }

    /**
     * Get the size of the content that the url is pointing by firing a HEAD request.
     *
     * @param url an url pointing to the content
     * @return the size of the content, in bytes
     */
    @Throws(IOException::class)
    fun getContentLength(url: String?): Long {
        try {
            val response: Response = head(url)
            return response.getHeader("Content-Length")!!.toLong()
        } catch (e: NumberFormatException) {
            throw IOException("Invalid content length", e)
        } catch (e: ReCaptchaException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    public override fun execute(request: Request): Response {
        val httpMethod: String = request.httpMethod()
        val url: String = request.url()
        val headers: Map<String, List<String>> = request.headers()
        val dataToSend: ByteArray? = request.dataToSend()
        var requestBody: RequestBody? = null
        if (dataToSend != null) {
            requestBody = RequestBody.create(dataToSend)
        }
        val requestBuilder: Builder = Builder()
                .method(httpMethod, requestBody).url(url)
                .addHeader("User-Agent", USER_AGENT)
        val cookies: String = getCookies(url)
        if (!cookies.isEmpty()) {
            requestBuilder.addHeader("Cookie", cookies)
        }
        for (pair: Map.Entry<String, List<String>> in headers.entries) {
            val headerName: String = pair.key
            val headerValueList: List<String> = pair.value
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                for (headerValue: String? in headerValueList) {
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList.get(0))
            }
        }
        val response: okhttp3.Response = client.newCall(requestBuilder.build()).execute()
        if (response.code() == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }
        val body: ResponseBody? = response.body()
        var responseBodyToReturn: String? = null
        if (body != null) {
            responseBodyToReturn = body.string()
        }
        val latestUrl: String = response.request().url().toString()
        return Response(response.code(), response.message(), response.headers().toMultimap(),
                responseBodyToReturn, latestUrl)
    }

    companion object {
        val USER_AGENT: String = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
        val YOUTUBE_RESTRICTED_MODE_COOKIE_KEY: String = "youtube_restricted_mode_key"
        val YOUTUBE_RESTRICTED_MODE_COOKIE: String = "PREF=f2=8000000"
        val YOUTUBE_DOMAIN: String = "youtube.com"
        private var instance: DownloaderImpl? = null

        /**
         * It's recommended to call exactly once in the entire lifetime of the application.
         *
         * @param builder if null, default builder will be used
         * @return a new instance of [DownloaderImpl]
         */
        fun init(builder: Builder?): DownloaderImpl? {
            instance = DownloaderImpl(
                    if (builder != null) builder else Builder())
            return instance
        }

        fun getInstance(): DownloaderImpl? {
            return instance
        }
    }
}
