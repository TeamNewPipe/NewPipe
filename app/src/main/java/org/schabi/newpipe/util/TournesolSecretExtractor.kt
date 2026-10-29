package org.schabi.newpipe.util

import android.util.Log
import io.reactivex.rxjava3.core.Single
import java.io.IOException
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl

class TournesolSecretExtractor {
    private val client = (DownloaderImpl.getInstance()?.getClient() ?: OkHttpClient())
        .newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun extractSecrets(): Single<Pair<String, String>> {
        return Single.create { emitter ->
            cachedSecrets?.let {
                emitter.onSuccess(it)
                return@create
            }

            try {
                Log.d(TAG, "Starting dynamic secret extraction...")

                val mainHtml = fetch(BASE_URL)
                val jsUrls = LinkedHashSet<String>()

                val hrefMatcher = HREF_PATTERN.matcher(mainHtml)
                while (hrefMatcher.find()) {
                    jsUrls.add(BASE_URL + hrefMatcher.group(1).substring(1))
                }

                val srcMatcher = SCRIPT_SRC_PATTERN.matcher(mainHtml)
                while (srcMatcher.find()) {
                    jsUrls.add(BASE_URL + srcMatcher.group(1).substring(1))
                }

                Log.d(TAG, "Found ${jsUrls.size} initial JS bundles.")

                val prioritizedUrls = ArrayList(jsUrls).sortedByDescending {
                    it.contains("components")
                }
                val chunkUrls = LinkedHashSet<String>()

                for (jsUrl in prioritizedUrls) {
                    if (emitter.isDisposed) {
                        return@create
                    }

                    Log.d(TAG, "Checking $jsUrl")
                    val jsContent = fetch(jsUrl)

                    val secrets = findCredentialsInText(jsContent)
                    if (secrets != null) {
                        cachedSecrets = secrets
                        emitter.onSuccess(secrets)
                        return@create
                    }

                    val chunkMatcher = CHUNK_PATTERN.matcher(jsContent)
                    while (chunkMatcher.find()) {
                        val chunkUrl = BASE_URL + chunkMatcher.group(1)
                        if (!jsUrls.contains(chunkUrl)) {
                            chunkUrls.add(chunkUrl)
                        }
                    }
                }

                for (chunkUrl in chunkUrls) {
                    if (emitter.isDisposed) {
                        return@create
                    }

                    Log.d(TAG, "Checking referenced chunk: $chunkUrl")
                    val chunkContent = fetch(chunkUrl)
                    val secrets = findCredentialsInText(chunkContent)
                    if (secrets != null) {
                        cachedSecrets = secrets
                        emitter.onSuccess(secrets)
                        return@create
                    }
                }

                emitter.onSuccess(FALLBACK_CREDENTIALS)
            } catch (e: Exception) {
                Log.w(TAG, "Dynamic secret extraction failed, using fallback credentials", e)
                if (!emitter.isDisposed) {
                    emitter.onSuccess(FALLBACK_CREDENTIALS)
                }
            }
        }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.body == null) {
                throw IOException("Failed to fetch $url: ${response.code}")
            }
            return response.body!!.string()
        }
    }

    private fun findCredentialsInText(text: String): Pair<String, String>? {
        val idMatcher = ID_PATTERN.matcher(text)
        while (idMatcher.find()) {
            val potentialId = idMatcher.group(1)
            val secretMatcher = SECRET_PATTERN.matcher(text)
            if (secretMatcher.find()) {
                val potentialSecret = secretMatcher.group(1)
                Log.d(TAG, "Found credentials! ID: ${potentialId.substring(0, 5)}...")
                return Pair(potentialId, potentialSecret)
            }
        }
        return null
    }

    companion object {
        private const val TAG = "TournesolSecrets"
        private const val BASE_URL = "https://tournesol.app/"
        private val FALLBACK_CREDENTIALS = Pair(
            "uUhctKD6C5LC4rPkklnshylcr3PHHQ8DTXBon8Mx",
            "yWeUwik8IrtZ8pwiIaFeCh6vGvp8vCKyxfn8N8cQMd79xM2gfKSMBosSgzCpIyXuTlSCwYCWoZSnEyjlqNnqo89MjeNNAbNT43GAKt7WvokMQDEQoWe0ef1ed8xYXPNn"
        )

        @Volatile
        private var cachedSecrets: Pair<String, String>? = null

        internal fun getDefaultOrCachedSecrets(): Pair<String, String> = cachedSecrets ?: FALLBACK_CREDENTIALS

        private val SCRIPT_SRC_PATTERN =
            Pattern.compile("src=\\\"(/assets/.*?-([\\w-]+)\\.js)\\\"")
        private val HREF_PATTERN =
            Pattern.compile("href=\\\"(/assets/.*?-([\\w-]+)\\.js)\\\"")
        private val CHUNK_PATTERN =
            Pattern.compile("[\\\"'`](assets/[a-zA-Z0-9_-]+\\.js)[\\\"'`]")

        private val ID_PATTERN = Pattern.compile("[\\\"'`]([a-zA-Z0-9]{40})[\\\"'`:]")
        private val SECRET_PATTERN = Pattern.compile("[\\\"'`:]([a-zA-Z0-9]{128})[\\\"'`]")
    }
}
