package org.schabi.newpipe.player.datasource;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getAndroidUserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getIosUserAgent;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isAndroidStreamingUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isIosStreamingUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isTvHtml5SimplyEmbeddedPlayerStreamingUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isWebStreamingUrl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.ResolvingDataSource;
import com.google.common.net.HttpHeaders;

import org.schabi.newpipe.extractor.utils.Parser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A {@link ResolvingDataSource.Resolver} for YouTube contents.
 *
 * <p>
 * This resolver tries to spoof official clients during playback, by sending HTTP headers of
 * official clients and by appending parameters on streaming URLs (both query parameters or
 * "path parameters" are supported).
 * </p>
 */
public final class YoutubeDataSourceResolver implements ResolvingDataSource.Resolver {

    private static final String YOUTUBE_BASE_URL = "https://www.youtube.com";

    private static final Pattern VIDEOPLAYBACK_PATTERN = Pattern.compile("/videoplayback/*");

    private static final String RANGE_PARAMETER_QUERY = "&range=";
    @SuppressWarnings("squid:S1075")
    private static final String RANGE_PARAMETER_PATH = "/range/";

    private static final String RN_PARAMETER_QUERY = "&rn=";
    @SuppressWarnings("squid:S1075")
    private static final String RN_PARAMETER_PATH = "/rn/";

    private final boolean rangeParameterEnabled;
    private final boolean rnParameterEnabled;
    private long requestNumber;

    public YoutubeDataSourceResolver(final boolean rangeParameterEnabled,
                                     final boolean rnParameterEnabled) {
        this.rangeParameterEnabled = rangeParameterEnabled;
        this.rnParameterEnabled = rnParameterEnabled;
    }

    @NonNull
    @Override
    public DataSpec resolveDataSpec(@NonNull final DataSpec dataSpec) {
        final DataSpec.Builder dataSpecBuilder = dataSpec.buildUpon();

        final Map<String, String> httpHeaders = new HashMap<>(dataSpec.httpRequestHeaders);
        addYoutubePlaybackHeaders(dataSpec, httpHeaders);
        dataSpecBuilder.setHttpRequestHeaders(httpHeaders);

        if (Parser.isMatch(VIDEOPLAYBACK_PATTERN, dataSpec.uri.getPath())) {
            String url = dataSpec.uri.toString();

            url += appendRangeParameterIfNeeded(dataSpec, dataSpecBuilder);
            url += appendRnParameterIfNeeded(dataSpec);

            dataSpecBuilder.setUri(url);
        }

        return dataSpecBuilder.build();
    }

    /**
     * Add headers sent by official YouTube clients on playback requests.
     *
     * <p>
     * This method set the {@code User-Agent} HTTP header corresponding to the client used, for
     * mobile clients, and set the CORS headers for HTML5 clients.
     * </p>
     *
     * @param originalDataSpec the original {@link DataSpec} provided by ExoPlayer in
     *                         {@link #resolveDataSpec(DataSpec)}
     * @param httpHeaders      the {@link Map} which will be used as the HTTP headers of the
     *                         {@link DataSpec} which will be returned by
     *                         {@link #resolveDataSpec(DataSpec)}
     */
    private void addYoutubePlaybackHeaders(@NonNull final DataSpec originalDataSpec,
                                           @NonNull final Map<String, String> httpHeaders) {
        final String url = originalDataSpec.uri.toString();
        if (isAndroidStreamingUrl(url)) {
            httpHeaders.put(HttpHeaders.USER_AGENT, getAndroidUserAgent(null));
        } else if (isIosStreamingUrl(url)) {
            httpHeaders.put(HttpHeaders.USER_AGENT, getIosUserAgent(null));
        } else if (isWebStreamingUrl(url) || isTvHtml5SimplyEmbeddedPlayerStreamingUrl(url)) {
            httpHeaders.put(HttpHeaders.ORIGIN, YOUTUBE_BASE_URL);
            httpHeaders.put(HttpHeaders.REFERER, YOUTUBE_BASE_URL);
            httpHeaders.put(HttpHeaders.SEC_FETCH_DEST, "empty");
            httpHeaders.put(HttpHeaders.SEC_FETCH_MODE, "cors");
            httpHeaders.put(HttpHeaders.SEC_FETCH_SITE, "cross-site");
        }
    }

    /**
     * Append the {@code rn} parameter to {@code videoplayback} streaming URLs, if enabled and
     * needed.
     *
     * <p>
     * To fetch its contents, YouTube use range requests which append a {@code range} parameter or
     * path to {@code videoplayback} URLs instead of the {@code Range} header (even if the server
     * respond correctly when requesting a range of a resource with the header).
     * </p>
     *
     * <p>
     * This method supports both path and query parameters.
     * </p>
     *
     * @param originalDataSpec the original {@link DataSpec} provided by ExoPlayer in
     *                         {{@link #resolveDataSpec(DataSpec)}}
     * @param dataSpecBuilder  the {@link DataSpec.Builder} used to modify the original
     *                         {@link DataSpec}
     * @return the range parameter to add, or an empty string
     */
    @NonNull
    private String appendRangeParameterIfNeeded(@NonNull final DataSpec originalDataSpec,
                                                @NonNull final DataSpec.Builder dataSpecBuilder) {
        if (rangeParameterEnabled) {
            final String rangeParameter = buildRangeParameter(
                    originalDataSpec.position, originalDataSpec.length);
            if (rangeParameter != null) {
                // Workaround: prevent ExoPlayer HttpDataSources sending a Range HTTP header by
                // setting the DataSpec position to 0 and its length to unset
                // See HttpUtil#buildRangeRequestHeader(long, long)
                // If the content is served with compression, this workaround may make ExoPlayer
                // not work properly
                dataSpecBuilder.setPosition(0);
                dataSpecBuilder.setLength(C.LENGTH_UNSET);

                if (originalDataSpec.uri.getQueryParameters(RANGE_PARAMETER_QUERY).isEmpty()) {
                    // If the streaming URL is using query parameters and doesn't contain the range
                    // query parameter, append it to the end of the URL
                    return RANGE_PARAMETER_QUERY + rangeParameter;
                } else if (!originalDataSpec.uri.getPath().contains(RANGE_PARAMETER_PATH)) {
                    // If the streaming URL is using path parameters and doesn't contain the range
                    // path parameter, append it to the end of the URL
                    return RANGE_PARAMETER_PATH + rangeParameter;
                }
            }
        }

        return "";
    }

    /**
     * Append the {@code rn} parameter to {@code videoplayback} streaming URLs, if enabled and
     * needed.
     *
     * <p>
     * This parameter means request number and is sent by official clients, sending the request
     * count made by a client in a playback session.
     * </p>
     *
     * <p>
     * This method aims to reproduce this behavior, by sending this request count, starting
     * initially from 1 and sharing this number between streams and contents, like official clients
     * do.
     * </p>
     *
     * <p>
     * This method supports both path and query parameters.
     * </p>
     *
     * @param originalDataSpec the original {@link DataSpec} provided by ExoPlayer in
     *                         {{@link #resolveDataSpec(DataSpec)}}
     * @return the rn parameter to add, or an empty string
     */
    @NonNull
    private String appendRnParameterIfNeeded(@NonNull final DataSpec originalDataSpec) {
        if (rnParameterEnabled) {
            if (originalDataSpec.uri.getQueryParameters(RN_PARAMETER_QUERY).isEmpty()) {
                // If the streaming URL is using query parameters and doesn't contain the rn query
                // parameter, append it to the end of the URL
                ++requestNumber;
               return RN_PARAMETER_QUERY + requestNumber;
            } else if (!originalDataSpec.uri.getPath().contains(RN_PARAMETER_PATH)) {
                // If the streaming URL is using path parameters and doesn't contain the rn path
                // parameter, append it to the end of the URL
                ++requestNumber;
                return RN_PARAMETER_PATH + requestNumber;
            }
        }

        return "";
    }

    /**
     * Builds a {@code range} parameter value for the given position and length.
     *
     * <p>
     * To fetch its contents, YouTube use range requests which append a {@code range} parameter or
     * path to {@code videoplayback} URLs instead of the {@code Range} header (even if the server
     * respond correctly when requesting a range of a resource with the header).
     * </p>
     *
     * <p>
     * The parameter is built in the same way as the header.
     * </p>
     *
     * @param position the request position
     * @param length   the request length, or {@link C#LENGTH_UNSET} if the request is unbounded
     * @return the corresponding {@code range} parameter, or {@code null} if this parameter is
     * unnecessary because the whole resource is being requested
     * @see com.google.android.exoplayer2.upstream.HttpUtil#buildRangeRequestHeader(long, long)
     */
    @Nullable
    private static String buildRangeParameter(final long position,
                                              final long length) {
        if (position == 0 && length == C.LENGTH_UNSET) {
            return null;
        }

        final StringBuilder rangeParameter = new StringBuilder();
        rangeParameter.append(position);
        rangeParameter.append("-");
        if (length != C.LENGTH_UNSET) {
            rangeParameter.append(position + length - 1);
        }
        return rangeParameter.toString();
    }
}
