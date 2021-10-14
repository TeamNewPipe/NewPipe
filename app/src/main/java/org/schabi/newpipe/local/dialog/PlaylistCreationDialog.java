package org.schabi.newpipe.local.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog.Builder;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

public final class PlaylistCreationDialog extends PlaylistDialog {
    public PlaylistCreationDialog(final List<StreamEntity> streamEntities) {
        super(streamEntities);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        if (getStreamEntities() == null) {
            return super.onCreateDialog(savedInstanceState);
        }

        final DialogEditTextBinding dialogBinding
                = DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.getRoot().getContext().setTheme(ThemeHelper.getDialogTheme(requireContext()));
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);

        final Builder dialogBuilder = new Builder(requireContext(),
                ThemeHelper.getDialogTheme(requireContext()))
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

                    playlistManager.createPlaylist(name, getStreamEntities())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(longs -> successToast.show());
                });
        return dialogBuilder.create();
    }
}
