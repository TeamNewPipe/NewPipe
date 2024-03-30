package org.schabi.newpipe.player.mediasession

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.CustomActionProvider
import org.schabi.newpipe.player.notification.NotificationActionData
import java.lang.ref.WeakReference

class SessionConnectorActionProvider(private val data: NotificationActionData?,
                                     context: Context) : CustomActionProvider {
    private val context: WeakReference<Context>

    init {
        this.context = WeakReference(context)
    }

    public override fun onCustomAction(player: Player,
                                       action: String,
                                       extras: Bundle?) {
        val actualContext: Context? = context.get()
        if (actualContext != null) {
            actualContext.sendBroadcast(Intent(action))
        }
    }

    public override fun getCustomAction(player: Player): PlaybackStateCompat.CustomAction? {
        return PlaybackStateCompat.CustomAction.Builder(
                data!!.action(), data.name(), data.icon()
        ).build()
    }
}
