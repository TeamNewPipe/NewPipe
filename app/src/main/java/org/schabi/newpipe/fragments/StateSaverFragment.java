package org.schabi.newpipe.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.util.StateSaver;

public abstract class StateSaverFragment extends BaseFragment implements StateSaver.WriteRead {
    private org.schabi.newpipe.util.SavedState savedState;

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        savedState = StateSaver.tryToSave(
                requireActivity().isChangingConfigurations(), savedState, outState, this);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        savedState = StateSaver.tryToRestore(savedInstanceState, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        StateSaver.onDestroy(savedState);
    }
}
