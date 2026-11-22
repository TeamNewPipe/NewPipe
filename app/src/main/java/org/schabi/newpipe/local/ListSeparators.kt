/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity

/** Presentation-only headings anchored to items, never inserted into playback or database order. */
class ListSeparators(context: Context, profileId: String, listId: String) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val preferenceKey = "list_separators_${profileId}_$listId"
    private val writeLock = locks.getOrPut(preferenceKey) { Any() }

    private val positionsKey = "${preferenceKey}_positions"

    fun labels(items: List<LocalItem>): Map<Int, String> {
        if (!preferences.contains(positionsKey)) {
            // Adapters request labels before asynchronous database rows have loaded.
            if (items.isEmpty()) return emptyMap()
            val old = read()
            var preceding = 0
            val migrated = linkedMapOf<Int, String>()
            keys(items).forEachIndexed { index, key ->
                old.optString(key).takeIf { it.isNotBlank() }?.let {
                    migrated[index + preceding++] = it
                }
            }
            writePositions(migrated)
        }
        val data = try {
            JSONObject(preferences.getString(positionsKey, "{}") ?: "{}")
        } catch (_: org.json.JSONException) {
            JSONObject()
        }
        val entries = data.keys().asSequence().mapNotNull { key ->
            key.toIntOrNull()?.let { it to data.optString(key) }
        }.filter { it.second.isNotBlank() }.sortedBy { it.first }.toList()
        val result = linkedMapOf<Int, String>()
        var previous = -1
        entries.forEachIndexed { index, entry ->
            val position = entry.first.coerceIn(previous + 1, items.size + index)
            result[position] = entry.second
            previous = position
        }
        return result
    }

    fun move(items: List<LocalItem>, from: Int, to: Int) {
        writePositions(
            labels(items).mapKeys { (position, _) ->
                when {
                    position == from -> to
                    from < to && position in (from + 1)..to -> position - 1
                    to < from && position in to until from -> position + 1
                    else -> position
                }
            }
        )
    }

    fun moveSelected(items: List<LocalItem>, selected: Set<Int>, boundary: Int): List<LocalItem> {
        val result = ListSelectionOrder.move(items.size, labels(items), selected, boundary)
        writePositions(result.labels)
        return result.itemIndices.map(items::get)
    }

    /** Publish staged headings only after the matching saved playlist edit commits. */
    fun commitSelection(items: List<LocalItem>, expected: Map<Int, String>, replacement: Map<Int, String>, change: () -> Boolean): Boolean = synchronized(writeLock) {
        if (labels(items) != expected || !change()) return@synchronized false
        writePositions(replacement)
        true
    }

    fun removeRow(items: List<LocalItem>, row: Int) {
        writePositions(
            labels(items).mapKeys { (position, _) ->
                if (position > row) position - 1 else position
            }
        )
    }

    fun insertRow(items: List<LocalItem>, row: Int) {
        writePositions(
            labels(items).mapKeys { (position, _) ->
                if (position >= row) position + 1 else position
            }
        )
    }

    fun edit(context: Context, items: List<LocalItem>, index: Int, changed: Runnable) {
        if (index !in items.indices) return
        val saved = labels(items)
        var row = index
        saved.keys.sorted().forEach { if (it <= row) row++ }
        editRow(context, items, row, changed, true)
    }

    fun editRow(context: Context, items: List<LocalItem>, row: Int, changed: Runnable, adding: Boolean = false) {
        val saved = labels(items).toMutableMap()
        val previous = if (adding) "" else saved[row] ?: return
        val input = EditText(context).apply {
            setSingleLine()
            hint = context.getString(R.string.subscription_separator_name)
            setText(previous)
        }
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.subscription_separator_name).setView(input)
            .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null)
        if (!adding) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                saved.remove(row)
                writePositions(saved.mapKeys { (position, _) -> if (position > row) position - 1 else position })
                changed.run()
            }
        }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isEmpty()) {
                input.error = context.getString(R.string.subscription_separator_name)
            } else {
                val updated = if (adding) {
                    saved.mapKeys { (position, _) ->
                        if (position >= row) position + 1 else position
                    }.toMutableMap()
                } else {
                    saved
                }
                updated[row] = name
                writePositions(updated)
                changed.run()
                dialog.dismiss()
            }
        }
    }

    private fun writePositions(labels: Map<Int, String>) = synchronized(writeLock) {
        val data = JSONObject()
        labels.forEach { (row, label) -> data.put(row.toString(), label) }
        preferences.edit().putString(positionsKey, data.toString()).apply()
    }

    private fun read(): JSONObject = try {
        JSONObject(preferences.getString(preferenceKey, "{}") ?: "{}")
    } catch (_: org.json.JSONException) {
        JSONObject()
    }

    companion object {
        private val locks = ConcurrentHashMap<String, Any>()

        internal fun keys(items: List<LocalItem>): List<String> {
            val occurrences = mutableMapOf<String, Int>()
            return items.map { item ->
                val key = when (item) {
                    is PlaylistStreamEntry -> "stream:${item.streamId}"
                    is PlaylistRemoteEntity -> "remote:${item.serviceId}:${item.url}"
                    is PlaylistLocalItem -> "local:${item.uid}"
                    else -> "unsupported"
                }
                val occurrence = occurrences.getOrDefault(key, 0)
                occurrences[key] = occurrence + 1
                "$key:$occurrence"
            }
        }
    }
}
