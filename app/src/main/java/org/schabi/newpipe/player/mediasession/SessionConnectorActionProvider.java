package org.schabi.newpipe.player.mediasession;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.player.notification.NotificationActionData;

import java.lang.ref.WeakReference;

public class SessionConnectorActionProvider implements MediaSessionConnector.CustomActionProvider {

    private final NotificationActionData data;
    @NonNull
    private final WeakReference<Context> context;

    public SessionConnectorActionProvider(final NotificationActionData notificationActionData,
                                          @NonNull final Context context) {
        this.data = notificationActionData;
        this.context = new WeakReference<>(context);
    }

    @Override
    public void onCustomAction(@NonNull final Player player,
                               @NonNull final String action,
                               @Nullable final Bundle extras) {
        final Context actualContext = context.get();
        if (actualContext != null) {
            actualContext.sendBroadcast(new Intent(action));
        }
    }

    @Nullable
    @Override
    public PlaybackStateCompat.CustomAction getCustomAction(@NonNull final Player player) {
        return new PlaybackStateCompat.CustomAction.Builder(
                data.action(), data.name(), data.icon()
        ).build();
    }
}
