/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.history

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.recovery.QueueRecovery
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager

/** Survives view recreation and an autoplay-off service stop, never process death or dismissal. */
class PreviousPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private data class Target(val serviceId: Int, val url: String) {
        fun matches(item: PlayQueueItem?) = item?.serviceId == serviceId && item.url == url
    }

    private val context = application.applicationContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val historyKey = context.getString(R.string.enable_watch_history_key)
    private val history = PreviousPlaybackHistory()
    private val mutableAvailable = MutableLiveData(false)
    val available: LiveData<Boolean> = mutableAvailable
    private var owner = ProfileManager.getActiveProfileId(context)
    private var epoch = QueueRecovery.generation.get()
    private var queue: PlayQueue? = null
    private var queueEligible = false
    private var requiresSelection = false
    private var expectedSelection: Target? = null
    private var restoring: PreviousPlayback? = null

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY ||
            key == IncognitoMode.KEY || key == historyKey
        ) {
            invalidate()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun allowed() = prefs.getBoolean(historyKey, true) &&
        !IncognitoMode.isEnabled(prefs) && !KidsMode.requiresParentAccess(context)

    private fun checkScope(): Boolean {
        val currentOwner = ProfileManager.getActiveProfileId(context)
        if (owner != currentOwner || epoch != QueueRecovery.generation.get() || !allowed()) {
            invalidate()
        }
        return allowed()
    }

    /** A privacy change remains latched for the existing playback even after changing back. */
    private fun invalidate() {
        history.clear()
        queue = null
        queueEligible = false
        requiresSelection = true
        expectedSelection = null
        restoring = null
        owner = ProfileManager.getActiveProfileId(context)
        epoch = QueueRecovery.generation.get()
        updateAvailable()
    }

    fun bindPlayer(player: Player) {
        player.playQueue?.let { bindQueue(it, player) }
    }

    fun acceptsQueue(value: PlayQueue): Boolean {
        checkScope()
        return restoring?.accepts(value) != false
    }

    fun bindQueue(value: PlayQueue, player: Player?) {
        if (!checkScope()) return
        if (!acceptsQueue(value)) return
        if (queue !== value) {
            queue = value
            queueEligible = allowed() && (!requiresSelection || expectedSelection?.matches(value.item) == true)
            if (queueEligible) {
                requiresSelection = false
                expectedSelection = null
            }
        }
        val pending = restoring
        if (pending != null && player?.playQueue === value && !player.exoPlayerIsNull()) {
            restoring = null
            player.exoPlayer.repeatMode = pending.repeatMode
        }
        updateAvailable()
    }

    /** Called before selectAndLoadVideo can stop the engine, dispose its queue or replace metadata. */
    fun beforeSelection(player: Player?, serviceId: Int, url: String?) {
        val target = url?.let { Target(serviceId, it) } ?: return
        cancelRestoration()
        val activeQueue = player?.playQueue
        if (target.matches(activeQueue?.item)) return
        val scopeAllowed = checkScope()
        if (player != null && activeQueue != null && activeQueue === queue && queueEligible && scopeAllowed) {
            val item = activeQueue.item
            val engine = player.takeUnless { it.exoPlayerIsNull() }?.exoPlayer
            val position = engine?.takeIf {
                player.currentItem === item && !it.currentTimeline.isEmpty &&
                    it.currentMediaItemIndex == activeQueue.index
            }?.contentPosition?.takeIf { it >= 0 }
                ?: item?.recoveryPosition?.takeIf { it != PlayQueueItem.RECOVERY_UNSET }
                ?: 0L
            history.push(activeQueue, position, player.playWhenReady, player.repeatMode)
        }
        // Rapid taps while a replacement is loading must not capture the old player twice.
        queueEligible = false
        requiresSelection = true
        expectedSelection = target
        restoring = null
        updateAvailable()
    }

    fun canReturn(): Boolean = checkScope() && !history.isEmpty && restoring == null

    fun takePrevious(): PreviousPlayback? {
        if (!canReturn()) return null
        val previous = history.pop()
        if (previous == null) {
            updateAvailable()
            return null
        }
        restoring = previous
        queueEligible = false
        requiresSelection = true
        val item = previous.queue.item!!
        expectedSelection = Target(item.serviceId, item.url)
        updateAvailable()
        return previous
    }

    fun restorationFor(value: PlayQueue): PreviousPlayback? = if (checkScope()) {
        restoring?.takeIf { it.queue.equalStreamsAndIndex(value) }
    } else {
        null
    }

    /** Failed/cancelled metadata loading must not consume the user's chance to return. */
    fun cancelRestoration() {
        val pending = restoring ?: return
        if (!checkScope() || restoring !== pending) return
        restoring = null
        history.push(
            pending.queue,
            pending.queue.item!!.recoveryPosition,
            pending.playWhenReady,
            pending.repeatMode
        )
        updateAvailable()
    }

    /** Closing the mini player is an explicit end to this navigation session. */
    fun clear() {
        history.clear()
        queue = null
        queueEligible = false
        requiresSelection = false
        expectedSelection = null
        restoring = null
        updateAvailable()
    }

    private fun updateAvailable() {
        val value = !history.isEmpty && restoring == null && allowed()
        if (mutableAvailable.value != value) mutableAvailable.value = value
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        clear()
    }
}
