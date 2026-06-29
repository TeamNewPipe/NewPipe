/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.player

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
import net.newpipe.app.composable.PreferenceCategoryHeader
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TextPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.player.AUDIO_FORMAT_M4A
import net.newpipe.app.viewmodel.settings.player.AUDIO_FORMAT_OGG
import net.newpipe.app.viewmodel.settings.player.AUDIO_FORMAT_WEBM
import net.newpipe.app.viewmodel.settings.player.LIMIT_DATA_OFF
import net.newpipe.app.viewmodel.settings.player.LIMIT_DATA_WIFI_ONLY
import net.newpipe.app.viewmodel.settings.player.PlayerSettingsViewModel
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_1080
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_1080_60
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_144
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_1440
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_2160
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_240
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_360
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_480
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_720
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_720_60
import net.newpipe.app.viewmodel.settings.player.RESOLUTION_BEST
import net.newpipe.app.viewmodel.settings.player.THUMBNAIL_HIGH
import net.newpipe.app.viewmodel.settings.player.THUMBNAIL_LOW
import net.newpipe.app.viewmodel.settings.player.THUMBNAIL_OFF
import net.newpipe.app.viewmodel.settings.player.VIDEO_FORMAT_3GP
import net.newpipe.app.viewmodel.settings.player.VIDEO_FORMAT_MP4
import net.newpipe.app.viewmodel.settings.player.VIDEO_FORMAT_WEBM
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.audio_format_m4a
import newpipe.shared.generated.resources.audio_format_ogg
import newpipe.shared.generated.resources.audio_format_webm
import newpipe.shared.generated.resources.best_resolution
import newpipe.shared.generated.resources.default_audio_format_title
import newpipe.shared.generated.resources.default_popup_resolution_title
import newpipe.shared.generated.resources.default_resolution_title
import newpipe.shared.generated.resources.default_video_format_title
import newpipe.shared.generated.resources.limit_data_usage_none
import newpipe.shared.generated.resources.limit_data_usage_wifi_only
import newpipe.shared.generated.resources.limit_mobile_data_usage_title
import newpipe.shared.generated.resources.prefer_descriptive_audio_summary
import newpipe.shared.generated.resources.prefer_descriptive_audio_title
import newpipe.shared.generated.resources.prefer_original_audio_summary
import newpipe.shared.generated.resources.prefer_original_audio_title
import newpipe.shared.generated.resources.resolution_1080p
import newpipe.shared.generated.resources.resolution_1080p60
import newpipe.shared.generated.resources.resolution_1440p
import newpipe.shared.generated.resources.resolution_144p
import newpipe.shared.generated.resources.resolution_2160p
import newpipe.shared.generated.resources.resolution_240p
import newpipe.shared.generated.resources.resolution_360p
import newpipe.shared.generated.resources.resolution_480p
import newpipe.shared.generated.resources.resolution_720p
import newpipe.shared.generated.resources.resolution_720p60
import newpipe.shared.generated.resources.seekbar_preview_thumbnail_high
import newpipe.shared.generated.resources.seekbar_preview_thumbnail_low
import newpipe.shared.generated.resources.seekbar_preview_thumbnail_off
import newpipe.shared.generated.resources.seekbar_preview_thumbnail_title
import newpipe.shared.generated.resources.settings_category_exoplayer_summary
import newpipe.shared.generated.resources.settings_category_exoplayer_title
import newpipe.shared.generated.resources.settings_category_player_title
import newpipe.shared.generated.resources.settings_category_video_audio_title
import newpipe.shared.generated.resources.show_higher_resolutions_summary
import newpipe.shared.generated.resources.show_higher_resolutions_title
import newpipe.shared.generated.resources.show_play_with_kodi_summary
import newpipe.shared.generated.resources.show_play_with_kodi_title
import newpipe.shared.generated.resources.use_external_audio_player_title
import newpipe.shared.generated.resources.use_external_video_player_summary
import newpipe.shared.generated.resources.use_external_video_player_title
import newpipe.shared.generated.resources.video_format_3gp
import newpipe.shared.generated.resources.video_format_mp4
import newpipe.shared.generated.resources.video_format_webm
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlayerSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: PlayerSettingsViewModel = koinViewModel()
) {
    val defaultResolution by viewModel.defaultResolution.collectAsState()
    val defaultPopupResolution by viewModel.defaultPopupResolution.collectAsState()
    val limitMobileDataUsage by viewModel.limitMobileDataUsage.collectAsState()
    val showHigherResolutions by viewModel.showHigherResolutions.collectAsState()
    val defaultVideoFormat by viewModel.defaultVideoFormat.collectAsState()
    val defaultAudioFormat by viewModel.defaultAudioFormat.collectAsState()
    val preferOriginalAudio by viewModel.preferOriginalAudio.collectAsState()
    val preferDescriptiveAudio by viewModel.preferDescriptiveAudio.collectAsState()
    val useExternalVideoPlayer by viewModel.useExternalVideoPlayer.collectAsState()
    val useExternalAudioPlayer by viewModel.useExternalAudioPlayer.collectAsState()
    val showPlayWithKodi by viewModel.showPlayWithKodi.collectAsState()
    val seekbarThumbnail by viewModel.seekbarThumbnail.collectAsState()

    PlayerSettingsContent(
        defaultResolution = defaultResolution,
        defaultPopupResolution = defaultPopupResolution,
        limitMobileDataUsage = limitMobileDataUsage,
        showHigherResolutions = showHigherResolutions,
        defaultVideoFormat = defaultVideoFormat,
        defaultAudioFormat = defaultAudioFormat,
        preferOriginalAudio = preferOriginalAudio,
        preferDescriptiveAudio = preferDescriptiveAudio,
        useExternalVideoPlayer = useExternalVideoPlayer,
        useExternalAudioPlayer = useExternalAudioPlayer,
        showPlayWithKodi = showPlayWithKodi,
        seekbarThumbnail = seekbarThumbnail,
        onNavigateUp = navigator::navigateUp,
        onSetDefaultResolution = viewModel::setDefaultResolution,
        onSetDefaultPopupResolution = viewModel::setDefaultPopupResolution,
        onSetLimitMobileDataUsage = viewModel::setLimitMobileDataUsage,
        onToggleShowHigherResolutions = viewModel::toggleShowHigherResolutions,
        onSetDefaultVideoFormat = viewModel::setDefaultVideoFormat,
        onSetDefaultAudioFormat = viewModel::setDefaultAudioFormat,
        onTogglePreferOriginalAudio = viewModel::togglePreferOriginalAudio,
        onTogglePreferDescriptiveAudio = viewModel::togglePreferDescriptiveAudio,
        onExoPlayerSettings = { navigator.navigateTo(Destination.Settings.ExoPlayer) },
        onToggleUseExternalVideoPlayer = viewModel::toggleUseExternalVideoPlayer,
        onToggleUseExternalAudioPlayer = viewModel::toggleUseExternalAudioPlayer,
        onToggleShowPlayWithKodi = viewModel::toggleShowPlayWithKodi,
        onSetSeekbarThumbnail = viewModel::setSeekbarThumbnail
    )
}

@Composable
private fun PlayerSettingsContent(
    defaultResolution: String,
    defaultPopupResolution: String,
    limitMobileDataUsage: String,
    showHigherResolutions: Boolean,
    defaultVideoFormat: String,
    defaultAudioFormat: String,
    preferOriginalAudio: Boolean,
    preferDescriptiveAudio: Boolean,
    useExternalVideoPlayer: Boolean,
    useExternalAudioPlayer: Boolean,
    showPlayWithKodi: Boolean,
    seekbarThumbnail: String,
    onNavigateUp: () -> Unit,
    onSetDefaultResolution: (String) -> Unit,
    onSetDefaultPopupResolution: (String) -> Unit,
    onSetLimitMobileDataUsage: (String) -> Unit,
    onToggleShowHigherResolutions: (Boolean) -> Unit,
    onSetDefaultVideoFormat: (String) -> Unit,
    onSetDefaultAudioFormat: (String) -> Unit,
    onTogglePreferOriginalAudio: (Boolean) -> Unit,
    onTogglePreferDescriptiveAudio: (Boolean) -> Unit,
    onExoPlayerSettings: () -> Unit,
    onToggleUseExternalVideoPlayer: (Boolean) -> Unit,
    onToggleUseExternalAudioPlayer: (Boolean) -> Unit,
    onToggleShowPlayWithKodi: (Boolean) -> Unit,
    onSetSeekbarThumbnail: (String) -> Unit
) {
    val resolutionOptions = resolutionOptions(showHigherResolutions)
    val limitDataOptions = limitDataOptions()
    val videoFormatOptions = videoFormatOptions()
    val audioFormatOptions = audioFormatOptions()
    val thumbnailOptions = thumbnailOptions()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_video_audio_title),
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
                title = stringResource(Res.string.default_resolution_title),
                options = resolutionOptions,
                selectedValue = defaultResolution,
                onValueChange = onSetDefaultResolution
            )
            ListPreference(
                title = stringResource(Res.string.default_popup_resolution_title),
                options = resolutionOptions,
                selectedValue = defaultPopupResolution,
                onValueChange = onSetDefaultPopupResolution
            )
            ListPreference(
                title = stringResource(Res.string.limit_mobile_data_usage_title),
                options = limitDataOptions,
                selectedValue = limitMobileDataUsage,
                onValueChange = onSetLimitMobileDataUsage
            )
            SwitchPreference(
                title = stringResource(Res.string.show_higher_resolutions_title),
                summary = stringResource(Res.string.show_higher_resolutions_summary),
                isChecked = showHigherResolutions,
                onCheckedChange = onToggleShowHigherResolutions
            )
            ListPreference(
                title = stringResource(Res.string.default_video_format_title),
                options = videoFormatOptions,
                selectedValue = defaultVideoFormat,
                onValueChange = onSetDefaultVideoFormat
            )
            ListPreference(
                title = stringResource(Res.string.default_audio_format_title),
                options = audioFormatOptions,
                selectedValue = defaultAudioFormat,
                onValueChange = onSetDefaultAudioFormat
            )
            SwitchPreference(
                title = stringResource(Res.string.prefer_original_audio_title),
                summary = stringResource(Res.string.prefer_original_audio_summary),
                isChecked = preferOriginalAudio,
                onCheckedChange = onTogglePreferOriginalAudio
            )
            SwitchPreference(
                title = stringResource(Res.string.prefer_descriptive_audio_title),
                summary = stringResource(Res.string.prefer_descriptive_audio_summary),
                isChecked = preferDescriptiveAudio,
                onCheckedChange = onTogglePreferDescriptiveAudio
            )
            TextPreference(
                title = stringResource(Res.string.settings_category_exoplayer_title),
                summary = stringResource(Res.string.settings_category_exoplayer_summary),
                onClick = onExoPlayerSettings
            )

            PreferenceCategoryHeader(
                title = stringResource(Res.string.settings_category_player_title)
            )

            SwitchPreference(
                title = stringResource(Res.string.use_external_video_player_title),
                summary = stringResource(Res.string.use_external_video_player_summary),
                isChecked = useExternalVideoPlayer,
                onCheckedChange = onToggleUseExternalVideoPlayer
            )
            SwitchPreference(
                title = stringResource(Res.string.use_external_audio_player_title),
                isChecked = useExternalAudioPlayer,
                onCheckedChange = onToggleUseExternalAudioPlayer
            )
            SwitchPreference(
                title = stringResource(Res.string.show_play_with_kodi_title),
                summary = stringResource(Res.string.show_play_with_kodi_summary),
                isChecked = showPlayWithKodi,
                onCheckedChange = onToggleShowPlayWithKodi
            )
            ListPreference(
                title = stringResource(Res.string.seekbar_preview_thumbnail_title),
                options = thumbnailOptions,
                selectedValue = seekbarThumbnail,
                onValueChange = onSetSeekbarThumbnail
            )
        }
    }
}

@Composable
private fun resolutionOptions(showHigh: Boolean): List<ListPreferenceOption> {
    val base = listOf(
        ListPreferenceOption(RESOLUTION_BEST, stringResource(Res.string.best_resolution)),
        ListPreferenceOption(RESOLUTION_1080_60, stringResource(Res.string.resolution_1080p60)),
        ListPreferenceOption(RESOLUTION_1080, stringResource(Res.string.resolution_1080p)),
        ListPreferenceOption(RESOLUTION_720_60, stringResource(Res.string.resolution_720p60)),
        ListPreferenceOption(RESOLUTION_720, stringResource(Res.string.resolution_720p)),
        ListPreferenceOption(RESOLUTION_480, stringResource(Res.string.resolution_480p)),
        ListPreferenceOption(RESOLUTION_360, stringResource(Res.string.resolution_360p)),
        ListPreferenceOption(RESOLUTION_240, stringResource(Res.string.resolution_240p)),
        ListPreferenceOption(RESOLUTION_144, stringResource(Res.string.resolution_144p))
    )
    val high = listOf(
        ListPreferenceOption(RESOLUTION_2160, stringResource(Res.string.resolution_2160p)),
        ListPreferenceOption(RESOLUTION_1440, stringResource(Res.string.resolution_1440p))
    )
    return if (showHigh) listOf(base.first()) + high + base.drop(1) else base
}

@Composable
private fun limitDataOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(LIMIT_DATA_OFF, stringResource(Res.string.limit_data_usage_none)),
    ListPreferenceOption(LIMIT_DATA_WIFI_ONLY, stringResource(Res.string.limit_data_usage_wifi_only))
)

@Composable
private fun videoFormatOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(VIDEO_FORMAT_MP4, stringResource(Res.string.video_format_mp4)),
    ListPreferenceOption(VIDEO_FORMAT_WEBM, stringResource(Res.string.video_format_webm)),
    ListPreferenceOption(VIDEO_FORMAT_3GP, stringResource(Res.string.video_format_3gp))
)

@Composable
private fun audioFormatOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(AUDIO_FORMAT_M4A, stringResource(Res.string.audio_format_m4a)),
    ListPreferenceOption(AUDIO_FORMAT_WEBM, stringResource(Res.string.audio_format_webm)),
    ListPreferenceOption(AUDIO_FORMAT_OGG, stringResource(Res.string.audio_format_ogg))
)

@Composable
private fun thumbnailOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(THUMBNAIL_HIGH, stringResource(Res.string.seekbar_preview_thumbnail_high)),
    ListPreferenceOption(THUMBNAIL_LOW, stringResource(Res.string.seekbar_preview_thumbnail_low)),
    ListPreferenceOption(THUMBNAIL_OFF, stringResource(Res.string.seekbar_preview_thumbnail_off))
)


@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlayerSettingsScreenPreview() {
    PlayerSettingsContent(
        defaultResolution = RESOLUTION_BEST,
        defaultPopupResolution = RESOLUTION_720,
        limitMobileDataUsage = LIMIT_DATA_OFF,
        showHigherResolutions = false,
        defaultVideoFormat = VIDEO_FORMAT_MP4,
        defaultAudioFormat = AUDIO_FORMAT_M4A,
        preferOriginalAudio = true,
        preferDescriptiveAudio = false,
        useExternalVideoPlayer = false,
        useExternalAudioPlayer = false,
        showPlayWithKodi = false,
        seekbarThumbnail = THUMBNAIL_HIGH,
        onNavigateUp = {},
        onSetDefaultResolution = {},
        onSetDefaultPopupResolution = {},
        onSetLimitMobileDataUsage = {},
        onToggleShowHigherResolutions = {},
        onSetDefaultVideoFormat = {},
        onSetDefaultAudioFormat = {},
        onTogglePreferOriginalAudio = {},
        onTogglePreferDescriptiveAudio = {},
        onExoPlayerSettings = {},
        onToggleUseExternalVideoPlayer = {},
        onToggleUseExternalAudioPlayer = {},
        onToggleShowPlayWithKodi = {},
        onSetSeekbarThumbnail = {}
    )
}
