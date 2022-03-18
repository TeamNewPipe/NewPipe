package org.schabi.newpipe.info_list;
import java.util.ArrayList;
import java.util.List;
import android.app.Activity;
import android.content.DialogInterface;
import android.view.View;
import android.widget.TextView;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.StreamDialogDefaultEntry;
import org.schabi.newpipe.util.StreamDialogEntry;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

public class InfoItemDialog {
    private final AlertDialog dialog;

    public InfoItemDialog(@NonNull final Activity activity,
                          @NonNull final StreamInfoItem info,
                          @NonNull final String[] commands,
                          @NonNull final DialogInterface.OnClickListener actions) {
        this(activity, commands, actions, info.getName(), info.getUploaderName());
    }

    public InfoItemDialog(@NonNull final Activity activity,
                          @NonNull final String[] commands,
                          @NonNull final DialogInterface.OnClickListener actions,
                          @NonNull final String title,
                          @Nullable final String additionalDetail) {

        final View bannerView = View.inflate(activity, R.layout.dialog_title, null);
        bannerView.setSelected(true);

        final TextView titleView = bannerView.findViewById(R.id.itemTitleView);
        titleView.setText(info.getName());

        final TextView detailsView = bannerView.findViewById(R.id.itemAdditionalDetails);
        if (additionalDetail != null) {
            detailsView.setText(additionalDetail);
        }
        if (info.getUploaderName() != null) {
            detailsView.setText(info.getUploaderName());
            detailsView.setVisibility(View.VISIBLE);
        } else {
            detailsView.setVisibility(View.GONE);
        }
        final String[] items = entries.stream()
                .map(entry -> entry.getString(activity)).toArray(String[]::new);

        final DialogInterface.OnClickListener action = (d, index) ->
                entries.get(index).action.onClick(fragment, info);

        dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(bannerView)
                .setItems(commands, actions)
                .create();
    }
    public void addChannelDetailsEntryIfPossible() {
        if (!isNullOrEmpty(info.getUploaderUrl())) {
            addEntry(StreamDialogDefaultEntry.SHOW_CHANNEL_DETAILS);
        }
    }

    public void addEnqueueEntriesIfNeeded() {
        if (PlayerHolder.getInstance().isPlayerOpen()) {
            addEntry(StreamDialogDefaultEntry.ENQUEUE);

            if (PlayerHolder.getInstance().getQueueSize() > 1) {
                addEntry(StreamDialogDefaultEntry.ENQUEUE_NEXT);
            }
        }
    }

    public void addStartHereEntries() {
        addEntry(StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND);
        if (info.getStreamType() != StreamType.AUDIO_STREAM
                && info.getStreamType() != StreamType.AUDIO_LIVE_STREAM) {
            addEntry(StreamDialogDefaultEntry.START_HERE_ON_POPUP);
        }
    }
    public void show() {
        dialog.show();
    }
}
