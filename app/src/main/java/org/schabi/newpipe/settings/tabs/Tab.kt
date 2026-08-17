package org.schabi.newpipe.settings.tabs

import android.content.Context
import androidx.annotation.DrawableRes
import kotlinx.serialization.json.*
import java.util.*
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem.LocalItemType
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.ServiceHelper

abstract class Tab {

    constructor()

    constructor(jsonObject: JsonObject) {
        readDataFromJson(jsonObject)
    }

    abstract val tabId: Int

    abstract fun getTabName(context: Context): String

    @DrawableRes
    abstract fun getTabIconRes(context: Context): Int

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tab) return false
        return tabId == other.tabId
    }

    override fun hashCode(): Int {
        return Objects.hashCode(tabId)
    }

    fun writeJsonObject(): JsonObject {
        return buildJsonObject {
            put(JSON_TAB_ID_KEY, tabId)
            writeDataToJson(this)
        }
    }

    protected open fun writeDataToJson(builder: JsonObjectBuilder) {
        // No-op
    }

    protected open fun readDataFromJson(jsonObject: JsonObject) {
        // No-op
    }

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

    companion object {
        private const val JSON_TAB_ID_KEY = "tab_id"
        private const val NO_NAME = "<no-name>"
        private const val NO_ID = "<no-id>"
        private const val NO_URL = "<no-url>"

        @JvmStatic
        fun from(jsonObject: JsonObject): Tab? {
            val tabId = jsonObject[JSON_TAB_ID_KEY]?.jsonPrimitive?.int ?: -1
            return if (tabId == -1) null else from(tabId, jsonObject)
        }

        @JvmStatic
        fun from(tabId: Int): Tab? {
            return from(tabId, null)
        }

        @JvmStatic
        fun typeFrom(tabId: Int): Type? {
            return Type.entries.find { it.tabId == tabId }
        }

        private fun from(tabId: Int, jsonObject: JsonObject?): Tab? {
            val type = typeFrom(tabId) ?: return null

            if (jsonObject != null) {
                return when (type) {
                    Type.KIOSK -> KioskTab(jsonObject)
                    Type.CHANNEL -> ChannelTab(jsonObject)
                    Type.PLAYLIST -> PlaylistTab(jsonObject)
                    Type.FEEDGROUP -> FeedGroupTab(jsonObject)
                    else -> type.tab
                }
            }

            return type.tab
        }
    }

    class BlankTab : Tab() {
        override val tabId: Int = ID

        override fun getTabName(context: Context): String = "NewPipe"

        override fun getTabIconRes(context: Context): Int = R.drawable.ic_crop_portrait

        companion object {
            const val ID = 0
        }
    }

    class SubscriptionsTab : Tab() {
        override val tabId: Int = ID

        override fun getTabName(context: Context): String = context.getString(R.string.tab_subscriptions)

        override fun getTabIconRes(context: Context): Int = R.drawable.ic_tv

        companion object {
            const val ID = 1
        }
    }

    class FeedTab : Tab() {
        override val tabId: Int = ID

        override fun getTabName(context: Context): String = context.getString(R.string.feed_title)

        override fun getTabIconRes(context: Context): Int = R.drawable.ic_subscriptions

        companion object {
            const val ID = 2
        }
    }

    class BookmarksTab : Tab() {
        override val tabId: Int = ID

        override fun getTabName(context: Context): String = context.getString(R.string.tab_bookmarks)

        override fun getTabIconRes(context: Context): Int = R.drawable.ic_bookmark

        companion object {
            const val ID = 3
        }
    }

    class HistoryTab : Tab() {
        override val tabId: Int = ID

        override fun getTabName(context: Context): String = context.getString(R.string.title_activity_history)

        override fun getTabIconRes(context: Context): Int = R.drawable.ic_history

        companion object {
            const val ID = 4
        }
    }

    class KioskTab : Tab {
        var kioskServiceId: Int = -1
            private set
        var kioskId: String = NO_ID
            private set

        constructor() : super()

        constructor(kioskServiceId: Int, kioskId: String) : super() {
            this.kioskServiceId = kioskServiceId
            this.kioskId = kioskId
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        override val tabId: Int = ID

        override fun getTabName(context: Context): String = KioskTranslator.getTranslatedKioskName(kioskId, context)

        override fun getTabIconRes(context: Context): Int {
            val kioskIcon = KioskTranslator.getKioskIcon(kioskId)
            if (kioskIcon <= 0) {
                throw IllegalStateException("Kiosk ID is not valid: \"$kioskId\"")
            }
            return kioskIcon
        }

        override fun writeDataToJson(builder: JsonObjectBuilder) {
            builder.put(JSON_KIOSK_SERVICE_ID_KEY, kioskServiceId)
            builder.put(JSON_KIOSK_ID_KEY, kioskId)
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            kioskServiceId = jsonObject[JSON_KIOSK_SERVICE_ID_KEY]?.jsonPrimitive?.int ?: -1
            kioskId = jsonObject[JSON_KIOSK_ID_KEY]?.jsonPrimitive?.content ?: NO_ID
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KioskTab) return false
            if (!super.equals(other)) return false
            return kioskServiceId == other.kioskServiceId && kioskId == other.kioskId
        }

        override fun hashCode(): Int {
            return Objects.hash(tabId, kioskServiceId, kioskId)
        }

        companion object {
            const val ID = 5
            private const val JSON_KIOSK_SERVICE_ID_KEY = "service_id"
            private const val JSON_KIOSK_ID_KEY = "kiosk_id"
        }
    }

    class ChannelTab : Tab {
        var channelServiceId: Int = -1
            private set
        var channelUrl: String = NO_URL
            private set
        var channelName: String = NO_NAME
            private set

        constructor() : super()

        constructor(channelServiceId: Int, channelUrl: String, channelName: String) : super() {
            this.channelServiceId = channelServiceId
            this.channelUrl = channelUrl
            this.channelName = channelName
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        override val tabId: Int = ID

        override fun getTabName(context: Context): String = channelName

        override fun getTabIconRes(context: Context): Int = R.drawable.ic_tv

        override fun writeDataToJson(builder: JsonObjectBuilder) {
            builder.put(JSON_CHANNEL_SERVICE_ID_KEY, channelServiceId)
            builder.put(JSON_CHANNEL_URL_KEY, channelUrl)
            builder.put(JSON_CHANNEL_NAME_KEY, channelName)
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            channelServiceId = jsonObject[JSON_CHANNEL_SERVICE_ID_KEY]?.jsonPrimitive?.int ?: -1
            channelUrl = jsonObject[JSON_CHANNEL_URL_KEY]?.jsonPrimitive?.content ?: NO_URL
            channelName = jsonObject[JSON_CHANNEL_NAME_KEY]?.jsonPrimitive?.content ?: NO_NAME
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChannelTab) return false
            if (!super.equals(other)) return false
            return channelServiceId == other.channelServiceId &&
                channelUrl == other.channelUrl &&
                channelName == other.channelName
        }

        override fun hashCode(): Int {
            return Objects.hash(tabId, channelServiceId, channelUrl, channelName)
        }

        companion object {
            const val ID = 6
            private const val JSON_CHANNEL_SERVICE_ID_KEY = "channel_service_id"
            private const val JSON_CHANNEL_URL_KEY = "channel_url"
            private const val JSON_CHANNEL_NAME_KEY = "channel_name"
        }
    }

    class DefaultKioskTab : Tab() {
        override val tabId: Int = ID

        override fun getTabName(context: Context): String = KioskTranslator.getTranslatedKioskName(getDefaultKioskId(context), context)

        override fun getTabIconRes(context: Context): Int = KioskTranslator.getKioskIcon(getDefaultKioskId(context))

        private fun getDefaultKioskId(context: Context): String {
            val kioskServiceId = ServiceHelper.getSelectedServiceId(context)
            return try {
                val service = NewPipe.getService(kioskServiceId)
                service.kioskList.defaultKioskId
            } catch (e: ExtractionException) {
                ""
            }
        }

        companion object {
            const val ID = 7
        }
    }

    class PlaylistTab : Tab {
        var playlistServiceId: Int = -1
            private set
        var playlistUrl: String = NO_URL
            private set
        var playlistName: String = NO_NAME
            private set
        var playlistId: Long = -1
            private set
        var playlistType: LocalItemType = LocalItemType.PLAYLIST_LOCAL_ITEM
            private set

        constructor() : super()

        constructor(playlistId: Long, playlistName: String) : super() {
            this.playlistName = playlistName
            this.playlistId = playlistId
            this.playlistType = LocalItemType.PLAYLIST_LOCAL_ITEM
            this.playlistServiceId = -1
            this.playlistUrl = NO_URL
        }

        constructor(playlistServiceId: Int, playlistUrl: String, playlistName: String) : super() {
            this.playlistServiceId = playlistServiceId
            this.playlistUrl = playlistUrl
            this.playlistName = playlistName
            this.playlistType = LocalItemType.PLAYLIST_REMOTE_ITEM
            this.playlistId = -1
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        override val tabId: Int = ID

        override fun getTabName(context: Context): String = playlistName

        override fun getTabIconRes(context: Context): Int = R.drawable.ic_bookmark

        override fun writeDataToJson(builder: JsonObjectBuilder) {
            builder.put(JSON_PLAYLIST_SERVICE_ID_KEY, playlistServiceId)
            builder.put(JSON_PLAYLIST_URL_KEY, playlistUrl)
            builder.put(JSON_PLAYLIST_NAME_KEY, playlistName)
            builder.put(JSON_PLAYLIST_ID_KEY, playlistId)
            builder.put(JSON_PLAYLIST_TYPE_KEY, playlistType.toString())
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            playlistServiceId = jsonObject[JSON_PLAYLIST_SERVICE_ID_KEY]?.jsonPrimitive?.int ?: -1
            playlistUrl = jsonObject[JSON_PLAYLIST_URL_KEY]?.jsonPrimitive?.content ?: NO_URL
            playlistName = jsonObject[JSON_PLAYLIST_NAME_KEY]?.jsonPrimitive?.content ?: NO_NAME
            playlistId = jsonObject[JSON_PLAYLIST_ID_KEY]?.jsonPrimitive?.long ?: -1L
            playlistType = LocalItemType.valueOf(
                jsonObject[JSON_PLAYLIST_TYPE_KEY]?.jsonPrimitive?.content ?: LocalItemType.PLAYLIST_LOCAL_ITEM.toString()
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PlaylistTab) return false
            if (!super.equals(other)) return false
            return playlistServiceId == other.playlistServiceId &&
                playlistId == other.playlistId &&
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

        companion object {
            const val ID = 8
            private const val JSON_PLAYLIST_SERVICE_ID_KEY = "playlist_service_id"
            private const val JSON_PLAYLIST_URL_KEY = "playlist_url"
            private const val JSON_PLAYLIST_NAME_KEY = "playlist_name"
            private const val JSON_PLAYLIST_ID_KEY = "playlist_id"
            private const val JSON_PLAYLIST_TYPE_KEY = "playlist_type"
        }
    }

    class FeedGroupTab : Tab {
        var feedGroupId: Long? = -1L
            private set
        var feedGroupName: String = NO_NAME
            private set
        var iconId: Int = R.drawable.ic_asterisk
            private set

        constructor() : super()

        constructor(feedGroupId: Long?, feedGroupName: String, iconId: Int) : super() {
            this.feedGroupId = feedGroupId
            this.feedGroupName = feedGroupName
            this.iconId = iconId
        }

        constructor(jsonObject: JsonObject) : super(jsonObject)

        override val tabId: Int = ID

        override fun getTabName(context: Context): String = context.getString(R.string.feed_title)

        override fun getTabIconRes(context: Context): Int = iconId

        override fun writeDataToJson(builder: JsonObjectBuilder) {
            builder.put(JSON_FEED_GROUP_ID_KEY, feedGroupId)
            builder.put(JSON_FEED_GROUP_NAME_KEY, feedGroupName)
            builder.put(JSON_FEED_GROUP_ICON_KEY, iconId)
        }

        override fun readDataFromJson(jsonObject: JsonObject) {
            feedGroupId = jsonObject[JSON_FEED_GROUP_ID_KEY]?.jsonPrimitive?.longOrNull ?: -1L
            feedGroupName = jsonObject[JSON_FEED_GROUP_NAME_KEY]?.jsonPrimitive?.content ?: NO_NAME
            iconId = jsonObject[JSON_FEED_GROUP_ICON_KEY]?.jsonPrimitive?.int ?: R.drawable.ic_asterisk
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FeedGroupTab) return false
            if (!super.equals(other)) return false
            return feedGroupId == other.feedGroupId &&
                feedGroupName == other.feedGroupName &&
                iconId == other.iconId
        }

        override fun hashCode(): Int {
            return Objects.hash(tabId, feedGroupId, feedGroupName, iconId)
        }

        companion object {
            const val ID = 9
            private const val JSON_FEED_GROUP_ID_KEY = "feed_group_id"
            private const val JSON_FEED_GROUP_NAME_KEY = "feed_group_name"
            private const val JSON_FEED_GROUP_ICON_KEY = "feed_group_icon"
        }
    }
}
