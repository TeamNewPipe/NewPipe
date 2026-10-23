/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package org.schabi.newpipe.settings

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.rx3.await
import net.newpipe.app.platform.HistoryActions
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.InfoCache

@Suppress("unused")
class ComposeHistoryActions(private val context: Context) : HistoryActions {

    private val recordManager = HistoryRecordManager(context)

    override fun wipeMetadataCache() = InfoCache.getInstance().clearCache()

    override suspend fun clearWatchHistory() = runDeletion(REQUEST_WATCH_HISTORY) {
        recordManager.deleteCompleteStreamStateHistory().await()
        recordManager.deleteWholeStreamHistory().await()
        recordManager.removeOrphanedRecords().await()
    }

    override suspend fun clearPlaybackStates() = runDeletion(REQUEST_PLAYBACK_STATES) {
        recordManager.deleteCompleteStreamStateHistory().await()
    }

    override suspend fun clearSearchHistory() = runDeletion(REQUEST_SEARCH_HISTORY) {
        recordManager.deleteCompleteSearchHistory().await()
    }

    override fun clearRecaptchaCookies() = DownloaderImpl.getInstance().setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, "")

    private suspend fun runDeletion(request: String, deletion: suspend () -> Unit): Boolean = try {
        deletion()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        ErrorUtil.openActivity(
            context,
            ErrorInfo(error, UserAction.DELETE_FROM_HISTORY, request)
        )
        false
    }

    private companion object {
        const val REQUEST_WATCH_HISTORY = "Delete from history"
        const val REQUEST_PLAYBACK_STATES = "Delete playback states"
        const val REQUEST_SEARCH_HISTORY = "Delete search history"
    }
}
