package org.schabi.newpipe.util.potoken

import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.collection.ArrayMap
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.DownloaderImpl
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PoTokenWebView private constructor(
    context: Context,
    // to be used exactly once only during initialization!
    private val continuation: Continuation<PoTokenGenerator>,
) : PoTokenGenerator {
    private val webView = WebView(context)
    private val scope = MainScope()
    private val poTokenContinuations =
        Collections.synchronizedMap(ArrayMap<String, Continuation<String>>())
    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        onInitializationErrorCloseAndCancel(t)
    }
    private lateinit var expirationInstant: Instant

    //region Initialization
    init {
        val webViewSettings = webView.settings
        //noinspection SetJavaScriptEnabled we want to use JavaScript!
        webViewSettings.javaScriptEnabled = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webViewSettings, false)
        }
        webViewSettings.userAgentString = USER_AGENT
        webViewSettings.blockNetworkLoads = true // the WebView does not need internet access

        // so that we can run async functions and get back the result
        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.message().contains("Uncaught")) {
                    // There should not be any uncaught errors while executing the code, because
                    // everything that can fail is guarded by try-catch. Therefore, this likely
                    // indicates that there was a syntax error in the code, i.e. the WebView only
                    // supports a really old version of JS.

                    val fmt = "\"${m.message()}\", source: ${m.sourceId()} (${m.lineNumber()})"
                    val exception = BadWebViewException(fmt)
                    Log.e(TAG, "This WebView implementation is broken: $fmt")

                    onInitializationErrorCloseAndCancel(exception)
                    popAllPoTokenContinuations().forEach { (_, cont) -> cont.resumeWithException(exception) }
                }
                return super.onConsoleMessage(m)
            }
        }
    }

    /**
     * Must be called right after instantiating [PoTokenWebView] to perform the actual
     * initialization. This will asynchronously go through all the steps needed to load BotGuard,
     * run it, and obtain an `integrityToken`.
     */
    private fun loadHtmlAndObtainBotguard() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "loadHtmlAndObtainBotguard() called")
        }

        scope.launch(exceptionHandler) {
            val html = withContext(Dispatchers.IO) {
                webView.context.assets.open("po_token.html").bufferedReader().use { it.readText() }
            }

            // calls downloadAndRunBotguard() when the page has finished loading
            val data = html.replaceFirst("</script>", "\n$JS_INTERFACE.downloadAndRunBotguard()</script>")
            webView.loadDataWithBaseURL("https://www.youtube.com", data, "text/html", "utf-8", null)
        }
    }

    /**
     * Called during initialization by the JavaScript snippet appended to the HTML page content in
     * [loadHtmlAndObtainBotguard] after the WebView content has been loaded.
     */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "downloadAndRunBotguard() called")
        }

        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/Create",
            "[ \"$REQUEST_KEY\" ]",
        ) { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            webView.evaluateJavascript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }

    /**
     * Called during initialization by the JavaScript snippets from either
     * [downloadAndRunBotguard] or [onRunBotguardResult].
     */
    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "Initialization error from JavaScript: $error")
        }
        onInitializationErrorCloseAndCancel(buildExceptionForJsError(error))
    }

    /**
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard] after
     * obtaining the BotGuard execution output [botguardResponse].
     */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "botguardResponse: $botguardResponse")
        }
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { responseBody ->
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "GenerateIT response: $responseBody")
            }
            val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)

            // leave 10 minutes of margin just to be sure
            expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds).minus(10, ChronoUnit.MINUTES)

            webView.evaluateJavascript("this.integrityToken = $integrityToken") {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "initialization finished, expiration=${expirationTimeInSeconds}s")
                }
                continuation.resume(this)
            }
        }
    }
    //endregion

    //region Obtaining poTokens
    override suspend fun generatePoToken(identifier: String): String {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "generatePoToken() called with identifier $identifier")
                }
                addPoTokenEmitter(identifier, cont)
                webView.evaluateJavascript(
                    """try {
                        identifier = "$identifier"
                        u8Identifier = ${stringToU8(identifier)}
                        poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                        for (i = 0; i < poTokenU8.length; i++) {
                            if (i != 0) poTokenU8String += ","
                            poTokenU8String += poTokenU8[i]
                        }
                        $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                    } catch (error) {
                        $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                    }""",
                    null
                )
            }
        }
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] when an error occurs in calling the
     * JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "obtainPoToken error from JavaScript: $error")
        }
        popPoTokenContinuation(identifier)?.resumeWithException(buildExceptionForJsError(error))
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] with the original identifier and the
     * result of the JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Generated poToken (before decoding): identifier=$identifier poTokenU8=$poTokenU8")
        }
        val poToken = try {
            u8ToBase64(poTokenU8)
        } catch (t: Throwable) {
            popPoTokenContinuation(identifier)?.resumeWithException(t)
            return
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Generated poToken: identifier=$identifier poToken=$poToken")
        }
        popPoTokenContinuation(identifier)?.resume(poToken)
    }

    override val isExpired get() = Instant.now() > expirationInstant
    //endregion

    //region Handling multiple emitters
    /**
     * Adds the ([identifier], [continuation]) pair to the [poTokenContinuations] list. This makes
     * it so that multiple poToken requests can be generated in parallel, and the results will be
     * notified to the right continuations.
     */
    private fun addPoTokenEmitter(identifier: String, continuation: Continuation<String>) {
        poTokenContinuations[identifier] = continuation
    }

    /**
     * Extracts and removes from the [poTokenContinuations] list a [Continuation] based on its
     * [identifier]. The continuation is supposed to be used immediately after to either signal a
     * success or an error.
     */
    private fun popPoTokenContinuation(identifier: String): Continuation<String>? {
        return poTokenContinuations.remove(identifier)
    }

    /**
     * Clears [poTokenContinuations] and returns its previous contents. The continuations are supposed
     * to be used immediately after to either signal a success or an error.
     */
    private fun popAllPoTokenContinuations(): Map<String, Continuation<String>> {
        val result = poTokenContinuations.toMap()
        poTokenContinuations.clear()
        return result
    }
    //endregion

    //region Utils
    /**
     * Makes a POST request to [url] with the given [data] by setting the correct headers. Calls
     * [onInitializationErrorCloseAndCancel] in case of any network errors and also if the response
     * does not have HTTP code 200, therefore this is supposed to be used only during
     * initialization. Calls [handleResponseBody] with the response body if the response is
     * successful. The request is performed in the background and a disposable is added to
     * [disposables].
     */
    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit,
    ) {
        scope.launch(exceptionHandler) {
            val headers = mapOf(
                // replace the downloader user agent
                "User-Agent" to listOf(USER_AGENT),
                "Accept" to listOf("application/json"),
                "Content-Type" to listOf("application/json+protobuf"),
                "x-goog-api-key" to listOf(GOOGLE_API_KEY),
                "x-user-agent" to listOf("grpc-web-javascript/0.1"),
            )
            val response = withContext(Dispatchers.IO) {
                DownloaderImpl.getInstance().post(url, headers, data.toByteArray())
            }
            val httpCode = response.responseCode()
            if (httpCode != 200) {
                onInitializationErrorCloseAndCancel(PoTokenException("Invalid response code: $httpCode"))
            } else {
                handleResponseBody(response.responseBody())
            }
        }
    }

    /**
     * Handles any error happening during initialization, releasing resources and sending the error
     * to [continuation].
     */
    private fun onInitializationErrorCloseAndCancel(error: Throwable) {
        close()
        continuation.resumeWithException(error)
    }

    /**
     * Releases all [webView] resources.
     */
    @MainThread
    override fun close() {
        scope.cancel()

        webView.clearHistory()
        // clears RAM cache and disk cache (globally for all WebViews)
        webView.clearCache(true)

        // ensures that the WebView isn't doing anything when destroying it
        webView.loadUrl("about:blank")

        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }
    //endregion

    companion object : PoTokenGenerator.Factory {
        private val TAG = PoTokenWebView::class.simpleName
        // Public API key used by BotGuard, which has been got by looking at BotGuard requests
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw" // NOSONAR
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"

        override suspend fun getNewPoTokenGenerator(context: Context): PoTokenGenerator {
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val potWv = PoTokenWebView(context, cont)
                    potWv.loadHtmlAndObtainBotguard()
                }
            }
        }
    }
}
