package org.schabi.newpipe.fragments.detail;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

public class SpinnerToolbarAdapter extends BaseAdapter {
    private final List<VideoStream> videoStreams;
    private final boolean showIconNoAudio;

    private final Context context;

    public SpinnerToolbarAdapter(Context context, List<VideoStream> videoStreams, boolean showIconNoAudio) {
        this.context = context;
        this.videoStreams = videoStreams;
        this.showIconNoAudio = showIconNoAudio;
    }

    @Override
    public int getCount() {
        return videoStreams.size();
    }

    @Override
    public Object getItem(int position) {
        return videoStreams.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getCustomView(position, convertView, parent, true);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getCustomView(((Spinner) parent).getSelectedItemPosition(), convertView, parent, false);
    }

    private View getCustomView(int position, View convertView, ViewGroup parent, boolean isDropdownItem) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.resolutions_spinner_item, parent, false);
        }

        ImageView woSoundIcon = convertView.findViewById(R.id.wo_sound_icon);
        TextView text = convertView.findViewById(android.R.id.text1);
        VideoStream item = (VideoStream) getItem(position);
        text.setText(item.getFormat().getName() + " " + item.getResolution());

        int visibility = !showIconNoAudio ? View.GONE
                : item.isVideoOnly ? View.VISIBLE
                : isDropdownItem ? View.INVISIBLE
                : View.GONE;
        woSoundIcon.setVisibility(visibility);

        return convertView;
    }

}