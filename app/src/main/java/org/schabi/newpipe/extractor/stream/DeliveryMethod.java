package org.schabi.newpipe.extractor.stream;

/**
 * An enum to represent the different delivery methods of {@link Stream streams} which are returned
 * by the extractor.
 */
public enum DeliveryMethod {

    /**
     * Used for {@link Stream}s served using the progressive HTTP streaming method.
     */
    PROGRESSIVE_HTTP,

    /**
     * Used for {@link Stream}s served using the DASH (Dynamic Adaptive Streaming over HTTP)
     * adaptive streaming method.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Dynamic_Adaptive_Streaming_over_HTTP">the
     * Dynamic Adaptive Streaming over HTTP Wikipedia page</a> and <a href="https://dashif.org/">
     * DASH Industry Forum's website</a> for more information about the DASH delivery method
     */
    DASH,

    /**
     * Used for {@link Stream}s served using the HLS (HTTP Live Streaming) adaptive streaming
     * method.
     *
     * @see <a href="https://en.wikipedia.org/wiki/HTTP_Live_Streaming">the HTTP Live Streaming
     * page</a> and <a href="https://developer.apple.com/streaming">Apple's developers website page
     * about HTTP Live Streaming</a> for more information about the HLS delivery method
     */
    HLS,

    /**
     * Used for {@link Stream}s served using the SmoothStreaming adaptive streaming method.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Adaptive_bitrate_streaming
     * #Microsoft_Smooth_Streaming_(MSS)">Wikipedia's page about adaptive bitrate streaming,
     * section <i>Microsoft Smooth Streaming (MSS)</i></a> for more information about the
     * SmoothStreaming delivery method
     */
    SS,

    /**
     * Used for {@link Stream}s served via a torrent file.
     *
     * @see <a href="https://en.wikipedia.org/wiki/BitTorrent">Wikipedia's BitTorrent's page</a>,
     * <a href="https://en.wikipedia.org/wiki/Torrent_file">Wikipedia's page about torrent files
     * </a> and <a href="https://www.bittorrent.org">Bitorrent's website</a> for more information
     * about the BitTorrent protocol
     */
    TORRENT,

    /**
     * Server-side adaptive bitrate delivery. The plugin API preserves this identifier;
     * players must explicitly support the protocol before using a stream that declares it.
     */
    SABR
}
