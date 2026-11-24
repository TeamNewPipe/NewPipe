package org.schabi.newpipe.themes

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.isVisible
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentFeedBinding
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.views.MainNavigationGeometry

/** Lets the selected home feed use the space behind the phone's navigation bars. */
internal class GlassFeedLayout private constructor(
    private val binding: FragmentFeedBinding,
    activity: MainActivity,
    private val setListBottomInset: (Int) -> Unit
) : ViewTreeObserver.OnPreDrawListener {
    private val holder = activity.findViewById<View>(R.id.fragment_holder)
    private val toolbar = activity.findViewById<View>(R.id.toolbar)
    private val incognito = activity.findViewById<View>(R.id.incognito_status)
    private val search = activity.findViewById<View>(R.id.toolbar_contextual_search_container)
    private val nav = activity.findViewById<View>(R.id.main_bottom_navigation)
    private val player = activity.findViewById<View>(R.id.fragment_player_holder)
    private val pager = activity.findViewById<View>(R.id.pager)
    private val playerBounds = Rect()
    private val observer = binding.root.viewTreeObserver
    private val refreshMargin = (binding.feedFloatingRefresh.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
    private val bannerMargin = (binding.newItemsLoadedButton.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
    private val toolbarClickable = toolbar.isClickable
    private var topMargin = 0
    private var bottomMargin = 0
    private var active = false
    private var appliedTop = 0
    private var appliedBottom = 0

    init {
        observer.addOnPreDrawListener(this)
    }

    override fun onPreDraw(): Boolean {
        val eligible = nav.isShown && nav.height > 0 && toolbar.height > 0 &&
            !incognito.isVisible && !search.isVisible &&
            binding.root.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
            !(player.isShown && player.getGlobalVisibleRect(playerBounds))
        if (!eligible) return !restore()
        val contentParams = holder.layoutParams as ViewGroup.MarginLayoutParams
        val pagerParams = pager.layoutParams as ViewGroup.MarginLayoutParams
        if (!active && (contentParams.topMargin <= 0 || pagerParams.bottomMargin <= 0)) return true
        if (contentParams.topMargin > 0) topMargin = contentParams.topMargin
        if (pagerParams.bottomMargin > 0) bottomMargin = pagerParams.bottomMargin
        if (active && contentParams.topMargin == 0 && pagerParams.bottomMargin == 0 &&
            appliedTop == toolbar.height && appliedBottom == MainNavigationGeometry.occupiedBottomHeight(nav)
        ) {
            return true
        }
        active = true
        contentParams.topMargin = 0
        pagerParams.bottomMargin = 0
        holder.layoutParams = contentParams
        pager.layoutParams = pagerParams
        toolbar.isClickable = true
        updateInsets(toolbar.height, MainNavigationGeometry.occupiedBottomHeight(nav))
        return false
    }

    fun detach() {
        if (observer.isAlive) observer.removeOnPreDrawListener(this)
        val current = binding.root.viewTreeObserver
        if (current !== observer && current.isAlive) current.removeOnPreDrawListener(this)
        restore()
    }

    private fun restore(): Boolean {
        if (!active) return false
        active = false
        val contentParams = holder.layoutParams as ViewGroup.MarginLayoutParams
        if (contentParams.topMargin == 0) {
            contentParams.topMargin = topMargin
            holder.layoutParams = contentParams
        }
        val pagerParams = pager.layoutParams as ViewGroup.MarginLayoutParams
        if (pagerParams.bottomMargin == 0) {
            pagerParams.bottomMargin = bottomMargin
            pager.layoutParams = pagerParams
        }
        toolbar.isClickable = toolbarClickable
        updateInsets(0, 0)
        return true
    }

    private fun updateInsets(top: Int, bottom: Int) {
        appliedTop = top
        appliedBottom = bottom
        val space = binding.feedGlassTopSpace
        space.layoutParams = space.layoutParams.apply { height = top }
        setListBottomInset(bottom)
        setBottomMargin(binding.feedFloatingRefresh, refreshMargin + bottom)
        setBottomMargin(binding.newItemsLoadedButton, bannerMargin + bottom)
    }

    private fun setBottomMargin(view: View, margin: Int) {
        val params = view.layoutParams as ViewGroup.MarginLayoutParams
        params.bottomMargin = margin
        view.layoutParams = params
    }

    companion object {
        fun attach(binding: FragmentFeedBinding, activity: MainActivity, setListBottomInset: (Int) -> Unit): GlassFeedLayout? {
            val pack = ThemePacks.active(activity) ?: return null
            if (pack.glass?.scrollUnderBars != true || pack.skin != null ||
                DeviceUtils.isTablet(activity) || DeviceUtils.isTv(activity) ||
                (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
            ) {
                return null
            }
            return GlassFeedLayout(binding, activity, setListBottomInset)
        }
    }
}
