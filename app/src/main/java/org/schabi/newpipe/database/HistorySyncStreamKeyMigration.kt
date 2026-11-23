/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.database

import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest
import org.json.JSONException
import org.json.JSONObject

/** Frozen v32-to-v33 derivation, matching the existing progress identity without changing wire data. */
internal object HistorySyncStreamKeyMigration {
    fun backfill(database: SupportSQLiteDatabase) {
        database.compileStatement(
            "UPDATE history_sync_records SET stream_key = ? WHERE category = 'WATCH' AND record_id = ?"
        ).use { update ->
            database.query(
                "SELECT record_id, profile_id, record_type, record_json FROM history_sync_records " +
                    "WHERE category = 'WATCH' AND record_type IN " +
                    "('WATCH_EVENT', 'PLAYBACK_PROGRESS', 'WATCH_STREAM_TOMBSTONE')"
            ).use { records ->
                while (records.moveToNext()) {
                    val key = streamKey(records.getString(1), records.getString(2), records.getString(3)) ?: continue
                    update.bindString(1, key)
                    update.bindString(2, records.getString(0))
                    update.executeUpdateDelete()
                }
            }
        }
    }

    private fun streamKey(profileId: String, type: String, json: String?): String? {
        if (json == null) return null
        val field = when (type) {
            "WATCH_EVENT" -> "watchEvent"
            "PLAYBACK_PROGRESS" -> "playbackProgress"
            "WATCH_STREAM_TOMBSTONE" -> "watchStreamTombstone"
            else -> return null
        }
        return try {
            val stream = JSONObject(json).getJSONObject(field).getJSONObject("stream")
            val legacyIdentity = "progress\u0000${stream.getInt("serviceId")}\u0000${stream.getString("url").trim()}"
            val identity = if (profileId == "00000000-0000-0000-0000-000000000000") legacyIdentity else "$profileId\u0000$legacyIdentity"
            val bytes = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            buildString(bytes.size * 2) {
                bytes.forEach { byte ->
                    val value = byte.toInt() and 0xff
                    append("0123456789abcdef"[value ushr 4])
                    append("0123456789abcdef"[value and 0x0f])
                }
            }
        } catch (_: JSONException) {
            // Preserve damaged records verbatim. Sync reports the unindexed metadata instead of
            // making the app's entire database unavailable during an otherwise safe migration.
            null
        }
    }
}
