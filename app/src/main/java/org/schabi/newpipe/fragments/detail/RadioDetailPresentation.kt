/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments.detail

import android.view.View
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.image.CoilHelper

/** Remove the preceding video's detail UI even when radio starts in the background. */
internal object RadioDetailPresentation {
    @JvmStatic
    fun show(binding: FragmentVideoDetailBinding, station: PlayQueueItem) {
        binding.detailLocalLikes.bind(null)
        binding.detailVideoTitleView.text = station.title
        binding.detailSubChannelTextView.text = station.uploader
        binding.detailUploaderTextView.text = binding.root.context.getString(R.string.live_radio)
        binding.detailSubChannelTextView.visibility = View.VISIBLE
        binding.detailUploaderTextView.visibility = View.VISIBLE
        binding.detailMetaInfoTextView.text = station.uploader
        binding.detailMetaInfoTextView.visibility = View.GONE
        binding.detailThumbnailPlayButton.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.root.findViewById<View>(R.id.detail_navigation)?.visibility = View.GONE
        binding.relatedItemsLayout?.visibility = View.GONE
        listOf(
            binding.detailToggleSecondaryControlsView,
            binding.detailSecondaryControlPanel,
            binding.detailSubChannelThumbnailView,
            binding.detailUploaderThumbnailView,
            binding.detailsPanel,
            binding.detailDurationView,
            binding.detailMetaInfoSeparator,
            binding.detailControlsPlaylistAppend,
            binding.detailControlsBackground,
            binding.detailControlsPopup,
            binding.detailControlsDownload,
            binding.detailControlsShare,
            binding.detailControlsOpenInBrowser,
            binding.detailControlsCast,
            binding.detailControlsPlayWithKodi
        ).forEach { it.visibility = View.GONE }
        binding.detailUploaderRootLayout.isClickable = false
        CoilHelper.loadRadioLogo(binding.detailThumbnailImageView, station.localThumbnailUrl)
    }
}
