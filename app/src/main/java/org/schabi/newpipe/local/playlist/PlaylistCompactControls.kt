package org.schabi.newpipe.local.playlist

import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import org.schabi.newpipe.R

/** Presentation only: the fragments and selection controller retain their existing actions. */
object PlaylistCompactControls {
    @JvmStatic
    fun update(root: View, editing: Boolean) {
        val localActions = root.findViewById<View>(R.id.local_playlist_edit_controls)
        localActions?.isVisible = true
        val select = root.findViewById<View>(R.id.collection_select_move)
        select.isVisible = localActions != null || editing
        val edit = root.findViewById<ImageView>(R.id.playlist_edit_toggle)
        edit.setImageResource(if (editing) R.drawable.ic_done else R.drawable.ic_edit)
        edit.contentDescription = root.context.getString(
            if (editing) R.string.playlist_edit_done else R.string.playlist_edit_mode
        )
        for (action in listOfNotNull(edit, select, root.findViewById<View>(R.id.playlist_category_manage))) {
            TooltipCompat.setTooltipText(action, action.contentDescription)
        }
    }
}
