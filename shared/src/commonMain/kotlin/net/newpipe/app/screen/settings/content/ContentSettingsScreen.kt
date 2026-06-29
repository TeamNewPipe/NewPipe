/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.screen.settings.content

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
import net.newpipe.app.composable.MultiSelectListPreference
import net.newpipe.app.composable.MultiSelectListPreferenceOption
import net.newpipe.app.composable.PreferenceCategoryHeader
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TextPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.content.CHANNEL_TAB_ABOUT
import net.newpipe.app.viewmodel.settings.content.CHANNEL_TAB_ALBUMS
import net.newpipe.app.viewmodel.settings.content.CHANNEL_TAB_CHANNELS
import net.newpipe.app.viewmodel.settings.content.CHANNEL_TAB_LIKES
import net.newpipe.app.viewmodel.settings.content.CHANNEL_TAB_LIVESTREAMS
import net.newpipe.app.viewmodel.settings.content.CHANNEL_TAB_PLAYLISTS
import net.newpipe.app.viewmodel.settings.content.CHANNEL_TAB_SHORTS
import net.newpipe.app.viewmodel.settings.content.CHANNEL_TAB_TRACKS
import net.newpipe.app.viewmodel.settings.content.CHANNEL_TAB_VIDEOS
import net.newpipe.app.viewmodel.settings.content.ContentSettingsViewModel
import net.newpipe.app.viewmodel.settings.content.FETCH_TAB_LIKES
import net.newpipe.app.viewmodel.settings.content.FETCH_TAB_LIVESTREAMS
import net.newpipe.app.viewmodel.settings.content.FETCH_TAB_SHORTS
import net.newpipe.app.viewmodel.settings.content.FETCH_TAB_TRACKS
import net.newpipe.app.viewmodel.settings.content.FETCH_TAB_VIDEOS
import net.newpipe.app.viewmodel.settings.content.IMAGE_QUALITY_HIGH
import net.newpipe.app.viewmodel.settings.content.IMAGE_QUALITY_LOW
import net.newpipe.app.viewmodel.settings.content.IMAGE_QUALITY_MEDIUM
import net.newpipe.app.viewmodel.settings.content.LOCALIZATION_SYSTEM
import net.newpipe.app.viewmodel.settings.content.SUGGESTIONS_LOCAL
import net.newpipe.app.viewmodel.settings.content.SUGGESTIONS_REMOTE
import net.newpipe.app.viewmodel.settings.content.THRESHOLD_12_HOURS
import net.newpipe.app.viewmodel.settings.content.THRESHOLD_15_MIN
import net.newpipe.app.viewmodel.settings.content.THRESHOLD_1_DAY
import net.newpipe.app.viewmodel.settings.content.THRESHOLD_1_HOUR
import net.newpipe.app.viewmodel.settings.content.THRESHOLD_5_MIN
import net.newpipe.app.viewmodel.settings.content.THRESHOLD_6_HOURS
import net.newpipe.app.viewmodel.settings.content.THRESHOLD_IMMEDIATE
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.app_language_title
import newpipe.shared.generated.resources.channel_tab_about
import newpipe.shared.generated.resources.channel_tab_albums
import newpipe.shared.generated.resources.channel_tab_channels
import newpipe.shared.generated.resources.channel_tab_likes
import newpipe.shared.generated.resources.channel_tab_livestreams
import newpipe.shared.generated.resources.channel_tab_playlists
import newpipe.shared.generated.resources.channel_tab_shorts
import newpipe.shared.generated.resources.channel_tab_tracks
import newpipe.shared.generated.resources.channel_tab_videos
import newpipe.shared.generated.resources.content
import newpipe.shared.generated.resources.content_language_title
import newpipe.shared.generated.resources.country_au
import newpipe.shared.generated.resources.country_br
import newpipe.shared.generated.resources.country_ca
import newpipe.shared.generated.resources.country_cn
import newpipe.shared.generated.resources.country_de
import newpipe.shared.generated.resources.country_es
import newpipe.shared.generated.resources.country_fr
import newpipe.shared.generated.resources.country_gb
import newpipe.shared.generated.resources.country_in
import newpipe.shared.generated.resources.country_it
import newpipe.shared.generated.resources.country_jp
import newpipe.shared.generated.resources.country_kr
import newpipe.shared.generated.resources.country_mx
import newpipe.shared.generated.resources.country_ru
import newpipe.shared.generated.resources.country_system
import newpipe.shared.generated.resources.country_us
import newpipe.shared.generated.resources.default_content_country_title
import newpipe.shared.generated.resources.feed_fetch_channel_tabs
import newpipe.shared.generated.resources.feed_fetch_channel_tabs_summary
import newpipe.shared.generated.resources.feed_update_threshold_summary
import newpipe.shared.generated.resources.feed_update_threshold_title
import newpipe.shared.generated.resources.feed_use_dedicated_fetch_method_summary
import newpipe.shared.generated.resources.feed_use_dedicated_fetch_method_title
import newpipe.shared.generated.resources.image_quality_high
import newpipe.shared.generated.resources.image_quality_low
import newpipe.shared.generated.resources.image_quality_medium
import newpipe.shared.generated.resources.image_quality_summary
import newpipe.shared.generated.resources.image_quality_title
import newpipe.shared.generated.resources.language_ar
import newpipe.shared.generated.resources.language_de
import newpipe.shared.generated.resources.language_en
import newpipe.shared.generated.resources.language_es
import newpipe.shared.generated.resources.language_fr
import newpipe.shared.generated.resources.language_hi
import newpipe.shared.generated.resources.language_it
import newpipe.shared.generated.resources.language_ja
import newpipe.shared.generated.resources.language_ko
import newpipe.shared.generated.resources.language_nl
import newpipe.shared.generated.resources.language_pl
import newpipe.shared.generated.resources.language_pt
import newpipe.shared.generated.resources.language_ru
import newpipe.shared.generated.resources.language_system
import newpipe.shared.generated.resources.language_tr
import newpipe.shared.generated.resources.language_zh
import newpipe.shared.generated.resources.local_search_suggestions
import newpipe.shared.generated.resources.main_page_content
import newpipe.shared.generated.resources.main_page_content_summary
import newpipe.shared.generated.resources.peertube_instance_url_summary
import newpipe.shared.generated.resources.peertube_instance_url_title
import newpipe.shared.generated.resources.remote_search_suggestions
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
import newpipe.shared.generated.resources.threshold_12_hours
import newpipe.shared.generated.resources.threshold_15_min
import newpipe.shared.generated.resources.threshold_1_day
import newpipe.shared.generated.resources.threshold_1_hour
import newpipe.shared.generated.resources.threshold_5_min
import newpipe.shared.generated.resources.threshold_6_hours
import newpipe.shared.generated.resources.threshold_immediate
import newpipe.shared.generated.resources.youtube_restricted_mode_enabled_summary
import newpipe.shared.generated.resources.youtube_restricted_mode_enabled_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ContentSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: ContentSettingsViewModel = koinViewModel()
) {
    val appLanguage by viewModel.appLanguage.collectAsState()
    val contentLanguage by viewModel.contentLanguage.collectAsState()
    val contentCountry by viewModel.contentCountry.collectAsState()
    val showChannelTabs by viewModel.showChannelTabs.collectAsState()
    val showSearchSuggestions by
    viewModel.showSearchSuggestions.collectAsState()
    val imageQuality by viewModel.imageQuality.collectAsState()
    val showAgeRestricted by viewModel.showAgeRestricted.collectAsState()
    val youtubeRestrictedMode by
    viewModel.youtubeRestrictedMode.collectAsState()
    val showComments by viewModel.showComments.collectAsState()
    val showNextVideo by viewModel.showNextVideo.collectAsState()
    val showDescription by viewModel.showDescription.collectAsState()
    val showMetaInfo by viewModel.showMetaInfo.collectAsState()
    val feedUpdateThreshold by
    viewModel.feedUpdateThreshold.collectAsState()
    val feedUseDedicatedFetch by
    viewModel.feedUseDedicatedFetch.collectAsState()
    val feedFetchChannelTabs by
    viewModel.feedFetchChannelTabs.collectAsState()

    ContentSettingsContent(
        appLanguage = appLanguage,
        contentLanguage = contentLanguage,
        contentCountry = contentCountry,
        showChannelTabs = showChannelTabs,
        showSearchSuggestions = showSearchSuggestions,
        imageQuality = imageQuality,
        showAgeRestricted = showAgeRestricted,
        youtubeRestrictedMode = youtubeRestrictedMode,
        showComments = showComments,
        showNextVideo = showNextVideo,
        showDescription = showDescription,
        showMetaInfo = showMetaInfo,
        feedUpdateThreshold = feedUpdateThreshold,
        feedUseDedicatedFetch = feedUseDedicatedFetch,
        feedFetchChannelTabs = feedFetchChannelTabs,
        onNavigateUp = navigator::navigateUp,
        onSetAppLanguage = viewModel::setAppLanguage,
        onSetContentLanguage = viewModel::setContentLanguage,
        onSetContentCountry = viewModel::setContentCountry,
        onOpenMainPageTabs = viewModel::openMainPageTabs,
        onSetShowChannelTabs = viewModel::setShowChannelTabs,
        onOpenPeertubeInstances = viewModel::openPeertubeInstances,
        onToggleShowAgeRestricted = viewModel::toggleShowAgeRestricted,
        onToggleYoutubeRestrictedMode =
            viewModel::toggleYoutubeRestrictedMode,
        onSetShowSearchSuggestions = viewModel::setShowSearchSuggestions,
        onSetImageQuality = viewModel::setImageQuality,
        onToggleShowComments = viewModel::toggleShowComments,
        onToggleShowNextVideo = viewModel::toggleShowNextVideo,
        onToggleShowDescription = viewModel::toggleShowDescription,
        onToggleShowMetaInfo = viewModel::toggleShowMetaInfo,
        onSetFeedUpdateThreshold = viewModel::setFeedUpdateThreshold,
        onToggleFeedUseDedicatedFetch =
            viewModel::toggleFeedUseDedicatedFetch,
        onSetFeedFetchChannelTabs = viewModel::setFeedFetchChannelTabs
    )
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun ContentSettingsContent(
    appLanguage: String,
    contentLanguage: String,
    contentCountry: String,
    showChannelTabs: Set<String>,
    showSearchSuggestions: Set<String>,
    imageQuality: String,
    showAgeRestricted: Boolean,
    youtubeRestrictedMode: Boolean,
    showComments: Boolean,
    showNextVideo: Boolean,
    showDescription: Boolean,
    showMetaInfo: Boolean,
    feedUpdateThreshold: String,
    feedUseDedicatedFetch: Boolean,
    feedFetchChannelTabs: Set<String>,
    onNavigateUp: () -> Unit,
    onSetAppLanguage: (String) -> Unit,
    onSetContentLanguage: (String) -> Unit,
    onSetContentCountry: (String) -> Unit,
    onOpenMainPageTabs: () -> Unit,
    onSetShowChannelTabs: (Set<String>) -> Unit,
    onOpenPeertubeInstances: () -> Unit,
    onToggleShowAgeRestricted: (Boolean) -> Unit,
    onToggleYoutubeRestrictedMode: (Boolean) -> Unit,
    onSetShowSearchSuggestions: (Set<String>) -> Unit,
    onSetImageQuality: (String) -> Unit,
    onToggleShowComments: (Boolean) -> Unit,
    onToggleShowNextVideo: (Boolean) -> Unit,
    onToggleShowDescription: (Boolean) -> Unit,
    onToggleShowMetaInfo: (Boolean) -> Unit,
    onSetFeedUpdateThreshold: (String) -> Unit,
    onToggleFeedUseDedicatedFetch: (Boolean) -> Unit,
    onSetFeedFetchChannelTabs: (Set<String>) -> Unit
) {
    val languageOptions = languageOptions()
    val countryOptions = countryOptions()
    val channelTabOptions = channelTabOptions()
    val suggestionOptions = suggestionOptions()
    val imageQualityOptions = imageQualityOptions()
    val thresholdOptions = thresholdOptions()
    val fetchTabOptions = fetchTabOptions()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.content),
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
                title = stringResource(Res.string.app_language_title),
                options = languageOptions,
                selectedValue = appLanguage,
                onValueChange = onSetAppLanguage
            )
            ListPreference(
                title = stringResource(Res.string.content_language_title),
                options = languageOptions,
                selectedValue = contentLanguage,
                onValueChange = onSetContentLanguage
            )
            ListPreference(
                title = stringResource(Res.string.default_content_country_title),
                options = countryOptions,
                selectedValue = contentCountry,
                onValueChange = onSetContentCountry
            )
            TextPreference(
                title = stringResource(Res.string.main_page_content),
                summary = stringResource(Res.string.main_page_content_summary),
                onClick = onOpenMainPageTabs
            )
            MultiSelectListPreference(
                title = stringResource(Res.string.show_channel_tabs),
                summary = stringResource(Res.string.show_channel_tabs_summary),
                options = channelTabOptions,
                selectedValues = showChannelTabs,
                onValuesChange = onSetShowChannelTabs
            )
            TextPreference(
                title = stringResource(Res.string.peertube_instance_url_title),
                summary = stringResource(Res.string.peertube_instance_url_summary),
                onClick = onOpenPeertubeInstances
            )
            SwitchPreference(
                title = stringResource(Res.string.show_age_restricted_content_title),
                summary = stringResource(Res.string.show_age_restricted_content_summary),
                isChecked = showAgeRestricted,
                onCheckedChange = onToggleShowAgeRestricted
            )
            SwitchPreference(
                title = stringResource(Res.string.youtube_restricted_mode_enabled_title),
                summary = stringResource(Res.string.youtube_restricted_mode_enabled_summary),
                isChecked = youtubeRestrictedMode,
                onCheckedChange = onToggleYoutubeRestrictedMode
            )
            MultiSelectListPreference(
                title = stringResource(Res.string.show_search_suggestions_title),
                summary = stringResource(Res.string.show_search_suggestions_summary),
                options = suggestionOptions,
                selectedValues = showSearchSuggestions,
                onValuesChange = onSetShowSearchSuggestions
            )
            ListPreference(
                title = stringResource(Res.string.image_quality_title),
                summary = stringResource(Res.string.image_quality_summary),
                options = imageQualityOptions,
                selectedValue = imageQuality,
                onValueChange = onSetImageQuality
            )
            SwitchPreference(
                title = stringResource(Res.string.show_comments_title),
                summary = stringResource(Res.string.show_comments_summary),
                isChecked = showComments,
                onCheckedChange = onToggleShowComments
            )
            SwitchPreference(
                title = stringResource(Res.string.show_next_and_similar_title),
                isChecked = showNextVideo,
                onCheckedChange = onToggleShowNextVideo
            )
            SwitchPreference(
                title = stringResource(Res.string.show_description_title),
                summary = stringResource(Res.string.show_description_summary),
                isChecked = showDescription,
                onCheckedChange = onToggleShowDescription
            )
            SwitchPreference(
                title = stringResource(Res.string.show_meta_info_title),
                summary = stringResource(Res.string.show_meta_info_summary),
                isChecked = showMetaInfo,
                onCheckedChange = onToggleShowMetaInfo
            )

            PreferenceCategoryHeader(
                title = stringResource(Res.string.settings_category_feed_title)
            )

            ListPreference(
                title = stringResource(Res.string.feed_update_threshold_title),
                summary = stringResource(Res.string.feed_update_threshold_summary),
                options = thresholdOptions,
                selectedValue = feedUpdateThreshold,
                onValueChange = onSetFeedUpdateThreshold
            )
            SwitchPreference(
                title = stringResource(Res.string.feed_use_dedicated_fetch_method_title),
                summary = stringResource(Res.string.feed_use_dedicated_fetch_method_summary),
                isChecked = feedUseDedicatedFetch,
                onCheckedChange = onToggleFeedUseDedicatedFetch
            )
            MultiSelectListPreference(
                title = stringResource(Res.string.feed_fetch_channel_tabs),
                summary = stringResource(Res.string.feed_fetch_channel_tabs_summary),
                options = fetchTabOptions,
                selectedValues = feedFetchChannelTabs,
                onValuesChange = onSetFeedFetchChannelTabs
            )
        }
    }
}

// Option lists (curated; see TODO at file top)

@Composable
private fun languageOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(LOCALIZATION_SYSTEM, stringResource(Res.string.language_system)),
    ListPreferenceOption("en", stringResource(Res.string.language_en)),
    ListPreferenceOption("es", stringResource(Res.string.language_es)),
    ListPreferenceOption("fr", stringResource(Res.string.language_fr)),
    ListPreferenceOption("de", stringResource(Res.string.language_de)),
    ListPreferenceOption("pt", stringResource(Res.string.language_pt)),
    ListPreferenceOption("ru", stringResource(Res.string.language_ru)),
    ListPreferenceOption("zh", stringResource(Res.string.language_zh)),
    ListPreferenceOption("ja", stringResource(Res.string.language_ja)),
    ListPreferenceOption("ko", stringResource(Res.string.language_ko)),
    ListPreferenceOption("it", stringResource(Res.string.language_it)),
    ListPreferenceOption("nl", stringResource(Res.string.language_nl)),
    ListPreferenceOption("pl", stringResource(Res.string.language_pl)),
    ListPreferenceOption("tr", stringResource(Res.string.language_tr)),
    ListPreferenceOption("ar", stringResource(Res.string.language_ar)),
    ListPreferenceOption("hi", stringResource(Res.string.language_hi))
)

@Composable
private fun countryOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(LOCALIZATION_SYSTEM, stringResource(Res.string.country_system)),
    ListPreferenceOption("US", stringResource(Res.string.country_us)),
    ListPreferenceOption("GB", stringResource(Res.string.country_gb)),
    ListPreferenceOption("DE", stringResource(Res.string.country_de)),
    ListPreferenceOption("FR", stringResource(Res.string.country_fr)),
    ListPreferenceOption("ES", stringResource(Res.string.country_es)),
    ListPreferenceOption("IT", stringResource(Res.string.country_it)),
    ListPreferenceOption("BR", stringResource(Res.string.country_br)),
    ListPreferenceOption("JP", stringResource(Res.string.country_jp)),
    ListPreferenceOption("KR", stringResource(Res.string.country_kr)),
    ListPreferenceOption("IN", stringResource(Res.string.country_in)),
    ListPreferenceOption("RU", stringResource(Res.string.country_ru)),
    ListPreferenceOption("CN", stringResource(Res.string.country_cn)),
    ListPreferenceOption("AU", stringResource(Res.string.country_au)),
    ListPreferenceOption("CA", stringResource(Res.string.country_ca)),
    ListPreferenceOption("MX", stringResource(Res.string.country_mx))
)

@Composable
private fun channelTabOptions(): List<MultiSelectListPreferenceOption> =
    listOf(
        MultiSelectListPreferenceOption(CHANNEL_TAB_VIDEOS, stringResource(Res.string.channel_tab_videos)),
        MultiSelectListPreferenceOption(CHANNEL_TAB_TRACKS, stringResource(Res.string.channel_tab_tracks)),
        MultiSelectListPreferenceOption(CHANNEL_TAB_SHORTS, stringResource(Res.string.channel_tab_shorts)),
        MultiSelectListPreferenceOption(CHANNEL_TAB_LIVESTREAMS, stringResource(Res.string.channel_tab_livestreams)),
        MultiSelectListPreferenceOption(CHANNEL_TAB_CHANNELS, stringResource(Res.string.channel_tab_channels)),
        MultiSelectListPreferenceOption(CHANNEL_TAB_PLAYLISTS, stringResource(Res.string.channel_tab_playlists)),
        MultiSelectListPreferenceOption(CHANNEL_TAB_ALBUMS, stringResource(Res.string.channel_tab_albums)),
        MultiSelectListPreferenceOption(CHANNEL_TAB_LIKES, stringResource(Res.string.channel_tab_likes)),
        MultiSelectListPreferenceOption(CHANNEL_TAB_ABOUT, stringResource(Res.string.channel_tab_about))
    )

@Composable
private fun suggestionOptions(): List<MultiSelectListPreferenceOption> =
    listOf(
        MultiSelectListPreferenceOption(SUGGESTIONS_LOCAL, stringResource(Res.string.local_search_suggestions)),
        MultiSelectListPreferenceOption(SUGGESTIONS_REMOTE, stringResource(Res.string.remote_search_suggestions))
    )

@Composable
private fun imageQualityOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(IMAGE_QUALITY_LOW, stringResource(Res.string.image_quality_low)),
    ListPreferenceOption(IMAGE_QUALITY_MEDIUM, stringResource(Res.string.image_quality_medium)),
    ListPreferenceOption(IMAGE_QUALITY_HIGH, stringResource(Res.string.image_quality_high))
)

@Composable
private fun thresholdOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(THRESHOLD_IMMEDIATE, stringResource(Res.string.threshold_immediate)),
    ListPreferenceOption(THRESHOLD_5_MIN, stringResource(Res.string.threshold_5_min)),
    ListPreferenceOption(THRESHOLD_15_MIN, stringResource(Res.string.threshold_15_min)),
    ListPreferenceOption(THRESHOLD_1_HOUR, stringResource(Res.string.threshold_1_hour)),
    ListPreferenceOption(THRESHOLD_6_HOURS, stringResource(Res.string.threshold_6_hours)),
    ListPreferenceOption(THRESHOLD_12_HOURS, stringResource(Res.string.threshold_12_hours)),
    ListPreferenceOption(THRESHOLD_1_DAY, stringResource(Res.string.threshold_1_day))
)

@Composable
private fun fetchTabOptions(): List<MultiSelectListPreferenceOption> =
    listOf(
        MultiSelectListPreferenceOption(FETCH_TAB_VIDEOS, stringResource(Res.string.channel_tab_videos)),
        MultiSelectListPreferenceOption(FETCH_TAB_TRACKS, stringResource(Res.string.channel_tab_tracks)),
        MultiSelectListPreferenceOption(FETCH_TAB_SHORTS, stringResource(Res.string.channel_tab_shorts)),
        MultiSelectListPreferenceOption(FETCH_TAB_LIVESTREAMS, stringResource(Res.string.channel_tab_livestreams)),
        MultiSelectListPreferenceOption(FETCH_TAB_LIKES, stringResource(Res.string.channel_tab_likes))
    )

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ContentSettingsScreenPreview() {
    ContentSettingsContent(
        appLanguage = LOCALIZATION_SYSTEM,
        contentLanguage = LOCALIZATION_SYSTEM,
        contentCountry = LOCALIZATION_SYSTEM,
        showChannelTabs = setOf(CHANNEL_TAB_VIDEOS, CHANNEL_TAB_TRACKS, CHANNEL_TAB_PLAYLISTS),
        showSearchSuggestions = setOf(SUGGESTIONS_LOCAL, SUGGESTIONS_REMOTE),
        imageQuality = IMAGE_QUALITY_MEDIUM,
        showAgeRestricted = false,
        youtubeRestrictedMode = false,
        showComments = true,
        showNextVideo = true,
        showDescription = true,
        showMetaInfo = true,
        feedUpdateThreshold = THRESHOLD_5_MIN,
        feedUseDedicatedFetch = false,
        feedFetchChannelTabs = setOf(FETCH_TAB_VIDEOS, FETCH_TAB_LIVESTREAMS),
        onNavigateUp = {},
        onSetAppLanguage = {},
        onSetContentLanguage = {},
        onSetContentCountry = {},
        onOpenMainPageTabs = {},
        onSetShowChannelTabs = {},
        onOpenPeertubeInstances = {},
        onToggleShowAgeRestricted = {},
        onToggleYoutubeRestrictedMode = {},
        onSetShowSearchSuggestions = {},
        onSetImageQuality = {},
        onToggleShowComments = {},
        onToggleShowNextVideo = {},
        onToggleShowDescription = {},
        onToggleShowMetaInfo = {},
        onSetFeedUpdateThreshold = {},
        onToggleFeedUseDedicatedFetch = {},
        onSetFeedFetchChannelTabs = {}
    )
}