package org.schabi.newpipe.local.subscription

import android.content.Context
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding

/** Presentation only; group creation and configured search remain owned by their existing hosts. */
internal class SubscriptionFloatingActions(
    private val binding: FragmentSubscriptionBinding,
    createGroup: () -> Unit,
    search: () -> Unit,
    private val onSearchVisibilityChanged: () -> Unit
) {
    private val initialBottomPadding = binding.itemsList.paddingBottom
    private val initialClipToPadding = binding.itemsList.clipToPadding
    private val clearance = binding.root.resources.getDimensionPixelSize(R.dimen.subscription_floating_action_size) +
        2 * binding.root.resources.getDimensionPixelSize(R.dimen.margin_normal)
    private var active = false
    private var available = false
    private var keyboardVisible = false

    val searchIsVisible: Boolean
        get() = binding.subscriptionFloatingActions.isVisible && binding.subscriptionFloatingSearch.isVisible

    init {
        binding.subscriptionFloatingCreate.setOnClickListener {
            if (binding.subscriptionFloatingActions.isVisible) createGroup()
        }
        binding.subscriptionFloatingSearch.setOnClickListener {
            if (searchIsVisible) search()
        }
        TooltipCompat.setTooltipText(binding.subscriptionFloatingCreate, binding.subscriptionFloatingCreate.contentDescription)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val effective = ViewCompat.getRootWindowInsets(view) ?: insets
            val visible = effective.isVisible(WindowInsetsCompat.Type.ime())
            if (visible != keyboardVisible) {
                keyboardVisible = visible
                render()
            }
            insets
        }
        binding.root.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    fun update(showActions: Boolean, searchAvailable: Boolean, searchLabel: String, searchIcon: Int) {
        active = showActions
        available = searchAvailable
        binding.subscriptionFloatingSearch.contentDescription = searchLabel
        binding.subscriptionFloatingSearch.setImageResource(searchIcon)
        TooltipCompat.setTooltipText(binding.subscriptionFloatingSearch, searchLabel)
        render()
    }

    private fun render() {
        val previousSearch = searchIsVisible
        val visible = active && !keyboardVisible
        binding.subscriptionFloatingActions.isVisible = visible
        binding.subscriptionFloatingSearch.isVisible = available
        val padding = initialBottomPadding + if (visible) clearance else 0
        binding.itemsList.setPaddingRelative(
            binding.itemsList.paddingStart,
            binding.itemsList.paddingTop,
            binding.itemsList.paddingEnd,
            padding
        )
        binding.itemsList.clipToPadding = if (visible) false else initialClipToPadding
        if (previousSearch != searchIsVisible) onSearchVisibilityChanged()
    }

    fun dispose() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, null)
        binding.subscriptionFloatingCreate.setOnClickListener(null)
        binding.subscriptionFloatingSearch.setOnClickListener(null)
        binding.subscriptionFloatingActions.isVisible = false
        binding.itemsList.setPaddingRelative(
            binding.itemsList.paddingStart,
            binding.itemsList.paddingTop,
            binding.itemsList.paddingEnd,
            initialBottomPadding
        )
        binding.itemsList.clipToPadding = initialClipToPadding
    }

    companion object {
        fun enabled(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(context.getString(R.string.subscription_actions_style_key), "classic") == "floating"
    }
}
