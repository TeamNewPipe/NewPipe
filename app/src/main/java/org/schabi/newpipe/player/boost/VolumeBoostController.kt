/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.boost

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.preference.PreferenceManager
import kotlin.math.abs

internal interface VolumeBoostStateStore {
    fun loadGainMb(): Int
    fun saveGainMb(gainMb: Int)
}

private class VolumeBoostPreferences(context: Context) : VolumeBoostStateStore {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    override fun loadGainMb(): Int = preferences.getInt(KEY_GAIN_MB, 0)

    override fun saveGainMb(gainMb: Int) {
        preferences.edit().putInt(KEY_GAIN_MB, gainMb).apply()
    }

    private companion object {
        const val KEY_GAIN_MB = "player_volume_boost_gain_mb"
    }
}

internal interface VolumeBoostEngine {
    fun setTargetGainMb(gainMb: Int)
    fun setEnabled(enabled: Boolean)
    fun release()
}

internal interface VolumeBoostEngineFactory {
    val isAvailable: Boolean
    fun create(audioSessionId: Int): VolumeBoostEngine
}

private class AndroidVolumeBoostEngineFactory : VolumeBoostEngineFactory {
    override val isAvailable: Boolean = true

    override fun create(audioSessionId: Int): VolumeBoostEngine = AndroidVolumeBoostEngine(audioSessionId)
}

private class AndroidVolumeBoostEngine(audioSessionId: Int) : VolumeBoostEngine {
    private val enhancer = LoudnessEnhancer(audioSessionId)

    override fun setTargetGainMb(gainMb: Int) {
        enhancer.setTargetGain(gainMb)
    }

    override fun setEnabled(enabled: Boolean) {
        enhancer.enabled = enabled
    }

    override fun release() {
        enhancer.release()
    }
}

/** Owns one real-time loudness enhancer and follows Media3 audio-session changes. */
internal class VolumeBoostController internal constructor(
    private val stateStore: VolumeBoostStateStore,
    private val engineFactory: VolumeBoostEngineFactory
) {
    constructor(context: Context) : this(
        VolumeBoostPreferences(context),
        AndroidVolumeBoostEngineFactory()
    )

    var gainMb: Int = nearestSupportedGain(stateStore.loadGainMb())
        private set

    private var audioSessionId = NO_AUDIO_SESSION
    private var engine: VolumeBoostEngine? = null

    val isEnabled: Boolean
        get() = gainMb > 0

    val isAvailable: Boolean
        get() = engine != null || engineFactory.isAvailable

    val isOperational: Boolean
        get() = engine != null

    fun attachAudioSession(newAudioSessionId: Int) {
        if (audioSessionId == newAudioSessionId && (engine != null || !isEnabled)) {
            return
        }
        releaseAudioSession()
        audioSessionId = newAudioSessionId
        ensureEngine()
    }

    fun releaseAudioSession() {
        releaseEngine()
        audioSessionId = NO_AUDIO_SESSION
    }

    fun updateGainMb(requestedGainMb: Int) {
        gainMb = nearestSupportedGain(requestedGainMb)
        stateStore.saveGainMb(gainMb)
        if (!isEnabled) {
            releaseEngine()
        } else if (engine == null) {
            ensureEngine()
        } else {
            applyGain()
        }
    }

    private fun ensureEngine() {
        if (engine != null || !isEnabled || audioSessionId <= 0 || !engineFactory.isAvailable) {
            return
        }
        try {
            engine = engineFactory.create(audioSessionId)
            applyGain()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Volume boost is unavailable for audio session $audioSessionId", error)
            releaseEngine()
        }
    }

    private fun applyGain() {
        val currentEngine = engine ?: return
        try {
            currentEngine.setTargetGainMb(gainMb)
            currentEngine.setEnabled(isEnabled)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not apply volume boost", error)
            releaseEngine()
        }
    }

    private fun releaseEngine() {
        val currentEngine = engine ?: return
        try {
            currentEngine.setEnabled(false)
        } catch (_: RuntimeException) {
            // Release remains mandatory even if a vendor audio effect is already invalid.
        }
        try {
            currentEngine.release()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not release volume boost cleanly", error)
        } finally {
            engine = null
        }
    }

    internal companion object {
        private const val TAG = "VolumeBoostController"
        private const val NO_AUDIO_SESSION = -1
        val supportedGainsMb = intArrayOf(0, 300, 600, 900, 1200)

        fun nearestSupportedGain(gainMb: Int): Int = supportedGainsMb.minBy { supported -> abs(supported - gainMb) }
    }
}
