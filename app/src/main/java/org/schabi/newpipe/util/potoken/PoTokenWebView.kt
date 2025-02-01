package org.schabi.newpipe.util.potoken

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.MainThread
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.DownloaderImpl
import java.time.Instant

class PoTokenWebView private constructor(
    context: Context,
    // to be used exactly once only during initialization!
    private val generatorEmitter: SingleEmitter<PoTokenGenerator>,
) : PoTokenGenerator {
    private val webView = WebView(context)
    private val disposables = CompositeDisposable() // used only during initialization
    private val poTokenEmitters = mutableListOf<Pair<String, SingleEmitter<String>>>()
    private lateinit var expirationInstant: Instant

    //region Initialization
    init {
        val webviewSettings = webView.settings
        //noinspection SetJavaScriptEnabled we want to use JavaScript!
        webviewSettings.javaScriptEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webviewSettings.safeBrowsingEnabled = false
        }
        webviewSettings.userAgentString = USER_AGENT
        webviewSettings.blockNetworkLoads = true // the WebView does not need internet access

        // so that we can run async functions and get back the result
        webView.addJavascriptInterface(this, JS_INTERFACE)
    }

    /**
     * Must be called right after instantiating [PoTokenWebView] to perform the actual
     * initialization. This will asynchronously go through all the steps needed to load BotGuard,
     * run it, and obtain an `integrityToken`.
     */
    private fun loadHtmlAndObtainBotguard(context: Context) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "loadHtmlAndObtainBotguard() called")
        }

        disposables.add(
            Single.fromCallable {
                val html = context.assets.open("po_token.html").bufferedReader()
                    .use { it.readText() }
                return@fromCallable html
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { html ->
                        webView.loadDataWithBaseURL(
                            "https://www.youtube.com",
                            html.replace(
                                "</script>",
                                // calls downloadAndRunBotguard() when the page has finished loading
                                "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"
                            ),
                            "text/html",
                            "utf-8",
                            null,
                        )
                    },
                    this::onInitializationErrorCloseAndCancel
                )
        )
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

        makeJnnPaGoogleapisRequest(
            "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/Create",
            "[ \"$REQUEST_KEY\" ]",
        ) { responseBody ->
            webView.evaluateJavascript(
                """(async function() {
                    try {
                        data = JSON.parse(String.raw`$responseBody`)
                        result = await runBotGuard(data)
                        globalThis.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error.toString())
                    }
                })();""",
            ) {}
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
        onInitializationErrorCloseAndCancel(PoTokenException(error))
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
        makeJnnPaGoogleapisRequest(
            "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { responseBody ->
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "GenerateIT response: $responseBody")
            }
            webView.evaluateJavascript(
                """(async function() {
                    try {
                        globalThis.integrityToken = JSON.parse(String.raw`$responseBody`)
                        $JS_INTERFACE.onInitializationFinished(integrityToken[1])
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error.toString())
                    }
                })();""",
            ) {}
        }
    }

    /**
     * Called during initialization by the JavaScript snippet from [onRunBotguardResult] when the
     * `integrityToken` has been received by JavaScript.
     *
     * @param expirationTimeInSeconds in how many seconds the integrity token expires, can be found
     * in `integrityToken[1]`
     */
    @JavascriptInterface
    fun onInitializationFinished(expirationTimeInSeconds: Long) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onInitializationFinished() called, expiration=${expirationTimeInSeconds}s")
        }
        // leave 10 minutes of margin just to be sure
        expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds - 600)
        generatorEmitter.onSuccess(this)
    }
    //endregion

    //region Obtaining poTokens
    /**
     * Adds the ([identifier], [emitter]) pair to the [poTokenEmitters] list. This makes it so that
     * multiple poToken requests can be generated invparallel, and the results will be notified to
     * the right emitters.
     */
    private fun addPoTokenEmitter(identifier: String, emitter: SingleEmitter<String>) {
        synchronized(poTokenEmitters) {
            poTokenEmitters.add(Pair(identifier, emitter))
        }
    }

    /**
     * Extracts and removes from the [poTokenEmitters] list a [SingleEmitter] based on its
     * [identifier]. The emitter is supposed to be used immediately after to either signal a success
     * or an error.
     */
    private fun popPoTokenEmitter(identifier: String): SingleEmitter<String>? {
        return synchronized(poTokenEmitters) {
            poTokenEmitters.indexOfFirst { it.first == identifier }.takeIf { it >= 0 }?.let {
                poTokenEmitters.removeAt(it).second
            }
        }
    }

    override fun generatePoToken(identifier: String): Single<String> =
        Single.create { emitter ->
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "generatePoToken() called with identifier $identifier")
            }
            runOnMainThread(emitter) {
                addPoTokenEmitter(identifier, emitter)
                webView.evaluateJavascript(
                    """(async function() {
                        identifier = String.raw`$identifier`
                        try {
                            poToken = await obtainPoToken(webPoSignalOutput, integrityToken,
                                identifier)
                            $JS_INTERFACE.onObtainPoTokenResult(identifier, poToken)
                        } catch (error) {
                            $JS_INTERFACE.onObtainPoTokenError(identifier, error.toString())
                        }
                    })();""",
                ) {}
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
        popPoTokenEmitter(identifier)?.onError(PoTokenException(error))
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] with the original identifier and the
     * result of the JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poToken: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Generated poToken: identifier=$identifier poToken=$poToken")
        }
        popPoTokenEmitter(identifier)?.onSuccess(poToken)
    }

    override fun isExpired(): Boolean {
        return Instant.now().isAfter(expirationInstant)
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
    private fun makeJnnPaGoogleapisRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit,
    ) {
        disposables.add(
            Single.fromCallable {
                return@fromCallable DownloaderImpl.getInstance().post(
                    url,
                    mapOf(
                        // replace the downloader user agent
                        "User-Agent" to listOf(USER_AGENT),
                        "Accept" to listOf("application/json"),
                        "Content-Type" to listOf("application/json+protobuf"),
                        "x-goog-api-key" to listOf(GOOGLE_API_KEY),
                        "x-user-agent" to listOf("grpc-web-javascript/0.1"),
                    ),
                    data.toByteArray()
                )
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { response ->
                        val httpCode = response.responseCode()
                        if (httpCode != 200) {
                            onInitializationErrorCloseAndCancel(
                                PoTokenException("Invalid response code: $httpCode")
                            )
                            return@subscribe
                        }
                        val responseBody = response.responseBody()
                        handleResponseBody(responseBody)
                    },
                    this::onInitializationErrorCloseAndCancel
                )
        )
    }

    /**
     * Handles any error happening during initialization, releasing resources and sending the error
     * to [generatorEmitter].
     */
    private fun onInitializationErrorCloseAndCancel(error: Throwable) {
        runOnMainThread(generatorEmitter) {
            close()
            generatorEmitter.onError(error)
        }
    }

    /**
     * Releases all [webView] and [disposables] resources.
     */
    @MainThread
    override fun close() {
        disposables.dispose()

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

        override fun newPoTokenGenerator(context: Context): Single<PoTokenGenerator> =
            Single.create { emitter ->
                runOnMainThread(emitter) {
                    val potWv = PoTokenWebView(context, emitter)
                    potWv.loadHtmlAndObtainBotguard(context)
                    emitter.setDisposable(potWv.disposables)
                }
            }

        /**
         * Runs [runnable] on the main thread using `Handler(Looper.getMainLooper()).post()`, and
         * if the `post` fails emits an error on [emitterIfPostFails].
         */
        private fun runOnMainThread(
            emitterIfPostFails: SingleEmitter<out Any>,
            runnable: () -> Unit,
        ) {
            if (!Handler(Looper.getMainLooper()).post(runnable)) {
                emitterIfPostFails.onError(PoTokenException("Could not run on main thread"))
            }
        }
    }
}
