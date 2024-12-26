package org.schabi.newpipe.player.ui

import androidx.core.util.Consumer
import java.util.Optional

class PlayerUiList(vararg initialPlayerUis: PlayerUi) {
    val playerUis = mutableListOf<PlayerUi>()

    /**
     * Creates a [PlayerUiList] starting with the provided player uis. The provided player uis
     * will not be prepared like those passed to [.addAndPrepare], because when
     * the [PlayerUiList] constructor is called, the player is still not running and it
     * wouldn't make sense to initialize uis then. Instead the player will initialize them by doing
     * proper calls to [.call].
     *
     * @param initialPlayerUis the player uis this list should start with; the order will be kept
     */
    init {
        playerUis.addAll(listOf(*initialPlayerUis))
    }

    /**
     * Adds the provided player ui to the list and calls on it the initialization functions that
     * apply based on the current player state. The preparation step needs to be done since when UIs
     * are removed and re-added, the player will not call e.g. initPlayer again since the exoplayer
     * is already initialized, but we need to notify the newly built UI that the player is ready
     * nonetheless.
     * @param playerUi the player ui to prepare and add to the list; its [PlayerUi.getPlayer]
     * will be used to query information about the player state
     */
    fun addAndPrepare(playerUi: PlayerUi) {
        if (playerUi.getPlayer().fragmentListener.isPresent) {
            // make sure UIs know whether a service is connected or not
            playerUi.onFragmentListenerSet()
        }

        if (!playerUi.getPlayer().exoPlayerIsNull()) {
            playerUi.initPlayer()
            if (playerUi.getPlayer().playQueue != null) {
                playerUi.initPlayback()
            }
        }

        playerUis.add(playerUi)
    }

    /**
     * Destroys all matching player UIs and removes them from the list.
     * @param playerUiType the class of the player UI to destroy;
     * the [Class.isInstance] method will be used, so even subclasses will be
     * destroyed and removed
     * @param T the class type parameter </T>
     * */
    fun <T> destroyAll(playerUiType: Class<T?>) {
        for (ui in playerUis) {
            if (playerUiType.isInstance(ui)) {
                ui.destroyPlayer()
                ui.destroy()
                playerUis.remove(ui)
            }
        }
    }

    /**
     * @param playerUiType the class of the player UI to return;
     * the [Class.isInstance] method will be used, so even subclasses could be returned
     * @param T the class type parameter
     * @return the first player UI of the required type found in the list, or null
     </T> */
    fun <T> get(playerUiType: Class<T>): T? {
        for (ui in playerUis) {
            if (playerUiType.isInstance(ui)) {
                when (val r = playerUiType.cast(ui)) {
                    // try all UIs before returning null
                    null -> continue
                    else -> return r
                }
            }
        }
        return null
    }

    /**
     * @param playerUiType the class of the player UI to return;
     * the [Class.isInstance] method will be used, so even subclasses could be returned
     * @param T the class type parameter
     * @return the first player UI of the required type found in the list, or an empty
     * [Optional] otherwise
     </T> */
    @Deprecated("use get", ReplaceWith("get(playerUiType)"))
    fun <T> getOpt(playerUiType: Class<T>): Optional<T & Any> =
        Optional.ofNullable(get(playerUiType))

    /**
     * Calls the provided consumer on all player UIs in the list, in order of addition.
     * @param consumer the consumer to call with player UIs
     */
    fun call(consumer: java.util.function.Consumer<PlayerUi>) {
        for (ui in playerUis) {
            consumer.accept(ui)
        }
    }
}
