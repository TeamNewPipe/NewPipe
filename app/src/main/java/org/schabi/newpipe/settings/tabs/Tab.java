package org.schabi.newpipe.settings.tabs;

import org.schabi.newpipe.recommendations.RecommendationsFragment;

import org.schabi.newpipe.learning.LearningDashboardFragment;
import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonStringWriter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem.LocalItemType;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.download.DownloadsTabFragment;
import org.schabi.newpipe.fragments.BlankFragment;
import org.schabi.newpipe.fragments.list.channel.ChannelFragment;
import org.schabi.newpipe.fragments.list.kiosk.DefaultKioskFragment;
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment;
import org.schabi.newpipe.local.bookmark.BookmarkFragment;
import org.schabi.newpipe.local.feed.FeedFragment;
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment;
import org.schabi.newpipe.local.media.LocalMediaFragment;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.local.subscription.FeedGroupIcon;
import org.schabi.newpipe.local.subscription.SubscriptionFragment;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.Objects;

public abstract class Tab {
    private static final String JSON_TAB_ID_KEY = "tab_id";
    private static final String JSON_CUSTOM_ICON_ID_KEY = "custom_icon_id";
    public static final int DEFAULT_ICON_ID = -1;

    private static final String NO_NAME = "<no-name>";
    private static final String NO_ID = "<no-id>";
    private static final String NO_URL = "<no-url>";

    private int customIconId = DEFAULT_ICON_ID;

    Tab() {
    }

    Tab(@NonNull final JsonObject jsonObject) {
        customIconId = jsonObject.getInt(JSON_CUSTOM_ICON_ID_KEY, DEFAULT_ICON_ID);
        readDataFromJson(jsonObject);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tab Handling
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    public static Tab from(@NonNull final JsonObject jsonObject) {
        final int tabId = jsonObject.getInt(Tab.JSON_TAB_ID_KEY, -1);

        if (tabId == -1) {
            return null;
        }

        return from(tabId, jsonObject);
    }

    @Nullable
    public static Tab from(final int tabId) {
        return from(tabId, null);
    }

    @Nullable
    public static Type typeFrom(final int tabId) {
        for (final Type available : Type.values()) {
            if (available.getTabId() == tabId) {
                return available;
            }
        }
        return null;
    }

    @Nullable
    private static Tab from(final int tabId, @Nullable final JsonObject jsonObject) {
        final Type type = typeFrom(tabId);

        if (type == null) {
            return null;
        }

        if (jsonObject != null) {
            switch (type) {
                case KIOSK:
                    return new KioskTab(jsonObject);
                case CHANNEL:
                    return new ChannelTab(jsonObject);
                case PLAYLIST:
                    return new PlaylistTab(jsonObject);
                case FEEDGROUP:
                    return new FeedGroupTab(jsonObject);
                default:
                    type.getTab().customIconId = jsonObject.getInt(
                            JSON_CUSTOM_ICON_ID_KEY, DEFAULT_ICON_ID);
                    break;
            }
        }

        return type.getTab();
    }

    public abstract int getTabId();

    public abstract String getTabName(Context context);

    @DrawableRes
    public final int getTabIconRes(final Context context) {
        final FeedGroupIcon customIcon = FeedGroupIcon.fromId(customIconId);
        return customIcon == null ? getDefaultTabIconRes(context)
                : customIcon.getDrawableResource();
    }

    @DrawableRes
    protected abstract int getDefaultTabIconRes(Context context);

    public int getCustomIconId() {
        return customIconId;
    }

    public void setCustomIconId(final int iconId) {
        customIconId = FeedGroupIcon.fromId(iconId) == null ? DEFAULT_ICON_ID : iconId;
    }

    /**
     * Return a instance of the fragment that this tab represent.
     *
     * @param context Android app context
     * @return the fragment this tab represents
     */
    public abstract Fragment getFragment(Context context) throws ExtractionException;

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Tab)) {
            return false;
        }
        final Tab other = (Tab) obj;
        return getTabId() == other.getTabId();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getTabId());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // JSON Handling
    //////////////////////////////////////////////////////////////////////////*/

    public void writeJsonOn(final JsonStringWriter jsonSink) {
        jsonSink.object();

        jsonSink.value(JSON_TAB_ID_KEY, getTabId());
        jsonSink.value(JSON_CUSTOM_ICON_ID_KEY, customIconId);
        writeDataToJson(jsonSink);

        jsonSink.end();
    }

    protected void writeDataToJson(final JsonStringWriter writerSink) {
        // No-op
    }

    protected void readDataFromJson(final JsonObject jsonObject) {
        // No-op
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Implementations
    //////////////////////////////////////////////////////////////////////////*/

    public enum Type {
        BLANK(new BlankTab()),
        DEFAULT_KIOSK(new DefaultKioskTab()),
        SUBSCRIPTIONS(new SubscriptionsTab()),
        FEED(new FeedTab()),
        BOOKMARKS(new BookmarksTab()),
        HISTORY(new HistoryTab()),
        KIOSK(new KioskTab()),
        CHANNEL(new ChannelTab()),
        PLAYLIST(new PlaylistTab()),
        FEEDGROUP(new FeedGroupTab()),
        DOWNLOADS(new DownloadsTab()),
        LOCAL_MEDIA(new LocalMediaTab()),
        LEARNING(new LearningTab()),
        RECOMMENDATIONS(new RecommendationsTab());

        private final Tab tab;

        Type(final Tab tab) {
            this.tab = tab;
        }

        public int getTabId() {
            return tab.getTabId();
        }

        public Tab getTab() {
            return tab;
        }
    }

    public static class RecommendationsTab extends Tab {
        public static final int ID = 13;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.local_recommendations_feed);
        }

        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_whatshot;
        }

        @Override
        public Fragment getFragment(final Context context) {
            return new RecommendationsFragment();
        }
    }

    public static class LearningTab extends Tab {
        public static final int ID = 12;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.learning_dashboard_title);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_school;
        }

        @Override
        public Fragment getFragment(final Context context) {
            return new LearningDashboardFragment();
        }
    }

    public static class BlankTab extends Tab {
        public static final int ID = 0;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.app_name);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_crop_portrait;
        }

        @Override
        public BlankFragment getFragment(final Context context) {
            return new BlankFragment();
        }
    }

    public static class SubscriptionsTab extends Tab {
        public static final int ID = 1;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.tab_subscriptions);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_tv;
        }

        @Override
        public SubscriptionFragment getFragment(final Context context) {
            return new SubscriptionFragment();
        }

    }

    public static class FeedTab extends Tab {
        public static final int ID = 2;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.fragment_feed_title);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_subscriptions;
        }

        @Override
        public FeedFragment getFragment(final Context context) {
            return new FeedFragment();
        }
    }

    public static class BookmarksTab extends Tab {
        public static final int ID = 3;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.tab_bookmarks);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_bookmark;
        }

        @Override
        public BookmarkFragment getFragment(final Context context) {
            return new BookmarkFragment();
        }
    }

    public static class HistoryTab extends Tab {
        public static final int ID = 4;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.title_activity_history);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_history;
        }

        @Override
        public StatisticsPlaylistFragment getFragment(final Context context) {
            return new StatisticsPlaylistFragment();
        }
    }

    public static class DownloadsTab extends Tab {
        public static final int ID = 10;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.downloads);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_file_download;
        }

        @Override
        public DownloadsTabFragment getFragment(final Context context) {
            return new DownloadsTabFragment();
        }
    }

    public static class LocalMediaTab extends Tab {
        public static final int ID = 11;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.local_media);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_music_note;
        }

        @Override
        public LocalMediaFragment getFragment(final Context context) {
            return new LocalMediaFragment();
        }
    }

    public static class KioskTab extends Tab {
        public static final int ID = 5;
        private static final String JSON_KIOSK_SERVICE_ID_KEY = "service_id";
        private static final String JSON_KIOSK_ID_KEY = "kiosk_id";
        private int kioskServiceId;
        private String kioskId;

        private KioskTab() {
            this(-1, NO_ID);
        }

        public KioskTab(final int kioskServiceId, final String kioskId) {
            this.kioskServiceId = kioskServiceId;
            this.kioskId = kioskId;
        }

        public KioskTab(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return KioskTranslator.getTranslatedKioskName(kioskId, context);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            final int kioskIcon = KioskTranslator.getKioskIcon(kioskId);

            if (kioskIcon <= 0) {
                throw new IllegalStateException("Kiosk ID is not valid: \"" + kioskId + "\"");
            }

            return kioskIcon;
        }

        @Override
        public KioskFragment getFragment(final Context context) throws ExtractionException {
            return KioskFragment.getInstance(kioskServiceId, kioskId);
        }

        @Override
        protected void writeDataToJson(final JsonStringWriter writerSink) {
            writerSink.value(JSON_KIOSK_SERVICE_ID_KEY, kioskServiceId)
                    .value(JSON_KIOSK_ID_KEY, kioskId);
        }

        @Override
        protected void readDataFromJson(final JsonObject jsonObject) {
            kioskServiceId = jsonObject.getInt(JSON_KIOSK_SERVICE_ID_KEY, -1);
            kioskId = jsonObject.getString(JSON_KIOSK_ID_KEY, NO_ID);
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof KioskTab)) {
                return false;
            }
            final KioskTab other = (KioskTab) obj;
            return super.equals(obj)
                    && kioskServiceId == other.kioskServiceId
                    && kioskId.equals(other.kioskId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(getTabId(), kioskServiceId, kioskId);
        }

        public int getKioskServiceId() {
            return kioskServiceId;
        }

        public String getKioskId() {
            return kioskId;
        }
    }

    public static class ChannelTab extends Tab {
        public static final int ID = 6;
        private static final String JSON_CHANNEL_SERVICE_ID_KEY = "channel_service_id";
        private static final String JSON_CHANNEL_URL_KEY = "channel_url";
        private static final String JSON_CHANNEL_NAME_KEY = "channel_name";
        private int channelServiceId;
        private String channelUrl;
        private String channelName;

        private ChannelTab() {
            this(-1, NO_URL, NO_NAME);
        }

        public ChannelTab(final int channelServiceId, final String channelUrl,
                          final String channelName) {
            this.channelServiceId = channelServiceId;
            this.channelUrl = channelUrl;
            this.channelName = channelName;
        }

        public ChannelTab(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return channelName;
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_tv;
        }

        @Override
        public ChannelFragment getFragment(final Context context) {
            return ChannelFragment.getInstance(channelServiceId, channelUrl, channelName);
        }

        @Override
        protected void writeDataToJson(final JsonStringWriter writerSink) {
            writerSink.value(JSON_CHANNEL_SERVICE_ID_KEY, channelServiceId)
                    .value(JSON_CHANNEL_URL_KEY, channelUrl)
                    .value(JSON_CHANNEL_NAME_KEY, channelName);
        }

        @Override
        protected void readDataFromJson(final JsonObject jsonObject) {
            channelServiceId = jsonObject.getInt(JSON_CHANNEL_SERVICE_ID_KEY, -1);
            channelUrl = jsonObject.getString(JSON_CHANNEL_URL_KEY, NO_URL);
            channelName = jsonObject.getString(JSON_CHANNEL_NAME_KEY, NO_NAME);
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof ChannelTab)) {
                return false;
            }
            final ChannelTab other = (ChannelTab) obj;
            return super.equals(obj)
                    && channelServiceId == other.channelServiceId
                    && channelUrl.equals(other.channelUrl)
                    && channelName.equals(other.channelName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(getTabId(), channelServiceId, channelUrl, channelName);
        }

        public int getChannelServiceId() {
            return channelServiceId;
        }

        public String getChannelUrl() {
            return channelUrl;
        }

        public String getChannelName() {
            return channelName;
        }
    }

    public static class DefaultKioskTab extends Tab {
        public static final int ID = 7;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return KioskTranslator.getTranslatedKioskName(getDefaultKioskId(context), context);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return KioskTranslator.getKioskIcon(getDefaultKioskId(context));
        }

        @Override
        public DefaultKioskFragment getFragment(final Context context) {
            return new DefaultKioskFragment();
        }

        private String getDefaultKioskId(final Context context) {
            final int kioskServiceId = ServiceHelper.getSelectedServiceId(context);

            String kioskId = "";
            try {
                final StreamingService service = NewPipe.getService(kioskServiceId);
                kioskId = service.getKioskList().getDefaultKioskId();
            } catch (final ExtractionException e) {
                ErrorUtil.showSnackbar(context, new ErrorInfo(e,
                        UserAction.REQUESTED_KIOSK, "Loading default kiosk for selected service"));
            }
            return kioskId;
        }
    }

    public static class PlaylistTab extends Tab {
        public static final int ID = 8;
        private static final String JSON_PLAYLIST_SERVICE_ID_KEY = "playlist_service_id";
        private static final String JSON_PLAYLIST_URL_KEY = "playlist_url";
        private static final String JSON_PLAYLIST_NAME_KEY = "playlist_name";
        private static final String JSON_PLAYLIST_ID_KEY = "playlist_id";
        private static final String JSON_PLAYLIST_TYPE_KEY = "playlist_type";
        private int playlistServiceId;
        private String playlistUrl;
        private String playlistName;
        private long playlistId;
        private LocalItemType playlistType;

        private PlaylistTab() {
            this(-1, NO_NAME);
        }

        public PlaylistTab(final long playlistId, final String playlistName) {
            this.playlistName = playlistName;
            this.playlistId = playlistId;
            this.playlistType = LocalItemType.PLAYLIST_LOCAL_ITEM;
            this.playlistServiceId = -1;
            this.playlistUrl = NO_URL;
        }

        public PlaylistTab(final int playlistServiceId, final String playlistUrl,
                           final String playlistName) {
            this.playlistServiceId = playlistServiceId;
            this.playlistUrl = playlistUrl;
            this.playlistName = playlistName;
            this.playlistType = LocalItemType.PLAYLIST_REMOTE_ITEM;
            this.playlistId = -1;
        }

        public PlaylistTab(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return playlistName;
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            return R.drawable.ic_bookmark;
        }

        @Override
        public Fragment getFragment(final Context context) {
            if (playlistType == LocalItemType.PLAYLIST_LOCAL_ITEM) {
                return LocalPlaylistFragment.getInstance(playlistId, playlistName);

            } else { // playlistType == LocalItemType.PLAYLIST_REMOTE_ITEM
                return PlaylistFragment.getInstance(playlistServiceId, playlistUrl, playlistName);
            }
        }

        @Override
        protected void writeDataToJson(final JsonStringWriter writerSink) {
            writerSink.value(JSON_PLAYLIST_SERVICE_ID_KEY, playlistServiceId)
                    .value(JSON_PLAYLIST_URL_KEY, playlistUrl)
                    .value(JSON_PLAYLIST_NAME_KEY, playlistName)
                    .value(JSON_PLAYLIST_ID_KEY, playlistId)
                    .value(JSON_PLAYLIST_TYPE_KEY, playlistType.toString());
        }

        @Override
        protected void readDataFromJson(final JsonObject jsonObject) {
            playlistServiceId = jsonObject.getInt(JSON_PLAYLIST_SERVICE_ID_KEY, -1);
            playlistUrl = jsonObject.getString(JSON_PLAYLIST_URL_KEY, NO_URL);
            playlistName = jsonObject.getString(JSON_PLAYLIST_NAME_KEY, NO_NAME);
            playlistId = jsonObject.getInt(JSON_PLAYLIST_ID_KEY, -1);
            playlistType = LocalItemType.valueOf(
                    jsonObject.getString(JSON_PLAYLIST_TYPE_KEY,
                            LocalItemType.PLAYLIST_LOCAL_ITEM.toString())
            );
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof PlaylistTab)) {
                return false;
            }

            final PlaylistTab other = (PlaylistTab) obj;

            return super.equals(obj)
                    && playlistServiceId == other.playlistServiceId // Remote
                    && playlistId == other.playlistId // Local
                    && playlistUrl.equals(other.playlistUrl)
                    && playlistName.equals(other.playlistName)
                    && playlistType == other.playlistType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    getTabId(),
                    playlistServiceId,
                    playlistId,
                    playlistUrl,
                    playlistName,
                    playlistType
            );
        }

        public int getPlaylistServiceId() {
            return playlistServiceId;
        }

        public String getPlaylistUrl() {
            return playlistUrl;
        }

        public String getPlaylistName() {
            return playlistName;
        }

        public long getPlaylistId() {
            return playlistId;
        }

        public LocalItemType getPlaylistType() {
            return playlistType;
        }
    }
    public static class FeedGroupTab extends Tab {
        public static final int ID = 9;
        private static final String JSON_FEED_GROUP_ID_KEY = "feed_group_id";
        private static final String JSON_FEED_GROUP_NAME_KEY = "feed_group_name";
        private static final String JSON_FEED_GROUP_ICON_KEY = "feed_group_icon";
        private static final String JSON_FEED_GROUP_ICON_ID_KEY = "feed_group_icon_id";
        private Long feedGroupId;
        private String feedGroupName;
        private int iconId;
        // Live group icons are not serialized: a manual Home-tab override takes priority.
        private int observedGroupIconId = DEFAULT_ICON_ID;

        private FeedGroupTab() {
            this((long) -1, NO_NAME, R.drawable.ic_asterisk);
        }

        public FeedGroupTab(final Long feedGroupId, final String feedGroupName,
                            final int iconId) {
            this.feedGroupId = feedGroupId;
            this.feedGroupName = feedGroupName;
            this.iconId = iconId;

        }

        public FeedGroupTab(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.fragment_feed_title);
        }

        @DrawableRes
        @Override
        protected int getDefaultTabIconRes(final Context context) {
            final int defaultIcon = observedGroupIconId > 0 ? observedGroupIconId : iconId;
            return defaultIcon > 0 ? defaultIcon : R.drawable.ic_asterisk;
        }

        /**
         * Refresh the icon inherited from the source Channel Group without replacing an
         * explicitly customized Home-tab icon or altering the saved tab identity.
         *
         * @param drawableRes the updated Channel Group icon drawable, or an invalid value
         *                    to restore the last saved fallback icon
         * @return whether the visible icon may have changed
         */
        public boolean setObservedGroupIcon(@DrawableRes final int drawableRes) {
            // Resource identifiers may use the sign bit; compare only with our sentinels.
            final int next = drawableRes == 0 || drawableRes == DEFAULT_ICON_ID
                    ? DEFAULT_ICON_ID : drawableRes;
            if (observedGroupIconId == next) {
                return false;
            }
            observedGroupIconId = next;
            return getCustomIconId() == DEFAULT_ICON_ID;
        }

        @Override
        public FeedFragment getFragment(final Context context) {
            return FeedFragment.newInstance(feedGroupId, feedGroupName);
        }

        @Override
        protected void writeDataToJson(final JsonStringWriter writerSink) {
            final FeedGroupIcon stableIcon = FeedGroupIcon.fromDrawableResource(iconId);
            writerSink.value(JSON_FEED_GROUP_ID_KEY, feedGroupId)
                    .value(JSON_FEED_GROUP_NAME_KEY, feedGroupName);
            if (stableIcon == null) {
                writerSink.value(JSON_FEED_GROUP_ICON_KEY, iconId);
            } else {
                writerSink.value(JSON_FEED_GROUP_ICON_ID_KEY, stableIcon.getId());
            }
        }

        @Override
        protected void readDataFromJson(final JsonObject jsonObject) {
            feedGroupId = jsonObject.getLong(JSON_FEED_GROUP_ID_KEY, -1);
            feedGroupName = jsonObject.getString(JSON_FEED_GROUP_NAME_KEY, NO_NAME);
            final int stableIconId = jsonObject.getInt(
                    JSON_FEED_GROUP_ICON_ID_KEY, DEFAULT_ICON_ID);
            final FeedGroupIcon stableIcon = FeedGroupIcon.fromId(stableIconId);
            if (stableIcon != null) {
                iconId = stableIcon.getDrawableResource();
                return;
            }

            final int storedIconId = jsonObject.getInt(
                    JSON_FEED_GROUP_ICON_KEY, R.drawable.ic_asterisk);
            final FeedGroupIcon legacyIcon = FeedGroupIcon.fromDrawableResource(storedIconId);
            if (legacyIcon == null) {
                iconId = storedIconId > 0 ? storedIconId : R.drawable.ic_asterisk;
            } else {
                iconId = legacyIcon.getDrawableResource();
            }
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof FeedGroupTab)) {
                return false;
            }
            final FeedGroupTab other = (FeedGroupTab) obj;
            return super.equals(obj)
                    && feedGroupId.equals(other.feedGroupId)
                    && feedGroupName.equals(other.feedGroupName)
                    && iconId == other.iconId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getTabId(), feedGroupId, feedGroupName, iconId);
        }

        public Long getFeedGroupId() {
            return feedGroupId;
        }

        public String getFeedGroupName() {
            return feedGroupName;
        }

        public int getIconId() {
            return iconId;
        }
    }
}

