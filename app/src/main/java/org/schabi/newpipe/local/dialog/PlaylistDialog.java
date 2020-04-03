package org.schabi.newpipe.local.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.util.StateSaver;

import java.util.List;
import java.util.Queue;

public abstract class PlaylistDialog extends DialogFragment implements StateSaver.WriteRead {
    private List<StreamEntity> streamEntities;

    private StateSaver.SavedState savedState;

    protected void setInfo(final List<StreamEntity> entities) {
        this.streamEntities = entities;
    }

    protected List<StreamEntity> getStreams() {
        return streamEntities;
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
}
