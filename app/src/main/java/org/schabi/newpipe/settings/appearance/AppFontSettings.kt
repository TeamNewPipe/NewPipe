/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings.appearance

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import kotlin.math.roundToInt
import org.schabi.newpipe.R
import org.schabi.newpipe.util.AppFontPreset
import org.schabi.newpipe.util.AppFonts

object AppFontSettings {
    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat) {
        fragment.findPreference<Preference>(AppFonts.KEY)?.apply {
            summaryProvider = Preference.SummaryProvider<Preference> { context.getString(AppFonts.selected(context).title) }
            setOnPreferenceClickListener {
                val manager = fragment.parentFragmentManager
                if (manager.findFragmentByTag(AppFontDialog.TAG) == null) AppFontDialog().show(manager, AppFontDialog.TAG)
                true
            }
        }
        fragment.parentFragmentManager.setFragmentResultListener(AppFontDialog.TAG, fragment) { _, _ ->
            fragment.findPreference<Preference>(AppFonts.KEY)?.summaryProvider =
                Preference.SummaryProvider<Preference> { fragment.getString(AppFonts.selected(fragment.requireContext()).title) }
        }
    }

    fun preview(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setTag(R.id.app_font_exempt, true)
        val padding = (24 * resources.displayMetrics.density).roundToInt()
        setPaddingRelative(padding, padding / 2, padding, padding / 2)
        fun sample(text: Int, id: Int = View.NO_ID, bold: Boolean = false) = MaterialTextView(context).apply {
            this.id = id
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setText(text)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            val gap = (8 * resources.displayMetrics.density).roundToInt()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = gap
            }
        }
        addView(sample(R.string.app_font_preview_title, bold = true))
        addView(sample(R.string.app_font_preview_normal, R.id.app_font_preview_normal))
        addView(sample(R.string.app_font_preview_bold, R.id.app_font_preview_bold, true))
        addView(sample(R.string.app_font_preview_numbers, R.id.app_font_preview_numbers))
        addView(sample(R.string.app_font_preview_long, R.id.app_font_preview_long))
        addView(sample(R.string.app_font_preview_scripts))
        addView(sample(R.string.app_font_preview_help))
    }

    fun updatePreview(preview: LinearLayout, preset: AppFontPreset) {
        for (index in 0 until preview.childCount) (preview.getChildAt(index) as? TextView)?.let { AppFonts.applyPreset(it, preset) }
    }
}

/** Selection remains a draft through rotation; Cancel never writes a preference. */
class AppFontDialog : DialogFragment() {
    private lateinit var selected: AppFontPreset

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = savedInstanceState?.getString(SELECTION)?.let(AppFontPreset::from) ?: AppFonts.selected(requireContext())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val preview = AppFontSettings.preview(builder.context)
        AppFontSettings.updatePreview(preview, selected)
        val presets = AppFontPreset.entries
        val dialog = builder.setTitle(R.string.app_font_title)
            .setSingleChoiceItems(presets.map { getString(it.title) }.toTypedArray(), presets.indexOf(selected)) { _, index ->
                selected = presets[index]
                AppFontSettings.updatePreview(preview, selected)
            }
            .setPositiveButton(R.string.app_font_apply) { _, _ ->
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString(AppFonts.KEY, selected.value).apply()
                parentFragmentManager.setFragmentResult(TAG, Bundle.EMPTY)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.listView.addFooterView(preview, null, false)
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SELECTION, selected.value)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val TAG = "app-font-selection"
        private const val SELECTION = "selected-font"
    }
}
