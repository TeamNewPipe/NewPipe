/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.preferences.ExoPlayerPreferences.DEFAULT_ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND
import net.newpipe.app.preferences.ExoPlayerPreferences.DEFAULT_DISABLE_MEDIA_TUNNELING
import net.newpipe.app.preferences.ExoPlayerPreferences.DEFAULT_PROGRESSIVE_LOAD_INTERVAL
import net.newpipe.app.preferences.ExoPlayerPreferences.DEFAULT_USE_EXOPLAYER_DECODER_FALLBACK
import net.newpipe.app.preferences.ExoPlayerPreferences.PROGRESSIVE_LOAD_INTERVALS
import net.newpipe.app.preferences.ExoPlayerPreferences.PROGRESSIVE_LOAD_INTERVAL_EXOPLAYER_DEFAULT
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.PlayerSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.always_use_exoplayer_set_output_surface_workaround_summary
import newpipe.shared.generated.resources.always_use_exoplayer_set_output_surface_workaround_title
import newpipe.shared.generated.resources.disable_media_tunneling_automatic_info
import newpipe.shared.generated.resources.disable_media_tunneling_summary
import newpipe.shared.generated.resources.disable_media_tunneling_title
import newpipe.shared.generated.resources.progressive_load_interval_exoplayer_default
import newpipe.shared.generated.resources.progressive_load_interval_summary
import newpipe.shared.generated.resources.progressive_load_interval_title
import newpipe.shared.generated.resources.use_exoplayer_decoder_fallback_summary
import newpipe.shared.generated.resources.use_exoplayer_decoder_fallback_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExoPlayerSettingsSections(viewModel: PlayerSettingsViewModel = koinViewModel()) {
    val progressiveLoadInterval by viewModel.progressiveLoadInterval.collectAsStateWithLifecycle()
    val useExoplayerDecoderFallback by viewModel.useExoplayerDecoderFallback.collectAsStateWithLifecycle()
    val disableMediaTunneling by viewModel.disableMediaTunneling.collectAsStateWithLifecycle()
    val mediaTunnelingAutoDisabled by viewModel.mediaTunnelingAutoDisabled.collectAsStateWithLifecycle()
    val alwaysUseSetOutputSurfaceWorkaround by viewModel.alwaysUseSetOutputSurfaceWorkaround
        .collectAsStateWithLifecycle()

    ExoPlayerSettingsSectionsContent(
        progressiveLoadInterval = progressiveLoadInterval,
        useExoplayerDecoderFallback = useExoplayerDecoderFallback,
        disableMediaTunneling = disableMediaTunneling,
        mediaTunnelingAutoDisabled = mediaTunnelingAutoDisabled,
        alwaysUseSetOutputSurfaceWorkaround = alwaysUseSetOutputSurfaceWorkaround,
        onProgressiveLoadIntervalChange = viewModel::setProgressiveLoadInterval,
        onUseExoplayerDecoderFallbackChange = viewModel::setUseExoplayerDecoderFallback,
        onDisableMediaTunnelingChange = viewModel::setDisableMediaTunneling,
        onAlwaysUseSetOutputSurfaceWorkaroundChange = viewModel::setAlwaysUseSetOutputSurfaceWorkaround
    )
}

@Composable
fun ExoPlayerSettingsSectionsContent(
    progressiveLoadInterval: String = DEFAULT_PROGRESSIVE_LOAD_INTERVAL,
    useExoplayerDecoderFallback: Boolean = DEFAULT_USE_EXOPLAYER_DECODER_FALLBACK,
    disableMediaTunneling: Boolean = DEFAULT_DISABLE_MEDIA_TUNNELING,
    mediaTunnelingAutoDisabled: Boolean = false,
    alwaysUseSetOutputSurfaceWorkaround: Boolean = DEFAULT_ALWAYS_USE_SET_OUTPUT_SURFACE_WORKAROUND,
    onProgressiveLoadIntervalChange: (String) -> Unit = {},
    onUseExoplayerDecoderFallbackChange: (Boolean) -> Unit = {},
    onDisableMediaTunnelingChange: (Boolean) -> Unit = {},
    onAlwaysUseSetOutputSurfaceWorkaroundChange: (Boolean) -> Unit = {}
) {
    val exoPlayerDefaultLabel = stringResource(Res.string.progressive_load_interval_exoplayer_default)

    val loadIntervalEntries = PROGRESSIVE_LOAD_INTERVALS.map { value ->
        ListPreferenceEntry(
            value = value,
            title = when (value) {
                PROGRESSIVE_LOAD_INTERVAL_EXOPLAYER_DEFAULT -> exoPlayerDefaultLabel
                else -> "$value KiB"
            }
        )
    }
    val selectedLoadIntervalTitle = loadIntervalEntries
        .firstOrNull { it.value == progressiveLoadInterval }
        ?.title
        .orEmpty()

    val tunnelingBaseSummary = stringResource(Res.string.disable_media_tunneling_summary)
    val tunnelingSummary = if (mediaTunnelingAutoDisabled) {
        tunnelingBaseSummary + " " + stringResource(Res.string.disable_media_tunneling_automatic_info)
    } else {
        tunnelingBaseSummary
    }

    ListPreference(
        title = stringResource(Res.string.progressive_load_interval_title),
        entries = loadIntervalEntries,
        selectedValue = progressiveLoadInterval,
        onValueSelected = onProgressiveLoadIntervalChange,
        summary = stringResource(Res.string.progressive_load_interval_summary, selectedLoadIntervalTitle)
    )
    SwitchPreference(
        title = stringResource(Res.string.use_exoplayer_decoder_fallback_title),
        summary = stringResource(Res.string.use_exoplayer_decoder_fallback_summary),
        checked = useExoplayerDecoderFallback,
        onCheckedChange = onUseExoplayerDecoderFallbackChange
    )
    SwitchPreference(
        title = stringResource(Res.string.disable_media_tunneling_title),
        summary = tunnelingSummary,
        checked = disableMediaTunneling,
        onCheckedChange = onDisableMediaTunnelingChange
    )
    SwitchPreference(
        title = stringResource(Res.string.always_use_exoplayer_set_output_surface_workaround_title),
        summary = stringResource(Res.string.always_use_exoplayer_set_output_surface_workaround_summary),
        checked = alwaysUseSetOutputSurfaceWorkaround,
        onCheckedChange = onAlwaysUseSetOutputSurfaceWorkaroundChange
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ExoPlayerSettingsSectionsPreview() {
    Column { ExoPlayerSettingsSectionsContent() }
}
