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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.MultiSelectListPreference
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.ContentActions
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_FEED_UPDATE_THRESHOLD
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_FEED_USE_DEDICATED_FETCH_METHOD
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_IMAGE_QUALITY
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_LOCALIZATION
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_SEARCH_SUGGESTIONS
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_SHOW_AGE_RESTRICTED_CONTENT
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_SHOW_COMMENTS
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_SHOW_DESCRIPTION
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_SHOW_META_INFO
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_SHOW_NEXT_VIDEO
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_YOUTUBE_RESTRICTED_MODE
import net.newpipe.app.preferences.ContentPreferences.FEED_UPDATE_THRESHOLDS
import net.newpipe.app.preferences.ContentPreferences.IMAGE_QUALITY_HIGH
import net.newpipe.app.preferences.ContentPreferences.IMAGE_QUALITY_LOW
import net.newpipe.app.preferences.ContentPreferences.IMAGE_QUALITY_MEDIUM
import net.newpipe.app.preferences.ContentPreferences.IMAGE_QUALITY_NONE
import net.newpipe.app.preferences.ContentPreferences.SEARCH_SUGGESTIONS_LOCAL
import net.newpipe.app.preferences.ContentPreferences.SEARCH_SUGGESTIONS_REMOTE
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.screen.settings.model.ChannelTab
import net.newpipe.app.viewmodel.settings.ContentSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.app_language_code
import newpipe.shared.generated.resources.app_language_name
import newpipe.shared.generated.resources.app_language_title
import newpipe.shared.generated.resources.content
import newpipe.shared.generated.resources.content_language_title
import newpipe.shared.generated.resources.country_codes
import newpipe.shared.generated.resources.country_names
import newpipe.shared.generated.resources.days
import newpipe.shared.generated.resources.default_content_country_title
import newpipe.shared.generated.resources.feed_fetch_channel_tabs
import newpipe.shared.generated.resources.feed_fetch_channel_tabs_summary
import newpipe.shared.generated.resources.feed_update_threshold_option_always_update
import newpipe.shared.generated.resources.feed_update_threshold_summary
import newpipe.shared.generated.resources.feed_update_threshold_title
import newpipe.shared.generated.resources.feed_use_dedicated_fetch_method_summary
import newpipe.shared.generated.resources.feed_use_dedicated_fetch_method_title
import newpipe.shared.generated.resources.hours
import newpipe.shared.generated.resources.image_quality_high
import newpipe.shared.generated.resources.image_quality_low
import newpipe.shared.generated.resources.image_quality_medium
import newpipe.shared.generated.resources.image_quality_none
import newpipe.shared.generated.resources.image_quality_summary
import newpipe.shared.generated.resources.image_quality_title
import newpipe.shared.generated.resources.language_codes
import newpipe.shared.generated.resources.language_names
import newpipe.shared.generated.resources.local_search_suggestions
import newpipe.shared.generated.resources.main_page_content
import newpipe.shared.generated.resources.main_page_content_summary
import newpipe.shared.generated.resources.minutes
import newpipe.shared.generated.resources.peertube_instance_url_summary
import newpipe.shared.generated.resources.peertube_instance_url_title
import newpipe.shared.generated.resources.remote_search_suggestions
import newpipe.shared.generated.resources.seconds
import newpipe.shared.generated.resources.settings_category_feed_title
import newpipe.shared.generated.resources.show_age_restricted_content_summary
import newpipe.shared.generated.resources.show_age_restricted_content_title
import newpipe.shared.generated.resources.show_channel_tabs
import newpipe.shared.generated.resources.show_channel_tabs_summary
import newpipe.shared.generated.resources.show_comments_summary
import newpipe.shared.generated.resources.show_comments_title
import newpipe.shared.generated.resources.show_description_summary
import newpipe.shared.generated.resources.show_description_title
import newpipe.shared.generated.resources.show_meta_info_summary
import newpipe.shared.generated.resources.show_meta_info_title
import newpipe.shared.generated.resources.show_next_and_similar_title
import newpipe.shared.generated.resources.show_search_suggestions_summary
import newpipe.shared.generated.resources.show_search_suggestions_title
import newpipe.shared.generated.resources.systems_language
import newpipe.shared.generated.resources.thumbnail_cache_wipe_complete_notice
import newpipe.shared.generated.resources.youtube_restricted_mode_enabled_summary
import newpipe.shared.generated.resources.youtube_restricted_mode_enabled_title
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
private const val SECONDS_PER_DAY = 86_400

@Composable
fun ContentSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: ContentSettingsViewModel = koinViewModel(),
    contentActions: ContentActions = koinInject()
) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val contentLanguage by viewModel.contentLanguage.collectAsStateWithLifecycle()
    val contentCountry by viewModel.contentCountry.collectAsStateWithLifecycle()
    val channelTabs by viewModel.channelTabs.collectAsStateWithLifecycle()
    val showAgeRestrictedContent by viewModel.showAgeRestrictedContent
        .collectAsStateWithLifecycle()
    val youtubeRestrictedMode by viewModel.youtubeRestrictedMode.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val imageQuality by viewModel.imageQuality.collectAsStateWithLifecycle()
    val showComments by viewModel.showComments.collectAsStateWithLifecycle()
    val showNextVideo by viewModel.showNextVideo.collectAsStateWithLifecycle()
    val showDescription by viewModel.showDescription.collectAsStateWithLifecycle()
    val showMetaInfo by viewModel.showMetaInfo.collectAsStateWithLifecycle()
    val feedUpdateThreshold by viewModel.feedUpdateThreshold.collectAsStateWithLifecycle()
    val feedUseDedicatedFetchMethod by viewModel.feedUseDedicatedFetchMethod
        .collectAsStateWithLifecycle()
    val feedFetchChannelTabs by viewModel.feedFetchChannelTabs.collectAsStateWithLifecycle()
    val imageCacheWiped by viewModel.imageCacheWiped.collectAsStateWithLifecycle()

    val systemAppLanguageName = remember { contentActions.systemAppLanguageName() }

    ContentSettingsScreenContent(
        appLanguage = appLanguage,
        usesSystemAppLanguageSettings = contentActions.usesSystemAppLanguageSettings,
        systemAppLanguageName = systemAppLanguageName,
        contentLanguage = contentLanguage,
        contentCountry = contentCountry,
        channelTabs = channelTabs,
        showAgeRestrictedContent = showAgeRestrictedContent,
        youtubeRestrictedMode = youtubeRestrictedMode,
        searchSuggestions = searchSuggestions,
        imageQuality = imageQuality,
        showComments = showComments,
        showNextVideo = showNextVideo,
        showDescription = showDescription,
        showMetaInfo = showMetaInfo,
        feedUpdateThreshold = feedUpdateThreshold,
        feedUseDedicatedFetchMethod = feedUseDedicatedFetchMethod,
        feedFetchChannelTabs = feedFetchChannelTabs,
        imageCacheWiped = imageCacheWiped,
        onAppLanguageChange = viewModel::setAppLanguage,
        onOpenSystemAppLanguageSettings = contentActions::openSystemAppLanguageSettings,
        onContentLanguageChange = viewModel::setContentLanguage,
        onContentCountryChange = viewModel::setContentCountry,
        onChannelTabsChange = viewModel::setChannelTabs,
        onShowAgeRestrictedContentChange = viewModel::setShowAgeRestrictedContent,
        onYoutubeRestrictedModeChange = viewModel::setYoutubeRestrictedMode,
        onSearchSuggestionsChange = viewModel::setSearchSuggestions,
        onImageQualityChange = viewModel::setImageQuality,
        onShowCommentsChange = viewModel::setShowComments,
        onShowNextVideoChange = viewModel::setShowNextVideo,
        onShowDescriptionChange = viewModel::setShowDescription,
        onShowMetaInfoChange = viewModel::setShowMetaInfo,
        onFeedUpdateThresholdChange = viewModel::setFeedUpdateThreshold,
        onFeedUseDedicatedFetchMethodChange = viewModel::setFeedUseDedicatedFetchMethod,
        onFeedFetchChannelTabsChange = viewModel::setFeedFetchChannelTabs,
        onImageCacheWipedShown = viewModel::onImageCacheWipedShown,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun ContentSettingsScreenContent(
    appLanguage: String = DEFAULT_LOCALIZATION,
    usesSystemAppLanguageSettings: Boolean = false,
    systemAppLanguageName: String? = null,
    contentLanguage: String = DEFAULT_LOCALIZATION,
    contentCountry: String = DEFAULT_LOCALIZATION,
    channelTabs: Set<String> = ChannelTab.SHOW_KEYS,
    showAgeRestrictedContent: Boolean = DEFAULT_SHOW_AGE_RESTRICTED_CONTENT,
    youtubeRestrictedMode: Boolean = DEFAULT_YOUTUBE_RESTRICTED_MODE,
    searchSuggestions: Set<String> = DEFAULT_SEARCH_SUGGESTIONS,
    imageQuality: String = DEFAULT_IMAGE_QUALITY,
    showComments: Boolean = DEFAULT_SHOW_COMMENTS,
    showNextVideo: Boolean = DEFAULT_SHOW_NEXT_VIDEO,
    showDescription: Boolean = DEFAULT_SHOW_DESCRIPTION,
    showMetaInfo: Boolean = DEFAULT_SHOW_META_INFO,
    feedUpdateThreshold: String = DEFAULT_FEED_UPDATE_THRESHOLD,
    feedUseDedicatedFetchMethod: Boolean = DEFAULT_FEED_USE_DEDICATED_FETCH_METHOD,
    feedFetchChannelTabs: Set<String> = ChannelTab.FETCH_KEYS,
    imageCacheWiped: Boolean = false,
    onAppLanguageChange: (String) -> Unit = {},
    onOpenSystemAppLanguageSettings: () -> Unit = {},
    onContentLanguageChange: (String) -> Unit = {},
    onContentCountryChange: (String) -> Unit = {},
    onChannelTabsChange: (Set<String>) -> Unit = {},
    onShowAgeRestrictedContentChange: (Boolean) -> Unit = {},
    onYoutubeRestrictedModeChange: (Boolean) -> Unit = {},
    onSearchSuggestionsChange: (Set<String>) -> Unit = {},
    onImageQualityChange: (String) -> Unit = {},
    onShowCommentsChange: (Boolean) -> Unit = {},
    onShowNextVideoChange: (Boolean) -> Unit = {},
    onShowDescriptionChange: (Boolean) -> Unit = {},
    onShowMetaInfoChange: (Boolean) -> Unit = {},
    onFeedUpdateThresholdChange: (String) -> Unit = {},
    onFeedUseDedicatedFetchMethodChange: (Boolean) -> Unit = {},
    onFeedFetchChannelTabsChange: (Set<String>) -> Unit = {},
    onImageCacheWipedShown: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val imageCacheWipedText = stringResource(Res.string.thumbnail_cache_wipe_complete_notice)

    LaunchedEffect(imageCacheWiped) {
        if (!imageCacheWiped) {
            return@LaunchedEffect
        }
        // Showing the snackbar from the surrounding scope instead of this effect, so that marking
        // it as shown below cannot cancel it by restarting the effect
        scope.launch { snackbarHostState.showSnackbar(imageCacheWipedText) }
        onImageCacheWipedShown()
    }

    val systemDefault = stringResource(Res.string.systems_language)
    val appLanguageEntries = localeEntries(
        systemDefault,
        Res.array.app_language_code,
        Res.array.app_language_name
    )
    val contentLanguageEntries = localeEntries(
        systemDefault,
        Res.array.language_codes,
        Res.array.language_names
    )
    val contentCountryEntries = localeEntries(
        systemDefault,
        Res.array.country_codes,
        Res.array.country_names
    )
    val channelTabEntries = ChannelTab.entries.map { tab ->
        ListPreferenceEntry(tab.showKey, stringResource(tab.title))
    }
    val feedFetchChannelTabEntries = ChannelTab.entries.mapNotNull { tab ->
        tab.fetchKey?.let { key -> ListPreferenceEntry(key, stringResource(tab.title)) }
    }
    val searchSuggestionEntries = listOf(
        ListPreferenceEntry(
            SEARCH_SUGGESTIONS_LOCAL,
            stringResource(Res.string.local_search_suggestions)
        ),
        ListPreferenceEntry(
            SEARCH_SUGGESTIONS_REMOTE,
            stringResource(Res.string.remote_search_suggestions)
        )
    )
    val imageQualityEntries = listOf(
        ListPreferenceEntry(IMAGE_QUALITY_NONE, stringResource(Res.string.image_quality_none)),
        ListPreferenceEntry(IMAGE_QUALITY_LOW, stringResource(Res.string.image_quality_low)),
        ListPreferenceEntry(IMAGE_QUALITY_MEDIUM, stringResource(Res.string.image_quality_medium)),
        ListPreferenceEntry(IMAGE_QUALITY_HIGH, stringResource(Res.string.image_quality_high))
    )
    val feedUpdateThresholdEntries = FEED_UPDATE_THRESHOLDS.map { seconds ->
        ListPreferenceEntry(seconds, feedUpdateThresholdLabel(seconds.toInt()))
    }

    fun selectedTitle(entries: List<ListPreferenceEntry>, value: String) = entries.firstOrNull { it.value == value }?.title.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.content),
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
            if (usesSystemAppLanguageSettings) {
                PreferenceRow(
                    title = stringResource(Res.string.app_language_title),
                    summary = systemAppLanguageName ?: systemDefault,
                    onClick = onOpenSystemAppLanguageSettings
                )
            } else {
                ListPreference(
                    title = stringResource(Res.string.app_language_title),
                    entries = appLanguageEntries,
                    selectedValue = appLanguage,
                    onValueSelected = onAppLanguageChange
                )
            }
            ListPreference(
                title = stringResource(Res.string.content_language_title),
                entries = contentLanguageEntries,
                selectedValue = contentLanguage,
                onValueSelected = onContentLanguageChange
            )
            ListPreference(
                title = stringResource(Res.string.default_content_country_title),
                entries = contentCountryEntries,
                selectedValue = contentCountry,
                onValueSelected = onContentCountryChange
            )
            // TODO: open the main page content screen once it is migrated
            PreferenceRow(
                title = stringResource(Res.string.main_page_content),
                summary = stringResource(Res.string.main_page_content_summary)
            )
            MultiSelectListPreference(
                title = stringResource(Res.string.show_channel_tabs),
                summary = stringResource(Res.string.show_channel_tabs_summary),
                entries = channelTabEntries,
                selectedValues = channelTabs,
                onValuesSelected = onChannelTabsChange
            )
            // TODO: open the PeerTube instances screen once it is migrated
            PreferenceRow(
                title = stringResource(Res.string.peertube_instance_url_title),
                summary = stringResource(Res.string.peertube_instance_url_summary)
            )
            SwitchPreference(
                title = stringResource(Res.string.show_age_restricted_content_title),
                summary = stringResource(Res.string.show_age_restricted_content_summary),
                checked = showAgeRestrictedContent,
                onCheckedChange = onShowAgeRestrictedContentChange
            )
            SwitchPreference(
                title = stringResource(Res.string.youtube_restricted_mode_enabled_title),
                summary = stringResource(Res.string.youtube_restricted_mode_enabled_summary),
                checked = youtubeRestrictedMode,
                onCheckedChange = onYoutubeRestrictedModeChange
            )
            MultiSelectListPreference(
                title = stringResource(Res.string.show_search_suggestions_title),
                summary = stringResource(Res.string.show_search_suggestions_summary),
                entries = searchSuggestionEntries,
                selectedValues = searchSuggestions,
                onValuesSelected = onSearchSuggestionsChange
            )
            ListPreference(
                title = stringResource(Res.string.image_quality_title),
                summary = stringResource(
                    Res.string.image_quality_summary,
                    selectedTitle(imageQualityEntries, imageQuality)
                ),
                entries = imageQualityEntries,
                selectedValue = imageQuality,
                onValueSelected = onImageQualityChange
            )
            SwitchPreference(
                title = stringResource(Res.string.show_comments_title),
                summary = stringResource(Res.string.show_comments_summary),
                checked = showComments,
                onCheckedChange = onShowCommentsChange
            )
            SwitchPreference(
                title = stringResource(Res.string.show_next_and_similar_title),
                checked = showNextVideo,
                onCheckedChange = onShowNextVideoChange
            )
            SwitchPreference(
                title = stringResource(Res.string.show_description_title),
                summary = stringResource(Res.string.show_description_summary),
                checked = showDescription,
                onCheckedChange = onShowDescriptionChange
            )
            SwitchPreference(
                title = stringResource(Res.string.show_meta_info_title),
                summary = stringResource(Res.string.show_meta_info_summary),
                checked = showMetaInfo,
                onCheckedChange = onShowMetaInfoChange
            )

            PreferenceCategoryTitle(title = stringResource(Res.string.settings_category_feed_title))

            ListPreference(
                title = stringResource(Res.string.feed_update_threshold_title),
                summary = stringResource(
                    Res.string.feed_update_threshold_summary,
                    selectedTitle(feedUpdateThresholdEntries, feedUpdateThreshold)
                ),
                entries = feedUpdateThresholdEntries,
                selectedValue = feedUpdateThreshold,
                onValueSelected = onFeedUpdateThresholdChange
            )
            SwitchPreference(
                title = stringResource(Res.string.feed_use_dedicated_fetch_method_title),
                summary = stringResource(Res.string.feed_use_dedicated_fetch_method_summary),
                checked = feedUseDedicatedFetchMethod,
                onCheckedChange = onFeedUseDedicatedFetchMethodChange
            )
            MultiSelectListPreference(
                title = stringResource(Res.string.feed_fetch_channel_tabs),
                summary = stringResource(Res.string.feed_fetch_channel_tabs_summary),
                entries = feedFetchChannelTabEntries,
                selectedValues = feedFetchChannelTabs,
                onValuesSelected = onFeedFetchChannelTabsChange
            )
        }
    }
}

/**
 * Pairs the codes and names of a language or country list, led by the system default entry
 */
@Composable
private fun localeEntries(
    systemDefault: String,
    codes: StringArrayResource,
    names: StringArrayResource
): List<ListPreferenceEntry> = listOf(ListPreferenceEntry(DEFAULT_LOCALIZATION, systemDefault)) +
    stringArrayResource(codes).zip(stringArrayResource(names), ::ListPreferenceEntry)

/**
 * Names a feed update threshold by its largest whole unit, as the old DurationListPreference did.
 */
@Composable
private fun feedUpdateThresholdLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(Res.string.feed_update_threshold_option_always_update)

    seconds >= SECONDS_PER_DAY -> {
        val days = seconds / SECONDS_PER_DAY
        pluralStringResource(Res.plurals.days, days, days)
    }

    seconds >= SECONDS_PER_HOUR -> {
        val hours = seconds / SECONDS_PER_HOUR
        pluralStringResource(Res.plurals.hours, hours, hours)
    }

    seconds >= SECONDS_PER_MINUTE -> {
        val minutes = seconds / SECONDS_PER_MINUTE
        pluralStringResource(Res.plurals.minutes, minutes, minutes)
    }

    else -> pluralStringResource(Res.plurals.seconds, seconds, seconds)
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ContentSettingsScreenPreview() {
    ContentSettingsScreenContent()
}
