/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util.image

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image

/** In-app, swipe-free picture viewer; no external browser or tracking is needed. */
class CommentImageViewerDialog : DialogFragment() {
    private var currentIndex = 0
    private var imageView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIndex = savedInstanceState?.getInt(CURRENT_INDEX) ?: 0
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(CURRENT_INDEX, currentIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val urls = requireArguments().getStringArrayList(URLS).orEmpty()
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val preview = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            minimumHeight = (220 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.55f).toInt()
            )
        }
        imageView = preview
        layout.addView(preview)
        val counter = TextView(requireContext()).apply {
            gravity = Gravity.CENTER
        }
        layout.addView(counter)
        val navigation = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val previous = MaterialButton(requireContext()).apply {
            setText(R.string.comment_image_previous)
        }
        val next = MaterialButton(requireContext()).apply {
            setText(R.string.comment_image_next)
        }
        navigation.addView(previous)
        navigation.addView(next)
        layout.addView(navigation)

        fun refresh() {
            if (urls.isEmpty() || !ImageStrategy.shouldLoadImages()) {
                preview.setImageDrawable(null)
                return
            }
            currentIndex = currentIndex.coerceIn(0, urls.lastIndex)
            CoilHelper.loadThumbnail(preview, urls[currentIndex])
            counter.text = getString(
                R.string.comment_image_viewer_title,
                currentIndex + 1,
                urls.size
            )
            previous.isEnabled = currentIndex > 0
            next.isEnabled = currentIndex < urls.lastIndex
            navigation.visibility = if (urls.size > 1) android.view.View.VISIBLE else android.view.View.GONE
        }
        previous.setOnClickListener {
            currentIndex--
            refresh()
        }
        next.setOnClickListener {
            currentIndex++
            refresh()
        }
        refresh()
        return MaterialAlertDialogBuilder(requireContext())
            .setView(layout)
            .setPositiveButton(R.string.ok, null)
            .create()
    }

    override fun onDestroy() {
        imageView?.let(CoilHelper::clearImage)
        imageView = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "comment_image_viewer"
        private const val URLS = "urls"
        private const val CURRENT_INDEX = "index"

        @JvmStatic
        fun show(activity: FragmentActivity, pictures: Collection<Image>) {
            if (!ImageStrategy.shouldLoadImages()) return
            if (activity.supportFragmentManager.findFragmentByTag(TAG) != null) return
            val urls = pictures.map(Image::getUrl)
                .filter { it.length <= 2_048 && it.startsWith("https://", ignoreCase = true) }
                .distinct()
                .take(10)
            if (urls.isEmpty()) return
            val dialog = CommentImageViewerDialog()
            dialog.arguments = Bundle().apply { putStringArrayList(URLS, ArrayList(urls)) }
            dialog.show(activity.supportFragmentManager, TAG)
        }
    }
}
