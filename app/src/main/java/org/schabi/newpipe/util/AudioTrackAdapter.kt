package org.schabi.newpipe.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper
import java.io.Serializable
import java.util.function.Function
import java.util.stream.Collectors

/**
 * A list adapter for groups of [AudioStream]s (audio tracks).
 */
class AudioTrackAdapter(private val tracksWrapper: AudioTracksWrapper?) : BaseAdapter() {
    public override fun getCount(): Int {
        return tracksWrapper!!.size()
    }

    public override fun getItem(position: Int): List<AudioStream?> {
        return tracksWrapper!!.tracksList.get(position).getStreamsList()
    }

    public override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    public override fun getView(position: Int, convertView: View, parent: ViewGroup): View {
        val context: Context = parent.getContext()
        val view: View
        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(
                    R.layout.stream_quality_item, parent, false)
        } else {
            view = convertView
        }
        val woSoundIconView: ImageView = view.findViewById(R.id.wo_sound_icon)
        val formatNameView: TextView = view.findViewById(R.id.stream_format_name)
        val qualityView: TextView = view.findViewById(R.id.stream_quality)
        val sizeView: TextView = view.findViewById(R.id.stream_size)
        val streams: List<AudioStream?> = getItem(position)
        val stream: AudioStream? = streams.get(0)
        woSoundIconView.setVisibility(View.GONE)
        sizeView.setVisibility(View.VISIBLE)
        if (stream!!.getAudioTrackId() != null) {
            formatNameView.setText(stream.getAudioTrackId())
        }
        qualityView.setText(Localization.audioTrackName(context, stream))
        return view
    }

    class AudioTracksWrapper(groupedAudioStreams: List<List<AudioStream?>?>,
                             context: Context?) : Serializable {
        val tracksList: List<StreamInfoWrapper<AudioStream?>>

        init {
            tracksList = groupedAudioStreams.stream().map<StreamInfoWrapper<AudioStream?>>(Function({ streams: List<AudioStream?> -> StreamInfoWrapper(streams, context) })).collect(Collectors.toList())
        }

        fun size(): Int {
            return tracksList.size
        }
    }
}
