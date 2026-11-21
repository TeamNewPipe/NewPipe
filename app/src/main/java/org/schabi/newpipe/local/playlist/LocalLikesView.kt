/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.recommendations.RecommendationWorkDispatcher

/** A single local-only like action; never alters the service's public like count. */
class LocalLikesView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val controlsPreferenceKey = context.getString(R.string.show_local_like_controls_key)
    private val tasks = CompositeDisposable()
    private val like = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
    private val minimumTouchSize = (48 * resources.displayMetrics.density + 0.5f).toInt()
    private var info: StreamInfo? = null
    private var generation = 0
    private var profileId = ""
    private var liked = false
    private var loaded = false
    private var busy = false
    private var snackbar: Snackbar? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (
            key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY ||
            key == controlsPreferenceKey
        ) {
            refresh()
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.START
        val density = resources.displayMetrics.density
        val outerPadding = (12 * density + 0.5f).toInt()
        setPadding(outerPadding, 0, outerPadding, 0)

        like.isCheckable = true
        like.text = ""
        like.minimumWidth = minimumTouchSize
        like.minWidth = minimumTouchSize
        like.minimumHeight = minimumTouchSize
        like.minHeight = minimumTouchSize
        like.iconSize = (24 * density + 0.5f).toInt()
        like.iconPadding = 0
        like.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        like.setPaddingRelative(0, 0, 0, 0)
        addView(like, LayoutParams(minimumTouchSize, minimumTouchSize))
        like.setOnClickListener { save() }
        like.setOnLongClickListener {
            message(R.string.local_like_explanation)
            true
        }
        render()
        visibility = GONE
    }

    fun bind(streamInfo: StreamInfo?) {
        info = streamInfo
        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        refresh()
    }

    override fun onDetachedFromWindow() {
        generation++
        tasks.clear()
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        snackbar?.dismiss()
        super.onDetachedFromWindow()
    }

    private fun refresh() {
        val token = ++generation
        tasks.clear()
        snackbar?.dismiss()
        liked = false
        loaded = false
        busy = false
        val stream = info
        val eligible = preferences.getBoolean(controlsPreferenceKey, true) &&
            stream?.streamType in setOf(
                StreamType.VIDEO_STREAM,
                StreamType.LIVE_STREAM,
                StreamType.POST_LIVE_STREAM
            )
        visibility = if (eligible) VISIBLE else GONE
        render()
        if (!eligible || stream == null || !isAttachedToWindow) return
        profileId = ProfileManager.getActiveProfileId(context)
        observeLikedState(token, stream)
    }

    /**
     * Observe only the state for the current video/profile. Cancel the old observation before a
     * local write: queued Room emissions from before the write must never replace its result.
     */
    private fun observeLikedState(token: Int, stream: StreamInfo) {
        tasks.add(
            store().observeLiked(stream.serviceId, stream.url)
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ saved ->
                    if (!active(token) || busy) return@subscribe
                    liked = saved
                    loaded = true
                    render()
                }, {
                    if (active(token) && !busy) message(R.string.local_like_failed)
                })
        )
    }

    private fun store() = LikedVideosStore(NewPipeDatabase.getInstance(context), preferences, profileId, context.getString(R.string.local_liked_videos))

    private fun active(token: Int): Boolean = token == generation && isAttachedToWindow &&
        ProfileManager.getActiveProfileId(context) == profileId

    private fun save() {
        val stream = info ?: return
        if (!loaded || busy || !active(generation)) return
        val token = generation
        val desired = !liked
        val target = store()
        val entity = StreamEntity(stream)
        // Prevent already-queued snapshots from racing with the completed write on the UI thread.
        tasks.clear()
        busy = true
        render()
        tasks.add(
            Single.fromCallable { target.setLiked(entity, desired) }
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ saved ->
                    if (!active(token)) return@subscribe
                    liked = saved
                    RecommendationWorkDispatcher.liked(context, stream, saved, profileId)
                    busy = false
                    loaded = true
                    render()
                    // Resubscribe only after the transaction has committed, to track later edits.
                    observeLikedState(token, stream)
                    message(if (saved) R.string.local_like_saved else R.string.local_like_removed)
                }, {
                    if (!active(token)) return@subscribe
                    busy = false
                    loaded = false
                    render()
                    // Restore the control from the database rather than trusting a failed write.
                    observeLikedState(token, stream)
                    message(R.string.local_like_failed)
                })
        )
    }

    private fun render() {
        like.isChecked = liked
        like.setIconResource(
            if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_outline
        )
        like.contentDescription = context.getString(
            if (liked) R.string.local_unlike_description else R.string.local_like_description
        )
        like.isEnabled = loaded && !busy
    }

    private fun message(text: Int) {
        if (!isAttachedToWindow) return
        snackbar?.dismiss()
        snackbar = Snackbar.make(this, text, Snackbar.LENGTH_LONG).also { it.show() }
    }
}
