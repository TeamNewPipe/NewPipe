package org.schabi.newpipe.local.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.DialogPlaylistNameBinding;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public final class PlaylistCreationDialog extends PlaylistDialog {
    private DialogPlaylistNameBinding binding;
    private Disposable disposable;

    public static PlaylistCreationDialog newInstance(final List<StreamEntity> streams) {
        final PlaylistCreationDialog dialog = new PlaylistCreationDialog();
        dialog.setInfo(streams);
        return dialog;
    }

    public static PlaylistCreationDialog newInstance(final PlaylistAppendDialog appendDialog) {
        final PlaylistCreationDialog dialog = new PlaylistCreationDialog();
        dialog.setInfo(appendDialog.getStreams());
        return dialog;
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

        binding = DialogPlaylistNameBinding.inflate(LayoutInflater.from(requireContext()));

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.create_playlist)
                .setView(binding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (dialogInterface, i) -> {
                    final String name = binding.playlistName.getText().toString();
                    final LocalPlaylistManager playlistManager =
                            new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));
                    final Toast successToast = Toast.makeText(getActivity(),
                            R.string.playlist_creation_success,
                            Toast.LENGTH_SHORT);

                    disposable = playlistManager.createPlaylist(name, getStreams())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(longs -> successToast.show());
                });

        return dialogBuilder.create();
    }

    @Override
    public void onDismiss(@NonNull @NotNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (disposable != null) {
            disposable.dispose();
        }
        binding = null;
    }
}
