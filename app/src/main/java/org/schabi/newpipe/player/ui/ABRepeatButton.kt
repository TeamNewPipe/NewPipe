package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.player.OptionalPlaybackFeatures
import org.schabi.newpipe.player.Player

class ABRepeatButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : PlayerActionTextView(context, attrs) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == OptionalPlaybackFeatures.AB_REPEAT_KEY) updateEnabled()
    }
    private var player: Player? = null
    private var dialog: androidx.appcompat.app.AlertDialog? = null

    fun bind(value: Player) {
        clear()
        player = value
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        updateEnabled()
        setOnClickListener {
            val repeat = value.abRepeat
            if (!repeat.enabled) return@setOnClickListener
            if (!repeat.available()) {
                Toast.makeText(context, R.string.ab_repeat_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val start = repeat.startMs?.let { "${it / 60000}:${(it / 1000 % 60).toString().padStart(2, '0')}" } ?: "—"
            val end = repeat.endMs?.let { "${it / 60000}:${(it / 1000 % 60).toString().padStart(2, '0')}" } ?: "—"
            dialog?.dismiss()
            dialog = MaterialAlertDialogBuilder(PlayerPopupMenu.dialogContext(this))
                .setTitle(context.getString(R.string.ab_repeat_range, start, end))
                .setItems(arrayOf(context.getString(R.string.ab_repeat_start), context.getString(R.string.ab_repeat_end), context.getString(R.string.ab_repeat_clear))) { _, index ->
                    if (!repeat.enabled) return@setItems
                    val success = when (index) {
                        0 -> repeat.markStart()

                        1 -> repeat.markEnd()

                        else -> {
                            repeat.clear()
                            true
                        }
                    }
                    Toast.makeText(context, if (success) R.string.ab_repeat_updated else R.string.ab_repeat_invalid, Toast.LENGTH_SHORT).show()
                    if (success) value.triggerProgressUpdate()
                }.setNegativeButton(R.string.cancel, null).create()
            dialog?.let { AnchoredPlaybackDialog.show(this, it) }
        }
    }

    private fun updateEnabled() {
        isVisible = player != null && preferences.getBoolean(OptionalPlaybackFeatures.AB_REPEAT_KEY, false)
        if (!isVisible) {
            dialog?.dismiss()
            dialog = null
        }
    }

    override fun onDetachedFromWindow() {
        dialog?.dismiss()
        super.onDetachedFromWindow()
    }

    fun clear() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        isVisible = false
        dialog?.dismiss()
        dialog = null
        player = null
        setOnClickListener(null)
    }
}
