package org.schabi.newpipe.settings.drawer_items;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonSink;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.BlankFragment;
import org.schabi.newpipe.fragments.list.channel.ChannelFragment;
import org.schabi.newpipe.fragments.list.kiosk.DefaultKioskFragment;
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment;
import org.schabi.newpipe.local.bookmark.BookmarkFragment;
import org.schabi.newpipe.local.feed.FeedFragment;
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.local.subscription.SubscriptionFragment;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.Objects;

public abstract class DrawerItem {

    // this HAS TO be the same as in MainActivity.java
    static final int ITEM_ID_BLANK = -1;
    static final int ITEM_ID_SETTINGS = -2;
    static final int ITEM_ID_ABOUT = -3;
    static final int ITEM_ID_BOOKMARKS = -4;
    static final int ITEM_ID_FEED = -5;
    static final int ITEM_ID_SUBSCRIPTIONS = -6;
    static final int ITEM_ID_DOWNLOADS = -7;
    static final int ITEM_ID_HISTORY = -8;
    static final int ITEM_ID_DEFAULT_KIOSK = -9;
    static final int ITEM_ID_KIOSK = -10;
    static final int ITEM_ID_CHANNEL = -11;
    static final int ITEM_ID_PLAYLIST = -12;

    private static final String JSON_DRAWER_ITEM_ID_KEY = "drawer_item_id";

    DrawerItem() {   }

    DrawerItem(@NonNull final JsonObject jsonObject) {
        readDataFromJson(jsonObject);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // DrawerItem Handling
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    public static DrawerItem from(@NonNull final JsonObject jsonObject) {
        final int drawerItemId = jsonObject.getInt(DrawerItem.JSON_DRAWER_ITEM_ID_KEY, -1);

        return from(drawerItemId, jsonObject);
    }

    @Nullable
    public static DrawerItem from(final int drawerItemId) {
        return from(drawerItemId, null);
    }

    @Nullable
    public static DrawerItem.Type typeFrom(final int drawerItemId) {
        for (final DrawerItem.Type available : DrawerItem.Type.values()) {
            if (available.getDrawerItemId() == drawerItemId) {
                return available;
            }
        }
        return null;
    }

    @Nullable
    private static DrawerItem from(final int drawerItemId, @Nullable final JsonObject jsonObject) {
        final DrawerItem.Type type = typeFrom(drawerItemId);

        if (type == null) {
            return null;
        }

        if (jsonObject != null) {
            switch (type) {
                case KIOSK:
                    return new KioskDrawerItem(jsonObject);
                case CHANNEL:
                    return new ChannelDrawerItem(jsonObject);
                case PLAYLIST:
                    return new PlaylistDrawerItem(jsonObject);
            }
        }

        return type.getDrawerItem();
    }

    public abstract int getDrawerItemId();

    public abstract String getDrawerItemName(Context context);

    @DrawableRes
    public abstract int getDrawerItemIconRes(Context context);

    /**
     * Return a instance of the fragment that this DrawerItem represent.
     *
     * @param context Android app context
     * @return the fragment this DrawerItem represents
     */
    public abstract Fragment getFragment(Context context) throws ExtractionException;

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }

        return obj instanceof DrawerItem && obj.getClass().equals(this.getClass())
                && ((DrawerItem) obj).getDrawerItemId() == this.getDrawerItemId();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // JSON Handling
    //////////////////////////////////////////////////////////////////////////*/

    public void writeJsonOn(final JsonSink jsonSink) {
        jsonSink.object();

        jsonSink.value(JSON_DRAWER_ITEM_ID_KEY, getDrawerItemId());
        writeDataToJson(jsonSink);

        jsonSink.end();
    }

    protected void writeDataToJson(final JsonSink writerSink) {
        // No-op
    }

    protected void readDataFromJson(final JsonObject jsonObject) {
        // No-op
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Implementations
    //////////////////////////////////////////////////////////////////////////*/

    public enum Type {
        BLANK(new BlankDrawerItem()),
        DOWNLOADS(new DownloadDrawerItem()),
        DEFAULT_KIOSK(new DefaultKioskDrawerItem()),
        SUBSCRIPTIONS(new SubscriptionsDrawerItem()),
        FEED(new FeedDrawerItem()),
        BOOKMARKS(new BookmarksDrawerItem()),
        HISTORY(new HistoryDrawerItem()),
        KIOSK(new KioskDrawerItem()),
        CHANNEL(new ChannelDrawerItem()),
        PLAYLIST(new PlaylistDrawerItem());

        private DrawerItem drawerItem;

        Type(final DrawerItem drawerItem) {
            this.drawerItem = drawerItem;
        }

        public int getDrawerItemId() {
            return drawerItem.getDrawerItemId();
        }

        public DrawerItem getDrawerItem() {
            return drawerItem;
        }
    }

    public static class BlankDrawerItem extends DrawerItem {
        public static final int ID = ITEM_ID_BLANK;

        @Override
        public int getDrawerItemId() {
            return ID;
        }

        @Override
        public String getDrawerItemName(final Context context) {
            return "NewPipe"; //context.getString(R.string.blank_page_summary);
        }

        @DrawableRes
        @Override
        public int getDrawerItemIconRes(final Context context) {
            return R.drawable.ic_crop_portrait;
        }

        @Override
        public BlankFragment getFragment(final Context context) {
            return new BlankFragment();
        }
    }

    public static class SubscriptionsDrawerItem extends DrawerItem {
        public static final int ID = ITEM_ID_SUBSCRIPTIONS;

        @Override
        public int getDrawerItemId() {
            return ID;
        }

        @Override
        public String getDrawerItemName(final Context context) {
            return context.getString(R.string.tab_subscriptions);
        }

        @DrawableRes
        @Override
        public int getDrawerItemIconRes(final Context context) {
            return R.drawable.ic_tv;
        }

        @Override
        public SubscriptionFragment getFragment(final Context context) {
            return new SubscriptionFragment();
        }
    }

    public static class DownloadDrawerItem extends DrawerItem {
        public static final int ID = ITEM_ID_DOWNLOADS;

        @Override
        public int getDrawerItemId() {
            return ID;
        }

        @Override
        public String getDrawerItemName(final Context context) {
            return context.getString(R.string.download);
        }

        @Override
        public int getDrawerItemIconRes(final Context context) {
            return R.drawable.ic_file_download;
        }

        @Override
        public Fragment getFragment(final Context context) {
            return new FeedFragment();
        }
    }

    public static class FeedDrawerItem extends DrawerItem {
        public static final int ID = ITEM_ID_FEED;

        @Override
        public int getDrawerItemId() {
            return ID;
        }

        @Override
        public String getDrawerItemName(final Context context) {
            return context.getString(R.string.fragment_feed_title);
        }

        @DrawableRes
        @Override
        public int getDrawerItemIconRes(final Context context) {
            return R.drawable.ic_rss_feed;
        }

        @Override
        public FeedFragment getFragment(final Context context) {
            return new FeedFragment();
        }
    }

    public static class BookmarksDrawerItem extends DrawerItem {
        public static final int ID = ITEM_ID_BOOKMARKS;

        @Override
        public int getDrawerItemId() {
            return ID;
        }

        @Override
        public String getDrawerItemName(final Context context) {
            return context.getString(R.string.tab_bookmarks);
        }

        @DrawableRes
        @Override
        public int getDrawerItemIconRes(final Context context) {
            return R.drawable.ic_bookmark;
        }

        @Override
        public BookmarkFragment getFragment(final Context context) {
            return new BookmarkFragment();
        }
    }

    public static class HistoryDrawerItem extends DrawerItem {
        public static final int ID = ITEM_ID_HISTORY;

        @Override
        public int getDrawerItemId() {
            return ID;
        }

        @Override
        public String getDrawerItemName(final Context context) {
            return context.getString(R.string.title_activity_history);
        }

        @DrawableRes
        @Override
        public int getDrawerItemIconRes(final Context context) {
            return R.drawable.ic_history;
        }

        @Override
        public StatisticsPlaylistFragment getFragment(final Context context) {
            return new StatisticsPlaylistFragment();
        }
    }

    public static class KioskDrawerItem extends DrawerItem {
        public static final int ID = ITEM_ID_KIOSK;
        private static final String JSON_KIOSK_SERVICE_ID_KEY = "service_id";
        private static final String JSON_KIOSK_ID_KEY = "kiosk_id";
        private int kioskServiceId;
        private String kioskId;

        private KioskDrawerItem() {
            this(-1, "<no-id>");
        }

        public KioskDrawerItem(final int kioskServiceId, final String kioskId) {
            this.kioskServiceId = kioskServiceId;
            this.kioskId = kioskId;
        }

        public KioskDrawerItem(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getDrawerItemId() {
            return ID;
        }

        @Override
        public String getDrawerItemName(final Context context) {
            return KioskTranslator.getTranslatedKioskName(kioskId, context);
        }

        @DrawableRes
        @Override
        public int getDrawerItemIconRes(final Context context) {
            final int kioskIcon = KioskTranslator.getKioskIcon(kioskId, context);

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
        protected void writeDataToJson(final JsonSink writerSink) {
            writerSink.value(JSON_KIOSK_SERVICE_ID_KEY, kioskServiceId)
                    .value(JSON_KIOSK_ID_KEY, kioskId);
        }

        @Override
        protected void readDataFromJson(final JsonObject jsonObject) {
            kioskServiceId = jsonObject.getInt(JSON_KIOSK_SERVICE_ID_KEY, -1);
            kioskId = jsonObject.getString(JSON_KIOSK_ID_KEY, "<no-id>");
        }

        @Override
        public boolean equals(final Object obj) {
            return super.equals(obj) && kioskServiceId == ((KioskDrawerItem) obj).kioskServiceId
                    && Objects.equals(kioskId, ((KioskDrawerItem) obj).kioskId);
        }

        public int getKioskServiceId() {
            return kioskServiceId;
        }

        public String getKioskId() {
            return kioskId;
        }
    }

    public static class ChannelDrawerItem extends DrawerItem {
        public static final int ID = ITEM_ID_CHANNEL;
        private static final String JSON_CHANNEL_SERVICE_ID_KEY = "channel_service_id";
        private static final String JSON_CHANNEL_URL_KEY = "channel_url";
        private static final String JSON_CHANNEL_NAME_KEY = "channel_name";
        private int channelServiceId;
        private String channelUrl;
        private String channelName;

        private ChannelDrawerItem() {
            this(-1, "<no-url>", "<no-name>");
        }

        public ChannelDrawerItem(final int channelServiceId, final String channelUrl,
                                 final String channelName) {
            this.channelServiceId = channelServiceId;
            this.channelUrl = channelUrl;
            this.channelName = channelName;
        }

        public ChannelDrawerItem(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getDrawerItemId() {
            return ID;
        }

        @Override
        public String getDrawerItemName(final Context context) {
            return channelName;
        }

        @DrawableRes
        @Override
        public int getDrawerItemIconRes(final Context context) {
            return R.drawable.ic_tv;
        }

        @Override
        public ChannelFragment getFragment(final Context context) {
            return ChannelFragment.getInstance(channelServiceId, channelUrl, channelName);
        }

        @Override
        protected void writeDataToJson(final JsonSink writerSink) {
            writerSink.value(JSON_CHANNEL_SERVICE_ID_KEY, channelServiceId)
                    .value(JSON_CHANNEL_URL_KEY, channelUrl)
                    .value(JSON_CHANNEL_NAME_KEY, channelName);
        }

        @Override
        protected void readDataFromJson(final JsonObject jsonObject) {
            channelServiceId = jsonObject.getInt(JSON_CHANNEL_SERVICE_ID_KEY, -1);
            channelUrl = jsonObject.getString(JSON_CHANNEL_URL_KEY, "<no-url>");
            channelName = jsonObject.getString(JSON_CHANNEL_NAME_KEY, "<no-name>");
        }

        @Override
        public boolean equals(final Object obj) {
            return super.equals(obj)
                    && channelServiceId == ((ChannelDrawerItem) obj).channelServiceId
                    && Objects.equals(channelUrl, ((ChannelDrawerItem) obj).channelUrl)
                    && Objects.equals(channelName, ((ChannelDrawerItem) obj).channelName);
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

    public static class DefaultKioskDrawerItem extends DrawerItem {
        public static final int ID = ITEM_ID_DEFAULT_KIOSK;

        @Override
        public int getDrawerItemId() {
            return ID;
        }

        @Override
        public String getDrawerItemName(final Context context) {
            return KioskTranslator.getTranslatedKioskName(getDefaultKioskId(context), context);
        }

        @DrawableRes
        @Override
        public int getDrawerItemIconRes(final Context context) {
            return KioskTranslator.getKioskIcon(getDefaultKioskId(context), context);
        }

        @Override
        public DefaultKioskFragment getFragment(final Context context) {
            return new DefaultKioskFragment();
        }

        public String getDefaultKioskId(final Context context) {
            final int kioskServiceId = ServiceHelper.getSelectedServiceId(context);

            String kioskId = "";
            try {
                final StreamingService service = NewPipe.getService(kioskServiceId);
                kioskId = service.getKioskList().getDefaultKioskId();
            } catch (final ExtractionException e) {
                ErrorActivity.reportErrorInSnackbar(context, new ErrorInfo(e,
                        UserAction.REQUESTED_KIOSK, "Loading default kiosk for selected service"));
            }
            return kioskId;
        }
    }

    public static class PlaylistDrawerItem extends DrawerItem {
        public static final int ID = ITEM_ID_PLAYLIST;
        private static final String JSON_PLAYLIST_SERVICE_ID_KEY = "playlist_service_id";
        private static final String JSON_PLAYLIST_URL_KEY = "playlist_url";
        private static final String JSON_PLAYLIST_NAME_KEY = "playlist_name";
        private static final String JSON_PLAYLIST_ID_KEY = "playlist_id";
        private static final String JSON_PLAYLIST_TYPE_KEY = "playlist_type";
        private int playlistServiceId;
        private String playlistUrl;
        private String playlistName;
        private long playlistId;
        private LocalItem.LocalItemType playlistType;

        private PlaylistDrawerItem() {
            this(-1, "<no-name>");
        }

        public PlaylistDrawerItem(final long playlistId, final String playlistName) {
            this.playlistName = playlistName;
            this.playlistId = playlistId;
            this.playlistType = LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM;
            this.playlistServiceId = -1;
            this.playlistUrl = "<no-url>";
        }

        public PlaylistDrawerItem(final int playlistServiceId, final String playlistUrl,
                                  final String playlistName) {
            this.playlistServiceId = playlistServiceId;
            this.playlistUrl = playlistUrl;
            this.playlistName = playlistName;
            this.playlistType = LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM;
            this.playlistId = -1;
        }

        public PlaylistDrawerItem(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getDrawerItemId() {
            return ID;
        }

        @Override
        public String getDrawerItemName(final Context context) {
            return playlistName;
        }

        @DrawableRes
        @Override
        public int getDrawerItemIconRes(final Context context) {
            return R.drawable.ic_bookmark;
        }

        @Override
        public Fragment getFragment(final Context context) {
            if (playlistType == LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM) {
                return LocalPlaylistFragment.getInstance(playlistId, playlistName);

            } else { // playlistType == LocalItemType.PLAYLIST_REMOTE_ITEM
                return PlaylistFragment.getInstance(playlistServiceId, playlistUrl, playlistName);
            }
        }

        @Override
        protected void writeDataToJson(final JsonSink writerSink) {
            writerSink.value(JSON_PLAYLIST_SERVICE_ID_KEY, playlistServiceId)
                    .value(JSON_PLAYLIST_URL_KEY, playlistUrl)
                    .value(JSON_PLAYLIST_NAME_KEY, playlistName)
                    .value(JSON_PLAYLIST_ID_KEY, playlistId)
                    .value(JSON_PLAYLIST_TYPE_KEY, playlistType.toString());
        }

        @Override
        protected void readDataFromJson(final JsonObject jsonObject) {
            playlistServiceId = jsonObject.getInt(JSON_PLAYLIST_SERVICE_ID_KEY, -1);
            playlistUrl = jsonObject.getString(JSON_PLAYLIST_URL_KEY, "<no-url>");
            playlistName = jsonObject.getString(JSON_PLAYLIST_NAME_KEY, "<no-name>");
            playlistId = jsonObject.getInt(JSON_PLAYLIST_ID_KEY, -1);
            playlistType = LocalItem.LocalItemType.valueOf(
                    jsonObject.getString(JSON_PLAYLIST_TYPE_KEY,
                            LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM.toString())
            );
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(super.equals(obj)
                    && Objects.equals(playlistType, ((PlaylistDrawerItem) obj).playlistType)
                    && Objects.equals(playlistName, ((PlaylistDrawerItem) obj).playlistName))) {
                return false; // base objects are different
            }

            return (playlistId == ((PlaylistDrawerItem) obj).playlistId)                // local
                    || (playlistServiceId == ((PlaylistDrawerItem) obj).playlistServiceId // remote
                    && Objects.equals(playlistUrl, ((PlaylistDrawerItem) obj).playlistUrl));
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

        public LocalItem.LocalItemType getPlaylistType() {
            return playlistType;
        }
    }
}
