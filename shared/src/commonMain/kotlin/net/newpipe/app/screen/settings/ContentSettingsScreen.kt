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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preferences.ContentPreferences
import net.newpipe.app.preferences.ContentPreferences.IMAGE_QUALITIES
import net.newpipe.app.preferences.ContentPreferences.IMAGE_QUALITY_HIGH
import net.newpipe.app.preferences.ContentPreferences.IMAGE_QUALITY_LOW
import net.newpipe.app.preferences.ContentPreferences.IMAGE_QUALITY_MEDIUM
import net.newpipe.app.preferences.ContentPreferences.IMAGE_QUALITY_NONE
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.ContentSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.app_language_title
import newpipe.shared.generated.resources.content
import newpipe.shared.generated.resources.image_quality_high
import newpipe.shared.generated.resources.image_quality_low
import newpipe.shared.generated.resources.image_quality_medium
import newpipe.shared.generated.resources.image_quality_none
import newpipe.shared.generated.resources.image_quality_summary
import newpipe.shared.generated.resources.image_quality_title
import newpipe.shared.generated.resources.systems_language
import newpipe.shared.generated.resources.thumbnail_cache_wipe_complete_notice
import newpipe.shared.generated.resources.youtube_restricted_mode_enabled_summary
import newpipe.shared.generated.resources.youtube_restricted_mode_enabled_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ContentSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: ContentSettingsViewModel = koinViewModel(),
) {
    val imageQuality by viewModel.imageQuality.collectAsStateWithLifecycle()
    val youtubeRestrictedModeEnabled by
        viewModel.youtubeRestrictedModeEnabled.collectAsStateWithLifecycle()
    val appLanguageLabel by viewModel.appLanguageLabel.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val cacheWipedMessage = stringResource(Res.string.thumbnail_cache_wipe_complete_notice)
    LaunchedEffect(viewModel) {
        viewModel.imageCacheWiped.collect {
            snackbarHostState.showSnackbar(cacheWipedMessage)
        }
        viewModel.refreshAppLanguageLabel()
    }

    ContentSettingsScreenContent(
        imageQuality = imageQuality,
        youtubeRestrictedModeEnabled = youtubeRestrictedModeEnabled,
        appLanguageLabel = appLanguageLabel,
        onImageQualitySelected = viewModel::setImageQuality,
        onRestrictedModeChanged = viewModel::setYoutubeRestrictedModeEnabled,
        onAppLanguageClicked = viewModel::onAppLanguageClicked,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { navigator.navigateUp() },
    )
}

@Composable
fun ContentSettingsScreenContent(
    imageQuality: String,
    youtubeRestrictedModeEnabled: Boolean,
    appLanguageLabel: String?,
    onImageQualitySelected: (String) -> Unit,
    onRestrictedModeChanged: (Boolean) -> Unit,
    onAppLanguageClicked: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.content),
                onNavigateUp = onNavigateUp,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            PreferenceRow(
                title = stringResource(Res.string.app_language_title),
                summary = appLanguageLabel ?: stringResource(Res.string.systems_language),
                onClick = onAppLanguageClicked,
            )

            ListPreference(
                title = stringResource(Res.string.image_quality_title),
                summary = stringResource(Res.string.image_quality_summary),
                entries = IMAGE_QUALITIES.map { value ->
                    ListPreferenceEntry(
                        value = value,
                        title = stringResource(imageQualityLabel(value)),
                    )
                },
                selectedValue = imageQuality,
                onValueSelected = onImageQualitySelected,
            )

            SwitchPreference(
                title = stringResource(Res.string.youtube_restricted_mode_enabled_title),
                summary = stringResource(Res.string.youtube_restricted_mode_enabled_summary),
                checked = youtubeRestrictedModeEnabled,
                onCheckedChange = onRestrictedModeChanged,
            )
        }
    }
}

private fun imageQualityLabel(value: String) = when (value) {
    IMAGE_QUALITY_NONE -> Res.string.image_quality_none
    IMAGE_QUALITY_LOW -> Res.string.image_quality_low
    IMAGE_QUALITY_HIGH -> Res.string.image_quality_high
    else -> Res.string.image_quality_medium
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ContentSettingsScreenPreview() {
    ContentSettingsScreenContent(
        imageQuality = ContentPreferences.DEFAULT_IMAGE_QUALITY,
        youtubeRestrictedModeEnabled = false,
        appLanguageLabel = null,
        onImageQualitySelected = {},
        onRestrictedModeChanged = {},
        onAppLanguageClicked = {},
    )
}