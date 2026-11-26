package org.schabi.newpipe.local.subscription

import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import com.google.android.material.color.MaterialColors
import org.schabi.newpipe.R

/** A compact feed state beside the channel name, independent of audio and notification settings. */
internal object SubscriptionFeedMuteBadge {
    fun bind(label: TextView, muted: Boolean?) {
        val drawable = muted?.let {
            AppCompatResources.getDrawable(label.context, if (it) R.drawable.ic_feed_muted else R.drawable.ic_rss_feed)
                ?.mutate()?.apply {
                    DrawableCompat.setTint(this, MaterialColors.getColor(label, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    val size = (18 * label.resources.displayMetrics.density).toInt()
                    setBounds(0, 0, size, size)
                }
        }
        label.setCompoundDrawablesRelative(null, null, drawable, null)
        label.compoundDrawablePadding = if (drawable == null) 0 else (6 * label.resources.displayMetrics.density).toInt()
        ViewCompat.setStateDescription(
            label,
            muted?.let { label.context.getString(if (it) R.string.feed_muted_state else R.string.feed_unmuted_state) }
        )
    }
}
