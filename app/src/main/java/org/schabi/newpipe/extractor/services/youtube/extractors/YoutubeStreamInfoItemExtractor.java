package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.JsonUtils;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getThumbnailUrlFromInfoItem;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getUrlFromNavigationEndpoint;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

/*
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * YoutubeStreamInfoItemExtractor.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class YoutubeStreamInfoItemExtractor implements StreamInfoItemExtractor {
    private static final Pattern VIEW_LABEL_PATTERN = Pattern.compile("\\bviews?\\b");
    private final JsonObject videoInfo;
    private final TimeAgoParser timeAgoParser;
    private StreamType cachedStreamType;
    private JsonObject tempVideoInfo;
    private String fallbackUploaderName;
    private String fallbackUploaderUrl;

    /**
     * Creates an extractor of StreamInfoItems from a YouTube page.
     *
     * @param videoInfoItem The JSON page element
     * @param timeAgoParser A parser of the textual dates or {@code null}.
     */
    public YoutubeStreamInfoItemExtractor(final JsonObject videoInfoItem,
                                          @Nullable final TimeAgoParser timeAgoParser) {
        this.videoInfo = videoInfoItem;
        this.timeAgoParser = timeAgoParser;
    }

    public YoutubeStreamInfoItemExtractor(final JsonObject videoInfoItem,
                                          @Nullable final TimeAgoParser timeAgoParser,
                                          @Nullable final JsonObject tempVideoInfoItem) {
        this.videoInfo = videoInfoItem;
        this.timeAgoParser = timeAgoParser;
        this.tempVideoInfo = tempVideoInfoItem;
    }

    public void setFallbackUploaderName(final String name) {
        this.fallbackUploaderName = name;
    }

    public void setFallbackUploaderUrl(final String url) {
        this.fallbackUploaderUrl = url;
    }

    @Override
    public StreamType getStreamType() {
        if (cachedStreamType != null) {
            return cachedStreamType;
        }

        final JsonArray badges = videoInfo.getArray("badges");
        for (final Object badge : badges) {
            final JsonObject badgeRenderer
                    = ((JsonObject) badge).getObject("metadataBadgeRenderer");
            if (badgeRenderer.getString("style", "").equals("BADGE_STYLE_TYPE_LIVE_NOW")
                    || badgeRenderer.getString("label", "").equals("LIVE NOW")) {
                cachedStreamType = StreamType.LIVE_STREAM;
                return cachedStreamType;
            }
        }

        final JsonArray thumbnailOverlays = videoInfo.getArray("thumbnailOverlays");
        for (final Object overlay : thumbnailOverlays) {
            final String style = ((JsonObject) overlay)
                    .getObject("thumbnailOverlayTimeStatusRenderer")
                    .getString("style", "");
            if (style.equalsIgnoreCase("LIVE")) {
                cachedStreamType = StreamType.LIVE_STREAM;
                return cachedStreamType;
            }
        }

        // lockupViewModel format
        try {
            final JsonArray overlays = videoInfo.getObject("contentImage")
                    .getObject("thumbnailViewModel")
                    .getArray("overlays");
            for (final Object overlay : overlays) {
                final JsonObject overlayObj = (JsonObject) overlay;
                if (overlayObj.has("thumbnailBottomOverlayViewModel")) {
                    final JsonArray lockupBadges = overlayObj.getObject("thumbnailBottomOverlayViewModel")
                            .getArray("badges");
                    for (final Object badge : lockupBadges) {
                        final JsonObject badgeViewModel = ((JsonObject) badge).getObject("thumbnailBadgeViewModel");
                        final String badgeStyle = badgeViewModel.getString("badgeStyle", "");
                        if (badgeStyle.equals("BADGE_STYLE_TYPE_LIVE_NOW")
                                || badgeStyle.equals("BADGE_STYLE_TYPE_LIVE")) {
                            cachedStreamType = StreamType.LIVE_STREAM;
                            return cachedStreamType;
                        }
                    }
                }
            }
            // Also check metadata for "watching"
            final JsonArray metadataRows = videoInfo.getObject("metadata")
                    .getObject("lockupMetadataViewModel")
                    .getObject("metadata")
                    .getObject("contentMetadataViewModel")
                    .getArray("metadataRows");
            for (final Object row : metadataRows) {
                final JsonArray metadataParts = ((JsonObject) row).getArray("metadataParts");
                for (final Object part : metadataParts) {
                    final String content = ((JsonObject) part)
                            .getObject("text")
                            .getString("content");
                    if (content != null && (content.toLowerCase().contains("watching")
                            || content.toLowerCase().contains("bukele"))) {
                        cachedStreamType = StreamType.LIVE_STREAM;
                        return cachedStreamType;
                    }
                }
            }
        } catch (final Exception ignored) {
            // Not a lockupViewModel format, ignore
        }

        cachedStreamType = StreamType.VIDEO_STREAM;
        return cachedStreamType;
    }

    @Nullable
    @Override
    public Boolean getUpcomingStatus() {
        return YoutubeUpcomingMetadata.status(videoInfo, getStreamType());
    }

    @Override
    public boolean isAd() throws ParsingException {
        return isPremium() || getName().equals("[Private video]")
                || getName().equals("[Deleted video]");
    }

    @Override
    public String getUrl() throws ParsingException {
        try {
            String videoId = videoInfo.getString("videoId");
            if (isNullOrEmpty(videoId)) {
                videoId = videoInfo.getString("contentId");
            }
            return YoutubeStreamLinkHandlerFactory.getInstance().getUrl(videoId);
        } catch (final Exception e) {
            throw new ParsingException("Could not get url", e);
        }
    }

    @Override
    public String getName() throws ParsingException {
        JsonObject source = (tempVideoInfo != null) ? tempVideoInfo : videoInfo;

        String name = getTextFromObject(source.getObject("title"));
        if (!isNullOrEmpty(name)) {
            return localizeDeletedPrivateVideoTitle(name);
        }

        name = getTextFromObject(source.getObject("headline"));
        if (!isNullOrEmpty(name)) {
            return localizeDeletedPrivateVideoTitle(name);
        }

        // lockupViewModel format
        try {
            name = source.getObject("metadata")
                    .getObject("lockupMetadataViewModel")
                    .getObject("title")
                    .getString("content");
            if (!isNullOrEmpty(name)) {
                return localizeDeletedPrivateVideoTitle(name);
            }
        } catch (final Exception ignored) {
            // Not a lockupViewModel format
        }

        throw new ParsingException("Could not get name");
    }

    private static String localizeDeletedPrivateVideoTitle(final String name) {
        if (name.contains("Ividiyo esusiwe")) {
            return "[Deleted video]";
        }
        if (name.contains("eyimfihlo")) {
            return "[Private video]";
        }
        return name;
    }


    @Override
    public long getDuration() throws ParsingException {
        if (getStreamType() == StreamType.LIVE_STREAM || isPremiere()) {
            return -1;
        }

        String duration = getTextFromObject(videoInfo.getObject("lengthText"));

        if (isNullOrEmpty(duration)) {
            for (final Object thumbnailOverlay : videoInfo.getArray("thumbnailOverlays")) {
                if (((JsonObject) thumbnailOverlay).has("thumbnailOverlayTimeStatusRenderer")) {
                    duration = getTextFromObject(((JsonObject) thumbnailOverlay)
                            .getObject("thumbnailOverlayTimeStatusRenderer").getObject("text"));
                }
            }

            if (isNullOrEmpty(duration)) {
                // lockupViewModel format: contentImage.thumbnailViewModel.overlays
                // Try two paths
                try {
                    final JsonArray overlays = videoInfo.getObject("contentImage")
                            .getObject("thumbnailViewModel")
                            .getArray("overlays");
                    for (final Object overlay : overlays) {
                        final JsonObject overlayObj = (JsonObject) overlay;
                        // Path 1: thumbnailOverlayBadgeViewModel
                        if (overlayObj.has("thumbnailOverlayBadgeViewModel")) {
                            duration = overlayObj.getObject("thumbnailOverlayBadgeViewModel")
                                    .getArray("thumbnailBadges")
                                    .getObject(0)
                                    .getObject("thumbnailBadgeViewModel")
                                    .getString("text");
                            if (!isNullOrEmpty(duration)) break;
                        }
                        // Path 2: thumbnailBottomOverlayViewModel
                        if (overlayObj.has("thumbnailBottomOverlayViewModel")) {
                            final JsonArray badges = overlayObj.getObject("thumbnailBottomOverlayViewModel")
                                    .getArray("badges");
                            if (!badges.isEmpty()) {
                                duration = badges.getObject(0)
                                        .getObject("thumbnailBadgeViewModel")
                                        .getString("text");
                                if (!isNullOrEmpty(duration)) break;
                            }
                        }
                    }
                } catch (final Exception ignored) {
                    // Not a lockupViewModel format
                }
            }

            if (isNullOrEmpty(duration)) {
                // Duration of short videos in channel tab
                // example: "simple is best - 49 seconds - play video"
                final String accessibilityLabel = videoInfo.getObject("accessibility")
                        .getObject("accessibilityData").getString("label");
                if (accessibilityLabel == null || timeAgoParser == null) {
                    return 0;
                }

                final String[] labelParts = accessibilityLabel.split(" \u2013 ");

                if (labelParts.length > 2) {
                    final String textualDuration = labelParts[labelParts.length - 2];
                    return timeAgoParser.parseDuration(textualDuration);
                } else {
                    return 0; // some videos don't have durations at all
                }
            }
        }

        // NewPipe#8034 - YT returns not a correct duration for "YT shorts" videos
        if ("SHORTS".equalsIgnoreCase(duration)) {
            return 0;
        }

        return YoutubeParsingHelper.parseDurationString(duration);
    }

    @Override
    public String getUploaderName() throws ParsingException {
        String name = getUploaderNameFromTextObject(videoInfo.getObject("longBylineText"));

        if (isNullOrEmpty(name)) {
            name = getUploaderNameFromTextObject(videoInfo.getObject("ownerText"));

            if (isNullOrEmpty(name)) {
                name = getUploaderNameFromTextObject(videoInfo.getObject("shortBylineText"));

                if (isNullOrEmpty(name)) {
                    if (!isNullOrEmpty(fallbackUploaderName)) {
                        return fallbackUploaderName;
                    }
                    throw new ParsingException("Could not get uploader name");
                }
            }
        }

        return name;
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        String url = getUploaderUrlFromTextObject(videoInfo.getObject("longBylineText"));

        if (isNullOrEmpty(url)) {
            url = getUploaderUrlFromTextObject(videoInfo.getObject("ownerText"));

            if (isNullOrEmpty(url)) {
                url = getUploaderUrlFromTextObject(videoInfo.getObject("shortBylineText"));

                if (isNullOrEmpty(url)) {
                    if (!isNullOrEmpty(fallbackUploaderUrl)) {
                        return fallbackUploaderUrl;
                    }
                    throw new ParsingException("Could not get uploader url");
                }
            }
        }

        return url;
    }

    @Nullable
    private String getUploaderNameFromTextObject(@Nullable final JsonObject textObject)
            throws ParsingException {
        if (textObject == null || textObject.isEmpty()) {
            return null;
        }

        final JsonObject navigationEndpoint = getUploaderNavigationEndpoint(textObject);
        if (navigationEndpoint != null && navigationEndpoint.has("showDialogCommand")) {
            final String name = getFirstContributorName(navigationEndpoint);
            if (!isNullOrEmpty(name)) {
                return name;
            }
        }

        return getTextFromObject(textObject);
    }

    @Nullable
    private String getUploaderUrlFromTextObject(@Nullable final JsonObject textObject)
            throws ParsingException {
        final JsonObject navigationEndpoint = getUploaderNavigationEndpoint(textObject);
        if (navigationEndpoint == null) {
            return null;
        }

        return getUrlFromNavigationEndpoint(navigationEndpoint);
    }

    @Nullable
    private JsonObject getUploaderNavigationEndpoint(@Nullable final JsonObject textObject) {
        if (textObject == null || textObject.isEmpty() || !textObject.has("runs")) {
            return null;
        }

        final JsonArray runs = textObject.getArray("runs");
        if (runs == null || runs.isEmpty()) {
            return null;
        }

        final JsonObject firstRun = runs.getObject(0);
        if (firstRun == null || !firstRun.has("navigationEndpoint")) {
            return null;
        }

        return firstRun.getObject("navigationEndpoint");
    }

    @Nullable
    private String getFirstContributorName(final JsonObject navigationEndpoint) {
        try {
            final JsonArray listItems = navigationEndpoint
                    .getObject("showDialogCommand")
                    .getObject("panelLoadingStrategy")
                    .getObject("inlineContent")
                    .getObject("dialogViewModel")
                    .getObject("customContent")
                    .getObject("listViewModel")
                    .getArray("listItems");

            if (listItems == null || listItems.isEmpty()) {
                return null;
            }

            final String name = listItems.getObject(0)
                    .getObject("listItemViewModel")
                    .getObject("title")
                    .getString("content", "");

            return isNullOrEmpty(name) ? null : name;
        } catch (final Exception ignored) {
            return null;
        }
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {

        if (videoInfo.has("channelThumbnailSupportedRenderers")) {
            return JsonUtils.getArray(videoInfo, "channelThumbnailSupportedRenderers"
                            + ".channelThumbnailWithLinkRenderer.thumbnail.thumbnails")
                    .getObject(0).getString("url");
        }

        if (videoInfo.has("channelThumbnail")) {
            return JsonUtils.getArray(videoInfo, "channelThumbnail.thumbnails")
                    .getObject(0).getString("url");
        }

        return null;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return YoutubeParsingHelper.isVerified(videoInfo.getArray("ownerBadges"));
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        if (getStreamType().equals(StreamType.LIVE_STREAM)) {
            return null;
        }

        if (isPremiere()) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(getDateFromPremiere());
        }

        final String publishedTimeText
                = getTextFromObject(videoInfo.getObject("publishedTimeText"));
        if (!isNullOrEmpty(publishedTimeText)) {
            return publishedTimeText;
        }

        // fallback: videoInfo.runs format (e.g. playlistVideoRenderer with nested videoInfo)
        try {
            final JsonArray runs = videoInfo.getObject("videoInfo").getArray("runs");
            for (final Object runObj : runs) {
                final String text = ((JsonObject) runObj).getString("text");
                if (isNullOrEmpty(text) || text.equals(" • ")) {
                    continue;
                }
                if (hasViewCountLabel(text)) {
                    continue;
                }
                if (timeAgoParser != null) {
                    try {
                        timeAgoParser.parse(text);
                        return text;
                    } catch (final ParsingException ignored) {
                    }
                }
                try {
                    YoutubeParsingHelper.parseDateFrom(text);
                    return text;
                } catch (final ParsingException ignored) {
                }
            }
        } catch (final Exception ignored) {
        }

        // lockupViewModel format - iterate through all metadata like PipePipe
        try {
            final JsonArray metadataRows = videoInfo.getObject("metadata")
                    .getObject("lockupMetadataViewModel")
                    .getObject("metadata")
                    .getObject("contentMetadataViewModel")
                    .getArray("metadataRows");
            for (final Object row : metadataRows) {
                final JsonArray metadataParts = ((JsonObject) row).getArray("metadataParts");
                for (final Object part : metadataParts) {
                    final String content = ((JsonObject) part)
                            .getObject("text")
                            .getString("content");

                    if (isNullOrEmpty(content)) {
                        continue;
                    }

                    if (timeAgoParser != null) {
                        try {
                            timeAgoParser.parse(content);
                            return content;
                        } catch (final ParsingException ignored) {
                        }
                    }

                    try {
                        YoutubeParsingHelper.parseDateFrom(content);
                        return content;
                    } catch (final ParsingException ignored) {
                    }
                }
            }
        } catch (final Exception ignored) {
            // Not a lockupViewModel format
        }

        final String shortsTimestampText = getTextFromObject(videoInfo
                .getObject("navigationEndpoint")
                .getObject("reelWatchEndpoint").getObject("overlay")
                .getObject("reelPlayerOverlayRenderer")
                .getObject("reelPlayerHeaderSupportedRenderers")
                .getObject("reelPlayerHeaderRenderer")
                .getObject("timestampText")
        );
        if (!isNullOrEmpty(shortsTimestampText)) {
            return shortsTimestampText;
        }

        return null;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        if (getStreamType().equals(StreamType.LIVE_STREAM)) {
            return null;
        }

        if (isPremiere()) {
            return new DateWrapper(getDateFromPremiere());
        }

        final String textualUploadDate = getTextualUploadDate();
        if (timeAgoParser != null && !isNullOrEmpty(textualUploadDate)) {
            try {
                return timeAgoParser.parse(textualUploadDate);
            } catch (final ParsingException e) {
                try {
                    return new DateWrapper(
                            YoutubeParsingHelper.parseDateFrom(textualUploadDate), true);
                } catch (final ParsingException e2) {
                    throw new ParsingException("Could not get upload date", e);
                }
            }
        }
        return null;
    }

    @Override
    public long getViewCount() throws ParsingException {
        try {
            if (videoInfo.has("topStandaloneBadge") || isPremium()) {
                return -1;
            }

            if (!videoInfo.has("viewCountText")) {
                // fallback: videoInfo.runs format (e.g. playlistVideoRenderer with nested videoInfo)
                try {
                    final JsonArray runs = videoInfo.getObject("videoInfo").getArray("runs");
                    for (final Object runObj : runs) {
                        final String text = ((JsonObject) runObj).getString("text");
                        final Long parsedViewCount = parseFallbackViewCount(text);
                        if (parsedViewCount != null) {
                            return parsedViewCount;
                        }
                    }
                } catch (final Exception ignored) {
                }

                // lockupViewModel format - iterate through all metadata like PipePipe
                final JsonArray metadataRows = videoInfo.getObject("metadata")
                        .getObject("lockupMetadataViewModel")
                        .getObject("metadata")
                        .getObject("contentMetadataViewModel")
                        .getArray("metadataRows");
                for (final Object row : metadataRows) {
                    final JsonArray metadataParts = ((JsonObject) row).getArray("metadataParts");
                    for (final Object part : metadataParts) {
                        final String content = ((JsonObject) part)
                                .getObject("text")
                                .getString("content");
                        final Long parsedViewCount = parseFallbackViewCount(content);
                        if (parsedViewCount != null) {
                            return parsedViewCount;
                        }
                    }
                }
                // This object is null when a video has its views hidden.
                return -1;
            }

            final String viewCount = getTextFromObject(videoInfo.getObject("viewCountText"));

            if (viewCount.toLowerCase().contains("no views")
                    || viewCount.toLowerCase().contains("akukho ukubukwa")
                    || viewCount.toLowerCase().contains("akukho kubukwa")) {
                return 0;
            } else if (viewCount.toLowerCase().contains("recommended")
                    || viewCount.toLowerCase().contains("okutusiwe")) {
                return -1;
            }

            final String digits = Utils.removeNonDigitCharacters(viewCount);
            return isNullOrEmpty(digits) ? -1 : Long.parseLong(digits);
        } catch (final Exception e) {
            throw new ParsingException("Could not get view count", e);
        }
    }

    @Nullable
    private static Long parseFallbackViewCount(@Nullable final String text) {
        if (isNullOrEmpty(text)) {
            return null;
        }

        final String lowercaseText = text.toLowerCase(Locale.ROOT);
        if (lowercaseText.contains("no views")
                || lowercaseText.contains("akukho ukubukwa")
                || lowercaseText.contains("akukho kubukwa")) {
            return 0L;
        }
        if (lowercaseText.contains("recommended") || lowercaseText.contains("okutusiwe")) {
            return -1L;
        }

        final boolean hasViewLabel = hasViewCountLabel(lowercaseText);
        final boolean hasDigit = containsDigit(text);
        if (!hasViewLabel || !hasDigit) {
            return null;
        }

        try {
            return Utils.mixedNumberWordToLong(text);
        } catch (final Exception ignored) {
            return null;
        }
    }

    static boolean containsDigit(final String text) {
        for (int index = 0; index < text.length();) {
            final int codePoint = Character.codePointAt(text, index);
            if (Character.isDigit(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean hasViewCountLabel(final String text) {
        final String lowercaseText = text.toLowerCase(Locale.ROOT);
        return VIEW_LABEL_PATTERN.matcher(lowercaseText).find()
                || lowercaseText.contains("ukubukwa");
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return getThumbnailUrlFromInfoItem(videoInfo);
    }

    private boolean isPremium() {
        final JsonArray badges = videoInfo.getArray("badges");
        for (final Object badge : badges) {
            if (((JsonObject) badge).getObject("metadataBadgeRenderer")
                    .getString("label", "").equals("Premium")) {
                return true;
            }
        }
        return false;
    }

    private boolean isPremiere() {
        return videoInfo.has("upcomingEventData");
    }

    private OffsetDateTime getDateFromPremiere() throws ParsingException {
        final JsonObject upcomingEventData = videoInfo.getObject("upcomingEventData");
        final String startTime = upcomingEventData.getString("startTime");

        try {
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(startTime)),
                    ZoneOffset.UTC);
        } catch (final Exception e) {
            throw new ParsingException("Could not parse date from premiere: \"" + startTime + "\"");
        }
    }

    @Nullable
    @Override
    public String getShortDescription() throws ParsingException {

        if (videoInfo.has("detailedMetadataSnippets")) {
            return getTextFromObject(videoInfo.getArray("detailedMetadataSnippets")
                    .getObject(0).getObject("snippetText"));
        }

        if (videoInfo.has("descriptionSnippet")) {
            return getTextFromObject(videoInfo.getObject("descriptionSnippet"));
        }

        return null;
    }
    @Override
    public boolean isShortFormContent() throws ParsingException {
        try {
            final String webPageType = videoInfo.getObject("navigationEndpoint")
                    .getObject("commandMetadata").getObject("webCommandMetadata")
                    .getString("webPageType");

            boolean isShort = !isNullOrEmpty(webPageType)
                    && webPageType.equals("WEB_PAGE_TYPE_SHORTS");

            if (!isShort) {
                isShort = videoInfo.getObject("navigationEndpoint").has("reelWatchEndpoint");
            }

            if (!isShort) {
                // lockupViewModel format
                final String lockupWebPageType = videoInfo.getObject("rendererContext")
                        .getObject("commandContext")
                        .getObject("onTap")
                        .getObject("innertubeCommand")
                        .getObject("commandMetadata")
                        .getObject("webCommandMetadata")
                        .getString("webPageType");
                isShort = !isNullOrEmpty(lockupWebPageType)
                        && lockupWebPageType.equals("WEB_PAGE_TYPE_SHORTS");
            }

            if (!isShort) {
                final JsonObject thumbnailTimeOverlay = videoInfo.getArray("thumbnailOverlays")
                        .stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .filter(thumbnailOverlay -> thumbnailOverlay.has(
                                "thumbnailOverlayTimeStatusRenderer"))
                        .map(thumbnailOverlay -> thumbnailOverlay.getObject(
                                "thumbnailOverlayTimeStatusRenderer"))
                        .findFirst()
                        .orElse(null);

                if (!isNullOrEmpty(thumbnailTimeOverlay)) {
                    isShort = thumbnailTimeOverlay.getString("style", "")
                            .equalsIgnoreCase("SHORTS")
                            || thumbnailTimeOverlay.getObject("icon")
                            .getString("iconType", "")
                            .toLowerCase()
                            .contains("shorts");
                }
            }

            return isShort;
        } catch (final Exception e) {
            throw new ParsingException("Could not determine if this is short-form content", e);
        }
    }

    @Override
    public boolean requiresMembership() throws ParsingException {
        final JsonArray badges = videoInfo.getArray("badges");
        for (final Object badge : badges) {
            if (((JsonObject) badge).getObject("metadataBadgeRenderer")
                    .getString("style", "").equals("BADGE_STYLE_TYPE_MEMBERS_ONLY")) {
                return true;
            }
        }
        try {
            final JsonArray metadataRows = videoInfo.getObject("metadata")
                    .getObject("lockupMetadataViewModel")
                    .getObject("metadata")
                    .getObject("contentMetadataViewModel")
                    .getArray("metadataRows");
            for (final Object row : metadataRows) {
                final JsonArray lockupBadges = ((JsonObject) row).getArray("badges");
                for (final Object badge : lockupBadges) {
                    if (((JsonObject) badge).getObject("badgeViewModel")
                            .getString("badgeStyle", "").equals("BADGE_MEMBERS_ONLY")) {
                        return true;
                    }
                }
            }
        } catch (final Exception ignored) {
            // Not lockupViewModel format or no badges
        }
        return false;
    }

}
