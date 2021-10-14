package org.schabi.newpipe.local.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.util.StateSaver;

import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public abstract class PlaylistDialog extends DialogFragment implements StateSaver.WriteRead {

    @Nullable
    private DialogInterface.OnDismissListener onDismissListener = null;

    private List<StreamEntity> streamEntities;

    private org.schabi.newpipe.util.SavedState savedState;

    public PlaylistDialog(final List<StreamEntity> streamEntities) {
        this.streamEntities = streamEntities;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        savedState = StateSaver.tryToRestore(savedInstanceState, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        StateSaver.onDestroy(savedState);
    }

    public List<StreamEntity> getStreamEntities() {
        return streamEntities;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Dialog dialog = super.onCreateDialog(savedInstanceState);
        //remove title
        final Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
        }
        return dialog;
    }

    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) {
            onDismissListener.onDismiss(dialog);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public String generateSuffix() {
        final int size = streamEntities == null ? 0 : streamEntities.size();
        return "." + size + ".list";
    }

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        objectsToSave.add(streamEntities);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull final Queue<Object> savedObjects) {
        streamEntities = (List<StreamEntity>) savedObjects.poll();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (getActivity() != null) {
            savedState = StateSaver.tryToSave(getActivity().isChangingConfigurations(),
                    savedState, outState, this);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Getter + Setter
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    public DialogInterface.OnDismissListener getOnDismissListener() {
        return onDismissListener;
    }

    public void setOnDismissListener(
            @Nullable final DialogInterface.OnDismissListener onDismissListener
    ) {
        this.onDismissListener = onDismissListener;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog creation
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Creates a {@link PlaylistAppendDialog} when playlists exists,
     * otherwise a {@link PlaylistCreationDialog}.
     *
     * @param context        context used for accessing the database
     * @param streamEntities used for crating the dialog
     * @param onExec         execution that should occur after a dialog got created, e.g. showing it
     * @return Disposable
     */
    public static Disposable createCorrespondingDialog(
            final Context context,
            final List<StreamEntity> streamEntities,
            final Consumer<PlaylistDialog> onExec
    ) {
        return new LocalPlaylistManager(NewPipeDatabase.getInstance(context))
                .hasPlaylists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(hasPlaylists ->
                        onExec.accept(hasPlaylists
                                ? new PlaylistAppendDialog(streamEntities)
                                : new PlaylistCreationDialog(streamEntities))
                );
    }
}
