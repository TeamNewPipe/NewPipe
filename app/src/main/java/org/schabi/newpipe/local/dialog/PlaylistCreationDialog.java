package org.schabi.newpipe.local.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

public final class PlaylistCreationDialog extends PlaylistDialog {

    /**
     * Create a new instance of {@link PlaylistCreationDialog}.
     *
     * @param streamEntities    a list of {@link StreamEntity} to be added to playlists
     * @return a new instance of {@link PlaylistCreationDialog}
     */
    public static PlaylistCreationDialog newInstance(final List<StreamEntity> streamEntities) {
        final PlaylistCreationDialog dialog = new PlaylistCreationDialog();
        dialog.setStreamEntities(streamEntities);
        return dialog;
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

        final int dialogTheme = ThemeHelper.getDialogTheme(requireContext());
        final ContextThemeWrapper themedContext =
                new ContextThemeWrapper(requireContext(), dialogTheme);
        final DialogEditTextBinding dialogBinding =
                DialogEditTextBinding.inflate(LayoutInflater.from(themedContext));
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);

        final MaterialAlertDialogBuilder dialogBuilder =
                new MaterialAlertDialogBuilder(themedContext)
                .setTitle(R.string.create_playlist)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (dialogInterface, i) -> {
                    final String name = dialogBinding.dialogEditText.getText().toString();
                    final LocalPlaylistManager playlistManager =
                            new LocalPlaylistManager(
                                    NewPipeDatabase.getInstance(requireContext()),
                                    ProfileManager.getActiveProfileId(requireContext()));
                    final Toast successToast = Toast.makeText(getActivity(),
                            R.string.playlist_creation_success,
                            Toast.LENGTH_SHORT);

                    playlistManager.createPlaylist(name, getStreamEntities())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(longs -> successToast.show(), error -> {
                                android.util.Log.e("PlaylistCreationDialog",
                                        "Unable to save playlist", error);
                                Toast.makeText(themedContext, R.string.feed_quick_add_failed,
                                        Toast.LENGTH_LONG).show();
                            });
                });
        return dialogBuilder.create();
    }
}
