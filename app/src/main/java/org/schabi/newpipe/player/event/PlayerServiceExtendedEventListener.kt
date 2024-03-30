package org.schabi.newpipe.player.event

import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService

open interface PlayerServiceExtendedEventListener : PlayerServiceEventListener {
    fun onServiceConnected(player: Player?,
                           playerService: PlayerService?,
                           playAfterConnect: Boolean)

    fun onServiceDisconnected()
}
