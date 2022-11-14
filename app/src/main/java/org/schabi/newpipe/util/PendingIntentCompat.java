package org.schabi.newpipe.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

public final class PendingIntentCompat {
    private PendingIntentCompat() {
    }

    private static int addImmutableFlag(final int flags) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? flags | PendingIntent.FLAG_IMMUTABLE : flags;
    }

    /**
     * Creates a {@link PendingIntent} to start an activity. It is immutable on API level 23 and
     * greater.
     *
     * @param context     The context in which the activity should be started.
     * @param requestCode The request code
     * @param intent      The Intent of the activity to be launched.
     * @param flags       The flags for the intent.
     * @return The pending intent.
     * @see PendingIntent#getActivity(Context, int, Intent, int)
     */
    @NonNull
    public static PendingIntent getActivity(@NonNull final Context context, final int requestCode,
                                            @NonNull final Intent intent, final int flags) {
        return PendingIntent.getActivity(context, requestCode, intent, addImmutableFlag(flags));
    }

    /**
     * Creates a {@link PendingIntent} to start a service. It is immutable on API level 23 and
     * greater.
     *
     * @param context     The context in which the service should be started.
     * @param requestCode The request code
     * @param intent      The Intent of the service to be launched.
     * @param flags       The flags for the intent.
     * @return The pending intent.
     * @see PendingIntent#getService(Context, int, Intent, int)
     */
    @NonNull
    public static PendingIntent getService(@NonNull final Context context, final int requestCode,
                                           @NonNull final Intent intent, final int flags) {
        return PendingIntent.getService(context, requestCode, intent, addImmutableFlag(flags));
    }

    /**
     * Creates a {@link PendingIntent} to perform a broadcast. It is immutable on API level 23 and
     * greater.
     *
     * @param context     The context in which the broadcast should be performed.
     * @param requestCode The request code
     * @param intent      The Intent to be broadcast.
     * @param flags       The flags for the intent.
     * @return The pending intent.
     * @see PendingIntent#getBroadcast(Context, int, Intent, int)
     */
    @NonNull
    public static PendingIntent getBroadcast(@NonNull final Context context, final int requestCode,
                                             @NonNull final Intent intent, final int flags) {
        return PendingIntent.getBroadcast(context, requestCode, intent, addImmutableFlag(flags));
    }
}
