package org.schabi.newpipe.player.ui

import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.player.Player

/** Keeps optional seek-history controls out of the already large player UI controller. */
internal object SeekHistoryUi {
    @JvmStatic
    fun bind(binding: PlayerBinding, player: Player) {
        binding.seekHistoryBack.setOnClickListener {
            player.seekHistoryBack()
            update(binding, player)
        }
        binding.seekHistoryForward.setOnClickListener {
            player.seekHistoryForward()
            update(binding, player)
        }
        update(binding, player)
    }

    @JvmStatic
    fun clear(binding: PlayerBinding) {
        binding.seekHistoryBack.setOnClickListener(null)
        binding.seekHistoryForward.setOnClickListener(null)
    }

    @JvmStatic
    fun update(binding: PlayerBinding, player: Player) {
        val back = player.canSeekHistoryBack()
        val forward = player.canSeekHistoryForward()
        binding.seekHistoryBack.isEnabled = back
        binding.seekHistoryForward.isEnabled = forward
        binding.seekHistoryBack.alpha = if (back) 1f else 0.45f
        binding.seekHistoryForward.alpha = if (forward) 1f else 0.45f
    }
}
