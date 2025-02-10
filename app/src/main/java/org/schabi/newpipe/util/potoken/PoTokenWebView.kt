package org.schabi.newpipe.util.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import org.schabi.newpipe.BuildConfig

class PoTokenWebView private constructor(
    context: Context,
    // to be used exactly once only during initialization!
    generatorEmitter: SingleEmitter<PoTokenGenerator>,
) : PoTokenGenerator(generatorEmitter) {
    private val webView = WebView(context)
    private val poTokenEmitters = mutableListOf<Pair<String, SingleEmitter<String>>>()

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
                    popAllPoTokenEmitters().forEach { (_, emitter) -> emitter.onError(exception) }
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
    private fun loadHtmlAndObtainBotguard(context: Context) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "loadHtmlAndObtainBotguard() called")
        }

        loadPoTokenHtml(context) { html ->
            webView.loadDataWithBaseURL(
                "https://www.youtube.com",
                html.replaceFirst(
                    "</script>",
                    // calls downloadAndRunBotguard() when the page has finished loading
                    "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"
                ),
                "text/html",
                "utf-8",
                null,
            )
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

        makeBotguardCreateRequest { responseBody ->
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
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard].
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

        makeBotguardGenerateITRequest(botguardResponse) { integrityToken ->
            webView.evaluateJavascript(
                "this.integrityToken = $integrityToken"
            ) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "initialization finished")
                }
                generatorEmitter.onSuccess(this)
            }
        }
    }
    //endregion

    //region Obtaining poTokens
    override fun generatePoToken(identifier: String): Single<String> =
        Single.create { emitter ->
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "generatePoToken() called with identifier $identifier")
            }
            runOnMainThread(emitter) {
                addPoTokenEmitter(identifier, emitter)
                val u8Identifier = stringToU8(identifier)
                webView.evaluateJavascript(
                    """try {
                        identifier = "$identifier"
                        u8Identifier = $u8Identifier
                        poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                        poTokenU8String = ""
                        for (i = 0; i < poTokenU8.length; i++) {
                            if (i != 0) poTokenU8String += ","
                            poTokenU8String += poTokenU8[i]
                        }
                        $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                    } catch (error) {
                        $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                    }""",
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
        popPoTokenEmitter(identifier)?.onError(buildExceptionForJsError(error))
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
            popPoTokenEmitter(identifier)?.onError(t)
            return
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Generated poToken: identifier=$identifier poToken=$poToken")
        }
        popPoTokenEmitter(identifier)?.onSuccess(poToken)
    }
    //endregion

    //region Handling multiple emitters
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

    /**
     * Clears [poTokenEmitters] and returns its previous contents. The emitters are supposed to be
     * used immediately after to either signal a success or an error.
     */
    private fun popAllPoTokenEmitters(): List<Pair<String, SingleEmitter<String>>> {
        return synchronized(poTokenEmitters) {
            val result = poTokenEmitters.toList()
            poTokenEmitters.clear()
            result
        }
    }
    //endregion

    //region Close
    /**
     * Even clearing the [webView] needs to run on the main thread.
     */
    @MainThread
    fun clearWebView() {
        webView.clearHistory()
        // clears RAM cache and disk cache (globally for all WebViews)
        webView.clearCache(true)

        // ensures that the WebView isn't doing anything when destroying it
        webView.loadUrl("about:blank")

        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }

    override fun onInitializationErrorCloseAndCancel(error: Throwable) {
        super.close()
        runOnMainThread(generatorEmitter) {
            try {
                clearWebView()
            } catch (t: Throwable) {
                // ignore errors while clearing the WebView
                Log.e(TAG, "Error while clearing webView", t)
            }

            // only emit the error after clearing the webView has finished
            generatorEmitter.onError(error)
        }
    }

    override fun close() {
        super.close()
        runOnMainThread(null) {
            clearWebView()
        }
    }
    //endregion

    companion object : Factory {
        private val TAG = PoTokenWebView::class.simpleName
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
            emitterIfPostFails: SingleEmitter<out Any>?,
            runnable: Runnable,
        ) {
            if (!Handler(Looper.getMainLooper()).post(runnable)) {
                emitterIfPostFails?.onError(PoTokenException("Could not run on main thread"))
            }
        }
    }
}
