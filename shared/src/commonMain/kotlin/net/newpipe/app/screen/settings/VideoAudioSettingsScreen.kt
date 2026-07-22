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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preferences.VideoAudioPreferences
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.VideoAudioSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.add_to_playlist
import newpipe.shared.generated.resources.always
import newpipe.shared.generated.resources.always_ask_open_action
import newpipe.shared.generated.resources.auto_queue_summary
import newpipe.shared.generated.resources.auto_queue_title
import newpipe.shared.generated.resources.autoplay_summary
import newpipe.shared.generated.resources.autoplay_title
import newpipe.shared.generated.resources.background_player
import newpipe.shared.generated.resources.best_resolution
import newpipe.shared.generated.resources.brightness
import newpipe.shared.generated.resources.clear_queue_confirmation_summary
import newpipe.shared.generated.resources.clear_queue_confirmation_title
import newpipe.shared.generated.resources.default_audio_format_title
import newpipe.shared.generated.resources.default_popup_resolution_title
import newpipe.shared.generated.resources.default_resolution_title
import newpipe.shared.generated.resources.default_video_format_title
import newpipe.shared.generated.resources.dont_show
import newpipe.shared.generated.resources.download
import newpipe.shared.generated.resources.enqueue_stream
import newpipe.shared.generated.resources.high_quality_larger
import newpipe.shared.generated.resources.ignore_hardware_media_buttons_summary
import newpipe.shared.generated.resources.ignore_hardware_media_buttons_title
import newpipe.shared.generated.resources.left_gesture_control_summary
import newpipe.shared.generated.resources.left_gesture_control_title
import newpipe.shared.generated.resources.limit_data_usage_none_description
import newpipe.shared.generated.resources.limit_mobile_data_usage_title
import newpipe.shared.generated.resources.low_quality_smaller
import newpipe.shared.generated.resources.minimize_on_exit_background_description
import newpipe.shared.generated.resources.minimize_on_exit_none_description
import newpipe.shared.generated.resources.minimize_on_exit_popup_description
import newpipe.shared.generated.resources.minimize_on_exit_summary
import newpipe.shared.generated.resources.minimize_on_exit_title
import newpipe.shared.generated.resources.never
import newpipe.shared.generated.resources.new_seek_duration_toast
import newpipe.shared.generated.resources.none
import newpipe.shared.generated.resources.popup_player
import newpipe.shared.generated.resources.popup_remember_size_pos_summary
import newpipe.shared.generated.resources.popup_remember_size_pos_title
import newpipe.shared.generated.resources.prefer_descriptive_audio_summary
import newpipe.shared.generated.resources.prefer_descriptive_audio_title
import newpipe.shared.generated.resources.prefer_original_audio_summary
import newpipe.shared.generated.resources.prefer_original_audio_title
import newpipe.shared.generated.resources.preferred_open_action_settings_summary
import newpipe.shared.generated.resources.preferred_open_action_settings_title
import newpipe.shared.generated.resources.resume_on_audio_focus_gain_summary
import newpipe.shared.generated.resources.resume_on_audio_focus_gain_title
import newpipe.shared.generated.resources.right_gesture_control_summary
import newpipe.shared.generated.resources.right_gesture_control_title
import newpipe.shared.generated.resources.seconds
import newpipe.shared.generated.resources.seek_duration_title
import newpipe.shared.generated.resources.seekbar_preview_thumbnail_title
import newpipe.shared.generated.resources.settings_category_player_behavior_title
import newpipe.shared.generated.resources.settings_category_player_title
import newpipe.shared.generated.resources.settings_category_video_audio_title
import newpipe.shared.generated.resources.show_higher_resolutions_summary
import newpipe.shared.generated.resources.show_higher_resolutions_title
import newpipe.shared.generated.resources.show_info
import newpipe.shared.generated.resources.show_play_with_kodi_summary
import newpipe.shared.generated.resources.show_play_with_kodi_title
import newpipe.shared.generated.resources.start_main_player_fullscreen_summary
import newpipe.shared.generated.resources.start_main_player_fullscreen_title
import newpipe.shared.generated.resources.use_external_audio_player_title
import newpipe.shared.generated.resources.use_external_video_player_summary
import newpipe.shared.generated.resources.use_external_video_player_title
import newpipe.shared.generated.resources.use_inexact_seek_summary
import newpipe.shared.generated.resources.use_inexact_seek_title
import newpipe.shared.generated.resources.video_player
import newpipe.shared.generated.resources.volume
import newpipe.shared.generated.resources.wifi_only
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
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
    val videoFormat by viewModel.videoFormat.collectAsStateWithLifecycle()
    val audioFormat by viewModel.audioFormat.collectAsStateWithLifecycle()
    val preferOriginalAudio by viewModel.preferOriginalAudio.collectAsStateWithLifecycle()
    val preferDescriptiveAudio by viewModel.preferDescriptiveAudio.collectAsStateWithLifecycle()
    val useExternalVideoPlayer by viewModel.useExternalVideoPlayer.collectAsStateWithLifecycle()
    val useExternalAudioPlayer by viewModel.useExternalAudioPlayer.collectAsStateWithLifecycle()
    val showPlayWithKodi by viewModel.showPlayWithKodi.collectAsStateWithLifecycle()
    val seekbarPreviewThumbnail by viewModel.seekbarPreviewThumbnail.collectAsStateWithLifecycle()
    val preferredOpenAction by viewModel.preferredOpenAction.collectAsStateWithLifecycle()
    val minimizeOnExit by viewModel.minimizeOnExit.collectAsStateWithLifecycle()
    val startMainPlayerFullscreen by viewModel.startMainPlayerFullscreen.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplay.collectAsStateWithLifecycle()
    val autoQueue by viewModel.autoQueue.collectAsStateWithLifecycle()
    val resumeOnAudioFocusGain by viewModel.resumeOnAudioFocusGain.collectAsStateWithLifecycle()
    val leftGestureControl by viewModel.leftGestureControl.collectAsStateWithLifecycle()
    val rightGestureControl by viewModel.rightGestureControl.collectAsStateWithLifecycle()
    val popupRememberSizePos by viewModel.popupRememberSizePos.collectAsStateWithLifecycle()
    val useInexactSeek by viewModel.useInexactSeek.collectAsStateWithLifecycle()
    val seekDuration by viewModel.seekDuration.collectAsStateWithLifecycle()
    val clearQueueConfirmation by viewModel.clearQueueConfirmation.collectAsStateWithLifecycle()
    val ignoreHardwareMediaButtons by viewModel.ignoreHardwareMediaButtons.collectAsStateWithLifecycle()

    // Option lists are pure functions of their toggles, so derive them instead of storing them.
    val resolutionValues = remember(showHigherResolutions) {
        VideoAudioPreferences.resolutions(showHigherResolutions)
    }
    val mobileDataResolutionValues = remember(showHigherResolutions) {
        VideoAudioPreferences.mobileDataResolutions(showHigherResolutions)
    }
    val seekDurationValues = remember(useInexactSeek) {
        VideoAudioPreferences.seekDurationsMs(useInexactSeek)
    }

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
        useExternalVideoPlayer = useExternalVideoPlayer,
        useExternalAudioPlayer = useExternalAudioPlayer,
        showPlayWithKodi = showPlayWithKodi,
        seekbarPreviewThumbnail = seekbarPreviewThumbnail,
        preferredOpenAction = preferredOpenAction,
        minimizeOnExit = minimizeOnExit,
        startMainPlayerFullscreen = startMainPlayerFullscreen,
        autoplay = autoplay,
        autoQueue = autoQueue,
        resumeOnAudioFocusGain = resumeOnAudioFocusGain,
        leftGestureControl = leftGestureControl,
        rightGestureControl = rightGestureControl,
        popupRememberSizePos = popupRememberSizePos,
        useInexactSeek = useInexactSeek,
        seekDuration = seekDuration,
        seekDurationValues = seekDurationValues,
        clearQueueConfirmation = clearQueueConfirmation,
        ignoreHardwareMediaButtons = ignoreHardwareMediaButtons,
        onDefaultResolutionChange = viewModel::setDefaultResolution,
        onDefaultPopupResolutionChange = viewModel::setDefaultPopupResolution,
        onMobileDataResolutionChange = viewModel::setMobileDataResolution,
        onShowHigherResolutionsChange = viewModel::setShowHigherResolutions,
        onVideoFormatChange = viewModel::setVideoFormat,
        onAudioFormatChange = viewModel::setAudioFormat,
        onPreferOriginalAudioChange = viewModel::setPreferOriginalAudio,
        onPreferDescriptiveAudioChange = viewModel::setPreferDescriptiveAudio,
        onUseExternalVideoPlayerChange = viewModel::setUseExternalVideoPlayer,
        onUseExternalAudioPlayerChange = viewModel::setUseExternalAudioPlayer,
        onShowPlayWithKodiChange = viewModel::setShowPlayWithKodi,
        onSeekbarPreviewThumbnailChange = viewModel::setSeekbarPreviewThumbnail,
        onPreferredOpenActionChange = viewModel::setPreferredOpenAction,
        onMinimizeOnExitChange = viewModel::setMinimizeOnExit,
        onStartMainPlayerFullscreenChange = viewModel::setStartMainPlayerFullscreen,
        onAutoplayChange = viewModel::setAutoplay,
        onAutoQueueChange = viewModel::setAutoQueue,
        onResumeOnAudioFocusGainChange = viewModel::setResumeOnAudioFocusGain,
        onLeftGestureControlChange = viewModel::setLeftGestureControl,
        onRightGestureControlChange = viewModel::setRightGestureControl,
        onPopupRememberSizePosChange = viewModel::setPopupRememberSizePos,
        onUseInexactSeekChange = viewModel::setUseInexactSeek,
        onSeekDurationChange = viewModel::setSeekDuration,
        onClearQueueConfirmationChange = viewModel::setClearQueueConfirmation,
        onIgnoreHardwareMediaButtonsChange = viewModel::setIgnoreHardwareMediaButtons,
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
    useExternalVideoPlayer: Boolean = VideoAudioPreferences.DEFAULT_USE_EXTERNAL_VIDEO_PLAYER,
    useExternalAudioPlayer: Boolean = VideoAudioPreferences.DEFAULT_USE_EXTERNAL_AUDIO_PLAYER,
    showPlayWithKodi: Boolean = VideoAudioPreferences.DEFAULT_SHOW_PLAY_WITH_KODI,
    seekbarPreviewThumbnail: String = VideoAudioPreferences.DEFAULT_SEEKBAR_PREVIEW_THUMBNAIL,
    preferredOpenAction: String = VideoAudioPreferences.DEFAULT_PREFERRED_OPEN_ACTION,
    minimizeOnExit: String = VideoAudioPreferences.DEFAULT_MINIMIZE_ON_EXIT,
    startMainPlayerFullscreen: Boolean = VideoAudioPreferences.DEFAULT_START_MAIN_PLAYER_FULLSCREEN,
    autoplay: String = VideoAudioPreferences.DEFAULT_AUTOPLAY,
    autoQueue: Boolean = VideoAudioPreferences.DEFAULT_AUTO_QUEUE,
    resumeOnAudioFocusGain: Boolean = VideoAudioPreferences.DEFAULT_RESUME_ON_AUDIO_FOCUS_GAIN,
    leftGestureControl: String = VideoAudioPreferences.DEFAULT_LEFT_GESTURE_CONTROL,
    rightGestureControl: String = VideoAudioPreferences.DEFAULT_RIGHT_GESTURE_CONTROL,
    popupRememberSizePos: Boolean = VideoAudioPreferences.DEFAULT_POPUP_REMEMBER_SIZE_POS,
    useInexactSeek: Boolean = VideoAudioPreferences.DEFAULT_USE_INEXACT_SEEK,
    seekDuration: String = VideoAudioPreferences.DEFAULT_SEEK_DURATION_MS,
    seekDurationValues: List<String> = VideoAudioPreferences.SEEK_DURATIONS_MS,
    clearQueueConfirmation: Boolean = VideoAudioPreferences.DEFAULT_CLEAR_QUEUE_CONFIRMATION,
    ignoreHardwareMediaButtons: Boolean = VideoAudioPreferences.DEFAULT_IGNORE_HARDWARE_MEDIA_BUTTONS,
    onDefaultResolutionChange: (String) -> Unit = {},
    onDefaultPopupResolutionChange: (String) -> Unit = {},
    onMobileDataResolutionChange: (String) -> Unit = {},
    onShowHigherResolutionsChange: (Boolean) -> Unit = {},
    onVideoFormatChange: (String) -> Unit = {},
    onAudioFormatChange: (String) -> Unit = {},
    onPreferOriginalAudioChange: (Boolean) -> Unit = {},
    onPreferDescriptiveAudioChange: (Boolean) -> Unit = {},
    onUseExternalVideoPlayerChange: (Boolean) -> Unit = {},
    onUseExternalAudioPlayerChange: (Boolean) -> Unit = {},
    onShowPlayWithKodiChange: (Boolean) -> Unit = {},
    onSeekbarPreviewThumbnailChange: (String) -> Unit = {},
    onPreferredOpenActionChange: (String) -> Unit = {},
    onMinimizeOnExitChange: (String) -> Unit = {},
    onStartMainPlayerFullscreenChange: (Boolean) -> Unit = {},
    onAutoplayChange: (String) -> Unit = {},
    onAutoQueueChange: (Boolean) -> Unit = {},
    onResumeOnAudioFocusGainChange: (Boolean) -> Unit = {},
    onLeftGestureControlChange: (String) -> Unit = {},
    onRightGestureControlChange: (String) -> Unit = {},
    onPopupRememberSizePosChange: (Boolean) -> Unit = {},
    onUseInexactSeekChange: (Boolean) -> Unit = {},
    onSeekDurationChange: (String) -> Unit = {},
    onClearQueueConfirmationChange: (Boolean) -> Unit = {},
    onIgnoreHardwareMediaButtonsChange: (Boolean) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    fun selectedTitle(entries: List<ListPreferenceEntry>, value: String) = entries.firstOrNull { it.value == value }?.title.orEmpty()

    val resolutionEntries = resolutionValues.map(::resolutionEntry)
    val mobileDataEntries = mobileDataResolutionValues.map(::mobileDataEntry)
    val videoFormatEntries = VideoAudioPreferences.VIDEO_FORMATS.map { (value, label) ->
        ListPreferenceEntry(value, label)
    }
    val audioFormatEntries = VideoAudioPreferences.AUDIO_FORMATS.map { (value, label) ->
        ListPreferenceEntry(value, label)
    }
    val seekbarThumbnailEntries = listOf(
        ListPreferenceEntry(VideoAudioPreferences.SEEKBAR_THUMBNAIL_HIGH_QUALITY, stringResource(Res.string.high_quality_larger)),
        ListPreferenceEntry(VideoAudioPreferences.SEEKBAR_THUMBNAIL_LOW_QUALITY, stringResource(Res.string.low_quality_smaller)),
        ListPreferenceEntry(VideoAudioPreferences.SEEKBAR_THUMBNAIL_NONE, stringResource(Res.string.dont_show))
    )
    val openActionEntries = listOf(
        ListPreferenceEntry(VideoAudioPreferences.OPEN_ACTION_SHOW_INFO, stringResource(Res.string.show_info)),
        ListPreferenceEntry(VideoAudioPreferences.OPEN_ACTION_VIDEO_PLAYER, stringResource(Res.string.video_player)),
        ListPreferenceEntry(VideoAudioPreferences.OPEN_ACTION_BACKGROUND_PLAYER, stringResource(Res.string.background_player)),
        ListPreferenceEntry(VideoAudioPreferences.OPEN_ACTION_POPUP_PLAYER, stringResource(Res.string.popup_player)),
        ListPreferenceEntry(VideoAudioPreferences.OPEN_ACTION_DOWNLOAD, stringResource(Res.string.download)),
        ListPreferenceEntry(VideoAudioPreferences.OPEN_ACTION_ADD_TO_PLAYLIST, stringResource(Res.string.add_to_playlist)),
        ListPreferenceEntry(VideoAudioPreferences.OPEN_ACTION_ENQUEUE, stringResource(Res.string.enqueue_stream)),
        ListPreferenceEntry(VideoAudioPreferences.OPEN_ACTION_ALWAYS_ASK, stringResource(Res.string.always_ask_open_action))
    )
    val minimizeOnExitEntries = listOf(
        ListPreferenceEntry(VideoAudioPreferences.MINIMIZE_ON_EXIT_NONE, stringResource(Res.string.minimize_on_exit_none_description)),
        ListPreferenceEntry(VideoAudioPreferences.MINIMIZE_ON_EXIT_BACKGROUND, stringResource(Res.string.minimize_on_exit_background_description)),
        ListPreferenceEntry(VideoAudioPreferences.MINIMIZE_ON_EXIT_POPUP, stringResource(Res.string.minimize_on_exit_popup_description))
    )
    val autoplayEntries = listOf(
        ListPreferenceEntry(VideoAudioPreferences.AUTOPLAY_ALWAYS, stringResource(Res.string.always)),
        ListPreferenceEntry(VideoAudioPreferences.AUTOPLAY_WIFI, stringResource(Res.string.wifi_only)),
        ListPreferenceEntry(VideoAudioPreferences.AUTOPLAY_NEVER, stringResource(Res.string.never))
    )
    val gestureEntries = listOf(
        ListPreferenceEntry(VideoAudioPreferences.GESTURE_BRIGHTNESS, stringResource(Res.string.brightness)),
        ListPreferenceEntry(VideoAudioPreferences.GESTURE_VOLUME, stringResource(Res.string.volume)),
        ListPreferenceEntry(VideoAudioPreferences.GESTURE_NONE, stringResource(Res.string.none))
    )
    val seekDurationEntries = seekDurationValues.map { value ->
        val seconds = VideoAudioPreferences.seekDurationSeconds(value)
        ListPreferenceEntry(value, pluralStringResource(Res.plurals.seconds, seconds, seconds))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_video_audio_title),
                onNavigateUp = onNavigateUp
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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

            // TODO: ExoPlayer settings sub-screen link goes here once that screen is migrated.

            PreferenceCategoryTitle(title = stringResource(Res.string.settings_category_player_title))
            SwitchPreference(
                title = stringResource(Res.string.use_external_video_player_title),
                summary = stringResource(Res.string.use_external_video_player_summary),
                checked = useExternalVideoPlayer,
                onCheckedChange = onUseExternalVideoPlayerChange
            )
            SwitchPreference(
                title = stringResource(Res.string.use_external_audio_player_title),
                checked = useExternalAudioPlayer,
                onCheckedChange = onUseExternalAudioPlayerChange
            )
            SwitchPreference(
                title = stringResource(Res.string.show_play_with_kodi_title),
                summary = stringResource(Res.string.show_play_with_kodi_summary),
                checked = showPlayWithKodi,
                onCheckedChange = onShowPlayWithKodiChange
            )
            ListPreference(
                title = stringResource(Res.string.seekbar_preview_thumbnail_title),
                entries = seekbarThumbnailEntries,
                selectedValue = seekbarPreviewThumbnail,
                onValueSelected = onSeekbarPreviewThumbnailChange
            )

            PreferenceCategoryTitle(title = stringResource(Res.string.settings_category_player_behavior_title))
            ListPreference(
                title = stringResource(Res.string.preferred_open_action_settings_title),
                entries = openActionEntries,
                selectedValue = preferredOpenAction,
                onValueSelected = onPreferredOpenActionChange,
                summary = stringResource(
                    Res.string.preferred_open_action_settings_summary,
                    selectedTitle(openActionEntries, preferredOpenAction)
                )
            )
            // TODO(follow-up): when popup is selected without draw-over-apps permission, the
            // legacy screen shows a snackbar linking to the system permission settings. Needs a
            // platform actions interface (like AppearanceActions) and is deliberately deferred.
            ListPreference(
                title = stringResource(Res.string.minimize_on_exit_title),
                entries = minimizeOnExitEntries,
                selectedValue = minimizeOnExit,
                onValueSelected = onMinimizeOnExitChange,
                summary = stringResource(
                    Res.string.minimize_on_exit_summary,
                    selectedTitle(minimizeOnExitEntries, minimizeOnExit)
                )
            )
            SwitchPreference(
                title = stringResource(Res.string.start_main_player_fullscreen_title),
                summary = stringResource(Res.string.start_main_player_fullscreen_summary),
                checked = startMainPlayerFullscreen,
                onCheckedChange = onStartMainPlayerFullscreenChange
            )
            ListPreference(
                title = stringResource(Res.string.autoplay_title),
                entries = autoplayEntries,
                selectedValue = autoplay,
                onValueSelected = onAutoplayChange,
                summary = stringResource(
                    Res.string.autoplay_summary,
                    selectedTitle(autoplayEntries, autoplay)
                )
            )
            SwitchPreference(
                title = stringResource(Res.string.auto_queue_title),
                summary = stringResource(Res.string.auto_queue_summary),
                checked = autoQueue,
                onCheckedChange = onAutoQueueChange
            )
            SwitchPreference(
                title = stringResource(Res.string.resume_on_audio_focus_gain_title),
                summary = stringResource(Res.string.resume_on_audio_focus_gain_summary),
                checked = resumeOnAudioFocusGain,
                onCheckedChange = onResumeOnAudioFocusGainChange
            )
            ListPreference(
                title = stringResource(Res.string.left_gesture_control_title),
                entries = gestureEntries,
                selectedValue = leftGestureControl,
                onValueSelected = onLeftGestureControlChange,
                summary = stringResource(Res.string.left_gesture_control_summary)
            )
            ListPreference(
                title = stringResource(Res.string.right_gesture_control_title),
                entries = gestureEntries,
                selectedValue = rightGestureControl,
                onValueSelected = onRightGestureControlChange,
                summary = stringResource(Res.string.right_gesture_control_summary)
            )
            SwitchPreference(
                title = stringResource(Res.string.popup_remember_size_pos_title),
                summary = stringResource(Res.string.popup_remember_size_pos_summary),
                checked = popupRememberSizePos,
                onCheckedChange = onPopupRememberSizePosChange
            )
            SwitchPreference(
                title = stringResource(Res.string.use_inexact_seek_title),
                summary = stringResource(Res.string.use_inexact_seek_summary),
                checked = useInexactSeek,
                onCheckedChange = { checked ->
                    // The view model applies the same adjustment. this only decides whether to
                    // tell the user about it, mirroring the legacy toast.
                    VideoAudioPreferences.adjustedSeekDurationMs(seekDuration, checked)?.let { adjustedMs ->
                        val newSeconds = VideoAudioPreferences.seekDurationSeconds(adjustedMs)
                        scope.launch {
                            snackbarHostState.showSnackbar(getString(Res.string.new_seek_duration_toast, newSeconds))
                        }
                    }
                    onUseInexactSeekChange(checked)
                }
            )
            ListPreference(
                title = stringResource(Res.string.seek_duration_title),
                entries = seekDurationEntries,
                selectedValue = seekDuration,
                onValueSelected = onSeekDurationChange
            )
            SwitchPreference(
                title = stringResource(Res.string.clear_queue_confirmation_title),
                summary = stringResource(Res.string.clear_queue_confirmation_summary),
                checked = clearQueueConfirmation,
                onCheckedChange = onClearQueueConfirmationChange
            )
            SwitchPreference(
                title = stringResource(Res.string.ignore_hardware_media_buttons_title),
                summary = stringResource(Res.string.ignore_hardware_media_buttons_summary),
                checked = ignoreHardwareMediaButtons,
                onCheckedChange = onIgnoreHardwareMediaButtonsChange
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
