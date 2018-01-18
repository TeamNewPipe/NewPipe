package org.schabi.newpipe.fragments.local;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.Collections;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;

public class PlaylistCreationDialog extends DialogFragment {
    private static final String TAG = PlaylistCreationDialog.class.getCanonicalName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    private static final String INFO_KEY = "info_key";

    private StreamInfo streamInfo;

    public static PlaylistCreationDialog newInstance(final StreamInfo info) {
        PlaylistCreationDialog dialog = new PlaylistCreationDialog();
        dialog.setInfo(info);
        return dialog;
    }

    private void setInfo(final StreamInfo info) {
        this.streamInfo = info;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (streamInfo != null) {
            outState.putSerializable(INFO_KEY, streamInfo);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null && streamInfo == null) {
            final Object infoCandidate = savedInstanceState.getSerializable(INFO_KEY);
            if (infoCandidate != null && infoCandidate instanceof StreamInfo) {
                streamInfo = (StreamInfo) infoCandidate;
            }
        }

        if (streamInfo == null) return super.onCreateDialog(savedInstanceState);

        View dialogView = View.inflate(getContext(),
                R.layout.dialog_create_playlist, null);
        EditText nameInput = dialogView.findViewById(R.id.playlist_name);

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext())
                .setTitle(R.string.create_playlist)
                .setView(dialogView)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (dialogInterface, i) -> {
                    final String name = nameInput.getText().toString();
                    final LocalPlaylistManager playlistManager =
                            new LocalPlaylistManager(NewPipeDatabase.getInstance(getContext()));
                    final List<StreamEntity> streams =
                            Collections.singletonList(new StreamEntity(streamInfo));
                    final Toast successToast = Toast.makeText(getActivity(),
                            "Playlist " + name + " successfully created",
                            Toast.LENGTH_SHORT);

                    playlistManager.createPlaylist(name, streams)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(longs -> successToast.show());
                });

        return dialogBuilder.create();
    }
}
