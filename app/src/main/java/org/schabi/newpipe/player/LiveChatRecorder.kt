/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager

/** User-initiated capture of only messages the live-chat extractor actually delivered. */
object LiveChatRecorder {
    private const val FOLDER = "recorded-live-chats"
    private const val MAX_MESSAGES = 5_000
    private const val MAX_DURATION_MS = 20L * 60L * 1_000L
    private const val MAX_FILES = 12
    private const val MAX_FILE_BYTES = 8L * 1024L * 1024L

    data class Entry(val atMillis: Long, val author: String, val text: String)

    private data class Session(
        val profileId: String,
        val serviceId: Int,
        val url: String,
        val startedAt: Long,
        val rows: MutableList<Entry> = mutableListOf(),
        val seen: MutableSet<String> = mutableSetOf(),
        var job: Job? = null,
        @Volatile var discarded: Boolean = false
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var active: Session? = null

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun directory(context: Context, profileId: String): File {
        return File(File(context.filesDir, FOLDER), digest(profileId))
    }

    private fun file(context: Context, profileId: String, serviceId: Int, url: String): File {
        return File(directory(context, profileId), digest("$serviceId:$url") + ".json.gz")
    }

    @JvmStatic
    @Synchronized
    fun isRecording(serviceId: Int, url: String): Boolean {
        return active?.let { it.serviceId == serviceId && it.url == url } == true
    }

    @JvmStatic
    @Synchronized
    fun start(context: Context, serviceId: Int, url: String): Boolean {
        if (url.isBlank() || active != null || IncognitoMode.isEnabled(context)) return false
        val app = context.applicationContext
        val session = Session(
            ProfileManager.getActiveProfileId(app),
            serviceId,
            url,
            System.currentTimeMillis()
        )
        active = session
        session.job = scope.launch {
            try {
                withTimeoutOrNull(MAX_DURATION_MS) {
                    LiveChatRepository.messages(serviceId, url).collect { result ->
                        if (IncognitoMode.isEnabled(app) ||
                            ProfileManager.getActiveProfileId(app) != session.profileId
                        ) {
                            session.discarded = true
                            session.job?.cancel()
                        }
                        result.getOrNull()?.let { batch ->
                            if (!session.discarded) {
                                recordBatch(session, batch, System.currentTimeMillis())
                            }
                        }
                        if (session.rows.size >= MAX_MESSAGES) {
                            session.job?.cancel()
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Explicit Stop means write the bounded messages collected so far.
            } finally {
                synchronized(this@LiveChatRecorder) {
                    if (active === session) active = null
                }
                persist(app, session)
            }
        }
        return true
    }

    @JvmStatic
    @Synchronized
    fun stop(serviceId: Int, url: String) {
        active?.takeIf { it.serviceId == serviceId && it.url == url }?.job?.cancel()
    }

    private fun recordBatch(session: Session, batch: List<BulletCommentsInfoItem>, now: Long) {
        synchronized(this) {
            batch.forEach { item ->
                if (session.rows.size >= MAX_MESSAGES) return
                val body = item.commentText.orEmpty().trim().take(2_000)
                if (body.isEmpty()) return@forEach
                val elapsed = (now - session.startedAt).coerceAtLeast(0)
                val extracted = item.duration?.toMillis()?.takeIf { it > 0 } ?: elapsed
                val author = item.name.orEmpty().take(160)
                val id = "$author:$body:$extracted"
                if (session.seen.add(id)) {
                    session.rows += Entry(extracted, author, body)
                }
            }
        }
    }

    @Synchronized
    private fun persist(context: Context, session: Session) {
        if (session.discarded || session.rows.isEmpty()) return
        runCatching {
            val dest = file(context, session.profileId, session.serviceId, session.url)
            if (dest.parentFile?.isDirectory != true && dest.parentFile?.mkdirs() != true) return
            val temp = File(dest.parentFile, dest.name + ".tmp")
            val json = JSONObject()
                .put("startedAt", session.startedAt)
                .put(
                    "rows",
                    JSONArray().apply {
                        session.rows.forEach { row ->
                            put(
                                JSONObject()
                                    .put("at", row.atMillis)
                                    .put("author", row.author)
                                    .put("text", row.text)
                            )
                        }
                    }
                )
            GZIPOutputStream(temp.outputStream()).bufferedWriter(Charsets.UTF_8).use {
                it.write(json.toString())
            }
            if (temp.length() > MAX_FILE_BYTES) {
                temp.delete()
                return
            }
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            val all = dest.parentFile?.listFiles { f -> f.name.endsWith(".json.gz") }.orEmpty()
                .sortedByDescending(File::lastModified)
            all.drop(MAX_FILES).forEach(File::delete)
        }
    }

    @JvmStatic
    fun hasSaved(context: Context, serviceId: Int, url: String): Boolean {
        if (IncognitoMode.isEnabled(context)) return false
        return file(context, ProfileManager.getActiveProfileId(context), serviceId, url).isFile
    }

    @JvmStatic
    fun read(context: Context, serviceId: Int, url: String): List<Entry> {
        if (IncognitoMode.isEnabled(context)) return emptyList()
        val archive = file(context, ProfileManager.getActiveProfileId(context), serviceId, url)
        if (!archive.isFile || archive.length() > MAX_FILE_BYTES) return emptyList()
        return runCatching {
            val data = GZIPInputStream(archive.inputStream()).bufferedReader().use {
                val data = StringBuilder()
                val buffer = CharArray(8_192)
                while (data.length < 12_000_000) {
                    val bytes = it.read(
                        buffer,
                        0,
                        minOf(buffer.size, 12_000_000 - data.length)
                    )
                    if (bytes <= 0) break
                    data.append(buffer, 0, bytes)
                }
                data.toString()
            }
            val rows = JSONObject(data).optJSONArray("rows") ?: return@runCatching emptyList()
            (0 until minOf(rows.length(), MAX_MESSAGES)).mapNotNull { index ->
                val item = rows.optJSONObject(index) ?: return@mapNotNull null
                Entry(item.optLong("at"), item.optString("author"), item.optString("text"))
            }
        }.getOrDefault(emptyList())
    }

    internal fun replayRows(rows: List<Entry>, atMillis: Long): List<Entry> {
        return rows.filter { it.atMillis <= atMillis }.takeLast(250)
    }

    @JvmStatic
    @Synchronized
    fun forgetProfile(context: Context, profileId: String) {
        active?.takeIf { it.profileId == profileId }?.let {
            it.discarded = true
            it.job?.cancel()
        }
        directory(context, profileId).deleteRecursively()
    }
}
