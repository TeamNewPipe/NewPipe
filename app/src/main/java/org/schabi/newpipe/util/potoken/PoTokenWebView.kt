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
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult

class PoTokenWebView private constructor(
    context: Context,
    // to be used exactly once only during initialization!
    private val generatorEmitter: SingleEmitter<PoTokenGenerator>,
) : PoTokenGenerator {
    private val webView = WebView(context)
    private val disposables = CompositeDisposable() // used only during initialization
    private val poTokenEmitters = mutableListOf<Pair<String, SingleEmitter<PoTokenResult>>>()

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
        webView.addJavascriptInterface(this, "PoTokenWebView")
    }

    /**
     * Must be called right after instantiating [PoTokenWebView] to perform the actual
     * initialization. This will asynchronously go through all the steps needed to load BotGuard,
     * run it, and obtain an `integrityToken`.
     */
    private fun loadHtmlAndObtainBotguard(context: Context) {
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
                            html,
                            "text/html",
                            "utf-8",
                            null,
                        )
                        downloadAndRunBotguard()
                    },
                    this::onInitializationErrorCloseAndCancel
                )
        )
    }

    /**
     * Called during initialization after the WebView content has been loaded.
     */
    private fun downloadAndRunBotguard() {
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
                        PoTokenWebView.onRunBotguardResult(result.botguardResponse)
                    } catch (error) {
                        PoTokenWebView.onJsInitializationError(error.toString())
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
        Log.e(TAG, "Initialization error from JavaScript: $error")
        onInitializationErrorCloseAndCancel(PoTokenException(error))
    }

    /**
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard] after
     * obtaining the BotGuard execution output [botguardResponse].
     */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        Log.e(TAG, "botguardResponse: $botguardResponse")
        makeJnnPaGoogleapisRequest(
            "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { responseBody ->
            webView.evaluateJavascript(
                """(async function() {
                    try {
                        globalThis.integrityToken = JSON.parse(String.raw`$responseBody`)
                        PoTokenWebView.onInitializationFinished()
                    } catch (error) {
                        PoTokenWebView.onJsInitializationError(error.toString())
                    }
                })();""",
            ) {}
        }
    }

    /**
     * Called during initialization by the JavaScript snippet from [onRunBotguardResult] when the
     * `integrityToken` has been received by JavaScript.
     */
    @JavascriptInterface
    fun onInitializationFinished() {
        generatorEmitter.onSuccess(this)
    }
    //endregion

    //region Obtaining poTokens
    /**
     * Adds the ([identifier], [emitter]) pair to the [poTokenEmitters] list. This makes it so that
     * multiple poToken requests can be generated invparallel, and the results will be notified to
     * the right emitters.
     */
    private fun addPoTokenEmitter(identifier: String, emitter: SingleEmitter<PoTokenResult>) {
        synchronized(poTokenEmitters) {
            poTokenEmitters.add(Pair(identifier, emitter))
        }
    }

    /**
     * Extracts and removes from the [poTokenEmitters] list a [SingleEmitter] based on its
     * [identifier]. The emitter is supposed to be used immediately after to either signal a success
     * or an error.
     */
    private fun popPoTokenEmitter(identifier: String): SingleEmitter<PoTokenResult>? {
        return synchronized(poTokenEmitters) {
            poTokenEmitters.indexOfFirst { it.first == identifier }.takeIf { it >= 0 }?.let {
                poTokenEmitters.removeAt(it).second
            }
        }
    }

    @MainThread
    override fun generatePoToken(identifier: String): Single<PoTokenResult> =
        Single.create { emitter ->
            addPoTokenEmitter(identifier, emitter)

            webView.evaluateJavascript(
                """(async function() {
                    identifier = String.raw`$identifier`
                    try {
                        poToken = await obtainPoToken(webPoSignalOutput, integrityToken, identifier)
                        PoTokenWebView.onObtainPoTokenResult(identifier, poToken)
                    } catch (error) {
                        PoTokenWebView.onObtainPoTokenError(identifier, error.toString())
                    }
                })();""",
            ) {}
        }

    /**
     * Called by the JavaScript snippet from [generatePoToken] when an error occurs in calling the
     * JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        Log.e(TAG, "obtainPoToken error from JavaScript: $error")
        popPoTokenEmitter(identifier)?.onError(PoTokenException(error))
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] with the original identifier and the
     * result of the JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poToken: String) {
        Log.e(TAG, "identifier=$identifier")
        Log.e(TAG, "poToken=$poToken")
        popPoTokenEmitter(identifier)?.onSuccess(PoTokenResult(identifier, poToken))
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
        Handler(Looper.getMainLooper()).post {
            close()
            generatorEmitter.onError(error)
        }
    }

    /**
     * Releases all [webView] and [disposables] resources.
     */
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
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

        @MainThread
        override fun newPoTokenGenerator(context: Context): Single<PoTokenGenerator> =
            Single.create { emitter ->
                val potWv = PoTokenWebView(context, emitter)
                potWv.loadHtmlAndObtainBotguard(context)
                emitter.setDisposable(potWv.disposables)
            }
    }
}
