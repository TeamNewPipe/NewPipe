/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments.list.comments

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePostCommentsTarget

/** Standalone host for the same comments, search and replies used by the video player. */
class PostCommentsFragment : CommentsFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = requireNotNull(
            YoutubePostCommentsTarget.fromUrl(requireArguments().getString(ARG_POST_URL))
        )
        setInitialData(ServiceList.YouTube.serviceId, target.canonicalUrl, getString(R.string.post_comments_title))
    }

    override fun onResume() {
        super.onResume()
        setTitle(null)
    }

    override fun setTitle(title: String?) {
        (getActivity() as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            setTitle(R.string.post_comments_title)
            subtitle = null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        (getActivity() as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    companion object {
        const val TAG = "PostCommentsFragment"
        private const val ARG_POST_URL = "post_comments_url"

        fun newInstance(url: String): PostCommentsFragment {
            val target = requireNotNull(YoutubePostCommentsTarget.fromUrl(url))
            return PostCommentsFragment().apply {
                arguments = bundleOf(ARG_POST_URL to target.canonicalUrl)
            }
        }
    }
}
