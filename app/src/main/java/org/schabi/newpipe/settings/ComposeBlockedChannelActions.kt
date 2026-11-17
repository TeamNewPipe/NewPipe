/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.Context
import androidx.annotation.Keep
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx3.await
import net.newpipe.app.model.BlockedChannel
import net.newpipe.app.platform.BlockedChannelActions
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.channel.BlockedChannelManager

@Keep
class ComposeBlockedChannelActions(private val context: Context) : BlockedChannelActions {
    private val manager = BlockedChannelManager(context)

    override fun getBlockedChannels() = manager.blockedChannels()
        .subscribeOn(Schedulers.io())
        .asFlow()
        .map { channels -> channels.map { BlockedChannel(it.url, it.name) } }
        .catch { error ->
            ErrorUtil.showSnackbar(context, ErrorInfo(error, UserAction.UI_ERROR, "Get blocked channels"))
        }

    override suspend fun unblockChannel(url: String) {
        try {
            manager.unblockChannel(url).subscribeOn(Schedulers.io()).await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            ErrorUtil.showSnackbar(context, ErrorInfo(error, UserAction.UI_ERROR, "Unblock channel"))
        }
    }
}
