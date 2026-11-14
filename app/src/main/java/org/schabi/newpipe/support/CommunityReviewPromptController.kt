/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.support

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.util.KEY_LINK_TYPE
import org.schabi.newpipe.util.KEY_OPEN_SEARCH

class CommunityReviewPromptController @JvmOverloads constructor(
    private val activity: AppCompatActivity,
    private val sessions: CommunityReviewSessions = CommunityReviews.sessions(activity)
) : DefaultLifecycleObserver {
    private val attempt = Runnable { maybeShow() }
    private var launchIntent = activity.intent

    init {
        activity.lifecycle.addObserver(this)
    }

    fun onNewIntent(intent: Intent) {
        launchIntent = intent
    }

    override fun onResume(owner: LifecycleOwner) {
        // Give launch notices and permission dialogs priority over this optional request.
        activity.window.decorView.removeCallbacks(attempt)
        activity.window.decorView.postDelayed(attempt, 1_000)
    }

    override fun onPause(owner: LifecycleOwner) {
        activity.window.decorView.removeCallbacks(attempt)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        activity.window.decorView.removeCallbacks(attempt)
    }

    internal fun maybeShow() {
        if (!sessions.state.shouldPrompt(System.currentTimeMillis()) || !sessions.claimAttempt()) return
        val intent = launchIntent
        val fragments = activity.supportFragmentManager
        if (CommunityReviews.destination(activity) == null || activity.isFinishing || activity.isDestroyed ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || fragments.isStateSaved ||
            !activity.hasWindowFocus() || fragments.fragments.any { it is DialogFragment } ||
            PlayerHolder.getInstance().isPlaying ||
            (intent.action != null && intent.action != Intent.ACTION_MAIN) ||
            intent.hasExtra(KEY_LINK_TYPE) || intent.hasExtra(KEY_OPEN_SEARCH)
        ) {
            return
        }
        CommunityReviewDialogFragment().showNow(fragments, CommunityReviewDialogFragment.TAG)
    }
}
