/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.ui

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.preference.PreferenceManager
import org.schabi.newpipe.databinding.PlayerBinding

/**
 * Opt-in static thumbnail glow behind the video surface, not live video-frame processing.
 * Reuses the thumbnail already loaded by the player and avoids extra network requests.
 */
class PlayerAmbientMode(private val binding: PlayerBinding) {
    private var thumbnail: Bitmap? = null
    private var softenedThumbnail: Bitmap? = null

    fun setThumbnail(bitmap: Bitmap?) {
        if (bitmap === thumbnail) return
        hide()
        releaseSoftenedThumbnail()
        thumbnail = bitmap?.takeUnless { it.isRecycled }
    }

    fun update(
        fullscreen: Boolean,
        rendersVideo: Boolean,
        spherical: Boolean,
        miniPlayer: Boolean
    ) {
        val enabled = PreferenceManager.getDefaultSharedPreferences(binding.root.context)
            .getBoolean(PREFERENCE_KEY, false)
        val bitmap = thumbnail?.takeUnless { it.isRecycled }
        if (!shouldShow(enabled, fullscreen, rendersVideo, spherical, miniPlayer) || bitmap == null) {
            hide()
            return
        }
        if (binding.playerAmbientBackdrop.drawable == null) {
            val backdrop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.playerAmbientBackdrop.setRenderEffect(
                    RenderEffect.createBlurEffect(36f, 36f, Shader.TileMode.CLAMP)
                )
                bitmap
            } else {
                softenedThumbnail ?: try {
                    Bitmap.createScaledBitmap(bitmap, 24, 24, true).also {
                        softenedThumbnail = it
                    }
                } catch (_: RuntimeException) {
                    bitmap
                }
            }
            binding.playerAmbientBackdrop.setImageBitmap(backdrop)
        }
        binding.playerAmbientBackdrop.visibility = View.VISIBLE
        binding.playerAmbientTint.visibility = View.VISIBLE
    }

    fun clear() {
        hide()
        releaseSoftenedThumbnail()
        thumbnail = null
    }

    private fun hide() {
        binding.playerAmbientBackdrop.visibility = View.GONE
        binding.playerAmbientTint.visibility = View.GONE
        binding.playerAmbientBackdrop.setImageDrawable(null)
    }

    private fun releaseSoftenedThumbnail() {
        softenedThumbnail?.takeIf { !it.isRecycled }?.recycle()
        softenedThumbnail = null
    }

    companion object {
        const val PREFERENCE_KEY = "player_ambient_mode"

        internal fun shouldShow(
            enabled: Boolean,
            fullscreen: Boolean,
            rendersVideo: Boolean,
            spherical: Boolean,
            miniPlayer: Boolean
        ): Boolean = enabled && fullscreen && rendersVideo && !spherical && !miniPlayer
    }
}
