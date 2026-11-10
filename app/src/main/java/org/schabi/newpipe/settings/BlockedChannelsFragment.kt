/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.compose.content
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.screens.settings.content.BlockedChannelsScreen
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.ThemeHelper

class BlockedChannelsFragment : Fragment() {
    private val viewModel: BlockedChannelsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        val blockedChannels by viewModel.blockedChannels.collectAsStateWithLifecycle()
        AppTheme {
            Surface {
                BlockedChannelsScreen(blockedChannels, viewModel::unblockChannel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.blocked_channels))
    }
}
