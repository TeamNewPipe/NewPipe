package org.schabi.newpipe.local.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

public final class PlaylistCreationDialog extends PlaylistDialog {
    @Inject
    LocalPlaylistManager playlistManager;

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

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        App.getApp().getAppComponent().inject(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        if (getStreams() == null) {
            return super.onCreateDialog(savedInstanceState);
        }

        final View dialogView = View.inflate(getContext(), R.layout.dialog_playlist_name, null);
        final EditText nameInput = dialogView.findViewById(R.id.playlist_name);

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext())
                .setTitle(R.string.create_playlist)
                .setView(dialogView)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (dialogInterface, i) -> {
                    final String name = nameInput.getText().toString();
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
