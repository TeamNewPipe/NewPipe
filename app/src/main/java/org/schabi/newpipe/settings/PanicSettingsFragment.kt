/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import net.newpipe.app.AppKoinContext
import net.newpipe.app.screen.settings.PanicSettingsScreen
import net.newpipe.app.theme.AppTheme
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper

class PanicSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            AppKoinContext {
                AppTheme {
                    Surface {
                        PanicSettingsScreen(
                            showTopBar = false
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.panic_settings_category))
    }
}
