package org.schabi.newpipe.info_list;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.databinding.DialogTitleBinding;
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
        final DialogTitleBinding binding = DialogTitleBinding
                .inflate(LayoutInflater.from(activity));
        binding.getRoot().setSelected(true);

        binding.itemTitleView.setText(title);

        if (additionalDetail != null) {
            binding.itemAdditionalDetails.setText(additionalDetail);
            binding.itemAdditionalDetails.setVisibility(View.VISIBLE);
        } else {
            binding.itemAdditionalDetails.setVisibility(View.GONE);
        }

        dialog = new AlertDialog.Builder(activity)
                .setCustomTitle(binding.getRoot())
                .setItems(commands, actions)
                .create();
    }

    public void show() {
        dialog.show();
    }
}
