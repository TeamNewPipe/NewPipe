package org.schabi.newpipe.util.potoken

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import org.htmlunit.BrowserVersion
import org.htmlunit.BrowserVersion.BrowserVersionBuilder
import org.htmlunit.StringWebResponse
import org.htmlunit.WebClient
import org.htmlunit.html.HtmlPage
import org.schabi.newpipe.BuildConfig
import java.net.URL

class PoTokenHtmlUnit private constructor(
    // to be used exactly once only during initialization!
    generatorEmitter: SingleEmitter<PoTokenGenerator>,
) : PoTokenGenerator(generatorEmitter) {
    private val webClient: WebClient
    private lateinit var webPage: HtmlPage

    //region Initialization
    init {
        val browserVersion = BrowserVersionBuilder(BrowserVersion.CHROME)
            .setUserAgent(USER_AGENT)
            .build()
        webClient = WebClient(browserVersion)

        // We are using alert() calls to send data from JavaScript to Java from async contexts,
        // since HtmlUnit does not provide any other way for JavaScript to communicate with Java
        // asynchronously. Note that this is only needed during initialization.
        webClient.setAlertHandler { _, message ->
            val argv = message.split(' ')
            when (argv[0]) {
                "onJsInitializationError" -> { onJsInitializationError(argv[1]) }
                "onRunBotguardResult" -> { onRunBotguardResult(argv[1]) }
            }
        }
    }

    /**
     * Must be called right after instantiating [PoTokenHtmlUnit] to perform the actual
     * initialization. This will asynchronously go through all the steps needed to load BotGuard,
     * run it, and obtain an `integrityToken`.
     */
    private fun loadHtmlAndObtainBotguard(context: Context) {
        loadPoTokenHtml(context) { html ->
            webPage = webClient.loadWebResponseInto(
                StringWebResponse(html.replace("this", "window"), URL("https://www.youtube.com")),
                webClient.currentWindow.topWindow,
            ) as HtmlPage
            webClient.javaScriptEngine
                .registerWindowAndMaybeStartEventLoop(webClient.currentWindow.topWindow)
            downloadAndRunBotguard()
        }
    }

    /**
     * Called during initialization after the HtmlUnit [webPage] content has been loaded.
     */
    private fun downloadAndRunBotguard() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "downloadAndRunBotguard() called")
        }

        makeBotguardCreateRequest { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            webPage.executeJavaScript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        window.webPoSignalOutput = result.webPoSignalOutput
                        alert("onRunBotguardResult " + result.botguardResponse)
                    }, function (error) {
                        alert("onJsInitializationError " + error + "\n" + error.stack)
                    })
                } catch (error) {
                    alert("onJsInitializationError " + error + "\n" + error.stack)
                }"""
            )
        }
    }

    /**
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard].
     * Note: the communication from JavaScript to Java relies on `alert()` calls, handled by
     * [WebClient.setAlertHandler].
     */
    private fun onJsInitializationError(error: String) {
        Log.e(TAG, "Initialization error from JavaScript: $error")
        onInitializationErrorCloseAndCancel(PoTokenException(error))
    }

    /**
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard] after
     * obtaining the BotGuard execution output [botguardResponse].
     * Note: the communication from JavaScript to Java relies on `alert()` calls, handled by
     * [WebClient.setAlertHandler].
     */
    private fun onRunBotguardResult(botguardResponse: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "botguardResponse: $botguardResponse")
        }

        makeBotguardGenerateITRequest(botguardResponse) { integrityToken ->
            webPage.executeJavaScript(
                "window.integrityToken = $integrityToken"
            )

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "initialization finished")
            }
            generatorEmitter.onSuccess(this)
        }
    }
    //endregion

    //region Obtaining poTokens
    @MainThread
    override fun generatePoToken(identifier: String): Single<String> =
        Single.fromCallable {
            val u8Identifier = stringToU8(identifier)
            val result = webPage.executeJavaScript(
                """result = ""
                try {
                    identifier = "$identifier"
                    u8Identifier = $u8Identifier
                    poTokenU8 = obtainPoToken(window.webPoSignalOutput, window.integrityToken, u8Identifier)
                    poTokenU8String = ""
                    for (i = 0; i < poTokenU8.length; i++) {
                        if (i != 0) poTokenU8String += ","
                        poTokenU8String += poTokenU8[i]
                    }
                    result = poTokenU8String
                } catch (error) {
                    result = "error " + error + "\n" + error.stack + "\n"
                }
                result""",
            ).javaScriptResult.toString()

            if (result.startsWith("error ")) {
                throw PoTokenException(result.substring(6))
            } else {
                return@fromCallable u8ToBase64(result)
            }
        }
    //endregion

    //region Close
    override fun onInitializationErrorCloseAndCancel(error: Throwable) {
        close()
        generatorEmitter.onError(error)
    }

    override fun close() {
        super.close()
        webClient.close()
    }
    //endregion

    companion object : Factory {
        private val TAG = PoTokenHtmlUnit::class.simpleName

        @MainThread
        override fun newPoTokenGenerator(context: Context): Single<PoTokenGenerator> =
            Single.create { emitter ->
                val potWv = PoTokenHtmlUnit(emitter)
                potWv.loadHtmlAndObtainBotguard(context)
                emitter.setDisposable(potWv.disposables)
            }
    }
}
