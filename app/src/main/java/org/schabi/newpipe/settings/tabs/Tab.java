package org.schabi.newpipe.settings.tabs;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonSink;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.BlankFragment;
import org.schabi.newpipe.fragments.list.channel.ChannelFragment;
import org.schabi.newpipe.fragments.list.kiosk.DefaultKioskFragment;
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment;
import org.schabi.newpipe.local.bookmark.BookmarkFragment;
import org.schabi.newpipe.local.feed.FeedFragment;
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment;
import org.schabi.newpipe.local.subscription.SubscriptionFragment;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.Objects;

public abstract class Tab {
    private static final String JSON_TAB_ID_KEY = "tab_id";

    Tab() { }

    Tab(@NonNull final JsonObject jsonObject) {
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
        for (Type available : Type.values()) {
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
            }
        }

        return type.getTab();
    }

    public abstract int getTabId();

    public abstract String getTabName(Context context);

    @DrawableRes
    public abstract int getTabIconRes(Context context);

    /**
     * Return a instance of the fragment that this tab represent.
     *
     * @param context Android app context
     * @return the fragment this tab represents
     */
    public abstract Fragment getFragment(Context context) throws ExtractionException;

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }

        return obj instanceof Tab && obj.getClass().equals(this.getClass())
                && ((Tab) obj).getTabId() == this.getTabId();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // JSON Handling
    //////////////////////////////////////////////////////////////////////////*/

    public void writeJsonOn(final JsonSink jsonSink) {
        jsonSink.object();

        jsonSink.value(JSON_TAB_ID_KEY, getTabId());
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
        BLANK(new BlankTab()),
        DEFAULT_KIOSK(new DefaultKioskTab()),
        SUBSCRIPTIONS(new SubscriptionsTab()),
        FEED(new FeedTab()),
        BOOKMARKS(new BookmarksTab()),
        HISTORY(new HistoryTab()),
        KIOSK(new KioskTab()),
        CHANNEL(new ChannelTab());

        private Tab tab;

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

    public static class BlankTab extends Tab {
        public static final int ID = 0;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return "NewPipe"; //context.getString(R.string.blank_page_summary);
        }

        @DrawableRes
        @Override
        public int getTabIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_blank_page);
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
        public int getTabIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_channel);
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
        public int getTabIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_rss);
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
        public int getTabIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_bookmark);
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
        public int getTabIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_history);
        }

        @Override
        public StatisticsPlaylistFragment getFragment(final Context context) {
            return new StatisticsPlaylistFragment();
        }
    }

    public static class KioskTab extends Tab {
        public static final int ID = 5;
        private static final String JSON_KIOSK_SERVICE_ID_KEY = "service_id";
        private static final String JSON_KIOSK_ID_KEY = "kiosk_id";
        private int kioskServiceId;
        private String kioskId;

        private KioskTab() {
            this(-1, "<no-id>");
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
        public int getTabIconRes(final Context context) {
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
            return super.equals(obj) && kioskServiceId == ((KioskTab) obj).kioskServiceId
                    && Objects.equals(kioskId, ((KioskTab) obj).kioskId);
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
            this(-1, "<no-url>", "<no-name>");
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
        public int getTabIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_channel);
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
            return super.equals(obj) && channelServiceId == ((ChannelTab) obj).channelServiceId
                    && Objects.equals(channelUrl, ((ChannelTab) obj).channelUrl)
                    && Objects.equals(channelName, ((ChannelTab) obj).channelName);
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
        public int getTabIconRes(final Context context) {
            return KioskTranslator.getKioskIcon(getDefaultKioskId(context), context);
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
            } catch (ExtractionException e) {
                ErrorActivity.reportError(context, e, null, null,
                        ErrorActivity.ErrorInfo.make(UserAction.REQUESTED_KIOSK, "none",
                                "Loading default kiosk from selected service", 0));
            }
            return kioskId;
        }
    }
}
