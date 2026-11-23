/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments.list.comments

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.fragment.app.FragmentActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.post.PostInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePostCommentsTarget

/** Keeps the post's comments action separate from its text and attachment gestures. */
object PostCommentsNavigation {
    @JvmStatic
    fun bind(button: View, post: PostInfoItem, context: Context) {
        val target = if (post.serviceId == ServiceList.YouTube.serviceId) {
            YoutubePostCommentsTarget.fromUrl(post.url)
        } else {
            null
        }
        val activity = findActivity(context)
        val available = target != null && activity != null
        button.visibility = if (available) View.VISIBLE else View.GONE
        button.isEnabled = available
        button.setOnClickListener(
            if (target != null && activity != null) {
                View.OnClickListener { open(activity, target.canonicalUrl) }
            } else {
                null
            }
        )
    }

    fun open(activity: FragmentActivity, url: String) {
        val target = YoutubePostCommentsTarget.fromUrl(url) ?: return
        val manager = activity.supportFragmentManager
        if (manager.isStateSaved) return
        manager.beginTransaction()
            .setCustomAnimations(
                R.animator.custom_fade_in,
                R.animator.custom_fade_out,
                R.animator.custom_fade_in,
                R.animator.custom_fade_out
            )
            .replace(
                R.id.fragment_holder,
                PostCommentsFragment.newInstance(target.canonicalUrl),
                PostCommentsFragment.TAG
            )
            .addToBackStack(PostCommentsFragment.TAG)
            .commit()
    }

    private tailrec fun findActivity(context: Context): FragmentActivity? = when {
        context is FragmentActivity -> context
        context is ContextWrapper && context.baseContext !== context -> findActivity(context.baseContext)
        else -> null
    }
}
