package org.schabi.newpipe.info_list;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.R;

import java.io.Serializable;

public class InfoItemDialog  {

    private final ItemDialogFragment idf;

    public InfoItemDialog(@NonNull final String title,
                          final String additionalInfo,
                          @NonNull final String[] commands,
                          @NonNull final OnClickListener actions) {

        idf = new ItemDialogFragment();
        final Bundle bundle = new Bundle();
        bundle.putString("InfoItemDialogTitle", title);
        bundle.putString("InfoItemDialogAdditionalInfo", additionalInfo);
        bundle.putCharSequenceArray("InfoItemDialogCommands", commands);

        final InfoItemDialogListener listener = (InfoItemDialogListener) actions::onClick;
        bundle.putSerializable("InfoItemDialogActions", listener);
        idf.setArguments(bundle);
    }

    public void show(@Nullable final FragmentManager fm) {
        if (fm != null) {
            idf.show(fm, "InfoItemDialog");
        }
    }

    public interface InfoItemDialogListener extends Serializable, OnClickListener { }

    private static class ItemDialogFragment extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            final Bundle args = requireArguments();
            final String title = args.getString("InfoItemDialogTitle");
            final String additionalInfo = args.getString("InfoItemDialogAdditionalInfo");
            final CharSequence[] commands = args.getCharSequenceArray("args");
            final OnClickListener listener = (OnClickListener) args
                    .getSerializable("InfoItemDialogActions");
            final View bannerView = getBannerView(title, additionalInfo);
            return new AlertDialog.Builder(requireContext())
                    .setCustomTitle(bannerView)
                    .setItems(commands, listener)
                    .create();
        }

        public View getBannerView(@NonNull final String title,
                                  @Nullable final String additionalDetail) {

            final View bannerView = View.inflate(requireContext(), R.layout.dialog_title, null);
            bannerView.setSelected(true);

            final TextView titleView = bannerView.findViewById(R.id.itemTitleView);
            titleView.setText(title);

            final TextView detailsView = bannerView.findViewById(R.id.itemAdditionalDetails);
            if (additionalDetail != null) {
                detailsView.setText(additionalDetail);
                detailsView.setVisibility(View.VISIBLE);
            } else {
                detailsView.setVisibility(View.GONE);
            }
            return bannerView;
        }
    }

}
