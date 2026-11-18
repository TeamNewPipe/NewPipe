package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.DISABLE_PRETTY_PRINT_PARAMETER;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.YOUTUBEI_V1_URL;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.addYoutubeHeaders;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.extractPlaylistTypeFromPlaylistUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.fixThumbnailUrl;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getUrlFromNavigationEndpoint;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getValidJsonResponseBody;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;
import static org.schabi.newpipe.extractor.utils.Utils.EMPTY_STRING;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class YoutubePlaylistExtractor extends PlaylistExtractor {
    // Minimum size of the stats array in the browse response which includes the streams count
    private static final int STATS_ARRAY_WITH_STREAMS_COUNT_MIN_SIZE = 2;

    // Names of some objects in JSON response frequently used in this class
    private static final String PLAYLIST_VIDEO_RENDERER = "playlistVideoRenderer";
    private static final String PLAYLIST_VIDEO_LIST_RENDERER = "playlistVideoListRenderer";
    private static final String VIDEO_OWNER_RENDERER = "videoOwnerRenderer";
    private static final String SIDEBAR = "sidebar";
    private static final String HEADER = "header";

    private JsonObject browseResponse;
    private JsonObject playlistInfo;
    private JsonObject playlistHeader;
    private boolean isNewPlaylistInterface;

    public YoutubePlaylistExtractor(final StreamingService service,
                                    final ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) throws IOException,
            ExtractionException {
        final Localization localization = getService().getLocalization();
        final byte[] body = JsonWriter.string(prepareDesktopJsonBuilder(localization,
                        getExtractorContentCountry())
                        .value("browseId", "VL" + getId())
                        .value("params", "wgYCCAA%3D") // Show unavailable videos
                        .done())
                .getBytes(StandardCharsets.UTF_8);

        browseResponse = getJsonPostResponse("browse", body, localization);
        YoutubeParsingHelper.defaultAlertsCheck(browseResponse);

        isNewPlaylistInterface = hasNewPlaylistInterface(browseResponse);
        playlistInfo = isNewPlaylistInterface ? new JsonObject() : getPlaylistInfo();
    }

    static boolean hasNewPlaylistInterface(final JsonObject response) {
        // The old playlist UI can be returned together with the new one.
        return response.has(HEADER) && !response.has(SIDEBAR);
    }

    private JsonObject getUploaderInfo() throws ParsingException {
        final JsonArray items = browseResponse.getObject("sidebar")
                .getObject("playlistSidebarRenderer")
                .getArray("items");

        JsonObject videoOwner = items.getObject(1)
                .getObject("playlistSidebarSecondaryInfoRenderer")
                .getObject("videoOwner");
        if (videoOwner.has(VIDEO_OWNER_RENDERER)) {
            return videoOwner.getObject(VIDEO_OWNER_RENDERER);
        }

        // we might want to create a loop here instead of using duplicated code
        videoOwner = items.getObject(items.size())
                .getObject("playlistSidebarSecondaryInfoRenderer")
                .getObject("videoOwner");
        if (videoOwner.has(VIDEO_OWNER_RENDERER)) {
            return videoOwner.getObject(VIDEO_OWNER_RENDERER);
        }
        throw new ParsingException("Could not get uploader info");
    }

    private JsonObject getPlaylistInfo() throws ParsingException {
        try {
            return browseResponse.getObject(SIDEBAR)
                    .getObject("playlistSidebarRenderer")
                    .getArray("items")
                    .getObject(0)
                    .getObject("playlistSidebarPrimaryInfoRenderer");
        } catch (final Exception e) {
            throw new ParsingException("Could not get PlaylistInfo", e);
        }
    }

    private JsonObject getPlaylistHeader() {
        if (playlistHeader == null) {
            playlistHeader = browseResponse.getObject(HEADER)
                    .getObject("playlistHeaderRenderer");
        }
        return playlistHeader;
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        return extractName(
                getId(),
                isNewPlaylistInterface ? getPlaylistHeader() : playlistInfo,
                browseResponse
        );
    }

    static String extractName(final String playlistId, final JsonObject primaryInfo,
                              final JsonObject response) throws ParsingException {
        String name = getTextFromObject(primaryInfo.getObject("title"));
        if (isNullOrEmpty(name)) {
            name = response.getObject("microformat")
                    .getObject("microformatDataRenderer")
                    .getString("title");
        }

        // OLAK5uy_ identifies YouTube's auto-generated album playlists. The service requests
        // Zulu to keep video titles untranslated, which adds this label to album titles.
        // Remove only that leading label, never text from an ordinary user-created playlist.
        final String albumPrefix = "I-albhamu - ";
        if (playlistId.startsWith("OLAK5uy_") && !isNullOrEmpty(name)
                && name.startsWith(albumPrefix) && name.length() > albumPrefix.length()) {
            return name.substring(albumPrefix.length());
        }
        return name;
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        return extractThumbnailUrl(playlistInfo, browseResponse, isNewPlaylistInterface);
    }

    static String extractThumbnailUrl(final JsonObject primaryInfo,
                                      final JsonObject response,
                                      final boolean preferHeader) throws ParsingException {
        JsonArray thumbnails = new JsonArray();

        if (preferHeader) {
            thumbnails = playlistHeaderThumbnails(response);
        }
        if (thumbnails.isEmpty()) {
            thumbnails = primaryInfo.getObject("thumbnailRenderer")
                    .getObject("playlistVideoThumbnailRenderer")
                    .getObject("thumbnail")
                    .getArray("thumbnails");
        }
        if (thumbnails.isEmpty()) {
            thumbnails = primaryInfo.getObject("thumbnailRenderer")
                    .getObject("playlistCustomThumbnailRenderer")
                    .getObject("thumbnail")
                    .getArray("thumbnails");
        }
        if (thumbnails.isEmpty()) {
            // Some responses expose both layouts but only populate the newer playlist header.
            thumbnails = playlistHeaderThumbnails(response);
        }
        if (thumbnails.isEmpty()) {
            // YouTube's newest playlist page header stores the artwork in a pageHeaderViewModel
            // hero image instead of playlistHeaderRenderer or sidebar metadata.
            thumbnails = pageHeaderThumbnails(response);
        }
        if (thumbnails.isEmpty()) {
            thumbnails = response.getObject("microformat")
                    .getObject("microformatDataRenderer")
                    .getObject("thumbnail")
                    .getArray("thumbnails");
        }
        if (thumbnails.isEmpty()) {
            throw new ParsingException("Could not get playlist thumbnail");
        }

        return fixThumbnailUrl(
                thumbnails.getObject(thumbnails.size() - 1).getString("url")
        );
    }

    private static JsonArray playlistHeaderThumbnails(final JsonObject response) {
        return response.getObject(HEADER)
                .getObject("playlistHeaderRenderer")
                .getObject("playlistHeaderBanner")
                .getObject("heroPlaylistThumbnailRenderer")
                .getObject("thumbnail")
                .getArray("thumbnails");
    }

    private static JsonArray pageHeaderThumbnails(final JsonObject response) {
        return response.getObject(HEADER)
                .getObject("pageHeaderRenderer")
                .getObject("content")
                .getObject("pageHeaderViewModel")
                .getObject("heroImage")
                .getObject("contentPreviewImageViewModel")
                .getObject("image")
                .getArray("sources");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        try {
            return getUrlFromNavigationEndpoint(
                    isNewPlaylistInterface
                            ? getPlaylistHeader().getObject("ownerText")
                                    .getArray("runs")
                                    .getObject(0)
                                    .getObject("navigationEndpoint")
                            : getUploaderInfo().getObject("navigationEndpoint")
            );
        } catch (final Exception e) {
            throw new ParsingException("Could not get playlist uploader url", e);
        }
    }

    @Override
    public String getUploaderName() throws ParsingException {
        try {
            return getTextFromObject(
                    isNewPlaylistInterface
                            ? getPlaylistHeader().getObject("ownerText")
                            : getUploaderInfo().getObject("title")
            );
        } catch (final Exception e) {
            throw new ParsingException("Could not get playlist uploader name", e);
        }
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        if (isNewPlaylistInterface) {
            // The new playlist interface does not expose an uploader avatar.
            return EMPTY_STRING;
        }

        try {
            final JsonArray thumbnails = getUploaderInfo()
                    .getObject("thumbnail")
                    .getArray("thumbnails");
            final String url = thumbnails
                    .getObject(thumbnails.size() - 1)
                    .getString("url");

            return fixThumbnailUrl(url);
        } catch (final Exception e) {
            throw new ParsingException("Could not get playlist uploader avatar", e);
        }
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        // YouTube doesn't provide this information
        return false;
    }

    @Override
    public long getStreamCount() throws ParsingException {
        if (isNewPlaylistInterface) {
            final String numVideosText =
                    getTextFromObject(getPlaylistHeader().getObject("numVideosText"));
            if (!isNullOrEmpty(numVideosText)) {
                try {
                    return Long.parseLong(Utils.removeNonDigitCharacters(numVideosText));
                } catch (final NumberFormatException ignored) {
                    // Try the other header metadata before falling back to unknown.
                }
            }

            final String bylineText = getTextFromObject(
                    getPlaylistHeader().getArray("byline")
                            .getObject(0)
                            .getObject("text")
            );
            if (!isNullOrEmpty(bylineText)) {
                try {
                    return Long.parseLong(Utils.removeNonDigitCharacters(bylineText));
                } catch (final NumberFormatException ignored) {
                    // Keep the existing behavior of returning an unknown count.
                }
            }
            return ITEM_COUNT_UNKNOWN;
        }

        try {
            final JsonArray stats = playlistInfo.getArray("stats");
            // For unknown reasons, YouTube don't provide the stream count for learning playlists
            // on the desktop client but only the number of views and the playlist modified date.
            if (stats.size() > STATS_ARRAY_WITH_STREAMS_COUNT_MIN_SIZE) {
                final String videosText = getTextFromObject(stats.getObject(0));
                if (videosText != null) {
                    return Long.parseLong(Utils.removeNonDigitCharacters(videosText));
                }
            }

            return ITEM_COUNT_UNKNOWN;
        } catch (final Exception e) {
            throw new ParsingException("Could not get video count from playlist", e);
        }
    }

    @Nonnull
    @Override
    public String getSubChannelName() {
        return EMPTY_STRING;
    }

    @Nonnull
    @Override
    public String getSubChannelUrl() {
        return EMPTY_STRING;
    }

    @Nonnull
    @Override
    public String getSubChannelAvatarUrl() {
        return EMPTY_STRING;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        Page nextPage = null;

        final JsonArray contents = browseResponse.getObject("contents")
                .getObject("twoColumnBrowseResultsRenderer")
                .getArray("tabs")
                .getObject(0)
                .getObject("tabRenderer")
                .getObject("content")
                .getObject("sectionListRenderer")
                .getArray("contents");

        final JsonObject videoPlaylistObject = contents.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(content -> content.getObject("itemSectionRenderer")
                        .getArray("contents")
                        .getObject(0))
                .filter(contentItemSectionRendererContents ->
                        contentItemSectionRendererContents.has(PLAYLIST_VIDEO_LIST_RENDERER)
                                || contentItemSectionRendererContents.has(
                                "playlistSegmentRenderer"))
                .findFirst()
                .orElse(null);

        if (videoPlaylistObject != null && videoPlaylistObject.has(PLAYLIST_VIDEO_LIST_RENDERER)) {
            final JsonArray videosArray = videoPlaylistObject
                    .getObject(PLAYLIST_VIDEO_LIST_RENDERER)
                    .getArray("contents");
            collectStreamsFrom(collector, videosArray);

            nextPage = getNextPageFrom(videosArray);
        } else {
            for (final Object content : contents) {
                if (!(content instanceof JsonObject)) {
                    continue;
                }
                final JsonArray itemContents = ((JsonObject) content)
                        .getObject("itemSectionRenderer")
                        .getArray("contents");
                collectStreamsFrom(collector, itemContents);
                if (nextPage == null) {
                    nextPage = getNextPageFrom(itemContents);
                }
            }
        }

        // Handle richGridRenderer for Shorts playlists
        final JsonObject richGridObject = contents.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(content -> content.getObject("itemSectionRenderer")
                        .getArray("contents")
                        .getObject(0))
                .filter(contentItemSectionRendererContents ->
                        contentItemSectionRendererContents.has("richGridRenderer"))
                .findFirst()
                .orElse(null);

        if (richGridObject != null) {
            final JsonArray richGridContents = richGridObject
                    .getObject("richGridRenderer")
                    .getArray("contents");
            
            richGridContents.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .filter(item -> item.has("richItemRenderer"))
                    .map(item -> item.getObject("richItemRenderer").getObject("content"))
                    .filter(content -> content.has("shortsLockupViewModel"))
                    .forEach(content -> {
                        collector.commit(new YoutubeShortsInfoItemExtractor(
                                content.getObject("shortsLockupViewModel")));
                    });
            
            // Get next page from sectionListRenderer level for richGridRenderer
            nextPage = getNextPageFrom(contents);
        }

        return new InfoItemsPage<>(collector, nextPage);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page) throws IOException,
            ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final Map<String, List<String>> headers = new HashMap<>();
        addYoutubeHeaders(headers);

        final Response response = getDownloader().post(page.getUrl(), headers, page.getBody(),
                getService().getLocalization());
        final JsonObject ajaxJson = JsonUtils.toJsonObject(getValidJsonResponseBody(response));

        final JsonArray continuation = ajaxJson.getArray("onResponseReceivedActions")
                .getObject(0)
                .getObject("appendContinuationItemsAction")
                .getArray("continuationItems");

        collectStreamsFrom(collector, continuation);

        return new InfoItemsPage<>(collector, getNextPageFrom(continuation));
    }

    @Nullable
    private Page getNextPageFrom(final JsonArray contents)
            throws IOException, ExtractionException {
        if (isNullOrEmpty(contents)) {
            return null;
        }

        final JsonObject lastElement = contents.getObject(contents.size() - 1);
        final JsonObject continuationObject;
        if (lastElement.has("continuationItemRenderer")) {
            final JsonObject continuationEndpoint = lastElement
                    .getObject("continuationItemRenderer")
                    .getObject("continuationEndpoint");

            if (continuationEndpoint.has("commandExecutorCommand")) {
                // This structure is only used at the time this code is written in initial playlist
                // responses. continuationItemRenderer objects return multiple commands: one
                // containing the continuation we need and one a playlistVotingRefreshPopupCommand
                continuationObject = continuationEndpoint.getObject("commandExecutorCommand")
                        .getArray("commands")
                        .stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .filter(command -> command.has("continuationCommand"))
                        .findFirst()
                        .orElse(new JsonObject());
            } else {
                // At the time this code is written, this "classic" continuation structure is only
                // returned in browse responses of continuation requests
                continuationObject = continuationEndpoint;
            }
        } else if (lastElement.has("continuationItemViewModel")) {
            continuationObject = lastElement
                    .getObject("continuationItemViewModel")
                    .getObject("continuationCommand")
                    .getObject("innertubeCommand");
        } else {
            return null;
        }

        final String continuation = continuationObject.getObject("continuationCommand")
                .getString("token");

        if (isNullOrEmpty(continuation)) {
            // Invalid continuation or no continuation found
            return null;
        }

        final byte[] body = JsonWriter.string(prepareDesktopJsonBuilder(
                        getService().getLocalization(), getExtractorContentCountry())
                        .value("continuation", continuation)
                        .done())
                .getBytes(StandardCharsets.UTF_8);

        return new Page(YOUTUBEI_V1_URL + "browse?" + DISABLE_PRETTY_PRINT_PARAMETER, body);
    }

    private void collectStreamsFrom(@Nonnull final StreamInfoItemsCollector collector,
                                    @Nonnull final JsonArray videos) {
        final TimeAgoParser timeAgoParser = getTimeAgoParser();

        String playlistUploaderName = null;
        String playlistUploaderUrl = null;
        try {
            // A user-created playlist can include videos from any channel.
            if (getId().startsWith("OLAK5uy_")) {
                playlistUploaderName = getUploaderName();
                playlistUploaderUrl = getUploaderUrl();
            }
        } catch (final Exception ignored) {
        }
        final String fallbackName = playlistUploaderName;
        final String fallbackUrl = playlistUploaderUrl;

        // Handle traditional playlistVideoRenderer
        videos.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(video -> video.has(PLAYLIST_VIDEO_RENDERER))
                .map(video -> {
                    final YoutubeStreamInfoItemExtractor extractor = new YoutubeStreamInfoItemExtractor(
                            video.getObject(PLAYLIST_VIDEO_RENDERER), timeAgoParser);
                    if (fallbackName != null) {
                        extractor.setFallbackUploaderName(fallbackName);
                    }
                    if (fallbackUrl != null) {
                        extractor.setFallbackUploaderUrl(fallbackUrl);
                    }
                    return extractor;
                })
                .forEachOrdered(collector::commit);

        videos.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(video -> video.has("lockupViewModel"))
                .map(video -> video.getObject("lockupViewModel"))
                .forEachOrdered(lockup -> commitLockupStreamIfSupported(
                        collector, lockup, timeAgoParser, fallbackName, fallbackUrl));

        // Handle Shorts in richItemRenderer format (for continuation responses)
        videos.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(video -> video.has("richItemRenderer"))
                .map(video -> video.getObject("richItemRenderer").getObject("content"))
                .filter(content -> content.has("shortsLockupViewModel"))
                .forEach(content -> {
                    collector.commit(new YoutubeShortsInfoItemExtractor(
                            content.getObject("shortsLockupViewModel")));
                });

        videos.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(video -> video.has("richItemRenderer"))
                .map(video -> video.getObject("richItemRenderer").getObject("content"))
                .filter(content -> content.has("lockupViewModel"))
                .map(content -> content.getObject("lockupViewModel"))
                .forEachOrdered(lockup -> commitLockupStreamIfSupported(
                        collector, lockup, timeAgoParser, fallbackName, fallbackUrl));
    }

    private void commitLockupStreamIfSupported(@Nonnull final StreamInfoItemsCollector collector,
                                               @Nonnull final JsonObject lockupViewModel,
                                               @Nonnull final TimeAgoParser timeAgoParser,
                                               @Nullable final String fallbackName,
                                               @Nullable final String fallbackUrl) {
        final String contentType = lockupViewModel.getString("contentType");
        if (!"LOCKUP_CONTENT_TYPE_VIDEO".equals(contentType)
                && !"LOCKUP_CONTENT_TYPE_EPISODE".equals(contentType)) {
            return;
        }
        if (isNullOrEmpty(lockupViewModel.getObject("metadata")
                .getObject("lockupMetadataViewModel")
                .getObject("title")
                .getString("content"))) {
            return;
        }

        collector.commit(new YoutubePlaylistLockupExtractor(
                lockupViewModel, timeAgoParser, fallbackName, fallbackUrl));
    }

    @Nonnull
    @Override
    public PlaylistInfo.PlaylistType getPlaylistType() throws ParsingException {
        return extractPlaylistTypeFromPlaylistUrl(getUrl());
    }
}
