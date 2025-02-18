package org.schabi.newpipe.player.event;

import androidx.annotation.NonNull;

import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.Player;

/**
 * In addition to {@link PlayerServiceEventListener}, provides callbacks for service and player
 * connections and disconnections. "Connected" here means that the service (resp. the
 * player) is running and is bound to {@link org.schabi.newpipe.player.helper.PlayerHolder}.
 * "Disconnected" means that either the service (resp. the player) was stopped completely, or that
 * {@link org.schabi.newpipe.player.helper.PlayerHolder} is not bound.
 */
public interface PlayerServiceExtendedEventListener extends PlayerServiceEventListener {
    /**
     * The player service just connected to {@link org.schabi.newpipe.player.helper.PlayerHolder},
     * but the player may not be active at this moment, e.g. in case the service is running to
     * respond to Android Auto media browser queries without playing anything.
     * {@link #onPlayerConnected(Player, boolean)} will be called right after this function if there
     * is a player.
     *
     * @param playerService the newly connected player service
     */
    void onServiceConnected(@NonNull PlayerService playerService);

    /**
     * The player service is already connected and the player was just started.
     *
     * @param player the newly connected or started player
     * @param playAfterConnect whether to open the video player in the video details fragment
     */
    void onPlayerConnected(@NonNull Player player, boolean playAfterConnect);

    /**
     * The player got disconnected, for one of these reasons: the player is getting closed while
     * leaving the service open for future media browser queries, the service is stopping
     * completely, or {@link org.schabi.newpipe.player.helper.PlayerHolder} is unbinding.
     */
    void onPlayerDisconnected();

    /**
     * The service got disconnected from {@link org.schabi.newpipe.player.helper.PlayerHolder},
     * either because {@link org.schabi.newpipe.player.helper.PlayerHolder} is unbinding or because
     * the service is stopping completely.
     */
    void onServiceDisconnected();
}
