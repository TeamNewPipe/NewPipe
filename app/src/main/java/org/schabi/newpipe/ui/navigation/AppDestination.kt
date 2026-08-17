package org.schabi.newpipe.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppDestination : NavKey {
    @Serializable
    data object Home : AppDestination

    @Serializable
    data class Search(val query: String = "") : AppDestination

    @Serializable
    data class VideoDetail(val url: String, val title: String = "", val playlistId: Long? = null) : AppDestination

    @Serializable
    data class Channel(val url: String, val name: String = "") : AppDestination

    @Serializable
    data class Playlist(val url: String, val name: String = "") : AppDestination

    @Serializable
    data object Subscriptions : AppDestination

    @Serializable
    data object Library : AppDestination

    @Serializable
    data object History : AppDestination

    @Serializable
    data object Downloads : AppDestination

    @Serializable
    data object LocalPlaylists : AppDestination

    @Serializable
    data class LocalPlaylistDetail(val playlistId: Long) : AppDestination

    @Serializable
    data object Settings : AppDestination
}
