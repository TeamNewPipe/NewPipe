package org.schabi.newpipe.util;

import android.os.Build;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.util.Collections;

public final class EdgeToEdgeWorkaround {
    private static final String TAG = EdgeToEdgeWorkaround.class.getSimpleName();
    private static final long ENFORCE_EDGE_TO_EDGE = 309578419L;

    private EdgeToEdgeWorkaround() {
    }

    public static void apply() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }

        try {
            final Class<?> changeConfigClass =
                    Class.forName("android.compat.Compatibility$ChangeConfig");
            final Object changeConfig = HiddenApiBypass.newInstance(changeConfigClass,
                    Collections.emptySet(), Collections.singleton(ENFORCE_EDGE_TO_EDGE));
            final Class<?> compatibilityClass = Class.forName("android.compat.Compatibility");
            HiddenApiBypass.invoke(compatibilityClass, null, "setOverrides", changeConfig);
        } catch (final Throwable throwable) {
            Log.e(TAG, "Unable to disable edge-to-edge enforcement", throwable);
        }
    }
}
