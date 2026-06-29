/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.exoplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceOption
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.exoplayer.ExoPlayerSettingsViewModel
import net.newpipe.app.viewmodel.settings.exoplayer.PROGRESSIVE_LOAD_INTERVAL_1
import net.newpipe.app.viewmodel.settings.exoplayer.PROGRESSIVE_LOAD_INTERVAL_16
import net.newpipe.app.viewmodel.settings.exoplayer.PROGRESSIVE_LOAD_INTERVAL_256
import net.newpipe.app.viewmodel.settings.exoplayer.PROGRESSIVE_LOAD_INTERVAL_64
import net.newpipe.app.viewmodel.settings.exoplayer.PROGRESSIVE_LOAD_INTERVAL_EXOPLAYER_DEFAULT
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.always_use_exoplayer_set_output_surface_workaround_summary
import newpipe.shared.generated.resources.always_use_exoplayer_set_output_surface_workaround_title
import newpipe.shared.generated.resources.disable_media_tunneling_summary
import newpipe.shared.generated.resources.disable_media_tunneling_title
import newpipe.shared.generated.resources.progressive_load_interval_1_kib
import newpipe.shared.generated.resources.progressive_load_interval_16_kib
import newpipe.shared.generated.resources.progressive_load_interval_256_kib
import newpipe.shared.generated.resources.progressive_load_interval_64_kib
import newpipe.shared.generated.resources.progressive_load_interval_exoplayer_default
import newpipe.shared.generated.resources.progressive_load_interval_summary
import newpipe.shared.generated.resources.progressive_load_interval_title
import newpipe.shared.generated.resources.settings_category_exoplayer_title
import newpipe.shared.generated.resources.use_exoplayer_decoder_fallback_summary
import newpipe.shared.generated.resources.use_exoplayer_decoder_fallback_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExoPlayerSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: ExoPlayerSettingsViewModel = koinViewModel()
) {
    val progressiveLoadInterval by viewModel.progressiveLoadInterval.collectAsState()
    val useDecoderFallback by viewModel.useDecoderFallback.collectAsState()
    val disableMediaTunneling by viewModel.disableMediaTunneling.collectAsState()
    val alwaysUseSetOutputSurfaceWorkaround by
        viewModel.alwaysUseSetOutputSurfaceWorkaround.collectAsState()

    ExoPlayerSettingsContent(
        progressiveLoadInterval = progressiveLoadInterval,
        useDecoderFallback = useDecoderFallback,
        disableMediaTunneling = disableMediaTunneling,
        alwaysUseSetOutputSurfaceWorkaround = alwaysUseSetOutputSurfaceWorkaround,
        onNavigateUp = navigator::navigateUp,
        onSetProgressiveLoadInterval = viewModel::setProgressiveLoadInterval,
        onToggleUseDecoderFallback = viewModel::toggleUseDecoderFallback,
        onToggleDisableMediaTunneling = viewModel::toggleDisableMediaTunneling,
        onToggleAlwaysUseSetOutputSurfaceWorkaround =
            viewModel::toggleAlwaysUseSetOutputSurfaceWorkaround
    )
}

@Composable
private fun ExoPlayerSettingsContent(
    progressiveLoadInterval: String,
    useDecoderFallback: Boolean,
    disableMediaTunneling: Boolean,
    alwaysUseSetOutputSurfaceWorkaround: Boolean,
    onNavigateUp: () -> Unit,
    onSetProgressiveLoadInterval: (String) -> Unit,
    onToggleUseDecoderFallback: (Boolean) -> Unit,
    onToggleDisableMediaTunneling: (Boolean) -> Unit,
    onToggleAlwaysUseSetOutputSurfaceWorkaround: (Boolean) -> Unit
) {
    val intervalOptions = progressiveLoadIntervalOptions()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_exoplayer_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            ListPreference(
                title = stringResource(Res.string.progressive_load_interval_title),
                options = intervalOptions,
                selectedValue = progressiveLoadInterval,
                onValueChange = onSetProgressiveLoadInterval
            )
            SwitchPreference(
                title = stringResource(Res.string.use_exoplayer_decoder_fallback_title),
                summary = stringResource(Res.string.use_exoplayer_decoder_fallback_summary),
                isChecked = useDecoderFallback,
                onCheckedChange = onToggleUseDecoderFallback
            )
            SwitchPreference(
                title = stringResource(Res.string.disable_media_tunneling_title),
                summary = stringResource(Res.string.disable_media_tunneling_summary),
                isChecked = disableMediaTunneling,
                onCheckedChange = onToggleDisableMediaTunneling
            )
            SwitchPreference(
                title = stringResource(
                    Res.string.always_use_exoplayer_set_output_surface_workaround_title
                ),
                summary = stringResource(
                    Res.string.always_use_exoplayer_set_output_surface_workaround_summary
                ),
                isChecked = alwaysUseSetOutputSurfaceWorkaround,
                onCheckedChange = onToggleAlwaysUseSetOutputSurfaceWorkaround
            )
        }
    }
}

@Composable
private fun progressiveLoadIntervalOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(
        PROGRESSIVE_LOAD_INTERVAL_1,
        stringResource(Res.string.progressive_load_interval_1_kib)
    ),
    ListPreferenceOption(
        PROGRESSIVE_LOAD_INTERVAL_16,
        stringResource(Res.string.progressive_load_interval_16_kib)
    ),
    ListPreferenceOption(
        PROGRESSIVE_LOAD_INTERVAL_64,
        stringResource(Res.string.progressive_load_interval_64_kib)
    ),
    ListPreferenceOption(
        PROGRESSIVE_LOAD_INTERVAL_256,
        stringResource(Res.string.progressive_load_interval_256_kib)
    ),
    ListPreferenceOption(
        PROGRESSIVE_LOAD_INTERVAL_EXOPLAYER_DEFAULT,
        stringResource(Res.string.progressive_load_interval_exoplayer_default)
    )
)

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ExoPlayerSettingsScreenPreview() {
    ExoPlayerSettingsContent(
        progressiveLoadInterval = PROGRESSIVE_LOAD_INTERVAL_64,
        useDecoderFallback = false,
        disableMediaTunneling = false,
        alwaysUseSetOutputSurfaceWorkaround = false,
        onNavigateUp = {},
        onSetProgressiveLoadInterval = {},
        onToggleUseDecoderFallback = {},
        onToggleDisableMediaTunneling = {},
        onToggleAlwaysUseSetOutputSurfaceWorkaround = {}
    )
}
