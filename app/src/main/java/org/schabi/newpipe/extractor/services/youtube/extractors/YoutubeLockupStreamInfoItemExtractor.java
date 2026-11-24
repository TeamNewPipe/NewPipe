package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getImagesFromThumbnailsArray;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getUrlFromNavigationEndpoint;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.hasArtistOrVerifiedIconBadgeAttachment;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;
import static org.schabi.newpipe.extractor.utils.Utils.mixedNumberWordToLong;
import static org.schabi.newpipe.extractor.utils.Utils.removeNonDigitCharacters;

public class YoutubeLockupStreamInfoItemExtractor implements StreamInfoItemExtractor {
    private final JsonObject lockupViewModel;
    private final JsonObject thumbnailViewModel;
    private final JsonObject lockupMetadataViewModel;
    private final TimeAgoParser timeAgoParser;
    private StreamType cachedStreamType;

    public YoutubeLockupStreamInfoItemExtractor(final JsonObject lockupViewModel,
                                                @Nullable final TimeAgoParser timeAgoParser) {
        this.lockupViewModel = lockupViewModel;
        this.thumbnailViewModel = lockupViewModel.getObject("contentImage")
                .getObject("thumbnailViewModel");
        this.lockupMetadataViewModel = lockupViewModel.getObject("metadata")
                .getObject("lockupMetadataViewModel");
        this.timeAgoParser = timeAgoParser;
    }

    @Override
    public String getName() throws ParsingException {
        final String name = lockupMetadataViewModel.getObject("title").getString("content");
        if (isNullOrEmpty(name)) {
            throw new ParsingException("Could not get name");
        }
        return name;
    }

    @Override
    public String getUrl() throws ParsingException {
        try {
            final String videoId = lockupViewModel.getString("contentId");
            return YoutubeStreamLinkHandlerFactory.getInstance().getUrl(videoId);
        } catch (final Exception e) {
            throw new ParsingException("Could not get url", e);
        }
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        final List<Image> thumbnails = getImagesFromThumbnailsArray(
                thumbnailViewModel.getObject("image").getArray("sources"));
        if (thumbnails.isEmpty()) {
            throw new ParsingException("Could not get thumbnail URL");
        }
        return thumbnails.get(thumbnails.size() - 1).getUrl();
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        if (cachedStreamType != null) {
            return cachedStreamType;
        }

        final JsonArray overlays = thumbnailViewModel.getArray("overlays");
        for (int i = 0; i < overlays.size(); i++) {
            final JsonObject overlay = overlays.getObject(i);

            if (overlay.has("thumbnailOverlayBadgeViewModel")) {
                final JsonArray thumbnailBadges = overlay.getObject("thumbnailOverlayBadgeViewModel")
                        .getArray("thumbnailBadges");
                for (int badgeIndex = 0; badgeIndex < thumbnailBadges.size(); badgeIndex++) {
                    final JsonObject badgeViewModel = thumbnailBadges.getObject(badgeIndex)
                            .getObject("thumbnailBadgeViewModel");
                    if (isLiveBadge(badgeViewModel)) {
                        cachedStreamType = StreamType.LIVE_STREAM;
                        return cachedStreamType;
                    }
                }
            }

            if (overlay.has("thumbnailBottomOverlayViewModel")) {
                final JsonArray badges = overlay.getObject("thumbnailBottomOverlayViewModel")
                        .getArray("badges");
                for (int badgeIndex = 0; badgeIndex < badges.size(); badgeIndex++) {
                    final JsonObject badgeViewModel = badges.getObject(badgeIndex)
                            .getObject("thumbnailBadgeViewModel");
                    if (isLiveBadge(badgeViewModel)) {
                        cachedStreamType = StreamType.LIVE_STREAM;
                        return cachedStreamType;
                    }
                }
            }
        }

        // Fallback: some locales only expose localized "watching"-type metadata.
        final JsonArray metadataRows = lockupMetadataViewModel.getObject("metadata")
                .getObject("contentMetadataViewModel")
                .getArray("metadataRows");
        for (int rowIndex = 0; rowIndex < metadataRows.size(); rowIndex++) {
            final JsonArray metadataParts = metadataRows.getObject(rowIndex).getArray("metadataParts");
            for (int partIndex = 0; partIndex < metadataParts.size(); partIndex++) {
                final String text = metadataParts.getObject(partIndex)
                        .getObject("text")
                        .getString("content");
                if (containsWatchingIndicator(text)) {
                    cachedStreamType = StreamType.LIVE_STREAM;
                    return cachedStreamType;
                }
            }
        }

        cachedStreamType = StreamType.VIDEO_STREAM;
        return cachedStreamType;
    }

    @Nullable
    @Override
    public Boolean getUpcomingStatus() throws ParsingException {
        return YoutubeUpcomingMetadata.status(lockupViewModel, getStreamType());
    }

    private boolean isLiveBadge(@Nonnull final JsonObject badgeViewModel) {
        final String badgeStyle = badgeViewModel.getString("badgeStyle", "");
        if (badgeStyle.toUpperCase(Locale.ROOT).contains("LIVE")) {
            return true;
        }

        final String text = badgeViewModel.getString("text", "");
        final String loweredText = text.toLowerCase(Locale.ROOT);
        if (loweredText.contains("live") || loweredText.contains("bukhoma")) {
            return true;
        }

        final String animatedText = badgeViewModel.getString("animatedText", "");
        final String loweredAnimatedText = animatedText.toLowerCase(Locale.ROOT);
        if (loweredAnimatedText.contains("live")) {
            return true;
        }

        try {
            final JsonArray sources = badgeViewModel.getObject("icon").getArray("sources");
            for (int i = 0; i < sources.size(); i++) {
                final String imageName = sources.getObject(i)
                        .getObject("clientResource")
                        .getString("imageName", "");
                if ("LIVE".equalsIgnoreCase(imageName)) {
                    return true;
                }
            }
        } catch (final Exception ignored) {
        }

        return false;
    }

    private boolean containsWatchingIndicator(@Nullable final String text) {
        if (isNullOrEmpty(text)) {
            return false;
        }
        final String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("watching")
                || lower.contains("viewer")
                || lower.contains("ababukele")
                || lower.contains("babukele")
                || lower.contains("bukele");
    }

    @Override
    public long getDuration() throws ParsingException {
        if (getStreamType() == StreamType.LIVE_STREAM) {
            return -1;
        }

        // Duration can be found in either thumbnailOverlayBadgeViewModel or
        // thumbnailBottomOverlayViewModel depending on the response format.
        final JsonArray overlays = thumbnailViewModel.getArray("overlays");
        for (int i = 0; i < overlays.size(); i++) {
            final String durationText = getDurationTextFromOverlay(overlays.getObject(i));
            if (isNullOrEmpty(durationText)) {
                continue;
            }
            if (isNullOrEmpty(removeNonDigitCharacters(durationText))) {
                continue;
            }

            try {
                return YoutubeParsingHelper.parseDurationString(durationText);
            } catch (final Exception ignored) {
                // Ignore unrecognized badge text and continue with the next candidate.
            }
        }

        return 0;
    }

    @Nullable
    private String getDurationTextFromOverlay(@Nonnull final JsonObject overlay) {
        if (overlay.has("thumbnailOverlayBadgeViewModel")) {
            final JsonArray thumbnailBadges = overlay.getObject("thumbnailOverlayBadgeViewModel")
                    .getArray("thumbnailBadges");
            for (int i = 0; i < thumbnailBadges.size(); i++) {
                final String text = thumbnailBadges.getObject(i)
                        .getObject("thumbnailBadgeViewModel")
                        .getString("text");
                if (!isNullOrEmpty(text)) {
                    return text;
                }
            }
        }

        if (overlay.has("thumbnailBottomOverlayViewModel")) {
            final JsonArray badges = overlay.getObject("thumbnailBottomOverlayViewModel")
                    .getArray("badges");
            for (int i = 0; i < badges.size(); i++) {
                final String text = badges.getObject(i)
                        .getObject("thumbnailBadgeViewModel")
                        .getString("text");
                if (!isNullOrEmpty(text)) {
                    return text;
                }
            }
        }

        return null;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        final JsonObject navigationEndpoint = getUploaderNavigationEndpoint();
        if (navigationEndpoint != null && navigationEndpoint.has("showDialogCommand")) {
            final String uploaderName = getFirstContributorName(navigationEndpoint);
            if (!isNullOrEmpty(uploaderName)) {
                return uploaderName;
            }
        }

        final JsonObject uploaderText = getUploaderText();
        if (uploaderText != null) {
            final String uploaderName = uploaderText.getString("content");
            if (!isNullOrEmpty(uploaderName)) {
                return uploaderName;
            }
        }

        throw new ParsingException("Could not get uploader name");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        final JsonObject navigationEndpoint = getUploaderNavigationEndpoint();
        if (navigationEndpoint != null) {
            final String uploaderUrl = getUrlFromNavigationEndpoint(navigationEndpoint);
            if (!isNullOrEmpty(uploaderUrl)) {
                return uploaderUrl;
            }
        }

        throw new ParsingException("Could not get uploader url");
    }

    @Nullable
    private JsonObject getUploaderText() {
        final JsonArray metadataRows = lockupMetadataViewModel.getObject("metadata")
                .getObject("contentMetadataViewModel")
                .getArray("metadataRows");

        for (int rowIndex = 0; rowIndex < metadataRows.size(); rowIndex++) {
            final JsonArray metadataParts = metadataRows.getObject(rowIndex).getArray("metadataParts");
            for (int partIndex = 0; partIndex < metadataParts.size(); partIndex++) {
                final JsonObject metadataPart = metadataParts.getObject(partIndex);
                final JsonObject text = metadataPart.getObject("text");
                final String content = text.getString("content");
                if (isNullOrEmpty(content)
                        || isViewCountText(content)
                        || isViewCountPart(metadataPart)
                        || isUploadDateText(content)) {
                    continue;
                }
                return text;
            }
        }

        return null;
    }

    @Nullable
    private JsonObject getUploaderNavigationEndpoint() {
        final JsonObject uploaderText = getUploaderText();
        if (uploaderText != null && uploaderText.has("commandRuns")) {
            final JsonArray commandRuns = uploaderText.getArray("commandRuns");
            if (!commandRuns.isEmpty()) {
                return commandRuns.getObject(0)
                        .getObject("onTap")
                        .getObject("innertubeCommand");
            }
        }

        final JsonObject image = lockupMetadataViewModel.getObject("image");
        if (image == null || image.isEmpty()) {
            return null;
        }

        if (image.has("decoratedAvatarViewModel")) {
            return image.getObject("decoratedAvatarViewModel")
                    .getObject("rendererContext")
                    .getObject("commandContext")
                    .getObject("onTap")
                    .getObject("innertubeCommand");
        }

        if (image.has("avatarStackViewModel")) {
            return image.getObject("avatarStackViewModel")
                    .getObject("rendererContext")
                    .getObject("commandContext")
                    .getObject("onTap")
                    .getObject("innertubeCommand");
        }

        return null;
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

            final String uploaderName = listItems.getObject(0)
                    .getObject("listItemViewModel")
                    .getObject("title")
                    .getString("content", "");
            return isNullOrEmpty(uploaderName) ? null : uploaderName;
        } catch (final Exception ignored) {
            return null;
        }
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        final JsonObject uploaderText = getUploaderText();
        if (uploaderText != null) {
            return hasArtistOrVerifiedIconBadgeAttachment(uploaderText.getArray("attachmentRuns"));
        }

        return false;
    }

    @Override
    public long getViewCount() throws ParsingException {
        final boolean isLiveStream = getStreamType() == StreamType.LIVE_STREAM;
        final JsonArray metadataRows = lockupMetadataViewModel.getObject("metadata")
                .getObject("contentMetadataViewModel")
                .getArray("metadataRows");

        for (int rowIndex = 0; rowIndex < metadataRows.size(); rowIndex++) {
            final JsonArray metadataParts = metadataRows.getObject(rowIndex)
                    .getArray("metadataParts");

            for (int partIndex = 0; partIndex < metadataParts.size(); partIndex++) {
                final Long viewCount = parseViewCount(metadataParts.getObject(partIndex), isLiveStream);
                if (viewCount != null) {
                    return viewCount;
                }
            }
        }

        return -1;
    }

    @Nullable
    private Long parseViewCount(@Nonnull final JsonObject metadataPart, final boolean isLiveStream) {
        final String viewsText = metadataPart.getObject("text").getString("content");
        final String accessibilityLabel = metadataPart.getString("accessibilityLabel");

        if (isViewCountText(viewsText)) {
            return parseViewCountText(viewsText, isLiveStream, false);
        }

        if (isViewCountText(accessibilityLabel) || isViewCountPart(metadataPart)) {
            final Long parsedFromText = parseViewCountText(viewsText, isLiveStream, true);
            if (parsedFromText != null) {
                return parsedFromText;
            }
            return parseViewCountText(accessibilityLabel, isLiveStream, true);
        }

        return null;
    }

    @Nullable
    private Long parseViewCountText(@Nullable final String viewsText,
                                    final boolean isLiveStream,
                                    final boolean assumeViewCount) {
        if (isNullOrEmpty(viewsText)) {
            return null;
        }

        final String lowerCaseViewsText = viewsText.toLowerCase(Locale.ROOT);
        if (lowerCaseViewsText.contains("no views")
                || lowerCaseViewsText.contains("akukho ukubukwa")
                || lowerCaseViewsText.contains("akukho kubukwa")) {
            return 0L;
        } else if (lowerCaseViewsText.contains("recommended")
                || lowerCaseViewsText.contains("okutusiwe")) {
            return -1L;
        }

        final boolean hasViewsKeyword = lowerCaseViewsText.contains("view")
                || lowerCaseViewsText.contains("ukubukwa")
                || containsWatchingIndicator(lowerCaseViewsText);
        if (!hasViewsKeyword && !isLiveStream && !assumeViewCount) {
            return null;
        }

        try {
            return mixedNumberWordToLong(viewsText);
        } catch (final Exception ignored) {
            final String digits = removeNonDigitCharacters(viewsText);
            if (!isNullOrEmpty(digits)) {
                try {
                    return Long.parseLong(digits);
                } catch (final NumberFormatException ignoredToo) {
                    return null;
                }
            }
            return null;
        }
    }

    private boolean isViewCountPart(@Nonnull final JsonObject metadataPart) {
        if (isViewCountText(metadataPart.getString("accessibilityLabel"))) {
            return true;
        }

        final JsonObject leadingIcon = metadataPart.getObject("leadingIcon");
        return leadingIcon != null
                && "PLAY_ARROW_OUTLINED".equals(leadingIcon.getString("name"));
    }

    private boolean isViewCountText(@Nullable final String text) {
        if (isNullOrEmpty(text)) {
            return false;
        }

        final String lowerCaseText = text.toLowerCase(Locale.ROOT);
        return lowerCaseText.contains("view")
                || lowerCaseText.contains("ukubukwa")
                || lowerCaseText.contains("no views")
                || lowerCaseText.contains("akukho ukubukwa")
                || lowerCaseText.contains("akukho kubukwa")
                || containsWatchingIndicator(lowerCaseText);
    }

    private boolean isUploadDateText(@Nullable final String text) {
        if (isNullOrEmpty(text)) {
            return false;
        }

        if (timeAgoParser != null) {
            try {
                timeAgoParser.parse(text);
                return true;
            } catch (final ParsingException ignored) {
            }
        }

        try {
            YoutubeParsingHelper.parseDateFrom(text);
            return true;
        } catch (final ParsingException ignored) {
            return false;
        }
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        final JsonArray metadataRows = lockupMetadataViewModel.getObject("metadata")
                .getObject("contentMetadataViewModel")
                .getArray("metadataRows");

        for (int rowIndex = 0; rowIndex < metadataRows.size(); rowIndex++) {
            final JsonArray metadataParts = metadataRows.getObject(rowIndex).getArray("metadataParts");
            for (int partIndex = 0; partIndex < metadataParts.size(); partIndex++) {
                final JsonObject metadataPart = metadataParts.getObject(partIndex);
                final String uploadText = metadataPart.getObject("text").getString("content");
                if (isUploadDateText(uploadText)) {
                    return uploadText;
                }
                // Compact channel cards use e.g. "9d ago" visually but retain a localized,
                // parseable "9 days ago" label. Without it regular uploads are dropped by feeds.
                final String accessibilityLabel = metadataPart.getString("accessibilityLabel");
                if (isUploadDateText(accessibilityLabel)) {
                    return accessibilityLabel;
                }
            }
        }

        return null;
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
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
    public boolean isAd() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getShortDescription() throws ParsingException {
        return null;
    }

    @Override
    public boolean isShortFormContent() throws ParsingException {
        return false;
    }

    @Override
    public boolean requiresMembership() throws ParsingException {
        try {
            final JsonArray metadataRows = lockupMetadataViewModel.getObject("metadata")
                    .getObject("contentMetadataViewModel")
                    .getArray("metadataRows");
            for (final Object row : metadataRows) {
                final JsonArray badges = ((JsonObject) row).getArray("badges");
                for (final Object badge : badges) {
                    if (((JsonObject) badge).getObject("badgeViewModel")
                            .getString("badgeStyle", "").equals("BADGE_MEMBERS_ONLY")) {
                        return true;
                    }
                }
            }
        } catch (final Exception ignored) {
            return false;
        }
        return false;
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        try {
            final JsonObject navigationEndpoint = getUploaderNavigationEndpoint();
            if (navigationEndpoint != null && navigationEndpoint.has("showDialogCommand")) {
                final JsonArray sources = navigationEndpoint
                        .getObject("showDialogCommand")
                        .getObject("panelLoadingStrategy")
                        .getObject("inlineContent")
                        .getObject("dialogViewModel")
                        .getObject("customContent")
                        .getObject("listViewModel")
                        .getArray("listItems")
                        .getObject(0)
                        .getObject("listItemViewModel")
                        .getObject("leadingAccessory")
                        .getObject("avatarViewModel")
                        .getObject("image")
                        .getArray("sources");
                if (!sources.isEmpty()) {
                    return sources.getObject(0).getString("url");
                }
            }

            final JsonObject image = lockupMetadataViewModel.getObject("image");
            if (image.has("decoratedAvatarViewModel")) {
                return image.getObject("decoratedAvatarViewModel")
                        .getObject("avatar")
                        .getObject("avatarViewModel")
                        .getObject("image")
                        .getArray("sources")
                        .getObject(0)
                        .getString("url");
            }

            if (image.has("avatarStackViewModel")) {
                return image.getObject("avatarStackViewModel")
                        .getArray("avatars")
                        .getObject(0)
                        .getObject("avatarViewModel")
                        .getObject("image")
                        .getArray("sources")
                        .getObject(0)
                        .getString("url");
            }

            return null;
        } catch (final Exception e) {
            return null;
        }
    }
}
