package org.schabi.newpipe.fragments.list.comments

import android.view.View
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import java.util.function.BooleanSupplier
import org.schabi.newpipe.R

class CommentsMiniPlayerAction(
    private val canMinimize: BooleanSupplier,
    private val minimize: Runnable
) {
    private val available = MutableLiveData(false)

    fun update() {
        val next = canMinimize.asBoolean
        if (available.value != next) available.value = next
    }

    fun bind(root: View, owner: LifecycleOwner) {
        val button = root.findViewById<View>(R.id.comments_minimize_player)
        TooltipCompat.setTooltipText(button, root.context.getString(R.string.comments_minimize_player))
        button.setOnClickListener {
            if (canMinimize.asBoolean) {
                ViewCompat.getWindowInsetsController(root)?.hide(WindowInsetsCompat.Type.ime())
                minimize.run()
            }
        }
        update()
        available.observe(owner) { button.isVisible = it == true }
    }
}
