/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.recommendations.LocalRecommendations
import org.schabi.newpipe.util.NavigationHelper

/** Explicit local feedback controls, separate from the service's public like count. */
class LocalLikesView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val tasks = CompositeDisposable()
    private val like = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
    private val library = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
    private var info: StreamInfo? = null
    private var generation = 0
    private var profileId = ""
    private var liked = false
    private var loaded = false
    private var busy = false
    private var snackbar: Snackbar? = null
    private val profileListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY) refresh()
    }

    init {
        orientation = HORIZONTAL
        val padding = (12 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, 0)
        like.isCheckable = true
        like.setIconResource(R.drawable.ic_thumb_up)
        library.setIconResource(R.drawable.ic_playlist_play)
        library.setText(R.string.local_liked_videos)
        addView(like, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(library, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        like.setOnClickListener { save() }
        like.setOnLongClickListener {
            message(R.string.local_like_explanation)
            true
        }
        library.setOnClickListener { openLibrary() }
        visibility = GONE
    }

    fun bind(streamInfo: StreamInfo?) {
        info = streamInfo
        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(profileListener)
        refresh()
    }

    override fun onDetachedFromWindow() {
        generation++
        tasks.clear()
        preferences.unregisterOnSharedPreferenceChangeListener(profileListener)
        snackbar?.dismiss()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val direction = if (w / resources.displayMetrics.density < 360 || resources.configuration.fontScale > 1.3f) VERTICAL else HORIZONTAL
        if (orientation != direction) {
            orientation = direction
            for (button in listOf(like, library)) {
                button.layoutParams = if (direction == VERTICAL) {
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                } else {
                    LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                }
            }
        }
    }

    private fun refresh() {
        val token = ++generation
        tasks.clear()
        snackbar?.dismiss()
        liked = false
        loaded = false
        busy = false
        val stream = info
        val eligible = stream?.streamType in setOf(StreamType.VIDEO_STREAM, StreamType.LIVE_STREAM, StreamType.POST_LIVE_STREAM)
        visibility = if (eligible) VISIBLE else GONE
        render()
        if (!eligible || stream == null || !isAttachedToWindow) return
        profileId = ProfileManager.getActiveProfileId(context)
        tasks.add(
            store().observeLiked(stream.serviceId, stream.url)
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ saved ->
                    if (!active(token)) return@subscribe
                    liked = saved
                    loaded = true
                    render()
                }, { if (active(token)) message(R.string.local_like_failed) })
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
        busy = true
        render()
        tasks.add(
            Single.fromCallable { target.setLiked(entity, desired) }
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ saved ->
                    if (!active(token)) return@subscribe
                    liked = saved
                    LocalRecommendations.liked(context, stream, saved)
                    busy = false
                    render()
                    message(if (saved) R.string.local_like_saved else R.string.local_like_removed)
                }, {
                    if (!active(token)) return@subscribe
                    busy = false
                    render()
                    message(R.string.local_like_failed)
                })
        )
    }

    private fun openLibrary() {
        if (!loaded || busy || !active(generation)) return
        val fragment = FragmentManager.findFragment<Fragment>(this)
        val manager = fragment.parentFragmentManager
        val token = generation
        val target = store()
        busy = true
        render()
        tasks.add(
            Single.fromCallable { target.getOrCreatePlaylist() }
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ id ->
                    if (!active(token)) return@subscribe
                    busy = false
                    render()
                    if (!manager.isStateSaved) {
                        PlayerHolder.getInstance().exitMainPlayerFullscreenForMiniPlayer()
                        fragment.activity?.findViewById<View>(R.id.fragment_player_holder)?.let { holder ->
                            BottomSheetBehavior.from(holder).state = BottomSheetBehavior.STATE_COLLAPSED
                        }
                        NavigationHelper.openLocalPlaylistFragment(manager, id, context.getString(R.string.local_liked_videos))
                    }
                }, {
                    if (!active(token)) return@subscribe
                    busy = false
                    render()
                    message(R.string.local_like_failed)
                })
        )
    }

    private fun render() {
        like.isChecked = liked
        like.setText(if (liked) R.string.local_liked else R.string.local_like)
        like.contentDescription = context.getString(if (liked) R.string.local_unlike_description else R.string.local_like_description)
        like.isEnabled = loaded && !busy
        library.isEnabled = loaded && !busy
    }

    private fun message(text: Int) {
        if (!isAttachedToWindow) return
        snackbar?.dismiss()
        snackbar = Snackbar.make(this, text, Snackbar.LENGTH_LONG).also { it.show() }
    }
}
