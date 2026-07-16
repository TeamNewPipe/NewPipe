/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preferences.VideoAudioPreferences
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.VideoAudioSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.best_resolution
import newpipe.shared.generated.resources.default_audio_format_title
import newpipe.shared.generated.resources.default_popup_resolution_title
import newpipe.shared.generated.resources.default_resolution_title
import newpipe.shared.generated.resources.default_video_format_title
import newpipe.shared.generated.resources.limit_data_usage_none_description
import newpipe.shared.generated.resources.limit_mobile_data_usage_title
import newpipe.shared.generated.resources.prefer_descriptive_audio_summary
import newpipe.shared.generated.resources.prefer_descriptive_audio_title
import newpipe.shared.generated.resources.prefer_original_audio_summary
import newpipe.shared.generated.resources.prefer_original_audio_title
import newpipe.shared.generated.resources.settings_category_video_audio_title
import newpipe.shared.generated.resources.show_higher_resolutions_summary
import newpipe.shared.generated.resources.show_higher_resolutions_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VideoAudioSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: VideoAudioSettingsViewModel = koinViewModel()
) {
    val defaultResolution by viewModel.defaultResolution.collectAsStateWithLifecycle()
    val defaultPopupResolution by viewModel.defaultPopupResolution.collectAsStateWithLifecycle()
    val mobileDataResolution by viewModel.mobileDataResolution.collectAsStateWithLifecycle()
    val showHigherResolutions by viewModel.showHigherResolutions.collectAsStateWithLifecycle()
    val resolutionValues by viewModel.resolutionValues.collectAsStateWithLifecycle()
    val mobileDataResolutionValues by viewModel.mobileDataResolutionValues.collectAsStateWithLifecycle()
    val videoFormat by viewModel.videoFormat.collectAsStateWithLifecycle()
    val audioFormat by viewModel.audioFormat.collectAsStateWithLifecycle()
    val preferOriginalAudio by viewModel.preferOriginalAudio.collectAsStateWithLifecycle()
    val preferDescriptiveAudio by viewModel.preferDescriptiveAudio.collectAsStateWithLifecycle()

    VideoAudioSettingsScreenContent(
        defaultResolution = defaultResolution,
        defaultPopupResolution = defaultPopupResolution,
        mobileDataResolution = mobileDataResolution,
        showHigherResolutions = showHigherResolutions,
        resolutionValues = resolutionValues,
        mobileDataResolutionValues = mobileDataResolutionValues,
        videoFormat = videoFormat,
        audioFormat = audioFormat,
        preferOriginalAudio = preferOriginalAudio,
        preferDescriptiveAudio = preferDescriptiveAudio,
        onDefaultResolutionChange = viewModel::setDefaultResolution,
        onDefaultPopupResolutionChange = viewModel::setDefaultPopupResolution,
        onMobileDataResolutionChange = viewModel::setMobileDataResolution,
        onShowHigherResolutionsChange = viewModel::setShowHigherResolutions,
        onVideoFormatChange = viewModel::setVideoFormat,
        onAudioFormatChange = viewModel::setAudioFormat,
        onPreferOriginalAudioChange = viewModel::setPreferOriginalAudio,
        onPreferDescriptiveAudioChange = viewModel::setPreferDescriptiveAudio,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun VideoAudioSettingsScreenContent(
    defaultResolution: String = VideoAudioPreferences.DEFAULT_RESOLUTION,
    defaultPopupResolution: String = VideoAudioPreferences.DEFAULT_POPUP_RESOLUTION,
    mobileDataResolution: String = VideoAudioPreferences.DEFAULT_LIMIT_MOBILE_DATA_USAGE,
    showHigherResolutions: Boolean = VideoAudioPreferences.DEFAULT_SHOW_HIGHER_RESOLUTIONS,
    resolutionValues: List<String> = VideoAudioPreferences.BASE_RESOLUTIONS,
    mobileDataResolutionValues: List<String> = VideoAudioPreferences.BASE_MOBILE_DATA_RESOLUTIONS,
    videoFormat: String = VideoAudioPreferences.DEFAULT_VIDEO_FORMAT,
    audioFormat: String = VideoAudioPreferences.DEFAULT_AUDIO_FORMAT,
    preferOriginalAudio: Boolean = VideoAudioPreferences.DEFAULT_PREFER_ORIGINAL_AUDIO,
    preferDescriptiveAudio: Boolean = VideoAudioPreferences.DEFAULT_PREFER_DESCRIPTIVE_AUDIO,
    onDefaultResolutionChange: (String) -> Unit = {},
    onDefaultPopupResolutionChange: (String) -> Unit = {},
    onMobileDataResolutionChange: (String) -> Unit = {},
    onShowHigherResolutionsChange: (Boolean) -> Unit = {},
    onVideoFormatChange: (String) -> Unit = {},
    onAudioFormatChange: (String) -> Unit = {},
    onPreferOriginalAudioChange: (Boolean) -> Unit = {},
    onPreferDescriptiveAudioChange: (Boolean) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val bestResolutionLabel = stringResource(Res.string.best_resolution)
    val noLimitLabel = stringResource(Res.string.limit_data_usage_none_description)

    fun resolutionEntry(value: String) = ListPreferenceEntry(
        value = value,
        title = if (value == VideoAudioPreferences.BEST_RESOLUTION) bestResolutionLabel else value
    )

    fun mobileDataEntry(value: String) = ListPreferenceEntry(
        value = value,
        title = if (value == VideoAudioPreferences.LIMIT_DATA_USAGE_NONE) noLimitLabel else value
    )

    val resolutionEntries = resolutionValues.map(::resolutionEntry)
    val mobileDataEntries = mobileDataResolutionValues.map(::mobileDataEntry)
    val videoFormatEntries = VideoAudioPreferences.VIDEO_FORMATS.map { (value, label) ->
        ListPreferenceEntry(value, label)
    }
    val audioFormatEntries = VideoAudioPreferences.AUDIO_FORMATS.map { (value, label) ->
        ListPreferenceEntry(value, label)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_video_audio_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            ListPreference(
                title = stringResource(Res.string.default_resolution_title),
                entries = resolutionEntries,
                selectedValue = defaultResolution,
                onValueSelected = onDefaultResolutionChange
            )
            ListPreference(
                title = stringResource(Res.string.default_popup_resolution_title),
                entries = resolutionEntries,
                selectedValue = defaultPopupResolution,
                onValueSelected = onDefaultPopupResolutionChange
            )
            ListPreference(
                title = stringResource(Res.string.limit_mobile_data_usage_title),
                entries = mobileDataEntries,
                selectedValue = mobileDataResolution,
                onValueSelected = onMobileDataResolutionChange
            )
            SwitchPreference(
                title = stringResource(Res.string.show_higher_resolutions_title),
                summary = stringResource(Res.string.show_higher_resolutions_summary),
                checked = showHigherResolutions,
                onCheckedChange = onShowHigherResolutionsChange
            )
            ListPreference(
                title = stringResource(Res.string.default_video_format_title),
                entries = videoFormatEntries,
                selectedValue = videoFormat,
                onValueSelected = onVideoFormatChange
            )
            ListPreference(
                title = stringResource(Res.string.default_audio_format_title),
                entries = audioFormatEntries,
                selectedValue = audioFormat,
                onValueSelected = onAudioFormatChange
            )
            SwitchPreference(
                title = stringResource(Res.string.prefer_original_audio_title),
                summary = stringResource(Res.string.prefer_original_audio_summary),
                checked = preferOriginalAudio,
                onCheckedChange = onPreferOriginalAudioChange
            )
            SwitchPreference(
                title = stringResource(Res.string.prefer_descriptive_audio_title),
                summary = stringResource(Res.string.prefer_descriptive_audio_summary),
                checked = preferDescriptiveAudio,
                onCheckedChange = onPreferDescriptiveAudioChange
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun VideoAudioSettingsScreenPreview() {
    VideoAudioSettingsScreenContent()
}
