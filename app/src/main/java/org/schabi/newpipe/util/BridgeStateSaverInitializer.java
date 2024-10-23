package org.schabi.newpipe.util;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.state.StateSaver;
import com.livefront.bridge.Bridge;
import com.livefront.bridge.SavedStateHandler;
import com.livefront.bridge.ViewSavedStateHandler;

/**
 * Configures Bridge's state saver.
 */
public final class BridgeStateSaverInitializer {

    public static void init(final Context context) {
        Bridge.initialize(
            context,
            new SavedStateHandler() {
                @Override
                public void saveInstanceState(
                    @NonNull final Object target,
                    @NonNull final Bundle state) {
                    StateSaver.saveInstanceState(target, state);
                }

                @Override
                public void restoreInstanceState(
                    @NonNull final Object target,
                    @Nullable final Bundle state) {
                    StateSaver.restoreInstanceState(target, state);
                }
            },
            new ViewSavedStateHandler() {
                @NonNull
                @Override
                public <T extends View> Parcelable saveInstanceState(
                    @NonNull final T target,
                    @Nullable final Parcelable parentState) {
                    return StateSaver.saveInstanceState(target, parentState);
                }

                @Nullable
                @Override
                public <T extends View> Parcelable restoreInstanceState(
                    @NonNull final T target,
                    @Nullable final Parcelable state) {
                    return StateSaver.restoreInstanceState(target, state);
                }
            }
        );
    }

    private BridgeStateSaverInitializer() {
    }
}
