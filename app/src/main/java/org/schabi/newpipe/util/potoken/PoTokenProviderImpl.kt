package org.schabi.newpipe.util.potoken

import android.util.Log
import org.schabi.newpipe.App
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.util.DeviceUtils

object PoTokenProviderImpl : PoTokenProvider {
    val TAG = PoTokenProviderImpl::class.simpleName
    private val webViewSupported by lazy { DeviceUtils.supportsWebView() }

    private object WebPoTokenGenLock
    private var webPoTokenVisitorData: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenGenerator? = null

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        if (!webViewSupported) {
            return null
        }

        val (poTokenGenerator, visitorData, streamingPot) = synchronized(WebPoTokenGenLock) {
            if (webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired()) {
                webPoTokenGenerator = PoTokenWebView.newPoTokenGenerator(App.getApp()).blockingGet()
                webPoTokenVisitorData = YoutubeParsingHelper
                    .randomVisitorData(NewPipe.getPreferredContentCountry())

                // The streaming poToken needs to be generated exactly once before generating any
                // other (player) tokens.
                webPoTokenStreamingPot = webPoTokenGenerator!!
                    .generatePoToken(webPoTokenVisitorData!!).blockingGet()
            }
            return@synchronized Triple(
                webPoTokenGenerator!!, webPoTokenVisitorData!!, webPoTokenStreamingPot!!
            )
        }

        // Not using synchronized here, since poTokenGenerator would be able to generate multiple
        // poTokens in parallel if needed. The only important thing is for exactly one
        // visitorData/streaming poToken to be generated before anything else.
        val playerPot = poTokenGenerator.generatePoToken(videoId).blockingGet()
        Log.e(TAG, "success($videoId) $playerPot,web.gvs+$streamingPot;visitor_data=$visitorData")

        return PoTokenResult(
            webPoTokenVisitorData!!,
            playerPot,
            webPoTokenStreamingPot!!,
        )
    }

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = null

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? = null

    override fun getIosClientPoToken(videoId: String): PoTokenResult? = null
}
