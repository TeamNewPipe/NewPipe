/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID

/** Four seconds of generated black video; provenance is in test/resources/local-chapters. */
internal class EmbeddedChapterFixture(private val context: Context) : AutoCloseable {
    private val directory = File(context.cacheDir, "update/embedded-${UUID.randomUUID()}").apply { mkdirs() }
    val file = File(directory, "local.mp4").apply {
        val encoded = InstrumentationRegistry.getInstrumentation().context.assets.open("local-chapters/nero.mp4.base64").use { it.readBytes() }
        writeBytes(Base64.decode(encoded, Base64.DEFAULT))
    }
    val uri: String get() = Uri.fromFile(file).toString()
    private val aliases = mutableListOf<String>()

    fun rememberAlias(alias: String) {
        aliases += alias
    }

    fun save(chapters: List<OfflineChapter>, duration: Long = 4000) {
        OfflinePlaybackMetadata.save(
            context,
            uri,
            file.name,
            OfflineDownloadMetadata(0, "https://www.youtube.com/watch?v=aaaaaaaaaaa", "Saved download title", "Saved channel", duration, 'v', false, chapters)
        )
    }

    override fun close() {
        aliases.forEach { OfflinePlaybackMetadata.remove(context, it) }
        OfflinePlaybackMetadata.remove(context, uri)
        directory.deleteRecursively()
    }
}
