package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.post.PostInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Stores and applies user-defined video, channel, and keyword blocking rules. */
public final class ContentBlockingHelper {
    private static final String ENTRY_SEPARATOR = "\t";

    private ContentBlockingHelper() {
    }

    @NonNull
    public static Rules getRules(@NonNull final Context context,
                                 @NonNull final Target target) {
        final SharedPreferences preferences = preferences(context);
        final String targetsKey = context.getString(R.string.content_blocking_targets_key);
        final Set<String> enabledTargets = preferences.contains(targetsKey)
                ? copySet(preferences, targetsKey) : null;
        final boolean enabled = preferences.getBoolean(context.getString(
                R.string.content_blocking_enabled_key), true)
                && isTargetEnabled(enabledTargets, target);
        final Set<String> aiBlockedChannels = enabled
                ? AiSListContentHelper.hiddenChannelEntries(context)
                : Collections.emptySet();
        final Rules rules = Rules.create(
                enabled,
                copySet(preferences, context.getString(R.string.blocked_videos_key)),
                copySet(preferences, context.getString(R.string.blocked_channels_key)),
                preferences.getString(context.getString(
                        R.string.blocked_keywords_key), ""),
                preferences.getBoolean(context.getString(
                        R.string.blocked_keywords_channel_names_key), false),
                aiBlockedChannels,
                copySet(preferences, context.getString(R.string.allowed_channels_key)));
        rules.regexFilters = RegexFilterRules.fromStored(preferences.getString(
                context.getString(R.string.content_regex_rules_key), ""));
        rules.commentFilters = new CommentFilterRules(
                preferences.getString(context.getString(R.string.comment_filter_keywords_key), ""),
                preferences.getString(context.getString(R.string.comment_filter_authors_key), ""),
                preferences.getInt(context.getString(R.string.comment_filter_min_likes_key), 0));
        return rules;
    }

    public static void blockVideo(@NonNull final Context context,
                                  @NonNull final StreamInfoItem item) {
        addEntry(context, R.string.blocked_videos_key, item.getUrl(), item.getName());
        enable(context);
    }

    public static void blockChannel(@NonNull final Context context,
                                    @Nullable final String channelUrl,
                                    @Nullable final String channelName) {
        final String stableKey = ChannelIdentity.stableKey(channelUrl);
        final String key = !stableKey.isEmpty() ? stableKey
                : !isBlank(channelUrl) ? channelUrl : channelName;
        if (isBlank(key)) {
            return;
        }
        addEntry(context, R.string.blocked_channels_key, key,
                isBlank(channelName) ? key : channelName);
        enable(context);
    }

    @NonNull
    public static List<Entry> getBlockedVideos(@NonNull final Context context) {
        return decodeEntries(copySet(preferences(context),
                context.getString(R.string.blocked_videos_key)));
    }

    @NonNull
    public static List<Entry> getBlockedChannels(@NonNull final Context context) {
        return decodeEntries(copySet(preferences(context),
                context.getString(R.string.blocked_channels_key)));
    }

    public static void saveBlockedVideos(@NonNull final Context context,
                                         @NonNull final List<Entry> entries) {
        saveEntries(context, R.string.blocked_videos_key, entries);
    }

    public static void saveBlockedChannels(@NonNull final Context context,
                                           @NonNull final List<Entry> entries) {
        saveEntries(context, R.string.blocked_channels_key, entries);
    }

    public static void allowChannel(@NonNull final Context context, final int serviceId,
                                    @NonNull final String url, @NonNull final String name) {
        addEntry(context, R.string.allowed_channels_key, serviceId + "|" + url, name);
    }

    @NonNull
    public static List<Entry> getAllowedChannels(@NonNull final Context context) {
        return decodeEntries(copySet(preferences(context),
                context.getString(R.string.allowed_channels_key)));
    }

    public static void saveAllowedChannels(@NonNull final Context context,
                                          @NonNull final List<Entry> entries) {
        saveEntries(context, R.string.allowed_channels_key, entries);
    }

    public static void clearAll(@NonNull final Context context) {
        preferences(context).edit()
                .remove(context.getString(R.string.blocked_videos_key))
                .remove(context.getString(R.string.blocked_channels_key))
                .remove(context.getString(R.string.blocked_keywords_key))
                .remove(context.getString(R.string.content_regex_rules_key))
                .remove(context.getString(R.string.comment_filter_keywords_key))
                .remove(context.getString(R.string.comment_filter_authors_key))
                .remove(context.getString(R.string.comment_filter_min_likes_key))
                .apply();
    }

    public static boolean isPreferenceKey(@NonNull final Context context,
                                          @Nullable final String key) {
        return getRuleChange(context, key) != null;
    }

    public static boolean shouldReloadForPreference(@NonNull final Context context,
                                                    @Nullable final String key,
                                                    @NonNull final Target target) {
        final RuleChange ruleChange = getRuleChange(context, key);
        return ruleChange != null && shouldReloadForRuleChange(target, ruleChange);
    }

    static boolean shouldReloadForRuleChange(@NonNull final Target target,
                                             @NonNull final RuleChange ruleChange) {
        if (target != Target.COMMENTS) {
            return true;
        }

        // Comments can only be affected by the master switch, target selection, or keyword rules.
        // Video/channel/AiSList rules apply to stream/channel-style items and should not trigger a
        // network comments refresh when they change.
        return ruleChange == RuleChange.ENABLED
                || ruleChange == RuleChange.TARGETS
                || ruleChange == RuleChange.KEYWORDS;
    }

    @Nullable
    private static RuleChange getRuleChange(@NonNull final Context context,
                                            @Nullable final String key) {
        if (context.getString(R.string.content_blocking_enabled_key).equals(key)) {
            return RuleChange.ENABLED;
        }
        if (context.getString(R.string.content_blocking_targets_key).equals(key)) {
            return RuleChange.TARGETS;
        }
        if (context.getString(R.string.blocked_videos_key).equals(key)) {
            return RuleChange.VIDEOS;
        }
        if (context.getString(R.string.allowed_channels_key).equals(key)
                || context.getString(R.string.blocked_channels_key).equals(key)) {
            return RuleChange.CHANNELS;
        }
        if (context.getString(R.string.content_regex_rules_key).equals(key)
                || context.getString(R.string.blocked_keywords_key).equals(key)
                || context.getString(R.string.comment_filter_keywords_key).equals(key)
                || context.getString(R.string.comment_filter_authors_key).equals(key)
                || context.getString(R.string.comment_filter_min_likes_key).equals(key)) {
            return RuleChange.KEYWORDS;
        }
        if (context.getString(R.string.blocked_keywords_channel_names_key).equals(key)) {
            return RuleChange.KEYWORD_CHANNEL_NAMES;
        }
        if (context.getString(R.string.hide_members_only_videos_key).equals(key)) {
            return RuleChange.MEMBERS_ONLY;
        }
        if (context.getString(R.string.aislist_enabled_key).equals(key)) {
            return RuleChange.AISLIST_ENABLED;
        }
        if (context.getString(R.string.aislist_warn_behavior_key).equals(key)) {
            return RuleChange.AISLIST_WARN_BEHAVIOR;
        }
        return null;
    }

    enum RuleChange {
        ENABLED,
        TARGETS,
        VIDEOS,
        CHANNELS,
        KEYWORDS,
        KEYWORD_CHANNEL_NAMES,
        MEMBERS_ONLY,
        AISLIST_ENABLED,
        AISLIST_WARN_BEHAVIOR
    }

    static boolean isTargetEnabled(@Nullable final Set<String> enabledTargets,
                                   @NonNull final Target target) {
        return enabledTargets == null || enabledTargets.contains(target.preferenceValue);
    }

    public enum Target {
        SEARCH("search"),
        KIOSKS("kiosks"),
        RELATED_ITEMS("related_items"),
        CHANNEL_PAGES("channel_pages"),
        SUBSCRIPTIONS("subscriptions"),
        REMOTE_PLAYLISTS("remote_playlists"),
        COMMENTS("comments");

        @NonNull
        private final String preferenceValue;

        Target(@NonNull final String preferenceValue) {
            this.preferenceValue = preferenceValue;
        }
    }

    private static void enable(@NonNull final Context context) {
        preferences(context).edit()
                .putBoolean(context.getString(R.string.content_blocking_enabled_key), true)
                .apply();
    }

    private static void addEntry(@NonNull final Context context,
                                 final int preferenceKey,
                                 @NonNull final String key,
                                 @Nullable final String label) {
        final SharedPreferences preferences = preferences(context);
        final String resolvedKey = context.getString(preferenceKey);
        final Set<String> entries = copySet(preferences, resolvedKey);
        entries.removeIf(value -> decodeEntry(value).key.equals(normalize(key)));
        entries.add(encodeEntry(key, label));
        preferences.edit().putStringSet(resolvedKey, entries).apply();
    }

    private static void saveEntries(@NonNull final Context context,
                                    final int preferenceKey,
                                    @NonNull final List<Entry> entries) {
        final Set<String> encoded = new HashSet<>();
        for (final Entry entry : entries) {
            encoded.add(encodeEntry(entry.key, entry.label));
        }
        preferences(context).edit()
                .putStringSet(context.getString(preferenceKey), encoded)
                .apply();
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    @NonNull
    private static Set<String> copySet(@NonNull final SharedPreferences preferences,
                                       @NonNull final String key) {
        return new HashSet<>(preferences.getStringSet(key, Collections.emptySet()));
    }

    @NonNull
    private static String encodeEntry(@NonNull final String key, @Nullable final String label) {
        final String safeLabel = isBlank(label) ? key : label.replace('\t', ' ');
        return normalize(key) + ENTRY_SEPARATOR + safeLabel.trim();
    }

    @NonNull
    private static List<Entry> decodeEntries(@NonNull final Set<String> values) {
        final List<Entry> entries = new ArrayList<>();
        for (final String value : values) {
            entries.add(decodeEntry(value));
        }
        entries.sort((first, second) -> first.label.compareToIgnoreCase(second.label));
        return entries;
    }

    @NonNull
    private static Entry decodeEntry(@NonNull final String value) {
        final int separator = value.indexOf(ENTRY_SEPARATOR);
        if (separator < 0) {
            return new Entry(normalize(value), value);
        }
        return new Entry(normalize(value.substring(0, separator)),
                value.substring(separator + ENTRY_SEPARATOR.length()));
    }

    private static boolean isBlank(@Nullable final String value) {
        return value == null || value.trim().isEmpty();
    }

    @NonNull
    private static String normalize(@Nullable final String value) {
        return value == null ? "" : trimWhitespace(value).toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static String trimWhitespace(@NonNull final String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isWhitespace(final char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    /**
     * Returns the canonical persisted representation of keyword rules.
     *
     * <p>Commas and line breaks separate rules. Surrounding Unicode whitespace, empty rules, and
     * case-insensitive duplicates are removed while the first spelling of each rule is retained.
     *
     * @param keywords raw comma- or line-break-separated keyword rules
     * @return canonical line-break-separated keyword rules
     */
    @NonNull
    public static String sanitizeKeywords(@Nullable final String keywords) {
        if (keywords == null) {
            return "";
        }

        final List<String> sanitized = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        for (final String value : keywords.split("[,\\n]")) {
            final String keyword = trimWhitespace(value);
            if (!keyword.isEmpty() && seen.add(normalize(keyword))) {
                sanitized.add(keyword);
            }
        }
        return String.join("\n", sanitized);
    }

    public static final class Entry {
        @NonNull
        private final String key;
        @NonNull
        private final String label;

        private Entry(@NonNull final String key, @NonNull final String label) {
            this.key = key;
            this.label = label;
        }

        @NonNull
        public String getKey() {
            return key;
        }

        @NonNull
        public String getLabel() {
            return label;
        }
    }

    public static final class Rules {
        private final boolean enabled;
        @NonNull
        private final Set<String> blockedVideoUrls;
        @NonNull
        private final Set<String> blockedChannelKeys;
        @NonNull
        private final Set<String> blockedChannelNames;
        private Set<String> nameOnlyChannelRules = Collections.emptySet();
        @NonNull
        private final List<String> blockedKeywords;
        private final boolean matchKeywordsInChannelNames;
        @NonNull
        private final Set<String> aiBlockedChannels;
        private Set<String> allowedChannelKeys = Collections.emptySet();
        private RegexFilterRules regexFilters = RegexFilterRules.fromStored("");
        private CommentFilterRules commentFilters = new CommentFilterRules("", "", 0);

        private Rules(final boolean enabled,
                      @NonNull final Set<String> blockedVideoUrls,
                      @NonNull final Set<String> blockedChannelKeys,
                      @NonNull final Set<String> blockedChannelNames,
                      @NonNull final List<String> blockedKeywords,
                      final boolean matchKeywordsInChannelNames,
                      @NonNull final Set<String> aiBlockedChannels) {
            this.enabled = enabled;
            this.blockedVideoUrls = blockedVideoUrls;
            this.blockedChannelKeys = blockedChannelKeys;
            this.blockedChannelNames = blockedChannelNames;
            this.blockedKeywords = blockedKeywords;
            this.matchKeywordsInChannelNames = matchKeywordsInChannelNames;
            this.aiBlockedChannels = aiBlockedChannels;
        }

        @NonNull
        static Rules create(final boolean enabled,
                            @NonNull final Set<String> videoEntries,
                            @NonNull final Set<String> channelEntries,
                            @Nullable final String keywords) {
            return create(enabled, videoEntries, channelEntries, keywords,
                    false, Collections.emptySet());
        }

        @NonNull
        static Rules create(final boolean enabled,
                            @NonNull final Set<String> videoEntries,
                            @NonNull final Set<String> channelEntries,
                            @Nullable final String keywords,
                            @NonNull final Set<String> aiBlockedChannels) {
            return create(enabled, videoEntries, channelEntries, keywords,
                    false, aiBlockedChannels);
        }

        @NonNull
        static Rules create(final boolean enabled,
                            @NonNull final Set<String> videoEntries,
                            @NonNull final Set<String> channelEntries,
                            @Nullable final String keywords,
                            final boolean matchKeywordsInChannelNames,
                            @NonNull final Set<String> aiBlockedChannels) {
            return create(enabled, videoEntries, channelEntries, keywords,
                    matchKeywordsInChannelNames, aiBlockedChannels, Collections.emptySet());
        }

        @NonNull
        static Rules create(final boolean enabled,
                            @NonNull final Set<String> videoEntries,
                            @NonNull final Set<String> channelEntries,
                            @Nullable final String keywords,
                            final boolean matchKeywordsInChannelNames,
                            @NonNull final Set<String> aiBlockedChannels,
                            @NonNull final Set<String> allowedEntries) {
            final Set<String> videos = new HashSet<>();
            for (final String value : videoEntries) {
                videos.add(decodeEntry(value).key);
            }

            final Set<String> channels = new HashSet<>();
            final Set<String> channelNames = new HashSet<>();
            final Set<String> nameOnlyChannels = new HashSet<>();
            for (final String value : channelEntries) {
                final Entry entry = decodeEntry(value);
                channels.add(entry.key);
                channelNames.add(normalize(entry.label));
                if (!entry.key.contains("://")
                        && !entry.key.startsWith("youtube-channel-id:")) {
                    nameOnlyChannels.add(normalize(entry.label));
                }
            }

            final List<String> keywordList = new ArrayList<>();
            for (final String value : sanitizeKeywords(keywords).split("\\n")) {
                final String keyword = normalize(value);
                if (!keyword.isEmpty()) {
                    keywordList.add(keyword);
                }
            }
            final Rules rules = new Rules(
                    enabled,
                    videos,
                    channels,
                    channelNames,
                    keywordList,
                    matchKeywordsInChannelNames,
                    new HashSet<>(aiBlockedChannels));
            final Set<String> allowed = new HashSet<>();
            for (final String value : allowedEntries) {
                allowed.add(decodeEntry(value).key);
            }
            rules.allowedChannelKeys = allowed;
            rules.nameOnlyChannelRules = nameOnlyChannels;
            return rules;
        }

        public boolean isBlocked(@Nullable final InfoItem item) {
            if (!enabled || item == null) {
                return false;
            }
            if (item instanceof CommentsInfoItem) {
                final CommentsInfoItem comment = (CommentsInfoItem) item;
                return commentFilters.isBlocked(comment)
                        || containsKeyword(comment.getName(), comment.getCommentTextContent());
            }
            if (item instanceof StreamInfoItem) {
                final StreamInfoItem stream = (StreamInfoItem) item;
                if (blockedVideoUrls.contains(normalize(stream.getUrl()))) {
                    return true;
                }
                if (isAllowedChannel(stream.getServiceId(), stream.getUploaderUrl())) {
                    return false;
                }
                return isBlockedChannel(
                                stream.getServiceId(),
                                stream.getUploaderUrl(),
                                stream.getUploaderName())
                        || containsKeyword(stream.getName())
                        || matchKeywordsInChannelNames
                        && containsKeyword(stream.getUploaderName());
            }
            if (item instanceof ChannelInfoItem) {
                if (isAllowedChannel(item.getServiceId(), item.getUrl())) {
                    return false;
                }
                return isBlockedChannel(item.getServiceId(), item.getUrl(), item.getName())
                        || containsKeyword(item.getName());
            }
            if (item instanceof PlaylistInfoItem) {
                final PlaylistInfoItem playlist = (PlaylistInfoItem) item;
                return isBlockedChannel(
                        playlist.getServiceId(), null, playlist.getUploaderName())
                        || containsKeyword(playlist.getName())
                        || matchKeywordsInChannelNames
                        && containsKeyword(playlist.getUploaderName());
            }
            if (item instanceof PostInfoItem) {
                final PostInfoItem post = (PostInfoItem) item;
                if (isAllowedChannel(post.getServiceId(), post.getUploaderUrl())) {
                    return false;
                }
                return isBlockedChannel(
                        post.getServiceId(), post.getUploaderUrl(), post.getUploaderName())
                        || containsKeyword(post.getName(), post.getContent())
                        || matchKeywordsInChannelNames
                        && containsKeyword(post.getUploaderName());
            }
            return containsKeyword(item.getName());
        }

        private boolean isAllowedChannel(final int serviceId, @Nullable final String url) {
            return !isBlank(url) && allowedChannelKeys.contains(normalize(serviceId + "|" + url));
        }

        private boolean isBlockedChannel(final int serviceId,
                                         @Nullable final String url,
                                         @Nullable final String name) {
            return blockedChannelKeys.contains(ChannelIdentity.stableKey(url))
                    || blockedChannelKeys.contains(normalize(url))
                    || nameOnlyChannelRules.contains(normalize(name))
                    || isBlank(url) && blockedChannelNames.contains(normalize(name))
                    || AiSListRepository.isListed(serviceId, aiBlockedChannels, url, name);
        }

        private boolean containsKeyword(@Nullable final String... values) {
            for (final String value : values) {
                if (regexFilters.matches(value)) {
                    return true;
                }
                final String normalized = normalize(value);
                for (final String keyword : blockedKeywords) {
                    if (normalized.contains(keyword)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
