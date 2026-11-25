package org.schabi.newpipe.extractor.services.youtube.extractors.kiosk;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.DISABLE_PRETTY_PRINT_PARAMETER;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getOriginReferrerHeaders;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getThumbnailUrlFromInfoItem;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getValidJsonResponseBody;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

/** Base extractor for the video charts served by YouTube Charts. */
abstract class YoutubeChartsBaseKioskExtractor extends KioskExtractor<StreamInfoItem> {
    private static final String CHARTS_CLIENT_ID = "31";
    private static final String CHARTS_CLIENT_NAME = "WEB_MUSIC_ANALYTICS";
    private static final String CHARTS_CLIENT_VERSION = "2.0";
    private static final String CHARTS_ENDPOINT =
            "https://charts.youtube.com/youtubei/v1/browse?alt=json&"
                    + DISABLE_PRETTY_PRINT_PARAMETER;

    protected static final Set<String> SUPPORTED_COUNTRY_CODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "AE", "AR", "AT", "AU", "BE", "BO", "BR", "CA", "CH", "CL", "CO", "CR",
                    "CZ", "DE", "DK", "DO", "EC", "EE", "EG", "ES", "FI", "FR", "GB", "GT",
                    "HN", "HU", "ID", "IE", "IL", "IN", "IS", "IT", "JP", "KE", "KR", "LU",
                    "MX", "NG", "NI", "NL", "NO", "NZ", "PA", "PE", "PL", "PT", "PY", "RO",
                    "RS", "RU", "SA", "SE", "SV", "TR", "TZ", "UA", "UG", "US", "UY", "ZA",
                    "ZW")));

    protected final String chartType;
    private JsonObject browseResponse;

    YoutubeChartsBaseKioskExtractor(final StreamingService streamingService,
                                    final ListLinkHandler linkHandler,
                                    final String kioskId,
                                    final String chartType) {
        super(streamingService, linkHandler, kioskId);
        this.chartType = chartType;
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final Localization localization = getExtractorLocalization();
        final ContentCountry contentCountry = getExtractorContentCountry();
        final HashMap<String, List<String>> headers =
                new HashMap<>(getOriginReferrerHeaders("https://charts.youtube.com"));
        headers.put("Content-Type", Collections.singletonList("application/json"));
        headers.put("X-YouTube-Client-Name", Collections.singletonList(CHARTS_CLIENT_ID));
        headers.put("X-YouTube-Client-Version",
                Collections.singletonList(CHARTS_CLIENT_VERSION));

        browseResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(downloader.post(
                CHARTS_ENDPOINT,
                headers,
                buildRequestBody(localization, contentCountry, chartType),
                localization)));
    }

    static byte[] buildRequestBody(final Localization localization,
                                   final ContentCountry contentCountry,
                                   final String chartType) {
        return JsonWriter.string(JsonObject.builder()
                .object("context")
                .object("client")
                .value("clientName", CHARTS_CLIENT_NAME)
                .value("clientVersion", CHARTS_CLIENT_VERSION)
                .value("hl", localization.getLocalizationCode())
                .value("gl", contentCountry.getCountryCode())
                .value("utcOffsetMinutes", 0)
                .end()
                .object("request")
                .array("internalExperimentFlags")
                .end()
                .value("useSsl", true)
                .end()
                .object("user")
                .value("lockedSafetyMode", false)
                .end()
                .end()
                .value("browseId", "FEmusic_analytics_charts_home")
                .value("query", "perspective=CHART_DETAILS&chart_params_country_code="
                        + contentCountry.getCountryCode() + "&chart_params_chart_type=" + chartType)
                .done()).getBytes(StandardCharsets.UTF_8);
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws ParsingException {
        final JsonArray videos = extractChartVideos(browseResponse);

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        videos.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .forEachOrdered(video -> collector.commit(new ChartsVideoInfoItemExtractor(video)));
        return new InfoItemsPage<>(collector, null);
    }

    static JsonArray extractChartVideos(final JsonObject response) {
        final JsonArray result = new JsonArray();
        response.getObject("contents")
                .getObject("sectionListRenderer")
                .getArray("contents")
                .getObject(0)
                .getObject("musicAnalyticsSectionRenderer")
                .getObject("content")
                .getArray("videos")
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(group -> group.getArray("videoViews"))
                .forEach(result::addAll);
        return result;
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) {
        return InfoItemsPage.emptyPage();
    }

    static final class ChartsVideoInfoItemExtractor implements StreamInfoItemExtractor {
        private final JsonObject video;

        ChartsVideoInfoItemExtractor(final JsonObject video) {
            this.video = video;
        }

        @Override
        public StreamType getStreamType() {
            return StreamType.VIDEO_STREAM;
        }

        @Override
        public long getDuration() {
            return video.getInt("videoDuration", -1);
        }

        @Override
        public long getViewCount() {
            return -1;
        }

        @Override
        public String getUploaderName() {
            return video.getString("channelName");
        }

        @Override
        public String getUploaderUrl() throws ParsingException {
            final String channelId = video.getString("externalChannelId");
            if (isNullOrEmpty(channelId)) {
                throw new ParsingException("Could not get channel ID");
            }
            return YoutubeChannelLinkHandlerFactory.getInstance().getUrl("channel/" + channelId);
        }

        @Nullable
        @Override
        public String getTextualUploadDate() {
            return getUploadDate().offsetDateTime().toLocalDate().toString();
        }

        @Nonnull
        @Override
        public DateWrapper getUploadDate() {
            final JsonObject releaseDate = video.getObject("releaseDate");
            final LocalDate localDate = LocalDate.of(releaseDate.getInt("year"),
                    releaseDate.getInt("month"), releaseDate.getInt("day"));
            return new DateWrapper(localDate.atStartOfDay().atOffset(ZoneOffset.UTC), true);
        }

        @Override
        public String getName() {
            return video.getString("title");
        }

        @Override
        public String getUrl() throws ParsingException {
            return YoutubeStreamLinkHandlerFactory.getInstance().getUrl(video.getString("id"));
        }

        @Override
        public String getThumbnailUrl() throws ParsingException {
            return getThumbnailUrlFromInfoItem(video);
        }
    }
}
