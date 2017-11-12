package org.schabi.newpipe.info_list;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;

public class InfoItemDialog {
    private final AlertDialog dialog;

    public InfoItemDialog(@NonNull final Activity activity,
                          @NonNull final InfoItem item,
                          @NonNull final String[] commands,
                          @NonNull final DialogInterface.OnClickListener actions) {

        final LayoutInflater inflater = activity.getLayoutInflater();
        final View bannerView = inflater.inflate(R.layout.dialog_title, null);
        bannerView.setSelected(true);
        TextView titleView = bannerView.findViewById(R.id.itemTitleView);
        titleView.setText(item.name);
        TextView typeView = bannerView.findViewById(R.id.itemTypeView);
        typeView.setText(item.info_type.name());

        dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(bannerView)
                .setItems(commands, actions)
                .create();
    }

    public void show() {
        dialog.show();
    }
}
