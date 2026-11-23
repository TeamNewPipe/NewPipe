/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download.offline

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONObject
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.download.OfflineSponsorBlockStore
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.FinishedMission
import us.shandian.giga.service.DownloadManager

/** Only newly opted-in automatic downloads are owned by this registry. It is not exported or synced. */
object OfflineLibrary {
    private const val STORE = "offline_library_ownership"
    private val lock = Any()
    private var playbackActive = false
    private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    @JvmStatic fun playbackStarted() = synchronized(lock) { playbackActive = true }

    @JvmStatic fun playbackStopped() = synchronized(lock) { playbackActive = false }

    internal fun rules(context: Context, profile: String): RetentionRules = runCatching {
        val json = JSONObject(prefs(context).getString("rules:$profile", "{}").orEmpty())
        RetentionRules(json.optBoolean("enabled"), json.optInt("latest").coerceIn(0, 1000), json.optLong("budget").coerceAtLeast(0), json.optBoolean("watched"))
    }.getOrDefault(RetentionRules())

    internal fun setRules(context: Context, profile: String, rules: RetentionRules) = synchronized(lock) {
        prefs(context).edit().putString(
            "rules:$profile",
            JSONObject().put("enabled", rules.enabled).put("latest", rules.latest)
                .put("budget", rules.budgetBytes).put("watched", rules.deleteWatched).toString()
        ).apply()
    }

    @JvmStatic fun trackPending(context: Context, mission: DownloadMission, info: StreamInfo, profile: String?) = synchronized(lock) {
        if (profile == null || !rules(context, profile).enabled || IncognitoMode.isEnabled(context)) return@synchronized
        val uri = mission.storage?.uri?.toString() ?: return@synchronized
        prefs(context).edit().putString(
            "pending:$uri",
            JSONObject().put("profile", profile).put("uri", uri)
                .put("timestamp", mission.timestamp).put("published", info.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: mission.timestamp).put("source", mission.source).put("service", info.serviceId)
                .put("channel", "${info.serviceId}:${info.uploaderUrl.orEmpty().ifBlank { info.uploaderName.orEmpty() }}").toString()
        ).apply()
    }

    @JvmStatic fun completed(context: Context, mission: FinishedMission) = synchronized(lock) {
        val uri = mission.storage?.uri?.toString() ?: return@synchronized
        val prefs = prefs(context)
        val raw = prefs.getString("pending:$uri", null) ?: return@synchronized
        prefs.edit().remove("pending:$uri").apply()
        runCatching {
            val json = JSONObject(raw)
            val profile = json.getString("profile")
            if (json.getLong("timestamp") == mission.timestamp && json.getString("source") == mission.source && rules(context, profile).enabled) {
                json.put("size", mission.storage.length()).put("keep", false)
                prefs.edit().putString("file:${mission.syncId}", json.toString()).apply()
            }
        }
    }

    internal data class Entry(val mission: FinishedMission, val metadata: JSONObject, val item: ManagedDownload)

    /** Called off the UI thread. A changed or missing file is never a deletion candidate. */
    internal fun entries(context: Context, manager: DownloadManager, profile: String): List<Entry> {
        val database = NewPipeDatabase.getInstance(context)
        val historyEnabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.enable_watch_history_key), true)
        return manager.finishedMissionsSnapshot.mapNotNull { mission ->
            runCatching {
                val data = JSONObject(prefs(context).getString("file:${mission.syncId}", null) ?: return@mapNotNull null)
                if (data.getString("profile") != profile || data.getString("source") != mission.source ||
                    data.getLong("timestamp") != mission.timestamp || data.getString("uri") != mission.storage?.uri?.toString() ||
                    mission.storage?.existsAsFile() != true || mission.storage.length() != data.getLong("size")
                ) {
                    return@mapNotNull null
                }
                val identities = mutableListOf(data.getInt("service").toLong() to mission.source, -1L to data.getString("uri"))
                if (mission.storage.isDirect) {
                    runCatching {
                        androidx.core.content.FileProvider.getUriForFile(context, org.schabi.newpipe.BuildConfig.APPLICATION_ID + ".provider", java.io.File(java.net.URI.create(data.getString("uri"))))
                    }.getOrNull()?.let { identities.add(-1L to it.toString()) }
                }
                val watched = historyEnabled && identities.any { (service, url) ->
                    val stream = database.streamDAO().getStream(service, url).blockingFirst().firstOrNull()
                    stream != null && database.streamStateDAO().getStateDirectForProfile(profile, stream.uid)?.isFinished(stream.duration) == true
                }
                Entry(mission, data, ManagedDownload(mission.syncId, data.getString("channel"), data.optLong("published", mission.timestamp), data.getLong("size"), watched, data.optBoolean("keep")))
            }.getOrNull()
        }
    }

    internal fun protect(context: Context, entry: Entry, keep: Boolean) = synchronized(lock) {
        val key = "file:${entry.item.id}"
        val current = prefs(context).getString(key, null) ?: return@synchronized
        prefs(context).edit().putString(key, JSONObject(current).put("keep", keep).toString()).apply()
    }

    /** Invoked by hourly automatic download checks, or explicitly through the cleanup preview. */
    @JvmStatic fun cleanup(context: Context, manager: DownloadManager): Int = cleanupSelected(context, manager, null)

    internal fun cleanupSelected(context: Context, manager: DownloadManager, approved: Set<String>?): Int {
        val profile = ProfileManager.getActiveProfileId(context)
        if (IncognitoMode.isEnabled(context) || KidsMode.requiresParentAccess(context) || !rules(context, profile).enabled) return 0
        val entries = entries(context, manager, profile)
        val selected = OfflineRetentionPolicy.candidates(entries.map { it.item }, rules(context, profile))
        var deleted = 0
        for (entry in entries.filter { it.item.id in selected && (approved == null || it.item.id in approved) }) {
            synchronized(lock) {
                if (playbackActive || IncognitoMode.isEnabled(context) || profile != ProfileManager.getActiveProfileId(context) || !rules(context, profile).enabled) return deleted
                val current = prefs(context).getString("file:${entry.item.id}", null)?.let(::JSONObject) ?: return@synchronized
                if (current.optBoolean("keep") || KidsMode.requiresParentAccess(context)) return@synchronized
                val historyEnabled = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(context.getString(R.string.enable_watch_history_key), true)
                val currentCandidates = OfflineRetentionPolicy.candidates(entries.map { it.item.copy(watched = it.item.watched && historyEnabled) }, rules(context, profile))
                if (entry.item.id !in currentCandidates) return@synchronized
                if (deleteFile(manager, entry.mission, entry.item.bytes)) {
                    OfflineSponsorBlockStore.remove(context, current.getString("uri"))
                    org.schabi.newpipe.download.OfflinePlaybackMetadata.remove(context, current.getString("uri"))
                    prefs(context).edit().remove("file:${entry.item.id}").apply()
                    deleted++
                }
            }
        }
        return deleted
    }

    internal fun deleteFile(manager: DownloadManager, mission: FinishedMission, expectedBytes: Long): Boolean = synchronized(manager) {
        runCatching {
            if (manager.finishedMissionsSnapshot.none { it === mission } || mission.storage.length() != expectedBytes) return@synchronized false
            if (!mission.storage.delete()) return@synchronized false
            manager.deleteMission(mission, false)
            true
        }.getOrDefault(false)
    }

    @JvmStatic fun forget(context: Context, mission: FinishedMission) {
        prefs(context).edit().remove("file:${mission.syncId}").apply()
    }

    @JvmStatic fun forgetProfile(context: Context, profile: String) {
        val editor = prefs(context).edit().remove("rules:$profile")
        prefs(context).all.forEach { (key, value) ->
            if (runCatching { JSONObject(value as String).optString("profile") == profile }.getOrDefault(false)) editor.remove(key)
        }
        editor.apply()
    }
}
