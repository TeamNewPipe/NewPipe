package org.schabi.newpipe.playlist;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;

import java.util.Locale;


public class PlaylistItemBuilder {

    private static final String TAG = PlaylistItemBuilder.class.toString();

    public interface OnSelectedListener {
        void selected(int serviceId, String url, String title);
    }

    private OnSelectedListener onStreamInfoItemSelectedListener;

    public PlaylistItemBuilder() {}

    public void setOnSelectedListener(OnSelectedListener listener) {
        this.onStreamInfoItemSelectedListener = listener;
    }

    public View buildView(ViewGroup parent, final PlayQueueItem item) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View itemView = inflater.inflate(R.layout.stream_item, parent, false);
        final PlayQueueItemHolder holder = new PlayQueueItemHolder(itemView);

        buildStreamInfoItem(holder, item);

        return itemView;
    }


    public void buildStreamInfoItem(PlayQueueItemHolder holder, final PlayQueueItem item) {
        if (!TextUtils.isEmpty(item.getTitle())) holder.itemVideoTitleView.setText(item.getTitle());

        if (item.getDuration() > 0) {
            holder.itemDurationView.setText(getDurationString(item.getDuration()));
        } else {
            holder.itemDurationView.setVisibility(View.GONE);
        }

        holder.itemRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(onStreamInfoItemSelectedListener != null) {
                    onStreamInfoItemSelectedListener.selected(item.getServiceId(), item.getUrl(), item.getTitle());
                }
            }
        });
    }


    public static String getDurationString(int duration) {
        if(duration < 0) {
            duration = 0;
        }
        String output;
        int days = duration / (24 * 60 * 60); /* greater than a day */
        duration %= (24 * 60 * 60);
        int hours = duration / (60 * 60); /* greater than an hour */
        duration %= (60 * 60);
        int minutes = duration / 60;
        int seconds = duration % 60;

        //handle days
        if (days > 0) {
            output = String.format(Locale.US, "%d:%02d:%02d:%02d", days, hours, minutes, seconds);
        } else if(hours > 0) {
            output = String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            output = String.format(Locale.US, "%d:%02d", minutes, seconds);
        }
        return output;
    }
}
