/*
   * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
   * SPDX-License-Identifier: GPL-3.0-or-later
   */

package net.newpipe.app.viewmodel.settings.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.platform.ContentActions
import net.newpipe.app.viewmodel.settings.BooleanPreference
import net.newpipe.app.viewmodel.settings.StringPreference
import net.newpipe.app.viewmodel.settings.StringSetPreference
import org.koin.core.annotation.KoinViewModel

// Keys mirror app/src/main/res/values/settings_keys.xml verbatim.
private const val APP_LANGUAGE_KEY = "app_language_key"
private const val CONTENT_LANGUAGE_KEY = "content_language"
private const val CONTENT_COUNTRY_KEY = "content_country"
private const val SHOW_CHANNEL_TABS_KEY = "channel_tabs"
private const val SHOW_AGE_RESTRICTED_KEY = "show_age_restricted_content"
private const val YOUTUBE_RESTRICTED_MODE_KEY =
    "youtube_restricted_mode_enabled"
private const val SHOW_SEARCH_SUGGESTIONS_KEY = "show_search_suggestions"
private const val IMAGE_QUALITY_KEY = "image_quality_key"
private const val SHOW_COMMENTS_KEY = "show_comments"
private const val SHOW_NEXT_VIDEO_KEY = "show_next_video"
private const val SHOW_DESCRIPTION_KEY = "show_description"
private const val SHOW_META_INFO_KEY = "show_meta_info"
private const val FEED_UPDATE_THRESHOLD_KEY = "feed_update_threshold_key"
private const val FEED_USE_DEDICATED_FETCH_KEY =
    "feed_use_dedicated_fetch_method"
private const val FEED_FETCH_CHANNEL_TABS_KEY = "feed_fetch_channel_tabs"

// Default localization
internal const val LOCALIZATION_SYSTEM = "system"

// Image quality values (match settings_keys.xml verbatim)
internal const val IMAGE_QUALITY_LOW = "image_quality_low"
internal const val IMAGE_QUALITY_MEDIUM = "image_quality_medium"
internal const val IMAGE_QUALITY_HIGH = "image_quality_high"

// Channel tab values
internal const val CHANNEL_TAB_VIDEOS = "show_channel_tabs_videos"
internal const val CHANNEL_TAB_TRACKS = "show_channel_tabs_tracks"
internal const val CHANNEL_TAB_SHORTS = "show_channel_tabs_shorts"
internal const val CHANNEL_TAB_LIVESTREAMS =
    "show_channel_tabs_livestreams"
internal const val CHANNEL_TAB_CHANNELS = "show_channel_tabs_channels"
internal const val CHANNEL_TAB_PLAYLISTS = "show_channel_tabs_playlists"
internal const val CHANNEL_TAB_ALBUMS = "show_channel_tabs_albums"
internal const val CHANNEL_TAB_LIKES = "show_channel_tabs_likes"
internal const val CHANNEL_TAB_ABOUT = "show_channel_tabs_about"

private val DEFAULT_CHANNEL_TABS = setOf(
    CHANNEL_TAB_VIDEOS, CHANNEL_TAB_TRACKS, CHANNEL_TAB_SHORTS,
    CHANNEL_TAB_LIVESTREAMS, CHANNEL_TAB_CHANNELS, CHANNEL_TAB_PLAYLISTS,
    CHANNEL_TAB_ALBUMS, CHANNEL_TAB_LIKES, CHANNEL_TAB_ABOUT
)

// Search-suggestion values
internal const val SUGGESTIONS_LOCAL = "show_local_search_suggestions"
internal const val SUGGESTIONS_REMOTE = "show_remote_search_suggestions"

private val DEFAULT_SUGGESTIONS = setOf(SUGGESTIONS_LOCAL,
    SUGGESTIONS_REMOTE)

// Feed update threshold values (seconds, stored as Strings)
internal const val THRESHOLD_IMMEDIATE = "0"
internal const val THRESHOLD_5_MIN = "300"
internal const val THRESHOLD_15_MIN = "900"
internal const val THRESHOLD_1_HOUR = "3600"
internal const val THRESHOLD_6_HOURS = "21600"
internal const val THRESHOLD_12_HOURS = "43200"
internal const val THRESHOLD_1_DAY = "86400"

// Feed fetch channel-tab values
internal const val FETCH_TAB_VIDEOS = "fetch_channel_tabs_videos"
internal const val FETCH_TAB_TRACKS = "fetch_channel_tabs_tracks"
internal const val FETCH_TAB_SHORTS = "fetch_channel_tabs_shorts"
internal const val FETCH_TAB_LIVESTREAMS = "fetch_channel_tabs_livestreams"
internal const val FETCH_TAB_LIKES = "fetch_channel_tabs_likes"

private val DEFAULT_FETCH_TABS = setOf(
    FETCH_TAB_VIDEOS, FETCH_TAB_TRACKS, FETCH_TAB_SHORTS, FETCH_TAB_LIVESTREAMS, FETCH_TAB_LIKES
)

@KoinViewModel
class ContentSettingsViewModel(
    settings: ObservableSettings,
    private val contentActions: ContentActions
) : ViewModel() {

    // ListPreferences
    private val appLanguagePref = StringPreference(
        APP_LANGUAGE_KEY, LOCALIZATION_SYSTEM, settings, viewModelScope
    )
    private val contentLanguagePref = StringPreference(
        CONTENT_LANGUAGE_KEY, LOCALIZATION_SYSTEM, settings, viewModelScope
    )
    private val contentCountryPref = StringPreference(
        CONTENT_COUNTRY_KEY, LOCALIZATION_SYSTEM, settings, viewModelScope
    )
    private val imageQualityPref = StringPreference(
        IMAGE_QUALITY_KEY, IMAGE_QUALITY_MEDIUM, settings, viewModelScope
    )
    private val feedUpdateThresholdPref = StringPreference(
        FEED_UPDATE_THRESHOLD_KEY, THRESHOLD_5_MIN, settings, viewModelScope
    )

    val appLanguage: StateFlow<String> = appLanguagePref.state
    val contentLanguage: StateFlow<String> = contentLanguagePref.state
    val contentCountry: StateFlow<String> = contentCountryPref.state
    val imageQuality: StateFlow<String> = imageQualityPref.state
    val feedUpdateThreshold: StateFlow<String> = feedUpdateThresholdPref.state

    fun setAppLanguage(value: String) {
        appLanguagePref.set(value)
        contentActions.onAppLanguageChanged()
    }
    fun setContentLanguage(value: String) = contentLanguagePref.set(value)
    fun setContentCountry(value: String) = contentCountryPref.set(value)
    fun setImageQuality(value: String) = imageQualityPref.set(value)
    fun setFeedUpdateThreshold(value: String) = feedUpdateThresholdPref.set(value)

    // ── Switches
    private val showAgeRestrictedPref = BooleanPreference(
        SHOW_AGE_RESTRICTED_KEY, false, settings, viewModelScope
    )
    private val youtubeRestrictedModePref = BooleanPreference(
        YOUTUBE_RESTRICTED_MODE_KEY, false, settings, viewModelScope
    )
    private val showCommentsPref = BooleanPreference(
        SHOW_COMMENTS_KEY, true, settings, viewModelScope
    )
    private val showNextVideoPref = BooleanPreference(
        SHOW_NEXT_VIDEO_KEY, true, settings, viewModelScope
    )
    private val showDescriptionPref = BooleanPreference(
        SHOW_DESCRIPTION_KEY, true, settings, viewModelScope
    )
    private val showMetaInfoPref = BooleanPreference(
        SHOW_META_INFO_KEY, true, settings, viewModelScope
    )
    private val feedUseDedicatedFetchPref = BooleanPreference(
        FEED_USE_DEDICATED_FETCH_KEY, false, settings, viewModelScope
    )

    val showAgeRestricted: StateFlow<Boolean> = showAgeRestrictedPref.state
    val youtubeRestrictedMode: StateFlow<Boolean> = youtubeRestrictedModePref.state
    val showComments: StateFlow<Boolean> = showCommentsPref.state
    val showNextVideo: StateFlow<Boolean> = showNextVideoPref.state
    val showDescription: StateFlow<Boolean> = showDescriptionPref.state
    val showMetaInfo: StateFlow<Boolean> = showMetaInfoPref.state
    val feedUseDedicatedFetch: StateFlow<Boolean> = feedUseDedicatedFetchPref.state

    fun toggleShowAgeRestricted(v: Boolean) = showAgeRestrictedPref.toggle(v)
    fun toggleYoutubeRestrictedMode(v: Boolean) = youtubeRestrictedModePref.toggle(v)
    fun toggleShowComments(v: Boolean) = showCommentsPref.toggle(v)
    fun toggleShowNextVideo(v: Boolean) = showNextVideoPref.toggle(v)
    fun toggleShowDescription(v: Boolean) = showDescriptionPref.toggle(v)
    fun toggleShowMetaInfo(v: Boolean) = showMetaInfoPref.toggle(v)
    fun toggleFeedUseDedicatedFetch(v: Boolean) = feedUseDedicatedFetchPref.toggle(v)

    // MultiSelect
    private val showChannelTabsPref = StringSetPreference(
        SHOW_CHANNEL_TABS_KEY, DEFAULT_CHANNEL_TABS, settings, viewModelScope
    )
    private val showSearchSuggestionsPref = StringSetPreference(
        SHOW_SEARCH_SUGGESTIONS_KEY, DEFAULT_SUGGESTIONS, settings, viewModelScope
    )
    private val feedFetchChannelTabsPref = StringSetPreference(
        FEED_FETCH_CHANNEL_TABS_KEY, DEFAULT_FETCH_TABS, settings, viewModelScope
    )

    val showChannelTabs: StateFlow<Set<String>> = showChannelTabsPref.state
    val showSearchSuggestions: StateFlow<Set<String>> = showSearchSuggestionsPref.state
    val feedFetchChannelTabs: StateFlow<Set<String>> = feedFetchChannelTabsPref.state

    fun setShowChannelTabs(values: Set<String>) = showChannelTabsPref.set(values)
    fun setShowSearchSuggestions(values: Set<String>) = showSearchSuggestionsPref.set(values)
    fun setFeedFetchChannelTabs(values: Set<String>) = feedFetchChannelTabsPref.set(values)

    //  Sub-screen launchers
    fun openMainPageTabs() = contentActions.openMainPageTabsChooser()
    fun openPeertubeInstances() = contentActions.openPeertubeInstanceList()
}