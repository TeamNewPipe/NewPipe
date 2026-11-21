/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings.export

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import org.schabi.newpipe.R
import org.schabi.newpipe.sync.PortableSettingId

/** Opt-in, per-setting import/export/reset. No passwords, cookies or pairing keys appear. */
class SelectiveSettingsDialog : DialogFragment() {
    private val selection = linkedSetOf<PortableSettingId>()
    private val exportFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val ctx = context ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        runCatching {
            val (document, count) = SelectiveSettingsTransfer.export(
                ctx,
                PreferenceManager.getDefaultSharedPreferences(ctx),
                selection
            )
            val output = requireNotNull(ctx.contentResolver.openOutputStream(uri))
            output.bufferedWriter().use { it.write(document) }
            count
        }.onSuccess {
            Toast.makeText(ctx, ctx.getString(R.string.selective_settings_exported, it), Toast.LENGTH_LONG).show()
            dismissAllowingStateLoss()
        }.onFailure {
            Toast.makeText(ctx, R.string.selective_settings_failed, Toast.LENGTH_LONG).show()
        }
    }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val ctx = context ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        val requested = selection.toSet()
        runCatching {
            val file = requireNotNull(ctx.contentResolver.openInputStream(uri))
                .bufferedReader().use { it.readText() }
            val preview = SelectiveSettingsTransfer.previewImport(
                ctx,
                PreferenceManager.getDefaultSharedPreferences(ctx),
                requested,
                file
            )
            file to preview
        }.onSuccess { (file, preview) ->
            val headline = ctx.getString(
                R.string.selective_settings_preview_summary,
                preview.changes.size,
                preview.unchangedCount,
                preview.missingCount
            )
            val changed = preview.changes.take(12).joinToString("\n") { entry ->
                "${settingLabel(entry.id)}: ${displayValue(entry.before)} → ${displayValue(entry.after)}"
            }
            val extra = (preview.changes.size - 12).takeIf { it > 0 }?.let {
                ctx.getString(R.string.selective_settings_preview_more, it)
            }.orEmpty()
            val body = listOf(
                headline,
                changed,
                extra,
                ctx.getString(R.string.selective_settings_preview_restart)
            ).filter { it.isNotBlank() }.joinToString("\n\n")
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.selective_settings_preview_title)
                .setMessage(body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.selective_settings_import) { _, _ ->
                    runCatching {
                        SelectiveSettingsTransfer.applyPreview(
                            ctx,
                            PreferenceManager.getDefaultSharedPreferences(ctx),
                            requested,
                            file,
                            preview
                        )
                    }.onSuccess {
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.selective_settings_done, it),
                            Toast.LENGTH_LONG
                        ).show()
                        dismissAllowingStateLoss()
                    }.onFailure {
                        Toast.makeText(ctx, R.string.selective_settings_failed, Toast.LENGTH_LONG).show()
                    }
                }
                .show()
        }.onFailure {
            Toast.makeText(ctx, R.string.selective_settings_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList(STATE_SELECTION)?.forEach { id ->
            runCatching { PortableSettingId.valueOf(id) }.getOrNull()?.let(selection::add)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            STATE_SELECTION,
            ArrayList(selection.map { it.name })
        )
        super.onSaveInstanceState(outState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val options = SelectiveSettingsTransfer.allowedIds(requireContext())
        val labels = options.map(::settingLabel).toTypedArray()
        val checked = options.map { it in selection }.toBooleanArray()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.selective_settings_choose)
            .setMultiChoiceItems(labels, checked) { _, index, enabled ->
                if (enabled) selection.add(options[index]) else selection.remove(options[index])
            }
            .setPositiveButton(R.string.selective_settings_export, null)
            .setNeutralButton(R.string.selective_settings_import, null)
            .setNegativeButton(R.string.selective_settings_reset, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = requireDialog() as AlertDialog
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            if (requireSelection()) {
                exportFile.launch("WizeStream-SelectedSettings.json")
            }
        }
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            if (requireSelection()) {
                importFile.launch(arrayOf("application/json", "text/plain"))
            }
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
            if (!requireSelection()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.selective_settings_reset)
                .setMessage(R.string.selective_settings_reset_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val ctx = requireContext()
                    runCatching {
                        SelectiveSettingsTransfer.reset(
                            ctx,
                            PreferenceManager.getDefaultSharedPreferences(ctx),
                            selection
                        )
                    }.onSuccess {
                        Toast.makeText(
                            ctx,
                            ctx.getString(R.string.selective_settings_done, it),
                            Toast.LENGTH_LONG
                        ).show()
                        dismissAllowingStateLoss()
                    }.onFailure {
                        Toast.makeText(ctx, R.string.selective_settings_failed, Toast.LENGTH_LONG).show()
                    }
                }
                .show()
        }
    }

    private fun settingLabel(id: PortableSettingId): String = id.name.split('_').joinToString(" ") { part ->
        part.lowercase(Locale.ROOT).replaceFirstChar(Char::titlecase)
    }

    private fun displayValue(value: Any?): String = when (value) {
        null -> getString(R.string.selective_settings_preview_default)
        is String -> value.replace('\n', ' ').replace('\r', ' ').take(60)
        else -> value.toString()
    }

    private fun requireSelection(): Boolean {
        if (selection.isNotEmpty()) return true
        Toast.makeText(requireContext(), R.string.selective_settings_none, Toast.LENGTH_SHORT).show()
        return false
    }

    companion object {
        const val TAG = "selective_settings_dialog"
        private const val STATE_SELECTION = "selected"
    }
}
