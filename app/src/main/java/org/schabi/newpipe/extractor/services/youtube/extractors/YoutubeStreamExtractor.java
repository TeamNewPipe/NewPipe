/*
 * Created by Christian Schabesberger on 06.08.15.
 *
 * Copyright (C) Christian Schabesberger 2019 <chris.schabesberger@mailbox.org>
 * YoutubeStreamExtractor.java is part of NewPipe Extractor.
 *
 * NewPipe Extractor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe Extractor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe Extractor. If not, see <https://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.*;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.channel.StaffInfoItem;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.*;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.localization.*;
import org.schabi.newpipe.extractor.services.youtube.*;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Pair;
import org.schabi.newpipe.extractor.utils.Parser;
import org.schabi.newpipe.extractor.utils.SubtitleDeduplicator;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.schabi.newpipe.extractor.services.youtube.ItagItem.APPROX_DURATION_MS_UNKNOWN;
import static org.schabi.newpipe.extractor.services.youtube.ItagItem.CONTENT_LENGTH_UNKNOWN;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.*;
import static org.schabi.newpipe.extractor.utils.Utils.EMPTY_STRING;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class YoutubeStreamExtractor extends StreamExtractor {
    private JSONObject dislikeData;
    private JsonObject playerCaptionsTracklistRenderer;
    /*//////////////////////////////////////////////////////////////////////////
    // Exceptions
    //////////////////////////////////////////////////////////////////////////*/

    public static class DeobfuscateException extends ParsingException {
        DeobfuscateException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    /*////////////////////////////////////////////////////////////////////////*/
    public JsonObject playerResponse;
    private JsonObject nextResponse;

    private JsonObject androidPlayerResponse;
    private JsonObject iosPlayerResponse;
    private JsonObject visionOsPlayerResponse;
    private JsonObject safariPlayerResponse;
    private JsonObject tvHtml5EmbedPlayerResponse;

    @Nullable
    private JsonObject androidStreamingData;
    @Nullable
    private JsonObject iosStreamingData;
    @Nullable
    private JsonObject visionOsStreamingData;

    private JsonObject tvHtml5SimplyEmbedStreamingData;

    private JsonObject webStreamingData;
    // WEB may supply storyboards even when the Android response selected for playback does not.
    private volatile String webStoryboardSpec;

    @Nullable
    private JsonObject safariStreamingData;

    private JsonObject videoPrimaryInfoRenderer;
    private JsonObject videoSecondaryInfoRenderer;
    public JsonObject playerMicroFormatRenderer;
    private int ageLimit = -1;
    private volatile StreamType streamType;

    // We need to store the contentPlaybackNonces because we need to append them to videoplayback
    // URLs (with the cpn parameter).
    // Also because a nonce should be unique, it should be different between clients used, so
    // three different strings are used.
    private String tvHtml5SimplyEmbedCpn;
    private String webCpn;
    private String androidCpn;
    private String iosCpn;
    private String visionOsCpn;
    private String safariCpn;

    public WatchDataCache watchDataCache;

    public final List<Throwable> errors = new CopyOnWriteArrayList<>();
    private boolean upcoming;

    public YoutubeStreamExtractor(final StreamingService service, final LinkHandler linkHandler, WatchDataCache watchDataCache) {
        super(service, linkHandler);
        this.watchDataCache = watchDataCache;
        watchDataCache.init(linkHandler.getUrl());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Impl
    //////////////////////////////////////////////////////////////////////////*/

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        assertPageFetched();
        String title = null;
//
//        try {
//            title = getTextFromObject(getVideoPrimaryInfoRenderer().getObject("title"));
//        } catch (final ParsingException ignored) {
//            // Age-restricted videos cause a ParsingException here
//        }

        if (isNullOrEmpty(title)) {
            title = playerResponse.getObject("videoDetails").getString("title");

            if (isNullOrEmpty(title)) {
                throw new ParsingException("Could not get name");
            }
        }

        return title;
    }

    @Override
    public long getStartAt() throws ParsingException {
        final DateWrapper uploadDate = getUploadDate();
        return uploadDate == null ? 0 : uploadDate.offsetDateTime().toEpochSecond() * 1000;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        watchDataCache.shouldBeLive = true;
        final JsonObject liveDetails = playerMicroFormatRenderer.getObject(
                "liveBroadcastDetails");
        if (!liveDetails.getString("endTimestamp", EMPTY_STRING).isEmpty()) {
            // an ended live stream
            return liveDetails.getString("endTimestamp");
        } else if (!liveDetails.getString("startTimestamp", EMPTY_STRING).isEmpty()) {
            // a running live stream
            return liveDetails.getString("startTimestamp");
        } else if (getStreamType() == StreamType.LIVE_STREAM) {
            // this should never be reached, but a live stream without upload date is valid
            return null;
        }

        watchDataCache.shouldBeLive = false;
        if (!playerMicroFormatRenderer.getString("uploadDate", EMPTY_STRING).isEmpty()) {
            return playerMicroFormatRenderer.getString("uploadDate");
        } else if (!playerMicroFormatRenderer.getString("publishDate", EMPTY_STRING).isEmpty()) {
            return playerMicroFormatRenderer.getString("publishDate");
        }

        final String videoPrimaryInfoRendererDateText =
                getTextFromObject(getVideoPrimaryInfoRenderer().getObject("dateText"));

        if (videoPrimaryInfoRendererDateText != null) {
            if (videoPrimaryInfoRendererDateText.startsWith("Premiered")) {
                final String time = videoPrimaryInfoRendererDateText.substring(13);

                try { // Premiered 20 hours ago
                    final TimeAgoParser timeAgoParser = TimeAgoPatternsManager.getTimeAgoParserFor(
                            Localization.fromLocalizationCode("en"));
                    final OffsetDateTime parsedTime = timeAgoParser.parse(time).offsetDateTime();
                    return DateTimeFormatter.ISO_LOCAL_DATE.format(parsedTime);
                } catch (final Exception ignored) {
                }

                try { // Premiered Feb 21, 2020
                    final LocalDate localDate = LocalDate.parse(time,
                            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH));
                    return DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
                } catch (final Exception ignored) {
                }

                try { // Premiered on 21 Feb 2020
                    final LocalDate localDate = LocalDate.parse(time,
                            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
                    return DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
                } catch (final Exception ignored) {
                }
            }

            try {
                // TODO: this parses English formatted dates only, we need a better approach to
                //  parse the textual date
                final LocalDate localDate = LocalDate.parse(videoPrimaryInfoRendererDateText,
                        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
                return DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
            } catch (final Exception e) {
                throw new ParsingException("Could not get upload date", e);
            }
        }

        throw new ParsingException("Could not get upload date");
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final String textualUploadDate = getTextualUploadDate();

        if (isNullOrEmpty(textualUploadDate)) {
            return null;
        }

        return new DateWrapper(YoutubeParsingHelper.parseDateFrom(textualUploadDate), true);
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        assertPageFetched();
        try {
            final JsonArray thumbnails = playerResponse
                    .getObject("videoDetails")
                    .getObject("thumbnail")
                    .getArray("thumbnails");
            // the last thumbnail is the one with the highest resolution
            final String url = thumbnails
                    .getObject(thumbnails.size() - 1)
                    .getString("url");

            return fixThumbnailUrl(url);
        } catch (final Exception e) {
            throw new ParsingException("Could not get thumbnail url");
        }

    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        assertPageFetched();
        try {
            return getImagesFromThumbnailsArray(playerResponse.getObject("videoDetails")
                    .getObject("thumbnail")
                    .getArray("thumbnails"));
        } catch (final Exception e) {
            throw new ParsingException("Could not get thumbnails");
        }
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        assertPageFetched();
        // Description with more info on links
        try {
            final String description = getTextFromObject(
                    getVideoSecondaryInfoRenderer().getObject("description"),
                    true);
            if (!isNullOrEmpty(description)) {
                return new Description(description, Description.HTML);
            }
        } catch (final ParsingException ignored) {
            // Age-restricted videos cause a ParsingException here
        }

        String description = playerResponse.getObject("videoDetails")
                .getString("shortDescription");
        if (description == null) {
            final JsonObject descriptionObject = playerMicroFormatRenderer.getObject("description");
            description = getTextFromObject(descriptionObject);
        }

        // Raw non-html description
        return new Description(description, Description.PLAIN_TEXT);
    }

    @Override
    public long getLength() throws ParsingException {
        assertPageFetched();

        try {
            final String duration = playerResponse
                    .getObject("videoDetails")
                    .getString("lengthSeconds");
            return Long.parseLong(duration);
        } catch (final Exception e) {
            return getDurationFromFirstAdaptiveFormat(Arrays.asList(
                    safariStreamingData, androidStreamingData, visionOsStreamingData,
                    iosStreamingData, tvHtml5SimplyEmbedStreamingData, webStreamingData));
        }
    }

    private int getDurationFromFirstAdaptiveFormat(@Nonnull final List<JsonObject> streamingDatas)
            throws ParsingException {
        for (final JsonObject streamingData : streamingDatas) {
            final JsonArray adaptiveFormats = streamingData.getArray(ADAPTIVE_FORMATS);
            if (adaptiveFormats.isEmpty()) {
                continue;
            }

            final String durationMs = adaptiveFormats.getObject(0)
                    .getString("approxDurationMs");
            try {
                return Math.round(Long.parseLong(durationMs) / 1000f);
            } catch (final NumberFormatException ignored) {
            }
        }

        throw new ParsingException("Could not get duration");
    }

    /**
     * Attempts to parse (and return) the offset to start playing the video from.
     *
     * @return the offset (in seconds), or 0 if no timestamp is found.
     */
    @Override
    public long getTimeStamp() throws ParsingException {
        final long timestamp =
                getTimestampSeconds("((#|&|\\?)t=\\d*h?\\d*m?\\d+s?)");

        if (timestamp == -2) {
            // Regex for timestamp was not found
            return 0;
        }
        return timestamp;
    }

    @Override
    public long getViewCount() throws ParsingException {
        String views = null;

        try {
            views = getTextFromObject(getVideoPrimaryInfoRenderer().getObject("viewCount")
                    .getObject("videoViewCountRenderer").getObject("viewCount"));
        } catch (final ParsingException ignored) {
            // Age-restricted videos cause a ParsingException here
        }

        if (isNullOrEmpty(views)) {
            views = playerResponse.getObject("videoDetails").getString("viewCount");

            if (isNullOrEmpty(views)) {
                throw new ParsingException("Could not get view count");
            }
        }

        if (views.toLowerCase().contains("no views")
                || views.toLowerCase().contains("akukho ukubukwa")
                || views.toLowerCase().contains("akukho kubukwa")) {
            return 0;
        }

        return Long.parseLong(Utils.removeNonDigitCharacters(views));
    }

    @Override
    public long getLikeCount() throws ParsingException {
        assertPageFetched();

        // If ratings are not allowed, there is no like count available
        if (!playerResponse.getObject("videoDetails").getBoolean("allowRatings")) {
            return -1;
        }

        String likesString = null;

        try {
            final List<JsonObject> topLevelButtons = getVideoPrimaryInfoRenderer()
                    .getObject("videoActions")
                    .getObject("menuRenderer")
                    .getArray("topLevelButtons")
                    .stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .collect(Collectors.toList());

            likesString = topLevelButtons.stream()
                    .map(btn -> btn.getObject("segmentedLikeDislikeButtonViewModel")
                            .getObject("likeButtonViewModel")
                            .getObject("likeButtonViewModel")
                            .getObject("toggleButtonViewModel")
                            .getObject("toggleButtonViewModel")
                            .getObject("defaultButtonViewModel")
                            .getObject("buttonViewModel")
                            .getString("accessibilityText"))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            // Old - pre Dec 2023 way
            if (likesString == null) {
                likesString = getPreDec2023LikeString(topLevelButtons);
            }

            // If ratings are allowed and the likes string is null, it means that we couldn't
            // extract the (real) like count from accessibility data
            if (likesString == null) {
                throw new ParsingException("Could not get like count from accessibility data");
            }

            // This check only works with English localizations!
            if (likesString.toLowerCase().contains("no likes")) {
                return 0;
            }

            return Integer.parseInt(Utils.removeNonDigitCharacters(likesString));
        } catch (final NumberFormatException nfe) {
            throw new ParsingException("Could not parse \"" + likesString + "\" as an Integer",
                    nfe);
        } catch (final Exception e) {
            throw new ParsingException("Could not get like count", e);
        }
    }

    protected String getPreDec2023LikeString(final List<JsonObject> topLevelButtons)
            throws ParsingException {
        // Try first with the new video actions buttons data structure
        JsonObject likeToggleButtonRenderer = topLevelButtons.stream()
                .map(button -> button.getObject("segmentedLikeDislikeButtonRenderer")
                        .getObject("likeButton")
                        .getObject("toggleButtonRenderer"))
                .filter(toggleButtonRenderer -> !isNullOrEmpty(toggleButtonRenderer))
                .findFirst()
                .orElse(null);

        // Use the old video actions buttons data structure if the new one isn't returned
        if (likeToggleButtonRenderer == null) {
            /*
            In the old video actions buttons data structure, there are 3 ways to detect whether
            a button is the like button, using its toggleButtonRenderer:
            - checking whether toggleButtonRenderer.targetId is equal to watch-like;
            - checking whether toggleButtonRenderer.defaultIcon.iconType is equal to LIKE;
            - checking whether
              toggleButtonRenderer.toggleButtonSupportedData.toggleButtonIdData.id
              is equal to TOGGLE_BUTTON_ID_TYPE_LIKE.
            */
            likeToggleButtonRenderer = topLevelButtons.stream()
                    .map(topLevelButton -> topLevelButton.getObject("toggleButtonRenderer"))
                    .filter(toggleButtonRenderer -> "watch-like".equalsIgnoreCase(
                            toggleButtonRenderer.getString("targetId"))
                            || "LIKE".equalsIgnoreCase(
                            toggleButtonRenderer.getObject("defaultIcon")
                                    .getString("iconType"))
                            || "TOGGLE_BUTTON_ID_TYPE_LIKE".equalsIgnoreCase(
                            toggleButtonRenderer.getObject("toggleButtonSupportedData")
                                    .getObject("toggleButtonIdData")
                                    .getString("id")))
                    .findFirst()
                    .orElseThrow(() -> new ParsingException(
                            "The like button is missing even though ratings are enabled"));
        }

        // Use one of the accessibility strings available (this one has the same path as the
        // one used for comments' like count extraction)
        String likesString = likeToggleButtonRenderer.getObject("accessibilityData")
                .getObject("accessibilityData")
                .getString("label");

        // Use the other accessibility string available which contains the exact like count
        if (likesString == null) {
            likesString = likeToggleButtonRenderer.getObject("accessibility")
                    .getString("label");
        }

        // Last method: use the defaultText's accessibility data, which contains the exact like
        // count too, except when it is equal to 0, where a localized string is returned instead
        if (likesString == null) {
            likesString = likeToggleButtonRenderer.getObject("defaultText")
                    .getObject("accessibility")
                    .getObject("accessibilityData")
                    .getString("label");
        }
        return likesString;
    }


    @Nonnull
    @Override
    public String getUploaderUrl() throws ParsingException {
        assertPageFetched();

        // Don't use the id in the videoSecondaryRenderer object to get real id of the uploader
        // The difference between the real id of the channel and the displayed id is especially
        // visible for music channels and autogenerated channels.
        final String uploaderId = playerResponse.getObject("videoDetails").getString("channelId");
        if (!isNullOrEmpty(uploaderId)) {
            return YoutubeChannelLinkHandlerFactory.getInstance().getUrl("channel/" + uploaderId);
        }

        throw new ParsingException("Could not get uploader url");
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        assertPageFetched();

        // Don't use the name in the videoSecondaryRenderer object to get real name of the uploader
        // The difference between the real name of the channel and the displayed name is especially
        // visible for music channels and autogenerated channels.
        final String uploaderName = playerResponse.getObject("videoDetails").getString("author");
        if (isNullOrEmpty(uploaderName)) {
            return "Unknown";
        }

        return uploaderName;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        JsonObject videoOwnerRenderer = getVideoSecondaryInfoRenderer()
                .getObject("owner")
                .getObject("videoOwnerRenderer");
        if (videoOwnerRenderer.has("badges")) {
            return YoutubeParsingHelper.isVerified(videoOwnerRenderer.getArray("badges"));
        }
        JsonObject navigationEndpoint = videoOwnerRenderer.getObject("navigationEndpoint");
        if (navigationEndpoint != null && navigationEndpoint.has("showDialogCommand")) {
            try {
                JsonArray listItems = navigationEndpoint
                        .getObject("showDialogCommand")
                        .getObject("panelLoadingStrategy")
                        .getObject("inlineContent")
                        .getObject("dialogViewModel")
                        .getObject("customContent")
                        .getObject("listViewModel")
                        .getArray("listItems");
                if (!listItems.isEmpty()) {
                    JsonObject firstItem = listItems.getObject(0)
                            .getObject("listItemViewModel")
                            .getObject("title");
                    if (firstItem.has("attachmentRuns")) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    @Nonnull
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        assertPageFetched();

        JsonObject videoOwnerRenderer = getVideoSecondaryInfoRenderer()
                .getObject("owner")
                .getObject("videoOwnerRenderer");

        if (videoOwnerRenderer.has("thumbnail")) {
            JsonArray thumbnails = videoOwnerRenderer
                    .getObject("thumbnail")
                    .getArray("thumbnails");
            final String url = thumbnails
                    .getObject(thumbnails.size() - 1)
                    .getString("url");

            if (isNullOrEmpty(url)) {
                if (ageLimit == NO_AGE_LIMIT) {
                    throw new ParsingException("Could not get uploader avatar URL");
                }
                return EMPTY_STRING;
            }
            return fixThumbnailUrl(url);
        }

        JsonObject navigationEndpoint = videoOwnerRenderer.getObject("navigationEndpoint");
        if (navigationEndpoint != null && navigationEndpoint.has("showDialogCommand")) {
            try {
                JsonArray listItems = navigationEndpoint
                        .getObject("showDialogCommand")
                        .getObject("panelLoadingStrategy")
                        .getObject("inlineContent")
                        .getObject("dialogViewModel")
                        .getObject("customContent")
                        .getObject("listViewModel")
                        .getArray("listItems");
                if (!listItems.isEmpty()) {
                    JsonArray sources = listItems.getObject(0)
                            .getObject("listItemViewModel")
                            .getObject("leadingAccessory")
                            .getObject("avatarViewModel")
                            .getObject("image")
                            .getArray("sources");
                    if (!sources.isEmpty()) {
                        String url = sources.getObject(0).getString("url");
                        if (!isNullOrEmpty(url)) {
                            return fixThumbnailUrl(url);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (ageLimit == NO_AGE_LIMIT) {
            throw new ParsingException("Could not get uploader avatar URL");
        }
        return EMPTY_STRING;
    }

    @Override
    public long getUploaderSubscriberCount() throws ParsingException {
        final JsonObject videoOwnerRenderer = JsonUtils.getObject(videoSecondaryInfoRenderer,
                "owner.videoOwnerRenderer");
        if (videoOwnerRenderer.has("subscriberCountText")) {
            try {
                return Utils.mixedNumberWordToLong(getTextFromObject(videoOwnerRenderer
                        .getObject("subscriberCountText")));
            } catch (final NumberFormatException e) {
                throw new ParsingException("Could not get uploader subscriber count", e);
            }
        }
        JsonObject navigationEndpoint = videoOwnerRenderer.getObject("navigationEndpoint");
        if (navigationEndpoint != null && navigationEndpoint.has("showDialogCommand")) {
            try {
                JsonArray listItems = navigationEndpoint
                        .getObject("showDialogCommand")
                        .getObject("panelLoadingStrategy")
                        .getObject("inlineContent")
                        .getObject("dialogViewModel")
                        .getObject("customContent")
                        .getObject("listViewModel")
                        .getArray("listItems");
                if (!listItems.isEmpty()) {
                    String subtitle = listItems.getObject(0)
                            .getObject("listItemViewModel")
                            .getObject("subtitle")
                            .getString("content", "");
                    int idx = subtitle.indexOf("•");
                    if (idx >= 0) {
                        String subCount = subtitle.substring(idx + 1).trim();
                        return Utils.mixedNumberWordToLong(subCount);
                    }
                }
            } catch (Exception ignored) {}
        }
        return UNKNOWN_SUBSCRIBER_COUNT;
    }

    @Nonnull
    @Override
    public List<StaffInfoItem> getStaffs() {
        final JsonObject videoOwnerRenderer = getVideoSecondaryInfoRenderer()
                .getObject("owner")
                .getObject("videoOwnerRenderer");
        JsonObject navigationEndpoint = videoOwnerRenderer.getObject("navigationEndpoint");
        if (navigationEndpoint == null || !navigationEndpoint.has("showDialogCommand")) {
            return Collections.emptyList();
        }
        try {
            JsonArray listItems = navigationEndpoint
                    .getObject("showDialogCommand")
                    .getObject("panelLoadingStrategy")
                    .getObject("inlineContent")
                    .getObject("dialogViewModel")
                    .getObject("customContent")
                    .getObject("listViewModel")
                    .getArray("listItems");
            List<StaffInfoItem> result = new ArrayList<>();
            for (Object item : listItems) {
                JsonObject listItemViewModel = ((JsonObject) item).getObject("listItemViewModel");
                String name = listItemViewModel.getObject("title").getString("content", "");
                String avatarUrl = "";
                JsonArray sources = listItemViewModel
                        .getObject("leadingAccessory")
                        .getObject("avatarViewModel")
                        .getObject("image")
                        .getArray("sources");
                if (!sources.isEmpty()) {
                    avatarUrl = sources.getObject(0).getString("url", "");
                }
                String channelUrl = "";
                JsonArray commandRuns = listItemViewModel.getObject("title").getArray("commandRuns");
                if (commandRuns != null && !commandRuns.isEmpty()) {
                    JsonObject browseEndpoint = commandRuns.getObject(0)
                            .getObject("onTap")
                            .getObject("innertubeCommand")
                            .getObject("browseEndpoint");
                    String browseId = browseEndpoint.getString("browseId", "");
                    if (!browseId.isEmpty()) {
                        channelUrl = YoutubeChannelLinkHandlerFactory.getInstance()
                                .getUrl("channel/" + browseId);
                    }
                }
                result.add(new StaffInfoItem(getServiceId(), channelUrl, name, null, avatarUrl));
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Nonnull
    @Override
    public String getDashMpdUrl() throws ParsingException {
        assertPageFetched();
        if (streamType == StreamType.VIDEO_STREAM
                && !playerResponse.getObject("videoDetails").getBoolean("isLive", false)
                && !playerResponse.getObject("playabilityStatus").has("liveStreamability")) {
            return "";
        }
        if (streamType == StreamType.LIVE_STREAM && !StringUtils.isBlank(ServiceList.YouTube.getTokens())) {
            return "";
        }
        String dashUrl = getManifestUrl(
                "dash",
                Arrays.asList(visionOsStreamingData, safariStreamingData, androidStreamingData,
                        iosStreamingData, tvHtml5SimplyEmbedStreamingData, webStreamingData));

        if (!dashUrl.isEmpty()) {
            dashUrl = deobfuscateManifestUrl(dashUrl);
        }

        return dashUrl;
    }

    @Nonnull
    @Override
    public String getHlsUrl() throws ParsingException {
        assertPageFetched();

        String hlsUrl = getManifestUrl(
                "hls",
                Arrays.asList(visionOsStreamingData, safariStreamingData, androidStreamingData,
                        iosStreamingData, tvHtml5SimplyEmbedStreamingData, webStreamingData));

        if (!hlsUrl.isEmpty()) {
            hlsUrl = deobfuscateManifestUrl(hlsUrl);
        }

        return hlsUrl;
    }

    @Nonnull
    private String deobfuscateManifestUrl(@Nonnull final String manifestUrl) throws ParsingException {
        final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/n/([^/]+)").matcher(manifestUrl);
        if (matcher.find()) {
            final String encryptedN = matcher.group(1);
            try {
                final String videoId = getId();
                final YoutubeApiDecoder.BatchDecodeResult result =
                        YoutubeJavaScriptPlayerManager.deobfuscateBatch(
                                videoId,
                                null,
                                Collections.singletonList(encryptedN));
                final String decryptedN = result.getNParameters().get(encryptedN);
                if (decryptedN != null) {
                    return manifestUrl.replace("/n/" + encryptedN, "/n/" + decryptedN);
                }
            } catch (final Exception e) {
                throw new ParsingException("Could not deobfuscate manifest URL n parameter", e);
            }
        }
        return manifestUrl;
    }

    @Nonnull
    private static String getManifestUrl(@Nonnull final String manifestType,
                                         @Nonnull final List<JsonObject> streamingDataObjects) {
        final String manifestKey = manifestType + "ManifestUrl";

        return streamingDataObjects.stream()
                .filter(Objects::nonNull)
                .map(streamingDataObject ->
                        streamingDataObject.getString(manifestKey, EMPTY_STRING))
                .filter(StringUtils::isNotBlank)
                .map(manifestUrl -> "dash".equals(manifestType)
                        ? appendMpdVersion(manifestUrl)
                        : manifestUrl)
                .findFirst()
                .orElse(EMPTY_STRING);
    }

    @Nonnull
    private static String appendMpdVersion(@Nonnull final String manifestUrl) {
        final int fragmentStart = manifestUrl.indexOf('#');
        final String url = fragmentStart < 0
                ? manifestUrl
                : manifestUrl.substring(0, fragmentStart);
        final String fragment = fragmentStart < 0
                ? EMPTY_STRING
                : manifestUrl.substring(fragmentStart);
        final String separator;
        if (!url.contains("?")) {
            separator = "?";
        } else if (url.endsWith("?") || url.endsWith("&")) {
            separator = EMPTY_STRING;
        } else {
            separator = "&";
        }

        return url + separator + "mpd_version=7" + fragment;
    }

    // Cache for batch-processed streams
    private List<AudioStream> cachedAudioStreams;
    private List<VideoStream> cachedVideoStreams;
    private List<VideoStream> cachedVideoOnlyStreams;
    private boolean streamsCached = false;

    /**
     * Returns direct stream responses in reliability order.
     *
     * <p>VISIONOS is preferred because YouTube can expose ANDROID_VR adaptive URLs that start
     * successfully but return HTTP 403 on a later byte-range request. Android remains the final
     * fallback for content unavailable through the other clients.</p>
     */
    @Nonnull
    private java.util.stream.Stream<Pair<JsonObject, String>>
            getPreferredStreamingData() {
        return java.util.stream.Stream.of(
                new Pair<>(visionOsStreamingData, visionOsCpn),
                new Pair<>(safariStreamingData, safariCpn),
                new Pair<>(iosStreamingData, iosCpn),
                new Pair<>(tvHtml5SimplyEmbedStreamingData, tvHtml5SimplyEmbedCpn),
                new Pair<>(androidStreamingData, androidCpn));
    }

    /**
     * Pre-fetch and batch-process all streams in a single API call.
     * This method collects all audio, video, and video-only streams,
     * then performs batch deobfuscation in one request.
     */
    private void ensureStreamsAreCached() throws ExtractionException {
        if (streamsCached) {
            return;
        }

        assertPageFetched();
        final String videoId = getId();

        // Collect all ItagInfo objects from all stream types
        final List<ItagInfo> allItagInfos = new ArrayList<>();
        final int audioStartIndex = 0;
        final int videoStartIndex;
        final int videoOnlyStartIndex;

        try {
            // Collect audio streams
            getPreferredStreamingData()
                    .flatMap(pair -> getStreamsFromStreamingDataKey(videoId, pair.getFirst(),
                            ADAPTIVE_FORMATS, ItagItem.ItagType.AUDIO, pair.getSecond()))
                    .forEachOrdered(allItagInfos::add);

            videoStartIndex = allItagInfos.size();

            // Collect video streams
            getPreferredStreamingData()
                    .flatMap(pair -> getStreamsFromStreamingDataKey(videoId, pair.getFirst(),
                            FORMATS, ItagItem.ItagType.VIDEO, pair.getSecond()))
                    .forEachOrdered(allItagInfos::add);

            videoOnlyStartIndex = allItagInfos.size();

            // Collect video-only streams
            getPreferredStreamingData()
                    .flatMap(pair -> getStreamsFromStreamingDataKey(videoId, pair.getFirst(),
                            ADAPTIVE_FORMATS, ItagItem.ItagType.VIDEO_ONLY, pair.getSecond()))
                    .forEachOrdered(allItagInfos::add);

            // Batch deobfuscate ALL streams in a single API call
            batchDeobfuscateItagUrls(videoId, allItagInfos);

            // Now build the separate stream lists
            cachedAudioStreams = new ArrayList<>();
            for (int i = audioStartIndex; i < videoStartIndex; i++) {
                final AudioStream stream = getAudioStreamBuilderHelper().apply(allItagInfos.get(i));
                if (!Stream.containSimilarStream(stream, cachedAudioStreams)) {
                    cachedAudioStreams.add(stream);
                }
            }
            Collections.sort(cachedAudioStreams, Comparator.comparingInt(AudioStream::getBitrate).reversed());

            cachedVideoStreams = new ArrayList<>();
            for (int i = videoStartIndex; i < videoOnlyStartIndex; i++) {
                final VideoStream stream = getVideoStreamBuilderHelper(false).apply(allItagInfos.get(i));
                if (!Stream.containSimilarStream(stream, cachedVideoStreams)) {
                    cachedVideoStreams.add(stream);
                }
            }

            cachedVideoOnlyStreams = new ArrayList<>();
            for (int i = videoOnlyStartIndex; i < allItagInfos.size(); i++) {
                final VideoStream stream = getVideoStreamBuilderHelper(true).apply(allItagInfos.get(i));
                if (!Stream.containSimilarStream(stream, cachedVideoOnlyStreams)) {
                    cachedVideoOnlyStreams.add(stream);
                }
            }

            if (cachedAudioStreams.isEmpty() && cachedVideoOnlyStreams.isEmpty()) {
                tryExtractHlsStreams(videoId);
            }

            streamsCached = true;

        } catch (final Exception e) {
            throw new ParsingException("Could not get streams", e);
        }
    }

    private void tryExtractHlsStreams(final String videoId) throws ExtractionException {
        final String hlsManifestUrl = getHlsManifestUrlFromStreamingData();
        if (hlsManifestUrl.isEmpty()) {
            return;
        }

        try {
            final String deobfuscatedUrl = deobfuscateManifestUrl(hlsManifestUrl);
            final Response response = NewPipe.getDownloader().get(deobfuscatedUrl);
            final String manifestContent = response.responseBody();
            parseHlsMasterManifest(manifestContent, videoId);
        } catch (final Exception e) {
            throw new ParsingException("Could not extract HLS streams", e);
        }
    }

    @Nonnull
    private String getHlsManifestUrlFromStreamingData() {
        for (final JsonObject sd : Arrays.asList(
                safariStreamingData)) {
            if (sd != null) {
                final String url = sd.getString("hlsManifestUrl");
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }
        }
        return EMPTY_STRING;
    }

    private void parseHlsMasterManifest(@Nonnull final String manifestContent,
                                         final String videoId) throws ParsingException {
        final String[] lines = manifestContent.split("\n");
        final String preferredAudioLanguage = ServiceList.YouTube.getAudioLanguage();

        final Map<String, String> audioTrackUrls = new LinkedHashMap<>();
        final Map<String, String> audioTrackNames = new LinkedHashMap<>();
        final Map<String, String> audioTrackLocales = new LinkedHashMap<>();
        final Map<String, AudioTrackType> audioTrackTypes = new LinkedHashMap<>();

        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (!line.startsWith("#EXT-X-STREAM-INF:")) {
                continue;
            }
            if (i + 1 >= lines.length) {
                break;
            }
            final String streamUrl = lines[i + 1].trim();
            if (streamUrl.isEmpty() || streamUrl.startsWith("#")) {
                continue;
            }

            final String resolution = extractHlsStringAttribute(line, "RESOLUTION");
            final String codecs = extractHlsStringAttribute(line, "CODECS");
            final int frameRate = extractHlsAttribute(line, "FRAME-RATE");
            final String audioContentId = extractHlsStringAttribute(line, "YT-EXT-AUDIO-CONTENT-ID");

            String videoCodec = null;
            if (codecs != null) {
                for (final String c : codecs.split(",")) {
                    final String trimmed = c.trim();
                    if (!trimmed.startsWith("mp4a") && !trimmed.startsWith("opus")
                            && !trimmed.startsWith("ac-3") && !trimmed.startsWith("ec-3")) {
                        videoCodec = trimmed;
                        break;
                    }
                }
            }

            final AudioTrackType audioTrackType = extractHlsAudioTrackType(streamUrl);

            if (resolution != null && !resolution.isEmpty()) {
                final String[] resParts = resolution.split("x");
                final String height = resParts.length == 2 ? resParts[1] + "p" : resolution;
                final String resString = frameRate > 30 ? height + frameRate : height;

                String audioTrackId = null;
                String audioLocale = null;
                String audioTrackName = null;
                if (audioContentId != null && !audioContentId.isEmpty()) {
                    audioTrackId = audioContentId;
                    final String langPart = audioContentId.split("\\.")[0];
                    audioLocale = langPart;
                    audioTrackName = langPart;

                    if (!audioTrackUrls.containsKey(audioTrackId)
                            || audioTrackType == AudioTrackType.ORIGINAL) {
                        audioTrackUrls.put(audioTrackId, streamUrl);
                    }
                    audioTrackNames.put(audioTrackId, audioTrackName);
                    audioTrackLocales.put(audioTrackId, audioLocale);
                    if (audioTrackType != null) {
                        audioTrackTypes.put(audioTrackId, audioTrackType);
                    }
                }

                final String streamId = "hls-" + videoId + "-" + resString
                        + (audioTrackId != null ? "-" + audioTrackId : "");

                final VideoStream.Builder builder = new VideoStream.Builder()
                        .setId(streamId)
                        .setContent(streamUrl, true)
                        .setIsVideoOnly(false)
                        .setResolution(resString)
                        .setDeliveryMethod(DeliveryMethod.HLS);

                if (videoCodec != null) {
                    builder.setCodec(videoCodec);
                }
                if (codecs != null) {
                    if (codecs.contains("avc") || codecs.contains("mp4a")) {
                        builder.setMediaFormat(MediaFormat.MPEG_4);
                    } else if (codecs.contains("vp9") || codecs.contains("vp09")) {
                        builder.setMediaFormat(MediaFormat.WEBM);
                    }
                }

                if (audioTrackId != null) {
                    builder.setAudioTrackId(audioTrackId);
                    builder.setAudioTrackName(audioTrackName);
                    builder.setAudioLocale(audioLocale);
                }

                cachedVideoStreams.add(builder.build());
            }
        }

        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (!line.startsWith("#EXT-X-MEDIA:") || !line.contains("TYPE=AUDIO")) {
                continue;
            }
            final String uri = extractHlsStringAttribute(line, "URI");
            if (uri == null || uri.isEmpty()) {
                continue;
            }

            final String groupId = extractHlsStringAttribute(line, "GROUP-ID");
            final String name = extractHlsStringAttribute(line, "NAME");
            final String language = extractHlsStringAttribute(line, "LANGUAGE");

            final AudioStream.Builder audioBuilder = new AudioStream.Builder()
                    .setId("hls-" + videoId + "-audio-" + (groupId != null ? groupId : "default")
                            + (language != null ? "-" + language : ""))
                    .setContent(uri, true)
                    .setMediaFormat(MediaFormat.M4A)
                    .setDeliveryMethod(DeliveryMethod.HLS);
            if (name != null) {
                audioBuilder.setAudioTrackName(name);
            }
            if (language != null) {
                audioBuilder.setAudioTrackId(language);
                audioBuilder.setAudioLocale(language);
            }
            final AudioTrackType audioTrackType = extractHlsAudioTrackType(uri);
            if (audioTrackType != null) {
                audioBuilder.setAudioTrackType(audioTrackType);
            }
            cachedAudioStreams.add(audioBuilder.build());
        }

        if (cachedAudioStreams.isEmpty() && !audioTrackUrls.isEmpty()) {
            for (final Map.Entry<String, String> entry : audioTrackUrls.entrySet()) {
                final String trackId = entry.getKey();
                final String url = entry.getValue();
                final String trackName = audioTrackNames.get(trackId);
                final String locale = audioTrackLocales.get(trackId);
                final AudioTrackType trackType = audioTrackTypes.get(trackId);

                final AudioStream.Builder audioBuilder = new AudioStream.Builder()
                        .setId("hls-" + videoId + "-audio-" + trackId)
                        .setContent(url, true)
                        .setMediaFormat(MediaFormat.M4A)
                        .setDeliveryMethod(DeliveryMethod.HLS)
                        .setAudioTrackId(trackId)
                        .setAudioLocale(locale)
                        .setAudioTrackType(trackType);
                if (trackName != null) {
                    audioBuilder.setAudioTrackName(trackName);
                }
                cachedAudioStreams.add(audioBuilder.build());
            }
        }
    }

    @Nullable
    private static AudioTrackType extractHlsAudioTrackType(@Nonnull final String url) {
        try {
            final String decoded = java.net.URLDecoder.decode(url, "UTF-8");
            final java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("xtags=([^/]+)").matcher(decoded);
            if (m.find()) {
                final String xtags = m.group(1);
                final AudioTrackType encodedType = extractAudioTrackType(xtags);
                if (encodedType != null) {
                    return encodedType;
                }
                for (final String part : xtags.split(":")) {
                    final String[] kv = part.split("=", 2);
                    if (kv.length == 2 && "acont".equals(kv[0])) {
                        return audioTrackTypeFromAudioContentType(kv[1]);
                    }
                }
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private static int extractHlsAttribute(@Nonnull final String line,
                                            @Nonnull final String attribute) {
        final java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(attribute + "=(\\d+)").matcher(line);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (final NumberFormatException ignored) {
            }
        }
        return -1;
    }

    @Nullable
    private static String extractHlsStringAttribute(@Nonnull final String line,
                                                     @Nonnull final String attribute) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(attribute + "=\"([^\"]+)\"").matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = java.util.regex.Pattern
                .compile(attribute + "=([^,\\s]+)").matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    public List<AudioStream> getAudioStreams() throws ExtractionException {
        ensureStreamsAreCached();
        return cachedAudioStreams;
    }

    @Override
    public boolean isSpherical() {
        return org.schabi.newpipe.extractor.services.youtube.YoutubeProjection.isSpherical(
                safariStreamingData, androidStreamingData, visionOsStreamingData,
                iosStreamingData, webStreamingData, tvHtml5SimplyEmbedStreamingData);
    }

    @Override
    public List<VideoStream> getVideoStreams() throws ExtractionException {
        ensureStreamsAreCached();
        return cachedVideoStreams;
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() throws ExtractionException {
        ensureStreamsAreCached();
        return cachedVideoOnlyStreams;
    }

    @Override
    @Nonnull
    public List<SubtitlesStream> getSubtitlesDefault() throws ParsingException {
        return getSubtitles(MediaFormat.TTML);
    }

    @Override
    @Nonnull
    public List<SubtitlesStream> getSubtitles(final MediaFormat format) throws ParsingException {
        assertPageFetched();

        if(playerCaptionsTracklistRenderer == null) {
            return new ArrayList<>();
        }

        // We cannot store the subtitles list because the media format may change
        final List<SubtitlesStream> subtitlesToReturn = new ArrayList<>();
        final JsonArray captionsArray = playerCaptionsTracklistRenderer.getArray("captionTracks");
        // TODO: use this to apply auto translation to different language from a source language
        // final JsonArray autoCaptionsArray = renderer.getArray("translationLanguages");

        boolean hasDuplicateEntries = false;

        for (int i = 0; i < captionsArray.size(); i++) {
            final String languageCode = captionsArray.getObject(i).getString("languageCode");
            final String baseUrl = captionsArray.getObject(i).getString("baseUrl");
            final String vssId = captionsArray.getObject(i).getString("vssId");

            if (languageCode != null && baseUrl != null && vssId != null) {
                final boolean isAutoGenerated = vssId.startsWith("a.");
                final String cleanUrl = baseUrl
                        // Remove preexisting format if exists
                        .replaceAll("&fmt=[^&]*", "")
                        // Remove translation language
                        .replaceAll("&tlang=[^&]*", "");

                String remoteSubtitleUrl = cleanUrl + "&fmt=" + format.getSuffix();

                if (i == 0) { // only check once
                    hasDuplicateEntries = SubtitleDeduplicator.hasDuplicateEntries(remoteSubtitleUrl);
                }

                subtitlesToReturn.add(new SubtitlesStream.Builder()
                        .setContent(hasDuplicateEntries? SubtitleDeduplicator.getDeduplicatedContent(remoteSubtitleUrl): remoteSubtitleUrl, !hasDuplicateEntries)
                        .setMediaFormat(format)
                        .setLanguageCode(languageCode)
                        .setAutoGenerated(isAutoGenerated)
                        .build());

            }
        }

        return subtitlesToReturn;
    }


    @Override
    public boolean isSupportComments() throws ParsingException {
        return !getStreamType().equals(StreamType.LIVE_STREAM);
    }

    @Override
    public StreamType getStreamType() {
        return streamType;
    }

    public void setStreamType() {
        streamType = YoutubeStreamKind.resolve(playerResponse,
                !StringUtils.isBlank(ServiceList.YouTube.getTokens()));
        watchDataCache.streamType = streamType;
    }

    @Nullable
    @Override
    public MultiInfoItemsCollector getRelatedItems() throws ExtractionException {
        assertPageFetched();

        if (getAgeLimit() != NO_AGE_LIMIT) {
            return null;
        }

        try {
            final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());

            final JsonArray results = nextResponse
                    .getObject("contents")
                    .getObject("twoColumnWatchNextResults")
                    .getObject("secondaryResults")
                    .getObject("secondaryResults")
                    .getArray("results");

            final TimeAgoParser timeAgoParser = getTimeAgoParser();
            results.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(result -> {
                        if (result.has("compactVideoRenderer")) {
                            return new YoutubeStreamInfoItemExtractor(
                                    result.getObject("compactVideoRenderer"), timeAgoParser);
                        } else if (result.has("compactRadioRenderer")) {
                            return new YoutubeMixOrPlaylistInfoItemExtractor(
                                    result.getObject("compactRadioRenderer"));
                        } else if (result.has("compactPlaylistRenderer")) {
                            return new YoutubeMixOrPlaylistInfoItemExtractor(
                                    result.getObject("compactPlaylistRenderer"));
                        } else if (result.has("lockupViewModel")) {
                            final JsonObject lockupViewModel = result.getObject("lockupViewModel");
                            if ("LOCKUP_CONTENT_TYPE_PLAYLIST".equals(
                                    lockupViewModel.getString("contentType"))) {
                                return new YoutubeMixOrPlaylistLockupInfoItemExtractor(
                                        lockupViewModel);
                            }
                            else if ("LOCKUP_CONTENT_TYPE_VIDEO".equals(
                                    lockupViewModel.getString("contentType"))){
                                return new YoutubeLockupStreamInfoItemExtractor(lockupViewModel, timeAgoParser);
                            }
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .forEach(collector::commit);

            if (ServiceList.YouTube.getFilterTypes().contains("related_item")) {
                collector.applyBlocking(ServiceList.YouTube.getFilterConfig());
            }
            return collector;
        } catch (final Exception e) {
            throw new ParsingException("Could not get related videos", e);
        }
    }

    @Override
    public long getDislikeCount() throws ParsingException {
        try {
            return dislikeData.getLong("dislikes");
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getErrorMessage() {
        try {
            return getTextFromObject(playerResponse.getObject("playabilityStatus")
                    .getObject("errorScreen").getObject("playerErrorMessageRenderer")
                    .getObject("reason"));
        } catch (final ParsingException | NullPointerException e) {
            return null; // No error message
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fetch page
    //////////////////////////////////////////////////////////////////////////*/

    private static final String FORMATS = "formats";
    private static final String ADAPTIVE_FORMATS = "adaptiveFormats";
    private static final String STREAMING_DATA = "streamingData";
    private static final String PLAYER = "player";
    private static final String NEXT = "next";
    private static final String SIGNATURE_CIPHER = "signatureCipher";
    private static final String CIPHER = "cipher";



    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {

        final String videoId = getId();
        // InnerTube's hl parameter is a language code, while the content country is sent as gl.
        // Passing Android's regional locale (for example en-AE) can produce unrelated localized
        // playability responses, so keep player requests in English and preserve the selected gl.
        final Localization localization = new Localization("en");
        final ContentCountry contentCountry = getExtractorContentCountry();

        errors.clear();
        playerResponse = null;
        webStoryboardSpec = null;
        androidPlayerResponse = null;
        iosPlayerResponse = null;
        visionOsPlayerResponse = null;
        safariPlayerResponse = null;
        tvHtml5EmbedPlayerResponse = null;
        androidStreamingData = null;
        iosStreamingData = null;
        visionOsStreamingData = null;
        safariStreamingData = null;
        webStreamingData = null;
        tvHtml5SimplyEmbedStreamingData = null;
        playerCaptionsTracklistRenderer = null;
        streamType = null;

        // Metadata does not depend on playback-client selection. Start it early so its
        // network round trips overlap the primary player request and any client fallback.
        CancellableCall webPageCall = YoutubeParsingHelper.getWebPlayerResponse(
                localization, contentCountry, videoId, this);

        final byte[] body = JsonWriter.string(
                prepareDesktopJsonBuilder(localization, contentCountry)
                        .value(VIDEO_ID, videoId)
                        .value(CONTENT_CHECK_OK, true)
                        .value(RACY_CHECK_OK, true)
                        .done())
                .getBytes(StandardCharsets.UTF_8);
       CancellableCall nextDataCall = getJsonPostResponseAsync(NEXT, body, localization, new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) throws ExtractionException {
                try {
                    nextResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        });

        // fetch dislike

            CancellableCall dislikeCall = null;
            if (ServiceList.YouTube.isFetchDislike()) {
                dislikeCall = downloader.getAsync("https://returnyoutubedislikeapi.com/votes?" + "videoId=" + videoId, new Downloader.AsyncCallback() {
                    @Override
                    public void onSuccess(Response response) throws ExtractionException {
                        try {
                            dislikeData = new JSONObject(getValidJsonResponseBody(response));
                        } catch (JSONException | MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

        // NewPipeExtractor 0.26.5 uses the standard ANDROID reel endpoint as its primary
        // anonymous playback request. Keep the direct ANDROID_VR request only as a fallback.
        if (StringUtils.isBlank(ServiceList.YouTube.getTokens())) {
            tryFetchAndroidReelJsonPlayer(contentCountry, localization, videoId);
        }
        boolean playableResponseSelected = selectPlayablePlayerResponse(videoId);

        if (!playableResponseSelected || needsDirectStreamFallback()) {
            tryFetchVisionOsJsonPlayer(contentCountry, localization, videoId);
            playableResponseSelected = selectPlayablePlayerResponse(videoId);
        }

        CancellableCall primaryCall = null;
        if (!playableResponseSelected || needsDirectStreamFallback()) {
            if (StringUtils.isBlank(ServiceList.YouTube.getTokens())) {
                primaryCall = fetchAndroidVRJsonPlayer(contentCountry, localization, videoId);
            } else {
                primaryCall = tryFetchSafariJsonPlayer(contentCountry, localization, videoId);
            }
        }

            waitForCallsToFinish(webPageCall, nextDataCall, primaryCall);

            playableResponseSelected = selectPlayablePlayerResponse(videoId);

            if (!playableResponseSelected || needsDirectStreamFallback()) {
                final CancellableCall iosCall = fetchIosMobileJsonPlayer(contentCountry,
                        localization, videoId);
                waitForCallsToFinish(iosCall);
                playableResponseSelected = selectPlayablePlayerResponse(videoId);
            }

            if (StringUtils.isBlank(ServiceList.YouTube.getTokens())
                    && (!playableResponseSelected || needsDirectStreamFallback())) {
                final CancellableCall safariCall = tryFetchSafariJsonPlayer(
                        contentCountry, localization, videoId);
                waitForCallsToFinish(safariCall);
                playableResponseSelected = selectPlayablePlayerResponse(videoId);
            }

            if (!playableResponseSelected || needsDirectStreamFallback()) {
                final CancellableCall tvHtml5Call = tryFetchTvHtml5EmbedJsonPlayer(
                        contentCountry, localization, videoId);
                waitForCallsToFinish(tvHtml5Call);
                playableResponseSelected = selectPlayablePlayerResponse(videoId);
            }

            if (!playableResponseSelected) {
                selectPrimaryErrorResponse(videoId);
            }

            // Watch-next metadata can be recovered separately. An earlier client timeout must
            // not discard a usable player response just because that metadata request failed.
            if (playerResponse == null) {
                for (Throwable e: errors) {
                    if (e instanceof AntiBotException) {
                        throw (AntiBotException) e;
                    }
                    if (e instanceof ContentNotAvailableException) {
                        throw (ContentNotAvailableException) e;
                    }
                    throw new ExtractionException(e) ;
                }
            }

        if (playerResponse == null) {
            throw new ContentNotSupportedException("YouTube returned SABR-only streaming data without usable stream URLs. "
                    + "Try logging in to get HLS fallback streams.");
        }

        final JsonObject playabilityStatus = playerResponse.getObject("playabilityStatus");
        upcoming = isUpcomingPlayabilityStatus(playabilityStatus);
        if (!upcoming) {
            checkPlayabilityStatus(playabilityStatus, videoId);
        }
        setStreamType();
        try {
            watchDataCache.startAt = getStartAt();
        } catch (final Exception error) {
            // Missing optional timing metadata must not discard playable stream data.
            errors.add(error);
        }

        if (streamType != StreamType.LIVE_STREAM && isSabrOnlyResponse()
                && getHlsManifestUrlFromStreamingData().isEmpty()) {
            throw new ContentNotSupportedException(
                    "YouTube returned SABR-only streaming data without usable stream URLs. "
                    + "Try logging in to get HLS fallback streams.");
        }
    }

    private void waitForCallsToFinish(@Nonnull final CancellableCall... calls)
            throws ExtractionException {
        final long startTime = System.nanoTime();
        final long budgetNanos = TimeUnit.SECONDS.toNanos(ServiceList.YouTube.getLoadingTimeout());
        try {
            for (final CancellableCall call : calls) {
                if (call != null && !call.awaitFinished(
                        Math.max(0, budgetNanos - (System.nanoTime() - startTime)),
                        TimeUnit.NANOSECONDS)) {
                    return;
                }
            }
        } catch (final InterruptedException error) {
            for (final CancellableCall call : calls) {
                if (call != null) {
                    call.cancel();
                }
            }
            Thread.currentThread().interrupt();
            throw new ExtractionException("Interrupted while waiting for YouTube responses", error);
        }
    }

    private boolean selectPlayablePlayerResponse(@Nonnull final String videoId) {
        // Keep the authoritative response deterministic: prefer the Android response used for
        // playback upstream, then deliberate playback fallbacks. WEB is fetched for metadata by
        // YoutubeParsingHelper and must not replace playback state with a temporary reload error.
        for (final JsonObject response : Arrays.asList(androidPlayerResponse,
                visionOsPlayerResponse, safariPlayerResponse, iosPlayerResponse,
                tvHtml5EmbedPlayerResponse)) {
            if (response == null || isPlayerResponseNotValid(response, videoId)) {
                continue;
            }
            final JsonObject playabilityStatus = response.getObject("playabilityStatus");
            final String status = playabilityStatus.getString("status");
            if ((status == null || status.equalsIgnoreCase("ok"))
                    && (hasUsablePlaybackData(response) || hasAnyUsablePlaybackData())) {
                playerResponse = response;
                if (isNullOrEmpty(playerCaptionsTracklistRenderer)) {
                    playerCaptionsTracklistRenderer = response.getObject("captions")
                            .getObject("playerCaptionsTracklistRenderer");
                }
                return true;
            }
        }
        return false;
    }

    private boolean hasUsablePlaybackData(@Nonnull final JsonObject response) {
        return hasUsableStreamingData(response.getObject(STREAMING_DATA));
    }

    private boolean hasAnyUsablePlaybackData() {
        return Arrays.asList(visionOsStreamingData, safariStreamingData, iosStreamingData,
                        tvHtml5SimplyEmbedStreamingData, androidStreamingData, webStreamingData)
                .stream()
                .anyMatch(this::hasUsableStreamingData);
    }

    private boolean needsDirectStreamFallback() {
        return Arrays.asList(visionOsStreamingData, safariStreamingData, iosStreamingData,
                        tvHtml5SimplyEmbedStreamingData, androidStreamingData, webStreamingData)
                .stream()
                .noneMatch(YoutubeStreamExtractor::hasUsableAdaptiveOrManifestData);
    }

    static boolean hasUsableAdaptiveOrManifestData(
            @Nullable final JsonObject streamingData) {
        if (streamingData == null || streamingData.isEmpty()) {
            return false;
        }
        return hasUsableFormats(streamingData.getArray(ADAPTIVE_FORMATS))
                || !isNullOrEmpty(streamingData.getString("hlsManifestUrl"))
                || !isNullOrEmpty(streamingData.getString("dashManifestUrl"));
    }

    private boolean hasUsableStreamingData(@Nullable final JsonObject streamingData) {
        if (streamingData == null || streamingData.isEmpty()) {
            return false;
        }
        return hasUsableFormats(streamingData.getArray(FORMATS))
                || hasUsableAdaptiveOrManifestData(streamingData);
    }

    private static boolean hasUsableFormats(@Nullable final JsonArray formats) {
        if (formats == null || formats.isEmpty()) {
            return false;
        }
        for (int i = 0; i < formats.size(); i++) {
            final JsonObject format = formats.getObject(i);
            if (format.has("url") || format.has(SIGNATURE_CIPHER) || format.has(CIPHER)) {
                return true;
            }
        }
        return false;
    }

    private void selectPrimaryErrorResponse(@Nonnull final String videoId) {
        // If no playable response exists after all deliberate fallbacks, report the primary
        // client's genuine playability error through the existing checkPlayabilityStatus logic.
        for (final JsonObject response : Arrays.asList(androidPlayerResponse,
                visionOsPlayerResponse, safariPlayerResponse, iosPlayerResponse,
                tvHtml5EmbedPlayerResponse)) {
            if (response != null && !isPlayerResponseNotValid(response, videoId)) {
                playerResponse = response;
                return;
            }
        }
    }

    private boolean isSabrOnlyResponse() {
        boolean foundSabrData = false;
        for (final JsonObject streamingData : Arrays.asList(
                safariStreamingData, androidStreamingData,
                visionOsStreamingData, iosStreamingData, webStreamingData,
                tvHtml5SimplyEmbedStreamingData)) {
            if (streamingData == null || streamingData.isEmpty()) {
                continue;
            }
            if (hasUsableStreamingData(streamingData)) {
                return false;
            }
            final JsonArray adaptive = streamingData.getArray(ADAPTIVE_FORMATS);
            foundSabrData |= (adaptive != null && !adaptive.isEmpty())
                    || !isNullOrEmpty(streamingData.getString("serverAbrStreamingUrl"));
        }
        return foundSabrData;
    }



    public static JsonObject checkPlayabilityStatus(@Nonnull JsonObject playabilityStatus, String videoId)
            throws ParsingException {
        String status = playabilityStatus.getString("status");
        if (status == null || status.equalsIgnoreCase("ok")) {
            return null;
        }

        try {
            JsonObject response = JsonParser.object().from(getWebPlayerResponseSync(videoId).responseBody());
            playabilityStatus = response.getObject("playabilityStatus");
            status = playabilityStatus.getString("status");
            if (status == null || status.equalsIgnoreCase("ok")) {
                return response;
            }
        } catch (IOException | ExtractionException | JsonParserException e) {
            // this should not happen
        }


        final String reason = playabilityStatus.getString("reason");

        if (status.equalsIgnoreCase("login_required")) {
            if (reason == null) {
                final String message = playabilityStatus.getArray("messages").getString(0);
                if (message != null && message.contains("private")) {
                    throw new PrivateContentException("This video is private");
                }
            } else if (reason.contains("age")) {
                throw new AgeRestrictedContentException(
                        "This age-restricted video cannot be watched anonymously");
            }
        }

        if ((status.equalsIgnoreCase("unplayable") || status.equalsIgnoreCase("error"))
                && reason != null) {
            if (reason.contains("Music Premium")) {
                throw new YoutubeMusicPremiumContentException();
            }

            if (reason.contains("payment")) {
                throw new PaidContentException("This video is a paid video");
            }

            if (reason.contains("members-only")) {
                throw new PaidContentException("This video is only available"
                        + " for members of the channel of this video");
            }

            if (reason.contains("unavailable")) {
                final String detailedErrorMessage = getTextFromObject(playabilityStatus
                        .getObject("errorScreen")
                        .getObject("playerErrorMessageRenderer")
                        .getObject("subreason"));
                if (detailedErrorMessage != null && detailedErrorMessage.contains("country")) {
                    throw new GeographicRestrictionException(
                            "This video is not available in client's country.");
                } else {
                    if(detailedErrorMessage != null) {
                        throw new ContentNotAvailableException(detailedErrorMessage);
                    }
                    throw new ContentNotAvailableException(reason);
                }
            }
        }
        if (reason != null && reason.contains("Sign in to confirm")) {
            throw new AntiBotException(reason);
        }

        if (reason != null && reason.contains("This live event will begin in")) {
            throw new LiveNotStartException(reason);
        }

        if (reason != null && reason.contains("Premieres in")) {
            throw new VideoNotReleaseException(reason);
        }

        throw new ContentNotAvailableException("Got error: \"" + reason + "\"");
    }

    static boolean isUpcomingPlayabilityStatus(@Nonnull final JsonObject playabilityStatus) {
        final String reason = playabilityStatus.getString("reason");
        return reason != null && (reason.contains("This live event will begin in")
                || reason.contains("Premieres in"));
    }

    @Override
    public boolean isUpcoming() {
        return upcoming;
    }

    /**
     * Fetch the Android VR API and assign the streaming data to the androidStreamingData JSON
     * object.
     */
    private void tryFetchAndroidReelJsonPlayer(
            @Nonnull final ContentCountry contentCountry,
            @Nonnull final Localization localization,
            @Nonnull final String videoId) {
        try {
            fetchAndroidReelJsonPlayer(contentCountry, localization, videoId);
        } catch (final Exception ex) {
            errors.add(new ExtractionException(
                    "Skipping primary ANDROID Reel player response", ex));
        }
    }

    private void fetchAndroidReelJsonPlayer(
            @Nonnull final ContentCountry contentCountry,
            @Nonnull final Localization localization,
            @Nonnull final String videoId) throws IOException, ExtractionException {
        androidCpn = generateContentPlaybackNonce();
        final InnertubeClientRequestInfo clientRequestInfo =
                InnertubeClientRequestInfo.ofAndroidClient();

        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", singletonList("application/json"));
        headers.put("User-Agent", singletonList(getAndroidUserAgent(localization)));
        headers.put("X-Goog-Api-Format-Version", singletonList("2"));

        final String visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                clientRequestInfo, localization, contentCountry, headers,
                YOUTUBEI_V1_GAPIS_URL, null, false);
        final byte[] body = JsonWriter.string(
                prepareAndroidMobileJsonBuilder(localization, contentCountry, visitorData)
                        .object("playerRequest")
                        .value(VIDEO_ID, videoId)
                        .value(CPN, androidCpn)
                        .value(CONTENT_CHECK_OK, true)
                        .value(RACY_CHECK_OK, true)
                        .end()
                        .value("disablePlayerResponse", false)
                        .done())
                .getBytes(StandardCharsets.UTF_8);

        androidPlayerResponse = getJsonAndroidPostResponse(
                "reel/reel_item_watch", body, localization,
                "&t=" + generateTParameter() + "&id=" + videoId
                        + "&$fields=playerResponse")
                .getObject("playerResponse");
        if (isPlayerResponseNotValid(androidPlayerResponse, videoId)) {
            return;
        }

        final JsonObject streamingData = androidPlayerResponse.getObject(STREAMING_DATA);
        if (!isNullOrEmpty(streamingData)) {
            androidStreamingData = streamingData;
            if (isNullOrEmpty(playerCaptionsTracklistRenderer)) {
                playerCaptionsTracklistRenderer = androidPlayerResponse
                        .getObject("captions")
                        .getObject("playerCaptionsTracklistRenderer");
            }
        }
    }

    private CancellableCall fetchAndroidVRJsonPlayer(@Nonnull final ContentCountry contentCountry,
                                              @Nonnull final Localization localization,
                                              @Nonnull final String videoId)
            throws IOException, ExtractionException {
        androidCpn = generateContentPlaybackNonce();
        final InnertubeClientRequestInfo innertubeClientRequestInfo =
                InnertubeClientRequestInfo.ofAndroidClient();

        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", singletonList("application/json"));
        headers.put("User-Agent", singletonList(getAndroidUserAgent(localization)));
        headers.put("X-Goog-Api-Format-Version", singletonList("2"));

        String visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(innertubeClientRequestInfo,
                localization, contentCountry, headers, YOUTUBEI_V1_GAPIS_URL, null, false);
        final byte[] body = JsonWriter.string(
                prepareAndroidVRJsonBuilder(localization, contentCountry, visitorData)
                        .value(VIDEO_ID, videoId)
                        .value(CPN, androidCpn)
                        .value(CONTENT_CHECK_OK, true)
                        .value(RACY_CHECK_OK, true)
                        .done())
                .getBytes(StandardCharsets.UTF_8);

        final Downloader.AsyncCallback callback = new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) throws ExtractionException {
                try {
                    androidPlayerResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                    if (isPlayerResponseNotValid(androidPlayerResponse, videoId)) {
                        return;
                    }

                    final JsonObject streamingData = androidPlayerResponse.getObject(STREAMING_DATA);
                    if (!isNullOrEmpty(streamingData)) {
                        androidStreamingData = streamingData;
                        if (isNullOrEmpty(playerCaptionsTracklistRenderer)) {
                            playerCaptionsTracklistRenderer = androidPlayerResponse.getObject("captions")
                                    .getObject("playerCaptionsTracklistRenderer");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        };

        return getJsonAndroidVRPostResponseAsync(PLAYER,
                body, localization, "&t=" + generateTParameter()
                        + "&id=" + videoId, callback);
    }

    /**
     * Fetch the iOS Mobile API and assign the streaming data to the iosStreamingData JSON
     * object.
     */
    private CancellableCall fetchIosMobileJsonPlayer(@Nonnull final ContentCountry contentCountry,
                                                     @Nonnull final Localization localization,
                                                     @Nonnull final String videoId)
            throws IOException, ExtractionException {
        iosCpn = generateContentPlaybackNonce();
        final byte[] mobileBody = JsonWriter.string(
                prepareIosMobileJsonBuilder(localization, contentCountry)
                        .value(VIDEO_ID, videoId)
                        .value(CPN, iosCpn)
                        .value(CONTENT_CHECK_OK, true)
                        .value(RACY_CHECK_OK, true)
                        .done())
                .getBytes(StandardCharsets.UTF_8);

        final Downloader.AsyncCallback callback = new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) throws ExtractionException {
                try {
                    iosPlayerResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                    if (isPlayerResponseNotValid(iosPlayerResponse, videoId)) {
                        if (iosPlayerResponse.toString().contains("Sign in to confirm")) {
                            throw new AntiBotException("IOS player response is not valid");
                        }
                        throw new ExtractionException("IOS player response is not valid");
                    }

                    final JsonObject streamingData = iosPlayerResponse.getObject(STREAMING_DATA);
                    if (!isNullOrEmpty(streamingData)) {
                        iosStreamingData = streamingData;
                        playerCaptionsTracklistRenderer = iosPlayerResponse.getObject("captions")
                                .getObject("playerCaptionsTracklistRenderer");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        };

        return getJsonIosPostResponseAsync(PLAYER,
                mobileBody, localization, "&t=" + generateTParameter()
                        + "&id=" + videoId, callback);
    }

    private void tryFetchVisionOsJsonPlayer(
            @Nonnull final ContentCountry contentCountry,
            @Nonnull final Localization localization,
            @Nonnull final String videoId) {
        try {
            fetchVisionOsJsonPlayer(contentCountry, localization, videoId);
        } catch (final Exception ex) {
            errors.add(new ExtractionException("Skipping optional VISIONOS player response", ex));
        }
    }

    private void fetchVisionOsJsonPlayer(
            @Nonnull final ContentCountry contentCountry,
            @Nonnull final Localization localization,
            @Nonnull final String videoId) throws IOException, ExtractionException {
        visionOsCpn = generateContentPlaybackNonce();
        final InnertubeClientRequestInfo clientRequestInfo =
                InnertubeClientRequestInfo.ofVisionOsClient();

        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", singletonList("application/json"));
        headers.put("User-Agent", singletonList(getVisionOsUserAgent(localization)));
        headers.put("X-Goog-Api-Format-Version", singletonList("2"));

        final String visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                clientRequestInfo, localization, contentCountry, headers, YOUTUBEI_V1_URL,
                null, false);
        final byte[] body = JsonWriter.string(
                prepareVisionOsJsonBuilder(localization, contentCountry, visitorData)
                        .value(VIDEO_ID, videoId)
                        .value(CPN, visionOsCpn)
                        .value(CONTENT_CHECK_OK, true)
                        .value(RACY_CHECK_OK, true)
                        .done())
                .getBytes(StandardCharsets.UTF_8);

        visionOsPlayerResponse = getJsonVisionOsPostResponse(PLAYER, body, localization,
                "&t=" + generateTParameter() + "&id=" + videoId);
        if (isPlayerResponseNotValid(visionOsPlayerResponse, videoId)) {
            return;
        }

        final JsonObject streamingData = visionOsPlayerResponse.getObject(STREAMING_DATA);
        if (!isNullOrEmpty(streamingData)) {
            visionOsStreamingData = streamingData;
            if (isNullOrEmpty(playerCaptionsTracklistRenderer)) {
                playerCaptionsTracklistRenderer = visionOsPlayerResponse
                        .getObject("captions")
                        .getObject("playerCaptionsTracklistRenderer");
            }
        }
    }

    /**
     * Download the {@code TVHTML5_SIMPLY_EMBEDDED_PLAYER} JSON player as an embed client to bypass
     * some age-restrictions and assign the streaming data to the {@code html5StreamingData} JSON
     * object.
     *
     * @param contentCountry the content country to use
     * @param localization   the localization to use
     * @param videoId        the video id
     */
    private CancellableCall fetchWebJsonPlayer(@Nonnull final ContentCountry contentCountry,
                                             @Nonnull final Localization localization,
                                             @Nonnull final String videoId)
            throws IOException, ExtractionException {
        webCpn = generateContentPlaybackNonce();

        final Downloader.AsyncCallback callback = new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) {
                JsonObject webPlayerResponse = null;
                try {
                    webPlayerResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                    if (isPlayerResponseNotValid(webPlayerResponse, videoId)) {
                        if (webPlayerResponse.toString().contains("Sign in to confirm")) {
                            throw new AntiBotException("Web player response is not valid");
                        }
                        throw new ExtractionException("Web player response is not valid");
                    }

                    // Keep only the small storyboard descriptor, never the entire WEB response.
                    webStoryboardSpec = storyboardSpec(webPlayerResponse);
                    final JsonObject streamingData = webPlayerResponse.getObject(STREAMING_DATA);
                    if (!isNullOrEmpty(streamingData)) {
                        webStreamingData = streamingData;
                        playerCaptionsTracklistRenderer = webPlayerResponse.getObject("captions")
                                .getObject("playerCaptionsTracklistRenderer");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        };

        return getLoggedJsonPostResponseAsync(PLAYER,
                createWebEmbedPlayerBody(localization,
                        contentCountry,
                        videoId,
                        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId),
                        webCpn), localization, callback);
    }

    @Nullable
    private CancellableCall tryFetchTvHtml5EmbedJsonPlayer(
            @Nonnull final ContentCountry contentCountry,
            @Nonnull final Localization localization,
            @Nonnull final String videoId) throws IOException, ExtractionException {
        try {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId);
        } catch (final ParsingException e) {
            errors.add(new ParsingException(
                    "Skipping TVHTML5 fallback: player metadata unavailable", e));
            return null;
        }
        return fetchTvHtml5EmbedJsonPlayer(contentCountry, localization, videoId);
    }

    private CancellableCall fetchTvHtml5EmbedJsonPlayer(@Nonnull final ContentCountry contentCountry,
                                                        @Nonnull final Localization localization,
                                                        @Nonnull final String videoId)
            throws IOException, ExtractionException {
        tvHtml5SimplyEmbedCpn = generateContentPlaybackNonce();

        final Downloader.AsyncCallback callback = new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) {
                try {
                    tvHtml5EmbedPlayerResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                    if (isPlayerResponseNotValid(tvHtml5EmbedPlayerResponse, videoId)) {
                        if (tvHtml5EmbedPlayerResponse.toString().contains("Sign in to confirm")) {
                            throw new AntiBotException("TVHTML5 player response is not valid");
                        }
                        throw new ExtractionException("TVHTML5 embed player response is not valid");
                    }

                    final JsonObject streamingData = tvHtml5EmbedPlayerResponse.getObject(STREAMING_DATA);
                    if (!isNullOrEmpty(streamingData)) {
                        tvHtml5SimplyEmbedStreamingData = streamingData;
                        playerCaptionsTracklistRenderer = tvHtml5EmbedPlayerResponse.getObject("captions")
                                .getObject("playerCaptionsTracklistRenderer");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        };

        return getLoggedJsonPostResponseAsync(PLAYER,
                createTvHtml5EmbedPlayerBody(localization,
                        contentCountry,
                        videoId,
                        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId),
                        tvHtml5SimplyEmbedCpn), localization, callback);
    }

    @Nullable
    private CancellableCall tryFetchSafariJsonPlayer(
            @Nonnull final ContentCountry contentCountry,
            @Nonnull final Localization localization,
            @Nonnull final String videoId) throws IOException, ExtractionException {
        try {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId);
        } catch (final ParsingException e) {
            errors.add(new ParsingException(
                    "Skipping Safari fallback: player metadata unavailable", e));
            return null;
        }
        return fetchSafariJsonPlayer(contentCountry, localization, videoId);
    }

    private CancellableCall fetchSafariJsonPlayer(@Nonnull final ContentCountry contentCountry,
                                                  @Nonnull final Localization localization,
                                                  @Nonnull final String videoId)
            throws IOException, ExtractionException {
        safariCpn = generateContentPlaybackNonce();

        final Downloader.AsyncCallback callback = new Downloader.AsyncCallback() {
            @Override
            public void onSuccess(Response response) {
                try {
                    safariPlayerResponse = JsonUtils.toJsonObject(getValidJsonResponseBody(response));
                    if (isPlayerResponseNotValid(safariPlayerResponse, videoId)) {
                        if (safariPlayerResponse.toString().contains("Sign in to confirm")) {
                            throw new AntiBotException("Safari player response is not valid");
                        }
                        throw new ExtractionException("Safari player response is not valid");
                    }

                    final JsonObject streamingData = safariPlayerResponse.getObject(STREAMING_DATA);
                    if (!isNullOrEmpty(streamingData)) {
                        safariStreamingData = streamingData;
                        if (isNullOrEmpty(playerCaptionsTracklistRenderer)) {
                            playerCaptionsTracklistRenderer = safariPlayerResponse.getObject("captions")
                                    .getObject("playerCaptionsTracklistRenderer");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add(e);
                }
            }
        };

        return getSafariPostResponseAsync(PLAYER,
                createSafariPlayerBody(localization,
                        contentCountry,
                        videoId,
                        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId),
                        safariCpn), localization, callback);
    }


    /**
     * Checks whether an additional player response is not valid.
     *
     * <p>
     * If YouTube detect that requests come from a third party client, they may replace the real
     * player response by another one of a video saying that this content is not available on this
     * app and to watch it on the latest version of YouTube.
     * </p>
     *
     * <p>
     * We can detect this by checking whether the video ID of the player response returned is the
     * same as the one requested by the extractor.
     * </p>
     *
     * <p>
     * This behavior has been already observed on the {@code ANDROID} client, see
     * <a href="https://github.com/TeamNewPipe/NewPipe/issues/8713">
     *     https://github.com/TeamNewPipe/NewPipe/issues/8713</a>.
     * </p>
     *
     * @param additionalPlayerResponse an additional response to the one of the {@code HTML5}
     *                                 client used
     * @param videoId                  the video ID of the content requested
     * @return whether the video ID of the player response is not equal to the one requested
     */
    public static boolean isPlayerResponseNotValid(
            @Nonnull final JsonObject additionalPlayerResponse,
            @Nonnull final String videoId) {
        return !videoId.equals(additionalPlayerResponse.getObject("videoDetails")
                .getString("videoId", ""));
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Nonnull
    private JsonObject getVideoPrimaryInfoRenderer() {
        if (videoPrimaryInfoRenderer != null) {
            return videoPrimaryInfoRenderer;
        }

        videoPrimaryInfoRenderer = getVideoInfoRenderer("videoPrimaryInfoRenderer");
        return videoPrimaryInfoRenderer;
    }

    @Nonnull
    private JsonObject getVideoSecondaryInfoRenderer() {
        if (videoSecondaryInfoRenderer != null) {
            return videoSecondaryInfoRenderer;
        }

        videoSecondaryInfoRenderer = getVideoInfoRenderer("videoSecondaryInfoRenderer");
        return videoSecondaryInfoRenderer;
    }

    @Nonnull
    private JsonObject getVideoInfoRenderer(@Nonnull final String videoRendererName) {
        return nextResponse.getObject("contents")
                .getObject("twoColumnWatchNextResults")
                .getObject("results")
                .getObject("results")
                .getArray("contents")
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(content -> content.has(videoRendererName))
                .map(content -> content.getObject(videoRendererName))
                .findFirst()
                .orElse(new JsonObject());
    }

    @Nonnull
    public <T extends Stream> List<T> getItags(
            final String streamingDataKey,
            final ItagItem.ItagType itagTypeWanted,
            final java.util.function.Function<ItagInfo, T> streamBuilderHelper,
            final String streamTypeExceptionMessage) throws ParsingException {
        try {
            final String videoId = getId();
            final List<T> streamList = new ArrayList<>();

            // First pass: collect all ItagInfo objects without deobfuscating URLs
            final List<ItagInfo> itagInfoList = new ArrayList<>();

            getPreferredStreamingData()
                    .flatMap(pair -> getStreamsFromStreamingDataKey(videoId, pair.getFirst(),
                            streamingDataKey, itagTypeWanted, pair.getSecond()))
                    .forEachOrdered(itagInfoList::add);

            // Second pass: batch deobfuscate all URLs if needed
            batchDeobfuscateItagUrls(videoId, itagInfoList);

            // Third pass: build stream objects
            for (final ItagInfo itagInfo : itagInfoList) {
                final T stream = streamBuilderHelper.apply(itagInfo);
                if (!Stream.containSimilarStream(stream, streamList)) {
                    streamList.add(stream);
                }
            }

            return streamList;
        } catch (final Exception e) {
            throw new ParsingException(
                    "Could not get " + streamTypeExceptionMessage + " streams", e);
        }
    }

    /**
     * Get the stream builder helper which will be used to build {@link AudioStream}s in
     * {@link #getItags(String, ItagItem.ItagType, java.util.function.Function, String)}
     *
     * <p>
     * The {@code StreamBuilderHelper} will set the following attributes in the
     * {@link AudioStream}s built:
     * <ul>
     *     <li>the {@link ItagItem}'s id of the stream as its id;</li>
     *     <li>{@link ItagInfo#getContent()} and {@link ItagInfo#getIsUrl()} as its content and
     *     and as the value of {@code isUrl};</li>
     *     <li>the media format returned by the {@link ItagItem} as its media format;</li>
     *     <li>its average bitrate with the value returned by {@link
     *     ItagItem#getAverageBitrate()};</li>
     *     <li>the {@link ItagItem};</li>
     *     <li>the {@link DeliveryMethod#DASH DASH delivery method}, for OTF streams, live streams
     *     and ended streams.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Note that the {@link ItagItem} comes from an {@link ItagInfo} instance.
     * </p>
     *
     * @return a stream builder helper to build {@link AudioStream}s
     */
    @Nonnull
    private java.util.function.Function<ItagInfo, AudioStream> getAudioStreamBuilderHelper() {
        return (itagInfo) -> {
            final ItagItem itagItem = itagInfo.getItagItem();
            final AudioStream.Builder builder;
            try {
                final String randomString = UUID.randomUUID().toString().replaceAll("[^a-zA-Z]", "");
                builder = new AudioStream.Builder()
                        .setId(randomString)
                        .setContent(itagInfo.getContent() + (itagInfo.getIsUrl()?("&pppid="+getId()):""), itagInfo.getIsUrl())
                        .setMediaFormat(itagItem.getMediaFormat())
                        .setAverageBitrate(itagItem.getAverageBitrate())
                        .setItagItem(itagItem)
                        .setAudioTrackId(itagInfo.getAudioTrackId())
                        .setAudioTrackName(itagInfo.getAudioTrackName())
                        .setAudioLocale(itagInfo.getAudioLocale())
                        .setAudioTrackType(itagInfo.getAudioTrackType());
            } catch (ParsingException e) {
                throw new RuntimeException(e);
            }

            if (streamType == StreamType.LIVE_STREAM
                    || streamType == StreamType.POST_LIVE_STREAM
                    || !itagInfo.getIsUrl()) {
                builder.setDeliveryMethod(DeliveryMethod.DASH);
            }

            return builder.build();
        };
    }

    /**
     * Get the stream builder helper which will be used to build {@link VideoStream}s in
     * {@link #getItags(String, ItagItem.ItagType, java.util.function.Function, String)}
     *
     * <p>
     * The {@code StreamBuilderHelper} will set the following attributes in the
     * {@link VideoStream}s built:
     * <ul>
     *     <li>the {@link ItagItem}'s id of the stream as its id;</li>
     *     <li>{@link ItagInfo#getContent()} and {@link ItagInfo#getIsUrl()} as its content and
     *     and as the value of {@code isUrl};</li>
     *     <li>the media format returned by the {@link ItagItem} as its media format;</li>
     *     <li>whether it is video-only with the {@code areStreamsVideoOnly} parameter</li>
     *     <li>the {@link ItagItem};</li>
     *     <li>the resolution, by trying to use, in this order:
     *         <ol>
     *             <li>the height returned by the {@link ItagItem} + {@code p} + the frame rate if
     *             it is more than 30;</li>
     *             <li>the default resolution string from the {@link ItagItem};</li>
     *             <li>an {@link Utils#EMPTY_STRING empty string}.</li>
     *         </ol>
     *     </li>
     *     <li>the {@link DeliveryMethod#DASH DASH delivery method}, for OTF streams, live streams
     *     and ended streams.</li>
     * </ul>
     *
     * <p>
     * Note that the {@link ItagItem} comes from an {@link ItagInfo} instance.
     * </p>
     *
     * @param areStreamsVideoOnly whether the stream builder helper will set the video
     *                            streams as video-only streams
     * @return a stream builder helper to build {@link VideoStream}s
     */
    @Nonnull
    private java.util.function.Function<ItagInfo, VideoStream> getVideoStreamBuilderHelper(
            final boolean areStreamsVideoOnly) {
        return (itagInfo) -> {
            final ItagItem itagItem = itagInfo.getItagItem();
            final VideoStream.Builder builder;
            try {
                builder = new VideoStream.Builder()
                        .setId(String.valueOf(itagItem.id))
                        .setContent(itagInfo.getContent() + (itagInfo.getIsUrl()?("&pppid="+getId()):""), itagInfo.getIsUrl())
                        .setMediaFormat(itagItem.getMediaFormat())
                        .setIsVideoOnly(areStreamsVideoOnly)
                        .setItagItem(itagItem);
            } catch (ParsingException e) {
                throw new RuntimeException(e);
            }

            final String resolutionString = itagItem.getResolutionString();
            builder.setResolution(resolutionString != null ? resolutionString
                    : EMPTY_STRING);

            if (streamType != StreamType.VIDEO_STREAM || !itagInfo.getIsUrl()) {
                // For YouTube videos on OTF streams and for all streams of post-live streams
                // and live streams, only the DASH delivery method can be used.
                builder.setDeliveryMethod(DeliveryMethod.DASH);
            }

            return builder.build();
        };
    }

    @Nonnull
    private java.util.stream.Stream<ItagInfo> getStreamsFromStreamingDataKey(
            final String videoId,
            final JsonObject streamingData,
            final String streamingDataKey,
            @Nonnull final ItagItem.ItagType itagTypeWanted,
            @Nonnull final String contentPlaybackNonce) {
        if (streamingData == null || !streamingData.has(streamingDataKey)) {
            return java.util.stream.Stream.empty();
        }

        java.util.stream.Stream<ItagInfo> result = streamingData.getArray(streamingDataKey).stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(formatData -> {
                    try {
                        final ItagItem itagItem = ItagItem.getItag(formatData.getInt("itag"));
                        if (itagItem.itagType == itagTypeWanted) {
                            final ItagInfo itagInfo = buildAndAddItagInfoToList(videoId, formatData, itagItem,
                                    itagItem.itagType, contentPlaybackNonce);
                            if (itagInfo != null && itagItem.itagType == ItagItem.ItagType.AUDIO) {
                                extractAndSetAudioTrackInfo(formatData, itagInfo);
                            }
                            return itagInfo;
                        }
                    } catch (final IOException | ExtractionException e) {
                        e.printStackTrace();
                    }
                    return null;
                })
                .filter(Objects::nonNull);
        return result;
    }

    private void extractAndSetAudioTrackInfo(final JsonObject formatData,
                                             final ItagInfo itagInfo) {
        if (!formatData.has("audioTrack")) {
            return;
        }
        final JsonObject audioTrack = formatData.getObject("audioTrack");
        if (audioTrack.has("id")) {
            final String audioTrackId = audioTrack.getString("id");
            final String displayName = audioTrack.getString("displayName");
            final String langPart = audioTrackId.split("\\.")[0];
            final String audioLocale = langPart;
            final String audioTrackName = isNullOrEmpty(displayName) ? langPart : displayName;
            final AudioTrackType audioTrackType =
                    extractAudioTrackType(formatData.getString("xtags"));
            itagInfo.setAudioTrackInfo(audioTrackId, audioTrackName, audioLocale, audioTrackType);
        }
    }

    private ItagInfo buildAndAddItagInfoToList(
            @Nonnull final String videoId,
            @Nonnull final JsonObject formatData,
            @Nonnull final ItagItem itagItem,
            @Nonnull final ItagItem.ItagType itagType,
            @Nonnull final String contentPlaybackNonce) throws IOException, ExtractionException {
        String streamUrl;
        String obfuscatedSignature = null;

        if (formatData.has("url")) {
            streamUrl = formatData.getString("url");
        } else if (formatData.has(SIGNATURE_CIPHER) || formatData.has(CIPHER)) {
            final String cipherString = formatData.getString(CIPHER,
                    formatData.getString(SIGNATURE_CIPHER));
            final Map<String, String> cipher = Parser.compatParseMap(cipherString);
            obfuscatedSignature = cipher.getOrDefault("s", "");
            streamUrl = cipher.get("url") + "&" + cipher.get("sp") + "=SIGNATURE_PLACEHOLDER";
        } else {
            return null;
        }

        // Add the content playback nonce to the stream URL
        streamUrl += "&" + CPN + "=" + contentPlaybackNonce;

        // Note: Signature and throttling parameter deobfuscation will be done
        // in batch by batchDeobfuscateItagUrls() method

        final JsonObject initRange = formatData.getObject("initRange");
        final JsonObject indexRange = formatData.getObject("indexRange");
        final String mimeType = formatData.getString("mimeType", EMPTY_STRING);
        final String codec = mimeType.contains("codecs")
                ? mimeType.split("\"")[1] : EMPTY_STRING;
        final int fps = formatData.getInt("fps", -1);

        itagItem.setBitrate(formatData.getInt("bitrate"));
        itagItem.setWidth(formatData.getInt("width"));
        itagItem.setHeight(formatData.getInt("height"));
        itagItem.setInitStart(Integer.parseInt(initRange.getString("start", "-1")));
        itagItem.setInitEnd(Integer.parseInt(initRange.getString("end", "-1")));
        itagItem.setIndexStart(Integer.parseInt(indexRange.getString("start", "-1")));
        itagItem.setIndexEnd(Integer.parseInt(indexRange.getString("end", "-1")));
        itagItem.setQuality(formatData.getString("quality"));
        itagItem.setCodec(codec);
        if (fps != -1) {
            itagItem.setFps(fps);
        }

        if (streamType == StreamType.LIVE_STREAM || streamType == StreamType.POST_LIVE_STREAM) {
            itagItem.setTargetDurationSec(formatData.getInt("targetDurationSec"));
        } else if (itagType == ItagItem.ItagType.VIDEO
                || itagType == ItagItem.ItagType.VIDEO_ONLY) {
            itagItem.setFps(formatData.getInt("fps"));
        } else if (itagType == ItagItem.ItagType.AUDIO) {
            // YouTube return the audio sample rate as a string
            itagItem.setSampleRate(Integer.parseInt(formatData.getString("audioSampleRate")));
            itagItem.setAudioChannels(formatData.getInt("audioChannels"));
        }

        // YouTube return the content length and the approximate duration as strings
        itagItem.setContentLength(Long.parseLong(formatData.getString("contentLength",
                String.valueOf(CONTENT_LENGTH_UNKNOWN))));
        itagItem.setApproxDurationMs(Long.parseLong(formatData.getString("approxDurationMs",
                String.valueOf(APPROX_DURATION_MS_UNKNOWN))));

        final ItagInfo itagInfo = new ItagInfo(streamUrl, itagItem);

        // Store the obfuscated signature in ItagInfo for batch processing
        if (obfuscatedSignature != null) {
            itagInfo.setObfuscatedSignature(obfuscatedSignature);
        }

        if (streamType == StreamType.VIDEO_STREAM) {
            itagInfo.setIsUrl(!formatData.getString("type", EMPTY_STRING)
                    .equalsIgnoreCase("FORMAT_STREAM_TYPE_OTF"));
        } else {
            // We are currently not able to generate DASH manifests for running
            // livestreams, so because of the requirements of StreamInfo
            // objects, return these streams as DASH URL streams (even if they
            // are not playable).
            // Ended livestreams are returned as non URL streams
            itagInfo.setIsUrl(streamType != StreamType.POST_LIVE_STREAM);
        }

        return itagInfo;
    }

    /**
     * Batch deobfuscate signatures and throttling parameters for all ItagInfo objects.
     * This replaces individual API calls with a single batch request.
     *
     * @param videoId      the video ID
     * @param itagInfoList list of ItagInfo objects to process
     * @throws ParsingException if batch deobfuscation fails
     */
    private void batchDeobfuscateItagUrls(@Nonnull final String videoId,
                                          @Nonnull final List<ItagInfo> itagInfoList)
            throws ParsingException {
        if (itagInfoList.isEmpty()) {
            return;
        }

        // Collect all unique signatures and throttling parameters
        // Use LinkedHashSet to preserve order and avoid duplicates
        final java.util.LinkedHashSet<String> uniqueSignatures = new java.util.LinkedHashSet<>();
        final java.util.LinkedHashSet<String> uniqueThrottlingParams = new java.util.LinkedHashSet<>();

        // Track which streams need which deobfuscations
        final List<StreamDeobfuscationInfo> streamInfos = new ArrayList<>();

        for (int i = 0; i < itagInfoList.size(); i++) {
            final ItagInfo itagInfo = itagInfoList.get(i);
            final String url = itagInfo.getContent();

            final String obfuscatedSig = itagInfo.getObfuscatedSignature();
            final String throttlingParam =
                YoutubeThrottlingParameterUtils.getThrottlingParameterFromStreamingUrl(url);

            // Create deobfuscation info for this stream
            final StreamDeobfuscationInfo info = new StreamDeobfuscationInfo(
                i, obfuscatedSig, throttlingParam);
            streamInfos.add(info);

            // Add to unique sets
            if (obfuscatedSig != null && !obfuscatedSig.isEmpty()) {
                uniqueSignatures.add(obfuscatedSig);
            }
            if (throttlingParam != null && !throttlingParam.isEmpty()) {
                uniqueThrottlingParams.add(throttlingParam);
            }
        }

        // If nothing to deobfuscate, return early
        if (uniqueSignatures.isEmpty() && uniqueThrottlingParams.isEmpty()) {
            return;
        }

        // Make batch API call
        final YoutubeApiDecoder.BatchDecodeResult result =
            YoutubeJavaScriptPlayerManager.deobfuscateBatch(
                videoId,
                new ArrayList<>(uniqueSignatures),
                new ArrayList<>(uniqueThrottlingParams));

        final Map<String, String> decodedSignatures = result.getSignatures();
        final Map<String, String> decodedThrottling = result.getNParameters();

        // Apply deobfuscated values to each stream
        for (final StreamDeobfuscationInfo info : streamInfos) {
            final ItagInfo itagInfo = itagInfoList.get(info.streamIndex);
            String updatedUrl = itagInfo.getContent();

            // Apply deobfuscated signature if needed
            if (info.obfuscatedSignature != null && !info.obfuscatedSignature.isEmpty()) {
                final String deobfuscatedSig = decodedSignatures.get(info.obfuscatedSignature);
                if (deobfuscatedSig != null) {
                    updatedUrl = updatedUrl.replace("SIGNATURE_PLACEHOLDER", deobfuscatedSig);
                }
            }

            // Apply deobfuscated throttling parameter if needed
            if (info.throttlingParam != null && !info.throttlingParam.isEmpty()) {
                final String deobfuscatedParam = decodedThrottling.get(info.throttlingParam);
                if (deobfuscatedParam != null) {
                    updatedUrl = updatedUrl.replace(info.throttlingParam, deobfuscatedParam);
                }
            }

            itagInfo.setContent(updatedUrl);
        }
    }

    /**
     * Helper class to track deobfuscation requirements for each stream.
     */
    private static class StreamDeobfuscationInfo {
        final int streamIndex;
        final String obfuscatedSignature;
        final String throttlingParam;

        StreamDeobfuscationInfo(final int streamIndex,
                                final String obfuscatedSignature,
                                final String throttlingParam) {
            this.streamIndex = streamIndex;
            this.obfuscatedSignature = obfuscatedSignature;
            this.throttlingParam = throttlingParam;
        }
    }

    /**
     * Player clients expose storyboards inconsistently. Prefer the playback response, then use
     * metadata fetched from the existing WEB request without issuing any additional requests.
     * @param response a candidate player response
     * @return a usable storyboard specification, or null if unavailable
     */
    static String storyboardSpec(@Nullable final JsonObject response) {
        if (response == null) {
            return null;
        }
        final JsonObject storyboards = response.getObject("storyboards");
        if (storyboards == null) {
            return null;
        }
        final JsonObject live = storyboards.getObject("playerLiveStoryboardSpecRenderer");
        final JsonObject regular = storyboards.getObject("playerStoryboardSpecRenderer");
        final String liveSpec = live == null ? null : live.getString("spec");
        if (liveSpec != null && !liveSpec.isBlank()) {
            return liveSpec;
        }
        final String regularSpec = regular == null ? null : regular.getString("spec");
        return regularSpec != null && !regularSpec.isBlank() ? regularSpec : null;
    }

    @Nonnull
    @Override
    public List<Frameset> getFrames() throws ExtractionException {
        try {
            String storyboard = storyboardSpec(playerResponse);
            if (storyboard == null) {
                storyboard = webStoryboardSpec;
            }
            if (storyboard == null) {
                return Collections.emptyList();
            }

            final String[] spec = storyboard.split("\\|");
            final String url = spec[0];
            final List<Frameset> result = new ArrayList<>(spec.length - 1);

            for (int i = 1; i < spec.length; ++i) {
                final String[] parts = spec[i].split("#");
                if (parts.length != 8 || Integer.parseInt(parts[5]) == 0) {
                    continue;
                }
                final int totalCount = Integer.parseInt(parts[2]);
                final int framesPerPageX = Integer.parseInt(parts[3]);
                final int framesPerPageY = Integer.parseInt(parts[4]);
                final String baseUrl = url.replace("$L", String.valueOf(i - 1))
                        .replace("$N", parts[6]) + "&sigh=" + parts[7];
                final List<String> urls;
                if (baseUrl.contains("$M")) {
                    final int totalPages = (int) Math.ceil(totalCount / (double)
                            (framesPerPageX * framesPerPageY));
                    urls = new ArrayList<>(totalPages);
                    for (int j = 0; j < totalPages; j++) {
                        urls.add(baseUrl.replace("$M", String.valueOf(j)));
                    }
                } else {
                    urls = Collections.singletonList(baseUrl);
                }
                result.add(new Frameset(
                        urls,
                        /*frameWidth=*/Integer.parseInt(parts[0]),
                        /*frameHeight=*/Integer.parseInt(parts[1]),
                        totalCount,
                        /*durationPerFrame=*/Integer.parseInt(parts[5]),
                        framesPerPageX,
                        framesPerPageY
                ));
            }
            return result;
        } catch (final Exception e) {
            throw new ExtractionException("Could not get frames", e);
        }
    }

    @Nonnull
    @Override
    public Privacy getPrivacy() {
        return playerMicroFormatRenderer.getBoolean("isUnlisted")
                ? Privacy.UNLISTED
                : Privacy.PUBLIC;
    }

    @Nonnull
    @Override
    public String getCategory() {
        return playerMicroFormatRenderer.getString("category", EMPTY_STRING);
    }

    @Nonnull
    @Override
    public String getLicence() throws ParsingException {
        final JsonObject metadataRowRenderer = getVideoSecondaryInfoRenderer()
                .getObject("metadataRowContainer")
                .getObject("metadataRowContainerRenderer")
                .getArray("rows")
                .getObject(0)
                .getObject("metadataRowRenderer");

        final JsonArray contents = metadataRowRenderer.getArray("contents");
        final String license = getTextFromObject(contents.getObject(0));
        return license != null
                && "Licence".equals(getTextFromObject(metadataRowRenderer.getObject("title")))
                ? license
                : "YouTube license";
    }

    @Override
    public Locale getLanguageInfo() {
        return null;
    }

    @Nonnull
    @Override
    public List<String> getTags() {
        return JsonUtils.getStringListFromJsonArray(playerResponse.getObject("videoDetails")
                .getArray("keywords"));
    }

    @Nonnull
    @Override
    public List<StreamSegment> getStreamSegments() throws ParsingException {

        if (!nextResponse.has("engagementPanels")) {
            return Collections.emptyList();
        }

        final JsonArray segmentsArray = nextResponse.getArray("engagementPanels")
                .stream()
                // Check if object is a JsonObject
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                // Check if the panel is the correct one
                .filter(panel -> "engagement-panel-macro-markers-description-chapters".equals(
                        panel
                                .getObject("engagementPanelSectionListRenderer")
                                .getString("panelIdentifier")))
                // Extract the data
                .map(panel -> panel
                        .getObject("engagementPanelSectionListRenderer")
                        .getObject("content")
                        .getObject("macroMarkersListRenderer")
                        .getArray("contents"))
                .findFirst()
                .orElse(null);

        // If no data was found exit
        if (segmentsArray == null) {
            return Collections.emptyList();
        }

        final long duration = getLength();
        final List<StreamSegment> segments = new ArrayList<>();
        for (final JsonObject segmentJson : segmentsArray.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(object -> object.getObject("macroMarkersListItemRenderer"))
                .collect(Collectors.toList())
        ) {
            final int startTimeSeconds = segmentJson.getObject("onTap")
                    .getObject("watchEndpoint").getInt("startTimeSeconds", -1);

            if (startTimeSeconds == -1) {
                throw new ParsingException("Could not get stream segment start time.");
            }
            if (startTimeSeconds > duration) {
                break;
            }

            final String title = getTextFromObject(segmentJson.getObject("title"));
            if (isNullOrEmpty(title)) {
                throw new ParsingException("Could not get stream segment title.");
            }

            final StreamSegment segment = new StreamSegment(title, startTimeSeconds);
            segment.setUrl(getUrl() + "?t=" + startTimeSeconds);
            if (segmentJson.has("thumbnail")) {
                final JsonArray previewsArray = segmentJson
                        .getObject("thumbnail")
                        .getArray("thumbnails");
                if (!previewsArray.isEmpty()) {
                    // Assume that the thumbnail with the highest resolution is at the last position
                    final String url = previewsArray
                            .getObject(previewsArray.size() - 1)
                            .getString("url");
                    segment.setPreviewUrl(fixThumbnailUrl(url));
                }
            }
            segments.add(segment);
        }
        return segments;
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return YoutubeContentDisclosure.appendTo(YoutubeParsingHelper.getMetaInfo(nextResponse
                .getObject("contents")
                .getObject("twoColumnWatchNextResults")
                .getObject("results")
                .getObject("results")
                .getArray("contents")), nextResponse);
    }
}
