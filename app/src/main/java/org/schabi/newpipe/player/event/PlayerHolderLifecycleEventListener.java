package org.schabi.newpipe.player.event;

import org.schabi.newpipe.player.PlayerService;

/** Gets signalled if our PlayerHolder (dis)connects from the PlayerService.
 * This is currently only implemented by the
 * {@link org.schabi.newpipe.fragments.detail.VideoDetailFragment}. */
public interface PlayerHolderLifecycleEventListener {

    /** Our {@link org.schabi.newpipe.player.helper.PlayerHolder} connected to its service.
     * @param playerService The service the holder connected to
     * @param playAfterConnect  */
    void onServiceConnected(PlayerService playerService,
                            boolean playAfterConnect);
    
    /** Our {@link org.schabi.newpipe.player.helper.PlayerHolder} was unbound and thus
     * disconnected from the {@link org.schabi.newpipe.player.PlayerService}. */
    void onServiceDisconnected();
}
