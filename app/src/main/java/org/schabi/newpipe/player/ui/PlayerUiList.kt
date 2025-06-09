package org.schabi.newpipe.player.ui

import org.schabi.newpipe.util.GuardedByMutex
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * Creates a [PlayerUiList] starting with the provided player uis. The provided player uis
 * will not be prepared like those passed to [.addAndPrepare], because when
 * the [PlayerUiList] constructor is called, the player is still not running and it
 * wouldn't make sense to initialize uis then. Instead the player will initialize them by doing
 * proper calls to [.call].
 *
 * @param initialPlayerUis the player uis this list should start with; the order will be kept
 */
class PlayerUiList(vararg initialPlayerUis: PlayerUi) {
    private val playerUis = GuardedByMutex(mutableListOf(*initialPlayerUis))

    /**
     * Adds the provided player ui to the list and calls on it the initialization functions that
     /**
     * Creates a [PlayerUiList] starting with the provided player uis. The provided player uis
     * will not be prepared like those passed to [.addAndPrepare], because when
     * the [PlayerUiList] constructor is called, the player is still not running and it
     * wouldn't make sense to initialize uis then. Instead the player will initialize them by doing
     * proper calls to [.call].
     *
     * @param initialPlayerUis the player uis this list should start with; the order will be kept
     */* apply based on the current player state. The preparation step needs to be done since when UIs
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

        playerUis.runWithLockSync {
            lockData.add(playerUi)
        }
    }

    /**
     * Destroys all matching player UIs and removes them from the list.
     * @param playerUiType the class of the player UI to destroy, everything if `null`.
     * The [Class.isInstance] method will be used, so even subclasses will be
     * destroyed and removed
     * @param T the class type parameter </T>
     * */
    fun <T : PlayerUi> destroyAllOfType(playerUiType: Class<T>? = null) {
        val toDestroy = mutableListOf<PlayerUi>()

        // short blocking removal from class to prevent interfering from other threads
        playerUis.runWithLockSync {
            val new = mutableListOf<PlayerUi>()
            for (ui in lockData) {
                if (playerUiType == null || playerUiType.isInstance(ui)) {
                    toDestroy.add(ui)
                } else {
                    new.add(ui)
                }
            }
            lockData = new
        }
        // then actually destroy the UIs
        for (ui in toDestroy) {
            ui.destroyPlayer()
            ui.destroy()
        }
    }

    /**
     * @param playerUiType the class of the player UI to return;
     * the [Class.isInstance] method will be used, so even subclasses could be returned
     * @param T the class type parameter
     * @return the first player UI of the required type found in the list, or null
     </T> */
    fun <T : PlayerUi> get(playerUiType: KClass<T>): T? =
        playerUis.runWithLockSync {
            for (ui in lockData) {
                if (playerUiType.isInstance(ui)) {
                    // try all UIs before returning null
                    playerUiType.safeCast(ui)?.let { return@runWithLockSync it }
                }
            }
            return@runWithLockSync null
        }

    fun <T : PlayerUi> get(playerUiType: Class<T>): T? =
        get(playerUiType.kotlin)

    /**
     * Calls the provided consumer on all player UIs in the list, in order of addition.
     * @param consumer the consumer to call with player UIs
     */
    fun call(consumer: java.util.function.Consumer<PlayerUi>) {
        // copy the list out of the mutex before calling the consumer which might block
        val new = playerUis.runWithLockSync {
            lockData.toMutableList()
        }
        for (ui in new) {
            consumer.accept(ui)
        }
    }
}
