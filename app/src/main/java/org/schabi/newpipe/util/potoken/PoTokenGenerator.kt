package org.schabi.newpipe.util.potoken

import android.content.Context
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import java.io.Closeable

interface PoTokenGenerator : Closeable {
    /**
     * Generates a poToken for the provided identifier, using the `integrityToken` and
     * `webPoSignalOutput` previously obtained in the initialization of [PoTokenWebView]. Can be
     * called multiple times.
     */
    fun generatePoToken(identifier: String): Single<PoTokenResult>

    interface Factory {
        /**
         * Initializes a [PoTokenGenerator] by loading the BotGuard VM, running it, and obtaining
         * an `integrityToken`. Can then be used multiple times to generate multiple poTokens with
         * [generatePoToken].
         *
         * @param context used e.g. to load the HTML asset or to instantiate a WebView
         */
        fun newPoTokenGenerator(context: Context): Single<PoTokenGenerator>
    }
}
