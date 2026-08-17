package org.schabi.newpipe.player.event

import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService

/**
 * In addition to [PlayerServiceEventListener], provides callbacks for service and player
 * connections and disconnections. "Connected" here means that the service (resp. the
 * player) is running and is bound to [org.schabi.newpipe.player.helper.PlayerHolder].
 * "Disconnected" means that either the service (resp. the player) was stopped completely, or that
 * [org.schabi.newpipe.player.helper.PlayerHolder] is not bound.
 */
interface PlayerServiceExtendedEventListener : PlayerServiceEventListener {
    /**
     * The player service just connected to [org.schabi.newpipe.player.helper.PlayerHolder],
     * but the player may not be active at this moment, e.g. in case the service is running to
     * respond to Android Auto media browser queries without playing anything.
     * [onPlayerConnected] will be called right after this function if there
     * is a player.
     *
     * @param playerService the newly connected player service
     */
    fun onServiceConnected(playerService: PlayerService)

    /**
     * The player service is already connected and the player was just started.
     *
     * @param player the newly connected or started player
     * @param playAfterConnect whether to open the video player in the video details fragment
     */
    fun onPlayerConnected(player: Player, playAfterConnect: Boolean)

    /**
     * The player got disconnected, for one of these reasons: the player is getting closed while
     * leaving the service open for future media browser queries, the service is stopping
     * completely, or [org.schabi.newpipe.player.helper.PlayerHolder] is unbinding.
     */
    fun onPlayerDisconnected()

    /**
     * The service got disconnected from [org.schabi.newpipe.player.helper.PlayerHolder],
     * either because [org.schabi.newpipe.player.helper.PlayerHolder] is unbinding or because
     * the service is stopping completely.
     */
    fun onServiceDisconnected()
}
