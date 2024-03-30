package org.schabi.newpipe.settings.tabs

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonStringWriter
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem.LocalItemType
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.fragments.BlankFragment
import org.schabi.newpipe.fragments.list.channel.ChannelFragment
import org.schabi.newpipe.fragments.list.kiosk.DefaultKioskFragment
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment
import org.schabi.newpipe.local.bookmark.BookmarkFragment
import org.schabi.newpipe.local.feed.FeedFragment
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment
import org.schabi.newpipe.local.subscription.SubscriptionFragment
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.ServiceHelper
import java.util.Objects

abstract class Tab {
    internal constructor()
    internal constructor(jsonObject: JsonObject) {
        readDataFromJson(jsonObject)
    }

    abstract fun getTabId(): Int
    abstract fun getTabName(context: Context): String?
    @DrawableRes
    abstract fun getTabIconRes(context: Context): Int

    /**
     * Return a instance of the fragment that this tab represent.
     *
     * @param context Android app context
     * @return the fragment this tab represents
     */
    @Throws(ExtractionException::class)
    abstract fun getFragment(context: Context?): Fragment
    public override fun equals(obj: Any?): Boolean {
        if (!(obj is Tab)) {
            return false
        }
        return getTabId() == obj.getTabId()
    }

    public override fun hashCode(): Int {
        return Objects.hashCode(getTabId())
    }

    /*//////////////////////////////////////////////////////////////////////////
    // JSON Handling
    ////////////////////////////////////////////////////////////////////////// */
    fun writeJsonOn(jsonSink: JsonStringWriter) {
        jsonSink.`object`()
        jsonSink.value(JSON_TAB_ID_KEY, getTabId())
        writeDataToJson(jsonSink)
        jsonSink.end()
    }

    protected open fun writeDataToJson(writerSink: JsonStringWriter) {
        // No-op
    }

    protected open fun readDataFromJson(jsonObject: JsonObject) {
        // No-op
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Implementations
    ////////////////////////////////////////////////////////////////////////// */
    enum class Type(private val tab: Tab) {
        BLANK(BlankTab()),
        DEFAULT_KIOSK(DefaultKioskTab()),
        SUBSCRIPTIONS(SubscriptionsTab()),
        FEED(FeedTab()),
        BOOKMARKS(BookmarksTab()),
        HISTORY(HistoryTab()),
        KIOSK(KioskTab()),
        CHANNEL(ChannelTab()),
        PLAYLIST(PlaylistTab());

        fun getTabId(): Int {
            return tab.getTabId()
        }

        fun getTab(): Tab {
            return tab
        }
    }

    class BlankTab() : Tab() {
        public override fun getTabId(): Int {
            return ID
        }

        public override fun getTabName(context: Context): String? {
            // TODO: find a better name for the blank tab (maybe "blank_tab") or replace it with
            //       context.getString(R.string.app_name);
            return "NewPipe" // context.getString(R.string.blank_page_summary);
        }

        @DrawableRes
        public override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_crop_portrait
        }

        public override fun getFragment(context: Context?): BlankFragment {
            return BlankFragment()
        }

        companion object {
            val ID: Int = 0
        }
    }

    class SubscriptionsTab() : Tab() {
        public override fun getTabId(): Int {
            return ID
        }

        public override fun getTabName(context: Context): String? {
            return context.getString(R.string.tab_subscriptions)
        }

        @DrawableRes
        public override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_tv
        }

        public override fun getFragment(context: Context?): SubscriptionFragment {
            return SubscriptionFragment()
        }

        companion object {
            val ID: Int = 1
        }
    }

    class FeedTab() : Tab() {
        public override fun getTabId(): Int {
            return ID
        }

        public override fun getTabName(context: Context): String? {
            return context.getString(R.string.fragment_feed_title)
        }

        @DrawableRes
        public override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_subscriptions
        }

        public override fun getFragment(context: Context?): FeedFragment {
            return FeedFragment()
        }

        companion object {
            val ID: Int = 2
        }
    }

    class BookmarksTab() : Tab() {
        public override fun getTabId(): Int {
            return ID
        }

        public override fun getTabName(context: Context): String? {
            return context.getString(R.string.tab_bookmarks)
        }

        @DrawableRes
        public override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_bookmark
        }

        public override fun getFragment(context: Context?): BookmarkFragment {
            return BookmarkFragment()
        }

        companion object {
            val ID: Int = 3
        }
    }

    class HistoryTab() : Tab() {
        public override fun getTabId(): Int {
            return ID
        }

        public override fun getTabName(context: Context): String? {
            return context.getString(R.string.title_activity_history)
        }

        @DrawableRes
        public override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_history
        }

        public override fun getFragment(context: Context?): StatisticsPlaylistFragment {
            return StatisticsPlaylistFragment()
        }

        companion object {
            val ID: Int = 4
        }
    }

    class KioskTab : Tab {
        private var kioskServiceId: Int = 0
        private var kioskId: String? = null

        constructor() : this(-1, NO_ID)
        constructor(kioskServiceId: Int, kioskId: String?) {
            this.kioskServiceId = kioskServiceId
            this.kioskId = kioskId
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        public override fun getTabId(): Int {
            return ID
        }

        public override fun getTabName(context: Context): String? {
            return KioskTranslator.getTranslatedKioskName(kioskId, context)
        }

        @DrawableRes
        public override fun getTabIconRes(context: Context): Int {
            val kioskIcon: Int = KioskTranslator.getKioskIcon(kioskId)
            if (kioskIcon <= 0) {
                throw IllegalStateException("Kiosk ID is not valid: \"" + kioskId + "\"")
            }
            return kioskIcon
        }

        @Throws(ExtractionException::class)
        public override fun getFragment(context: Context?): KioskFragment {
            return KioskFragment.Companion.getInstance(kioskServiceId, kioskId)
        }

        override fun writeDataToJson(writerSink: JsonStringWriter) {
            writerSink.value(JSON_KIOSK_SERVICE_ID_KEY, kioskServiceId)
                    .value(JSON_KIOSK_ID_KEY, kioskId)
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            kioskServiceId = jsonObject.getInt(JSON_KIOSK_SERVICE_ID_KEY, -1)
            kioskId = jsonObject.getString(JSON_KIOSK_ID_KEY, NO_ID)
        }

        public override fun equals(obj: Any?): Boolean {
            if (!(obj is KioskTab)) {
                return false
            }
            val other: KioskTab = obj
            return (super.equals(obj)
                    && (kioskServiceId == other.kioskServiceId
                    ) && (kioskId == other.kioskId))
        }

        public override fun hashCode(): Int {
            return Objects.hash(getTabId(), kioskServiceId, kioskId)
        }

        fun getKioskServiceId(): Int {
            return kioskServiceId
        }

        fun getKioskId(): String? {
            return kioskId
        }

        companion object {
            val ID: Int = 5
            private val JSON_KIOSK_SERVICE_ID_KEY: String = "service_id"
            private val JSON_KIOSK_ID_KEY: String = "kiosk_id"
        }
    }

    class ChannelTab : Tab {
        private var channelServiceId: Int = 0
        private var channelUrl: String? = null
        private var channelName: String? = null

        constructor() : this(-1, NO_URL, NO_NAME)
        constructor(channelServiceId: Int, channelUrl: String?,
                    channelName: String?) {
            this.channelServiceId = channelServiceId
            this.channelUrl = channelUrl
            this.channelName = channelName
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        public override fun getTabId(): Int {
            return ID
        }

        public override fun getTabName(context: Context): String? {
            return channelName
        }

        @DrawableRes
        public override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_tv
        }

        public override fun getFragment(context: Context?): ChannelFragment {
            return ChannelFragment.Companion.getInstance(channelServiceId, channelUrl, channelName)
        }

        override fun writeDataToJson(writerSink: JsonStringWriter) {
            writerSink.value(JSON_CHANNEL_SERVICE_ID_KEY, channelServiceId)
                    .value(JSON_CHANNEL_URL_KEY, channelUrl)
                    .value(JSON_CHANNEL_NAME_KEY, channelName)
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            channelServiceId = jsonObject.getInt(JSON_CHANNEL_SERVICE_ID_KEY, -1)
            channelUrl = jsonObject.getString(JSON_CHANNEL_URL_KEY, NO_URL)
            channelName = jsonObject.getString(JSON_CHANNEL_NAME_KEY, NO_NAME)
        }

        public override fun equals(obj: Any?): Boolean {
            if (!(obj is ChannelTab)) {
                return false
            }
            val other: ChannelTab = obj
            return (super.equals(obj)
                    && (channelServiceId == other.channelServiceId
                    ) && (channelUrl == other.channelName) && (channelName == other.channelName))
        }

        public override fun hashCode(): Int {
            return Objects.hash(getTabId(), channelServiceId, channelUrl, channelName)
        }

        fun getChannelServiceId(): Int {
            return channelServiceId
        }

        fun getChannelUrl(): String? {
            return channelUrl
        }

        fun getChannelName(): String? {
            return channelName
        }

        companion object {
            val ID: Int = 6
            private val JSON_CHANNEL_SERVICE_ID_KEY: String = "channel_service_id"
            private val JSON_CHANNEL_URL_KEY: String = "channel_url"
            private val JSON_CHANNEL_NAME_KEY: String = "channel_name"
        }
    }

    class DefaultKioskTab() : Tab() {
        public override fun getTabId(): Int {
            return ID
        }

        public override fun getTabName(context: Context): String? {
            return KioskTranslator.getTranslatedKioskName(getDefaultKioskId(context), context)
        }

        @DrawableRes
        public override fun getTabIconRes(context: Context): Int {
            return KioskTranslator.getKioskIcon(getDefaultKioskId(context))
        }

        public override fun getFragment(context: Context?): DefaultKioskFragment {
            return DefaultKioskFragment()
        }

        private fun getDefaultKioskId(context: Context): String {
            val kioskServiceId: Int = ServiceHelper.getSelectedServiceId(context)
            var kioskId: String = ""
            try {
                val service: StreamingService = NewPipe.getService(kioskServiceId)
                kioskId = service.getKioskList().getDefaultKioskId()
            } catch (e: ExtractionException) {
                showSnackbar(context, ErrorInfo(e,
                        UserAction.REQUESTED_KIOSK, "Loading default kiosk for selected service"))
            }
            return kioskId
        }

        companion object {
            val ID: Int = 7
        }
    }

    class PlaylistTab : Tab {
        private var playlistServiceId: Int = 0
        private var playlistUrl: String? = null
        private var playlistName: String? = null
        private var playlistId: Long = 0
        private var playlistType: LocalItemType? = null

        constructor() : this(-1, NO_NAME)
        constructor(playlistId: Long, playlistName: String?) {
            this.playlistName = playlistName
            this.playlistId = playlistId
            playlistType = LocalItemType.PLAYLIST_LOCAL_ITEM
            playlistServiceId = -1
            playlistUrl = NO_URL
        }

        constructor(playlistServiceId: Int, playlistUrl: String?,
                    playlistName: String?) {
            this.playlistServiceId = playlistServiceId
            this.playlistUrl = playlistUrl
            this.playlistName = playlistName
            playlistType = LocalItemType.PLAYLIST_REMOTE_ITEM
            playlistId = -1
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        public override fun getTabId(): Int {
            return ID
        }

        public override fun getTabName(context: Context): String? {
            return playlistName
        }

        @DrawableRes
        public override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_bookmark
        }

        public override fun getFragment(context: Context?): Fragment {
            if (playlistType == LocalItemType.PLAYLIST_LOCAL_ITEM) {
                return LocalPlaylistFragment.Companion.getInstance(playlistId, playlistName)
            } else { // playlistType == LocalItemType.PLAYLIST_REMOTE_ITEM
                return PlaylistFragment.Companion.getInstance(playlistServiceId, playlistUrl, playlistName)
            }
        }

        override fun writeDataToJson(writerSink: JsonStringWriter) {
            writerSink.value(JSON_PLAYLIST_SERVICE_ID_KEY, playlistServiceId)
                    .value(JSON_PLAYLIST_URL_KEY, playlistUrl)
                    .value(JSON_PLAYLIST_NAME_KEY, playlistName)
                    .value(JSON_PLAYLIST_ID_KEY, playlistId)
                    .value(JSON_PLAYLIST_TYPE_KEY, playlistType.toString())
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            playlistServiceId = jsonObject.getInt(JSON_PLAYLIST_SERVICE_ID_KEY, -1)
            playlistUrl = jsonObject.getString(JSON_PLAYLIST_URL_KEY, NO_URL)
            playlistName = jsonObject.getString(JSON_PLAYLIST_NAME_KEY, NO_NAME)
            playlistId = jsonObject.getInt(JSON_PLAYLIST_ID_KEY, -1).toLong()
            playlistType = LocalItemType.valueOf(
                    jsonObject.getString(JSON_PLAYLIST_TYPE_KEY,
                            LocalItemType.PLAYLIST_LOCAL_ITEM.toString())
            )
        }

        public override fun equals(obj: Any?): Boolean {
            if (!(obj is PlaylistTab)) {
                return false
            }
            val other: PlaylistTab = obj
            return (super.equals(obj)
                    && (playlistServiceId == other.playlistServiceId // Remote
                    ) && (playlistId == other.playlistId // Local
                    ) && (playlistUrl == other.playlistUrl) && (playlistName == other.playlistName) && (playlistType == other.playlistType))
        }

        public override fun hashCode(): Int {
            return Objects.hash(
                    getTabId(),
                    playlistServiceId,
                    playlistId,
                    playlistUrl,
                    playlistName,
                    playlistType
            )
        }

        fun getPlaylistServiceId(): Int {
            return playlistServiceId
        }

        fun getPlaylistUrl(): String? {
            return playlistUrl
        }

        fun getPlaylistName(): String? {
            return playlistName
        }

        fun getPlaylistId(): Long {
            return playlistId
        }

        fun getPlaylistType(): LocalItemType? {
            return playlistType
        }

        companion object {
            val ID: Int = 8
            private val JSON_PLAYLIST_SERVICE_ID_KEY: String = "playlist_service_id"
            private val JSON_PLAYLIST_URL_KEY: String = "playlist_url"
            private val JSON_PLAYLIST_NAME_KEY: String = "playlist_name"
            private val JSON_PLAYLIST_ID_KEY: String = "playlist_id"
            private val JSON_PLAYLIST_TYPE_KEY: String = "playlist_type"
        }
    }

    companion object {
        private val JSON_TAB_ID_KEY: String = "tab_id"
        private val NO_NAME: String = "<no-name>"
        private val NO_ID: String = "<no-id>"
        private val NO_URL: String = "<no-url>"

        /*//////////////////////////////////////////////////////////////////////////
    // Tab Handling
    ////////////////////////////////////////////////////////////////////////// */
        @JvmStatic
        fun from(jsonObject: JsonObject): Tab? {
            val tabId: Int = jsonObject.getInt(JSON_TAB_ID_KEY, -1)
            if (tabId == -1) {
                return null
            }
            return from(tabId, jsonObject)
        }

        fun from(tabId: Int): Tab? {
            return from(tabId, null)
        }

        fun typeFrom(tabId: Int): Type? {
            for (available: Type in Type.entries) {
                if (available.getTabId() == tabId) {
                    return available
                }
            }
            return null
        }

        private fun from(tabId: Int, jsonObject: JsonObject?): Tab? {
            val type: Type? = typeFrom(tabId)
            if (type == null) {
                return null
            }
            if (jsonObject != null) {
                when (type) {
                    Type.KIOSK -> return KioskTab(jsonObject)
                    Type.CHANNEL -> return ChannelTab(jsonObject)
                    Type.PLAYLIST -> return PlaylistTab(jsonObject)
                }
            }
            return type.getTab()
        }
    }
}
