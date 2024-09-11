package com.kt.apps.video.data

import org.schabi.newpipe.player.playqueue.PlayQueue

sealed interface PlayerType {
    data object Origin : PlayerType
    data object Web : PlayerType
}
data class VersionedPlayer(val playerType: PlayerType, val version: Long)
data class PlayerChooser(val versionPlayers: List<VersionedPlayer> = listOf())
data class OpenVideoDetailData(
    val serviceId: Int,
    val url: String?,
    val title: String,
    val playQueue: PlayQueue? = null,
    val switchingPlayers: Boolean = false,
    val externalSource: Int = 0
)
