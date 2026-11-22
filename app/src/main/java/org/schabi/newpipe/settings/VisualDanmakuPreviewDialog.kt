package org.schabi.newpipe.settings

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R

/** Local preview for visual Danmaku settings; Cancel never changes the saved preferences. */
object VisualDanmakuPreviewDialog {
    @JvmStatic
    fun show(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        fun read(key: Int, default: Int) = preferences.getInt(context.getString(key), default)
        var settings = DanmakuPreviewValues(
            opacity = read(R.string.danmaku_opacity_key, 85),
            fontSize = read(R.string.danmaku_text_size_key, 18),
            rows = read(R.string.danmaku_max_rows_key, 6),
            seconds = read(R.string.danmaku_duration_key, 8)
        ).normalized()
        val density = context.resources.displayMetrics.density
        val dp = { v: Int -> (v * density + 0.5f).toInt() }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val sample = TextView(context).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(24, 27, 31))
            setTextColor(Color.WHITE)
            minHeight = dp(140)
            setPadding(dp(8), dp(12), dp(8), dp(12))
            contentDescription = context.getString(R.string.danmaku_preview_sample_description)
        }
        container.addView(
            sample,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(150)
            )
        )
        fun render() {
            settings = settings.normalized()
            val lines = listOf(
                context.getString(R.string.danmaku_preview_sample_one),
                context.getString(R.string.danmaku_preview_sample_two),
                context.getString(R.string.danmaku_preview_sample_three)
            )
            sample.text = lines.take(settings.rows.coerceAtMost(lines.size)).joinToString("\n")
            sample.textSize = settings.fontSize.toFloat()
            sample.setTextColor(Color.argb(settings.opacity * 255 / 100, 255, 255, 255))
            sample.contentDescription = context.getString(
                R.string.danmaku_preview_sample_description
            )
        }
        fun addControl(
            titleRes: Int,
            minValue: Int,
            maxValue: Int,
            initial: Int,
            change: (Int) -> Unit
        ) {
            val title = TextView(context).apply {
                text = context.getString(titleRes) + ": " + initial
                setPadding(0, dp(12), 0, 0)
            }
            val slider = SeekBar(context).apply {
                max = maxValue - minValue
                progress = initial - minValue
                contentDescription = context.getString(titleRes)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            val value = progress + minValue
                            title.text = context.getString(titleRes) + ": " + value
                            change(value)
                            render()
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            }
            container.addView(title)
            container.addView(slider)
        }
        render()
        addControl(R.string.danmaku_opacity_title, 10, 100, settings.opacity) {
            settings = settings.copy(opacity = it)
        }
        addControl(R.string.danmaku_text_size_title, 12, 32, settings.fontSize) {
            settings = settings.copy(fontSize = it)
        }
        addControl(R.string.danmaku_max_rows_title, 1, 12, settings.rows) {
            settings = settings.copy(rows = it)
        }
        addControl(R.string.danmaku_duration_title, 4, 15, settings.seconds) {
            settings = settings.copy(seconds = it)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.danmaku_preview_title)
            .setView(container)
            .setPositiveButton(R.string.danmaku_preview_apply) { _, _ ->
                preferences.edit()
                    .putInt(context.getString(R.string.danmaku_opacity_key), settings.opacity)
                    .putInt(context.getString(R.string.danmaku_text_size_key), settings.fontSize)
                    .putInt(context.getString(R.string.danmaku_max_rows_key), settings.rows)
                    .putInt(context.getString(R.string.danmaku_duration_key), settings.seconds)
                    .apply()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

internal data class DanmakuPreviewValues(
    val opacity: Int,
    val fontSize: Int,
    val rows: Int,
    val seconds: Int
) {
    fun normalized() = copy(
        opacity = opacity.coerceIn(10, 100),
        fontSize = fontSize.coerceIn(12, 32),
        rows = rows.coerceIn(1, 12),
        seconds = seconds.coerceIn(4, 15)
    )
}
