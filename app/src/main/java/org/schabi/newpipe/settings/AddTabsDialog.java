package org.schabi.newpipe.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

public class AddTabsDialog {
    private final AlertDialog dialog;

    public AddTabsDialog(@NonNull final Context context,
                          @NonNull final String title,
                          @NonNull final String[] commands,
                          @NonNull final DialogInterface.OnClickListener actions) {

        final View bannerView = View.inflate(context, R.layout.dialog_title, null);
        bannerView.setSelected(true);

        TextView titleView = bannerView.findViewById(R.id.itemTitleView);
        titleView.setText(title);

        TextView detailsView = bannerView.findViewById(R.id.itemAdditionalDetails);
        detailsView.setVisibility(View.GONE);

        dialog = new AlertDialog.Builder(context)
                .setCustomTitle(bannerView)
                .setItems(commands, actions)
                .create();
    }

    public void show() {
        dialog.show();
    }
}
