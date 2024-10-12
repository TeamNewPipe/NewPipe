package org.schabi.newpipe.local.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.util.StateSaver;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public abstract class PlaylistDialog extends DialogFragment implements StateSaver.WriteRead {

    @Nullable
    private DialogInterface.OnDismissListener onDismissListener = null;

    private List<StreamEntity> streamEntities;

    private org.schabi.newpipe.util.SavedState savedState;

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
    public void onSaveInstanceState(@NonNull final Bundle outState) {
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

    protected void setStreamEntities(final List<StreamEntity> streamEntities) {
        this.streamEntities = streamEntities;
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
     * @return the disposable that was created
     */
    public static Disposable createCorrespondingDialog(
            final Context context,
            final List<StreamEntity> streamEntities,
            final Consumer<PlaylistDialog> onExec) {

        return new LocalPlaylistManager(NewPipeDatabase.getInstance(context))
                .hasPlaylists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(hasPlaylists ->
                        onExec.accept(hasPlaylists
                                ? PlaylistAppendDialog.newInstance(streamEntities)
                                : PlaylistCreationDialog.newInstance(streamEntities))
                );
    }

    /**
     * Creates a {@link PlaylistAppendDialog} when playlists exists,
     * otherwise a {@link PlaylistCreationDialog}. If the player's play queue is null or empty, no
     * dialog will be created.
     *
     * @param player          the player from which to extract the context and the play queue
     * @param fragmentManager the fragment manager to use to show the dialog
     * @return the disposable that was created
     */
    public static Disposable showForPlayQueue(
            @NonNull final Player player,
            @NonNull final FragmentManager fragmentManager) {

        final List<StreamEntity> streamEntities = Stream.of(player.getPlayQueue())
                .filter(Objects::nonNull)
                .flatMap(playQueue -> playQueue.getStreams().stream())
                .map(StreamEntity::new)
                .collect(Collectors.toList());
        if (streamEntities.isEmpty()) {
            return Disposable.empty();
        }

        return PlaylistDialog.createCorrespondingDialog(player.getContext(), streamEntities,
                dialog -> dialog.show(fragmentManager, "PlaylistDialog"));
    }
}
