package org.schabi.newpipe.local.feed.quickadd

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.profiles.ProfileManager

class QuickAddPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var subscription: Disposable? = null
    private var playlists = emptyList<PlaylistEntity>()
    private var profileId = ""
    private var loaded = false

    init {
        isPersistent = false
    }

    override fun onAttached() {
        super.onAttached()
        profileId = ProfileManager.getActiveProfileId(context)
        subscription = NewPipeDatabase.getInstance(context).playlistDAO().getAllForProfile(profileId)
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ entries ->
                playlists = entries
                loaded = true
                updateSummary()
            }, { Toast.makeText(context, R.string.feed_quick_add_failed, Toast.LENGTH_SHORT).show() })
    }

    override fun onDetached() {
        subscription?.dispose()
        subscription = null
        loaded = false
        super.onDetached()
    }

    override fun onClick() {
        if (!loaded || ProfileManager.getActiveProfileId(context) != profileId) return
        val choices = playlists.toList()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val selected = readTargetId(prefs, profileId)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.feed_quick_add_title)
            .setSingleChoiceItems((listOf(context.getString(R.string.feed_quick_add_off)) + choices.map { it.name.orEmpty() }).toTypedArray(), choices.indexOfFirst { it.uid == selected } + 1) { dialog, index ->
                if (ProfileManager.getActiveProfileId(context) == profileId) {
                    prefs.edit().putLong(targetKey(profileId), choices.getOrNull(index - 1)?.uid ?: -1).apply()
                    updateSummary()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        if (choices.isEmpty()) Toast.makeText(context, R.string.feed_quick_add_create_first, Toast.LENGTH_LONG).show()
    }

    private fun updateSummary() {
        val selected = readTargetId(PreferenceManager.getDefaultSharedPreferences(context), profileId)
        summary = playlists.find { it.uid == selected }?.let { context.getString(R.string.feed_quick_add_target, it.name) }
            ?: context.getString(R.string.feed_quick_add_disabled)
    }

    companion object {
        const val TARGET_KEY_PREFIX = "feed_quick_add_target_"

        fun targetKey(profileId: String) = "$TARGET_KEY_PREFIX$profileId"

        /**
         * Older JSON backups lose the original Int/Long distinction for small playlist IDs.
         * Repair an already-restored value as well as newly imported backups so opening the
         * feed or settings cannot crash when SharedPreferences.getLong sees an Integer.
         */
        fun readTargetId(preferences: SharedPreferences, profileId: String): Long {
            val key = targetKey(profileId)
            return try {
                preferences.getLong(key, -1L)
            } catch (_: ClassCastException) {
                val recovered = when (val stored = preferences.all[key]) {
                    is Int -> stored.toLong()
                    is Long -> stored
                    else -> -1L
                }
                preferences.edit().putLong(key, recovered).apply()
                recovered
            }
        }
    }
}
