/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.comments

import android.content.Context
import androidx.preference.PreferenceManager
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.R
import org.schabi.newpipe.download.DownloadedAvailabilityIndex
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager

data class CachedComment(
    val id: String,
    val author: String,
    val body: String,
    val date: String,
    val parentId: String?
)

/** Bounded, compressed on-device snapshots of only pages the user actually viewed. */
object CommentArchive {
    private const val FOLDER = "offline-comment-archive"
    private const val MAX_FILES = 100
    private const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
    private const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_ITEM_TEXT_LENGTH = 2_000
    private const val MAX_ITEMS_HARD_LIMIT = 1_000

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        return !IncognitoMode.isEnabled(context) &&
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                context.getString(R.string.comment_archive_enabled_key),
                false
            )
    }

    private fun limit(context: Context): Int {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(context.getString(R.string.comment_archive_limit_key), "300")
            ?.toIntOrNull()?.coerceIn(100, MAX_ITEMS_HARD_LIMIT) ?: 300
    }

    private fun hash(value: String): String {
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun directory(context: Context, profileId: String): File {
        return File(File(context.filesDir, FOLDER), hash(profileId))
    }

    private fun archiveFile(context: Context, profileId: String, serviceId: Int, url: String): File {
        return File(directory(context, profileId), hash("$serviceId:$url") + ".json.gz")
    }

    private fun retainedFile(context: Context, profileId: String, serviceId: Int, url: String): File {
        return File(directory(context, profileId), "saved-" + hash("$serviceId:$url") + ".json.gz")
    }

    private fun readFile(file: File): List<CachedComment> = runCatching {
        if (!file.isFile || file.length() > MAX_TOTAL_BYTES) return@runCatching emptyList()
        val raw = GZIPInputStream(file.inputStream()).bufferedReader(Charsets.UTF_8).use {
            it.readText().take(4_000_000)
        }
        val array = JSONObject(raw).optJSONArray("items") ?: return@runCatching emptyList()
        (0 until minOf(array.length(), MAX_ITEMS_HARD_LIMIT)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            CachedComment(
                item.optString("id"),
                item.optString("author"),
                item.optString("body"),
                item.optString("date"),
                item.optString("parentId").takeIf(String::isNotBlank)
            )
        }
    }.getOrDefault(emptyList())

    @JvmStatic
    @Synchronized
    fun read(context: Context, serviceId: Int, url: String): List<CachedComment> {
        val profileId = ProfileManager.getActiveProfileId(context)
        val saved = retainedFile(context, profileId, serviceId, url)
        return readFile(if (saved.isFile) saved else archiveFile(context, profileId, serviceId, url))
    }

    internal fun merge(
        old: List<CachedComment>,
        incoming: List<CachedComment>,
        limit: Int
    ): List<CachedComment> {
        val combined = linkedMapOf<String, CachedComment>()
        (old + incoming).forEach { item ->
            val identity = "${item.parentId.orEmpty()}/${item.id}"
            combined[identity] = item
        }
        return combined.values.take(limit.coerceIn(1, MAX_ITEMS_HARD_LIMIT))
    }

    @JvmStatic
    @Synchronized
    fun record(
        context: Context,
        serviceId: Int,
        url: String,
        comments: List<CommentsInfoItem>,
        parentId: String?
    ) {
        if (!isEnabled(context) || comments.isEmpty() || url.isBlank()) return
        recordForProfile(context, ProfileManager.getActiveProfileId(context), serviceId, url, comments, parentId)
    }

    @JvmStatic
    @Synchronized
    fun recordForProfile(
        context: Context,
        profileId: String,
        serviceId: Int,
        url: String,
        comments: List<CommentsInfoItem>,
        parentId: String?
    ) {
        if (!isEnabled(context) || comments.isEmpty() || url.isBlank()) return
        if (ProfileManager.getActiveProfileId(context) != profileId) return
        runCatching {
            val fresh = comments.map { item ->
                CachedComment(
                    id = item.commentId?.takeIf(String::isNotBlank)
                        ?: hash("${item.uploaderName}:${item.commentText}:${item.textualUploadDate}"),
                    author = item.uploaderName.orEmpty().take(160),
                    body = item.commentText.orEmpty().take(MAX_ITEM_TEXT_LENGTH),
                    date = item.uploadDate?.offsetDateTime()?.toString()
                        ?: item.textualUploadDate.orEmpty().take(80),
                    parentId = parentId
                )
            }.filter { it.body.isNotBlank() }
            if (fresh.isEmpty()) return@runCatching
            val normal = archiveFile(context, profileId, serviceId, url)
            val retained = retainedFile(context, profileId, serviceId, url)
            val keepDownloads = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.comment_archive_keep_downloaded_key), false)
            val pinned = keepDownloads && DownloadedAvailabilityIndex.isAvailable(serviceId, url)
            val destination = if (pinned || retained.isFile && keepDownloads) retained else normal
            val previous = readFile(if (retained.isFile) retained else normal)
            val items = merge(previous, fresh, limit(context))
            if (!destination.parentFile.orEmptyDirectory()) return@runCatching
            val payload = JSONObject().put(
                "items",
                JSONArray().apply {
                    items.forEach { item ->
                        put(
                            JSONObject().put("id", item.id)
                                .put("author", item.author)
                                .put("body", item.body)
                                .put("date", item.date)
                                .put("parentId", item.parentId.orEmpty())
                        )
                    }
                }
            )
            val temp = File(destination.parentFile, destination.name + ".tmp")
            GZIPOutputStream(temp.outputStream()).bufferedWriter(Charsets.UTF_8).use {
                it.write(payload.toString())
            }
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }
            if (destination == normal) retained.delete() else normal.delete()
            prune(context, keepDownloads)
        }
    }

    private fun File?.orEmptyDirectory(): Boolean = this?.let { it.isDirectory || it.mkdirs() } == true

    private fun prune(context: Context, keepDownloads: Boolean) {
        val root = File(context.filesDir, FOLDER)
        val files = root.walkTopDown().filter { it.isFile && it.name.endsWith(".json.gz") }
            .sortedWith(
                compareByDescending<File> {
                    keepDownloads && it.name.startsWith("saved-")
                }.thenByDescending(File::lastModified)
            ).toList()
        val now = System.currentTimeMillis()
        var bytes = 0L
        files.forEachIndexed { position, file ->
            val expired = now - file.lastModified() > MAX_AGE_MILLIS &&
                !(keepDownloads && file.name.startsWith("saved-"))
            val allowed = position < MAX_FILES && !expired &&
                bytes + file.length() <= MAX_TOTAL_BYTES
            if (allowed) bytes += file.length() else file.delete()
        }
    }

    @JvmStatic
    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FOLDER).deleteRecursively()
    }

    @JvmStatic
    @Synchronized
    fun forgetProfile(context: Context, profileId: String) {
        directory(context, profileId).deleteRecursively()
    }
}
