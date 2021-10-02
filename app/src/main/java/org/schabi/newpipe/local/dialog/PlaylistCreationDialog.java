package org.schabi.newpipe.local.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

public final class PlaylistCreationDialog extends PlaylistDialog {
    @Nullable
    private OnDismissListener onDismissListener = null;

    public static PlaylistCreationDialog newInstance(final List<StreamEntity> streams) {
        final PlaylistCreationDialog dialog = new PlaylistCreationDialog();
        dialog.setInfo(streams);
        return dialog;
    }

    public static PlaylistCreationDialog newInstance(final PlaylistAppendDialog appendDialog) {
        final PlaylistCreationDialog dialog = new PlaylistCreationDialog();
        dialog.setInfo(appendDialog.getStreams());
        dialog.setOnDismissListener(appendDialog.getOnDismissListener());
        return dialog;
    }

    public void setOnDismissListener(@Nullable final OnDismissListener onDismissListener) {
        this.onDismissListener = onDismissListener;
    }

    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) {
            onDismissListener.onDismiss(dialog);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        if (getStreams() == null) {
            return super.onCreateDialog(savedInstanceState);
        }

        final DialogEditTextBinding dialogBinding
                = DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.create_playlist)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (dialogInterface, i) -> {
                    final String name = dialogBinding.dialogEditText.getText().toString();
                    final LocalPlaylistManager playlistManager =
                            new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));
                    final Toast successToast = Toast.makeText(getActivity(),
                            R.string.playlist_creation_success,
                            Toast.LENGTH_SHORT);

                    playlistManager.createPlaylist(name, getStreams())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(longs -> successToast.show());
                });

        return dialogBuilder.create();
    }
}
