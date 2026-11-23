/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.download.DownloadPlayback
import org.schabi.newpipe.download.OfflinePlaybackMetadata
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.transcript.TranscriptDialog
import org.schabi.newpipe.util.Localization
import us.shandian.giga.get.FinishedMission

class LocalMediaDescriptionFragment : BaseDescriptionFragment() {
    private lateinit var item: PlayQueueItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = requireNotNull(
            BundleCompat.getSerializable(
                requireArguments(),
                ARG_ITEM,
                PlayQueueItem::class.java
            )
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val inflater = LayoutInflater.from(requireContext())
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val (metadata, download, offline) = withContext(Dispatchers.IO) {
                val download = DownloadPlayback.find(appContext, item.url)
                Triple(LocalMediaMetadataReader.read(appContext, item), download, OfflinePlaybackMetadata.resolve(appContext, item.url, download))
            }
            populateMetadata(inflater, metadata)
            download?.let(::showDownloadMetadata)
            TranscriptDialog.addLocalButton(binding.detailMetadataLayout, this@LocalMediaDescriptionFragment, item, offline)
        }
    }

    private fun showDownloadMetadata(download: FinishedMission) {
        val description = DownloadPlayback.description(download)
        if (description.isNotBlank()) {
            binding.detailDescriptionView.text = description
            binding.detailDescriptionView.setTextIsSelectable(true)
            binding.detailDescriptionView.visibility = View.VISIBLE
        }
        val uploadDate = DownloadPlayback.parsedUploadDate(download)
            ?.let(Localization::formatDate)
            ?: DownloadPlayback.uploadDate(download)
        if (uploadDate != null) {
            binding.detailUploadDateView.text = getString(R.string.upload_date_text, uploadDate)
            binding.detailUploadDateView.visibility = View.VISIBLE
        }
        addMetadataItem(
            layoutInflater,
            binding.detailMetadataLayout,
            false,
            R.string.metadata_tags,
            download.tags.orEmpty().lineSequence().filter { it.isNotBlank() }.joinToString(", ")
        )
    }

    override fun displayDescription(): Description = Description.EMPTY_DESCRIPTION

    override fun getService(): StreamingService = error(
        "Local media descriptions do not use a streaming service"
    )

    override fun getServiceId(): Int = PlayQueueItem.LOCAL_SERVICE_ID

    override fun getStreamUrl(): String = item.url

    override fun getTags(): List<String> = emptyList()

    override fun setupMetadata(inflater: LayoutInflater, layout: LinearLayout) {
        binding.detailUploadDateView.visibility = View.GONE
    }

    private fun populateMetadata(
        inflater: LayoutInflater,
        metadata: LocalMediaTechnicalMetadata
    ) {
        val layout = binding.detailMetadataLayout
        layout.removeAllViews()

        addMetadataItem(
            inflater,
            layout,
            false,
            R.string.local_media_metadata_format,
            LocalMediaMetadataFormatter.format(item.mimeType)
        )
        addMetadataItem(
            inflater,
            layout,
            false,
            R.string.local_media_metadata_resolution,
            LocalMediaMetadataFormatter.resolution(metadata)
        )
        if (item.duration > 0) {
            addMetadataItem(
                inflater,
                layout,
                false,
                R.string.local_media_metadata_length,
                Localization.getDurationString(item.duration)
            )
        }
        if (metadata.capturedAtMillis > 0) {
            addMetadataItem(
                inflater,
                layout,
                false,
                R.string.local_media_metadata_captured_on,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(
                    Date(metadata.capturedAtMillis)
                )
            )
        }

        val audioQuality = formatAudioQuality(metadata)
        addMetadataItem(
            inflater,
            layout,
            false,
            R.string.local_media_metadata_audio_quality,
            audioQuality
        )
    }

    private fun formatAudioQuality(metadata: LocalMediaTechnicalMetadata): String {
        val values = buildList {
            if (metadata.audioSampleRate > 0) {
                val sampleRate = NumberFormat.getNumberInstance().apply {
                    maximumFractionDigits = 1
                }.format(metadata.audioSampleRate / 1_000.0)
                add(getString(R.string.local_media_sample_rate, sampleRate))
            }
            when (metadata.audioChannelCount) {
                1 -> add(getString(R.string.local_media_audio_mono))

                2 -> add(getString(R.string.local_media_audio_stereo))

                in 3..Int.MAX_VALUE -> add(
                    getString(
                        R.string.local_media_audio_channels,
                        metadata.audioChannelCount
                    )
                )
            }
            if (metadata.audioBitrate > 0) {
                add(
                    getString(
                        R.string.local_media_audio_bitrate,
                        metadata.audioBitrate / 1_000
                    )
                )
            }
        }
        return values.joinToString(" • ")
    }

    companion object {
        private const val ARG_ITEM = "local_media_item"

        @JvmStatic
        fun newInstance(item: PlayQueueItem) = LocalMediaDescriptionFragment().apply {
            arguments = Bundle().apply { putSerializable(ARG_ITEM, item) }
        }
    }
}
