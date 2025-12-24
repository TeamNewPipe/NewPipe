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

    abstract val tabId: Int

    abstract fun getTabName(context: Context): String

    @DrawableRes
    abstract fun getTabIconRes(context: Context): Int

    /**
     * Return a instance of the fragment that this tab represent.
     *
     * @param context Android app context
     * @return the fragment this tab represents
     */
    @Throws(ExtractionException::class)
    abstract fun getFragment(context: Context): Fragment?

    override fun equals(other: Any?): Boolean {
        if (other !is Tab) {
            return false
        }
        return this.tabId == other.tabId
    }

    override fun hashCode(): Int {
        return Objects.hashCode(this.tabId)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // JSON Handling
    ////////////////////////////////////////////////////////////////////////// */
    fun writeJsonOn(jsonSink: JsonStringWriter) {
        jsonSink.`object`()

        jsonSink.value(JSON_TAB_ID_KEY, this.tabId)
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
    enum class Type(val tab: Tab) {
        BLANK(BlankTab()),
        DEFAULT_KIOSK(DefaultKioskTab()),
        SUBSCRIPTIONS(SubscriptionsTab()),
        FEED(FeedTab()),
        BOOKMARKS(BookmarksTab()),
        HISTORY(HistoryTab()),
        KIOSK(KioskTab()),
        CHANNEL(ChannelTab()),
        PLAYLIST(PlaylistTab()),
        FEEDGROUP(FeedGroupTab());

        val tabId: Int
            get() = tab.tabId
    }

    class BlankTab : Tab() {
        override val tabId: Int = 0

        override fun getTabName(context: Context): String {
            // TODO: find a better name for the blank tab (maybe "blank_tab") or replace it with
            //       context.getString(R.string.app_name);
            return "NewPipe" // context.getString(R.string.blank_page_summary);
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_crop_portrait
        }

        override fun getFragment(context: Context): BlankFragment {
            return BlankFragment()
        }
    }

    class SubscriptionsTab : Tab() {
        override val tabId: Int = 1

        override fun getTabName(context: Context): String {
            return context.getString(R.string.tab_subscriptions)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_tv
        }

        override fun getFragment(context: Context): SubscriptionFragment {
            return SubscriptionFragment()
        }
    }

    class FeedTab : Tab() {
        override val tabId: Int = 2

        override fun getTabName(context: Context): String {
            return context.getString(R.string.fragment_feed_title)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_subscriptions
        }

        override fun getFragment(context: Context): FeedFragment {
            return FeedFragment()
        }
    }

    class BookmarksTab : Tab() {
        override val tabId: Int = 3

        override fun getTabName(context: Context): String {
            return context.getString(R.string.tab_bookmarks)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_bookmark
        }

        override fun getFragment(context: Context): BookmarkFragment {
            return BookmarkFragment()
        }
    }

    class HistoryTab : Tab() {
        override val tabId: Int = 4

        override fun getTabName(context: Context): String {
            return context.getString(R.string.title_activity_history)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_history
        }

        override fun getFragment(context: Context): StatisticsPlaylistFragment {
            return StatisticsPlaylistFragment()
        }
    }

    class KioskTab : Tab {
        override val tabId: Int = 5
        var kioskServiceId: Int = 0
            private set
        private var kioskId: String? = null

        constructor() : this(-1, NO_ID)

        constructor(kioskServiceId: Int, kioskId: String) {
            this.kioskServiceId = kioskServiceId
            this.kioskId = kioskId
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        override fun getTabName(context: Context): String {
            return KioskTranslator.getTranslatedKioskName(kioskId, context)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            val kioskIcon = KioskTranslator.getKioskIcon(kioskId)

            check(kioskIcon > 0) { "Kiosk ID is not valid: \"$kioskId\"" }

            return kioskIcon
        }

        @Throws(ExtractionException::class)
        override fun getFragment(context: Context): KioskFragment {
            return KioskFragment.getInstance(kioskServiceId, kioskId)
        }

        override fun writeDataToJson(writerSink: JsonStringWriter) {
            writerSink.value(JSON_KIOSK_SERVICE_ID_KEY, kioskServiceId)
                .value(JSON_KIOSK_ID_KEY, kioskId)
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            kioskServiceId = jsonObject.getInt(JSON_KIOSK_SERVICE_ID_KEY, -1)
            kioskId = jsonObject.getString(JSON_KIOSK_ID_KEY, NO_ID)
        }

        override fun equals(other: Any?): Boolean {
            if (other !is KioskTab) {
                return false
            }
            val other = other
            return super.equals(other) &&
                    kioskServiceId == other.kioskServiceId && kioskId == other.kioskId
        }

        override fun hashCode(): Int {
            return Objects.hash(tabId, kioskServiceId, kioskId)
        }

        fun getKioskId(): String {
            return kioskId!!
        }

        companion object {
            private const val JSON_KIOSK_SERVICE_ID_KEY = "service_id"
            private const val JSON_KIOSK_ID_KEY = "kiosk_id"
        }
    }

    class ChannelTab : Tab {
        override val tabId: Int = 6
        var channelServiceId: Int = 0
            private set
        private var channelUrl: String? = null
        private var channelName: String? = null

        constructor() : this(-1, NO_URL, NO_NAME)

        constructor(
            channelServiceId: Int, channelUrl: String,
            channelName: String
        ) {
            this.channelServiceId = channelServiceId
            this.channelUrl = channelUrl
            this.channelName = channelName
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        override fun getTabName(context: Context): String {
            return channelName!!
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_tv
        }

        override fun getFragment(context: Context): ChannelFragment {
            return ChannelFragment.getInstance(channelServiceId, channelUrl, channelName)
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

        override fun equals(other: Any?): Boolean {
            if (other !is ChannelTab) {
                return false
            }
            val other = other
            return super.equals(other) &&
                    channelServiceId == other.channelServiceId && channelUrl == other.channelUrl &&
                    channelName == other.channelName
        }

        override fun hashCode(): Int {
            return Objects.hash(tabId, channelServiceId, channelUrl, channelName)
        }

        fun getChannelUrl(): String {
            return channelUrl!!
        }

        fun getChannelName(): String {
            return channelName!!
        }

        companion object {
            private const val JSON_CHANNEL_SERVICE_ID_KEY = "channel_service_id"
            private const val JSON_CHANNEL_URL_KEY = "channel_url"
            private const val JSON_CHANNEL_NAME_KEY = "channel_name"
        }
    }

    class DefaultKioskTab : Tab() {
        override val tabId: Int = 7

        override fun getTabName(context: Context): String {
            return KioskTranslator.getTranslatedKioskName(getDefaultKioskId(context), context)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return KioskTranslator.getKioskIcon(getDefaultKioskId(context))
        }

        override fun getFragment(context: Context): DefaultKioskFragment {
            return DefaultKioskFragment()
        }

        private fun getDefaultKioskId(context: Context): String? {
            val kioskServiceId = ServiceHelper.getSelectedServiceId(context)

            var kioskId: String? = ""
            try {
                val service = NewPipe.getService(kioskServiceId)
                kioskId = service.getKioskList().getDefaultKioskId()
            } catch (e: ExtractionException) {
                showSnackbar(
                    context,
                        ErrorInfo(
                        e,
                        UserAction.REQUESTED_KIOSK, "Loading default kiosk for selected service"
                    )
                )
            }
            return kioskId
        }
    }

    class PlaylistTab : Tab {
        override val tabId: Int = 8
        var playlistServiceId: Int = 0
            private set
        private var playlistUrl: String? = null
        private var playlistName: String? = null
        var playlistId: Long = 0
            private set
        private var playlistType: LocalItemType? = null

        constructor() : this(-1, NO_NAME)

        constructor(playlistId: Long, playlistName: String) {
            this.playlistName = playlistName
            this.playlistId = playlistId
            this.playlistType = LocalItemType.PLAYLIST_LOCAL_ITEM
            this.playlistServiceId = -1
            this.playlistUrl = NO_URL
        }

        constructor(
            playlistServiceId: Int, playlistUrl: String,
            playlistName: String
        ) {
            this.playlistServiceId = playlistServiceId
            this.playlistUrl = playlistUrl
            this.playlistName = playlistName
            this.playlistType = LocalItemType.PLAYLIST_REMOTE_ITEM
            this.playlistId = -1
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        override fun getTabName(context: Context): String {
            return playlistName!!
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return R.drawable.ic_bookmark
        }

        override fun getFragment(context: Context): Fragment {
            if (playlistType == LocalItemType.PLAYLIST_LOCAL_ITEM) {
                return LocalPlaylistFragment.getInstance(playlistId, playlistName)
            } else { // playlistType == LocalItemType.PLAYLIST_REMOTE_ITEM
                return PlaylistFragment.getInstance(playlistServiceId, playlistUrl, playlistName)
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
                jsonObject.getString(
                    JSON_PLAYLIST_TYPE_KEY,
                    LocalItemType.PLAYLIST_LOCAL_ITEM.toString()
                )
            )
        }

        override fun equals(other: Any?): Boolean {
            if (other !is PlaylistTab) {
                return false
            }

            return super.equals(other) &&
                    playlistServiceId == other.playlistServiceId && // Remote
                    playlistId == other.playlistId && // Local
                    playlistUrl == other.playlistUrl &&
                    playlistName == other.playlistName &&
                    playlistType == other.playlistType
        }

        override fun hashCode(): Int {
            return Objects.hash(
                tabId,
                playlistServiceId,
                playlistId,
                playlistUrl,
                playlistName,
                playlistType
            )
        }

        fun getPlaylistUrl(): String {
            return playlistUrl!!
        }

        fun getPlaylistName(): String {
            return playlistName!!
        }

        fun getPlaylistType(): LocalItemType {
            return playlistType!!
        }

        companion object {
            private const val JSON_PLAYLIST_SERVICE_ID_KEY = "playlist_service_id"
            private const val JSON_PLAYLIST_URL_KEY = "playlist_url"
            private const val JSON_PLAYLIST_NAME_KEY = "playlist_name"
            private const val JSON_PLAYLIST_ID_KEY = "playlist_id"
            private const val JSON_PLAYLIST_TYPE_KEY = "playlist_type"
        }
    }

    class FeedGroupTab : Tab {
        override val tabId: Int = 9
        private var feedGroupId: Long? = null
        private var feedGroupName: String? = null
        var iconId: Int = 0
            private set

        constructor() : this(-1L, NO_NAME, R.drawable.ic_asterisk)

        constructor(
            feedGroupId: Long, feedGroupName: String,
            iconId: Int
        ) {
            this.feedGroupId = feedGroupId
            this.feedGroupName = feedGroupName
            this.iconId = iconId
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        override fun getTabName(context: Context): String {
            return context.getString(R.string.fragment_feed_title)
        }

        @DrawableRes
        override fun getTabIconRes(context: Context): Int {
            return this.iconId
        }

        override fun getFragment(context: Context): FeedFragment {
            return FeedFragment.newInstance(feedGroupId!!, feedGroupName)
        }

        override fun writeDataToJson(writerSink: JsonStringWriter) {
            writerSink.value(JSON_FEED_GROUP_ID_KEY, feedGroupId)
                .value(JSON_FEED_GROUP_NAME_KEY, feedGroupName)
                .value(JSON_FEED_GROUP_ICON_KEY, iconId)
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            feedGroupId = jsonObject.getLong(JSON_FEED_GROUP_ID_KEY, -1)
            feedGroupName = jsonObject.getString(JSON_FEED_GROUP_NAME_KEY, NO_NAME)
            iconId = jsonObject.getInt(JSON_FEED_GROUP_ICON_KEY, R.drawable.ic_asterisk)
        }

        override fun equals(other: Any?): Boolean {
            if (other !is FeedGroupTab) {
                return false
            }

            return super.equals(other) &&
                    feedGroupId == other.feedGroupId &&
                    feedGroupName == other.feedGroupName &&
                    iconId == other.iconId
        }

        override fun hashCode(): Int {
            return Objects.hash(tabId, feedGroupId, feedGroupName, iconId)
        }

        fun getFeedGroupId(): Long {
            return feedGroupId!!
        }

        fun getFeedGroupName(): String {
            return feedGroupName!!
        }

        companion object {
            private const val JSON_FEED_GROUP_ID_KEY = "feed_group_id"
            private const val JSON_FEED_GROUP_NAME_KEY = "feed_group_name"
            private const val JSON_FEED_GROUP_ICON_KEY = "feed_group_icon"
        }
    }

    companion object {
        private const val JSON_TAB_ID_KEY = "tab_id"

        private const val NO_NAME = "<no-name>"
        private const val NO_ID = "<no-id>"
        private const val NO_URL = "<no-url>"

        /*//////////////////////////////////////////////////////////////////////////
    // Tab Handling
    ////////////////////////////////////////////////////////////////////////// */
        @JvmStatic
        fun from(jsonObject: JsonObject): Tab? {
            val tabId = jsonObject.getInt(JSON_TAB_ID_KEY, -1)

            if (tabId == -1) {
                return null
            }

            return from(tabId, jsonObject)
        }

        fun from(tabId: Int): Tab? {
            return from(tabId, null)
        }

        @JvmStatic
        fun typeFrom(tabId: Int): Type? {
            for (available in Type.entries) {
                if (available.tabId == tabId) {
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
                    Type.FEEDGROUP -> return FeedGroupTab(jsonObject)
                    else -> {}
                }
            }

            return type.tab
        }
    }
}
