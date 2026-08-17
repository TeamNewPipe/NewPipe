package org.schabi.newpipe.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.ui.components.StreamQualityItem
import java.io.Serializable

/**
 * A list adapter for groups of [AudioStream]s (audio tracks).
 */
class AudioTrackAdapter(private val tracksWrapper: AudioTracksWrapper) : BaseAdapter() {

    override fun getCount(): Int = tracksWrapper.size()

    override fun getItem(position: Int): List<AudioStream> {
        return tracksWrapper.tracksList[position].streamsList
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val context = parent.context
        val composeView = (convertView as? ComposeView) ?: ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }

        val streams = getItem(position)
        val stream = streams[0]
        val wrapper = tracksWrapper.tracksList[position]

        // Size is visible in original Java, but no text was set.
        // We can show the size of the first stream in the group if available.
        val sizeText = if (wrapper.getSizeInBytes(0) > 0) wrapper.getFormattedSize(0) else null

        composeView.setContent {
            StreamQualityItem(
                formatName = stream.audioTrackId ?: "",
                quality = Localization.audioTrackName(context, stream),
                size = sizeText,
                isVideoOnly = false
            )
        }

        return composeView
    }

    class AudioTracksWrapper(
        groupedAudioStreams: List<List<AudioStream>>,
        context: Context?
    ) : Serializable {
        val tracksList: List<StreamItemAdapter.StreamInfoWrapper<AudioStream>> =
            groupedAudioStreams.map { streams ->
                StreamItemAdapter.StreamInfoWrapper(streams, context)
            }

        fun size(): Int = tracksList.size
    }
}
