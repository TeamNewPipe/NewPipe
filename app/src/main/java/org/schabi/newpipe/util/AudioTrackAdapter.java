package org.schabi.newpipe.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.util.StreamItemAdapter.StreamInfoWrapper;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A list adapter for groups of {@link AudioStream}s (audio tracks).
 */
public class AudioTrackAdapter extends BaseAdapter {
    private final AudioTracksWrapper tracksWrapper;

    public AudioTrackAdapter(final AudioTracksWrapper tracksWrapper) {
        this.tracksWrapper = tracksWrapper;
    }

    @Override
    public int getCount() {
        return tracksWrapper.size();
    }

    @Override
    public List<AudioStream> getItem(final int position) {
        return tracksWrapper.getTracksList().get(position).getStreamsList();
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final var context = parent.getContext();
        final View view;
        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(
                    R.layout.stream_quality_item, parent, false);
        } else {
            view = convertView;
        }

        final ImageView woSoundIconView = view.findViewById(R.id.wo_sound_icon);
        final TextView formatNameView = view.findViewById(R.id.stream_format_name);
        final TextView qualityView = view.findViewById(R.id.stream_quality);
        final TextView sizeView = view.findViewById(R.id.stream_size);

        final List<AudioStream> streams = getItem(position);
        final AudioStream stream = streams.get(0);

        woSoundIconView.setVisibility(View.GONE);
        sizeView.setVisibility(View.VISIBLE);

        if (stream.getAudioTrackId() != null) {
            formatNameView.setText(stream.getAudioTrackId());
        }
        qualityView.setText(Localization.audioTrackName(context, stream));

        return view;
    }

    public static class AudioTracksWrapper implements Serializable {
        private final List<StreamInfoWrapper<AudioStream>> tracksList;

        public AudioTracksWrapper(@NonNull final List<List<AudioStream>> groupedAudioStreams,
                                  @Nullable final Context context) {
            this.tracksList = groupedAudioStreams.stream().map(streams ->
                    new StreamInfoWrapper<>(streams, context)).collect(Collectors.toList());
        }

        public List<StreamInfoWrapper<AudioStream>> getTracksList() {
            return tracksList;
        }

        public int size() {
            return tracksList.size();
        }
    }
}
