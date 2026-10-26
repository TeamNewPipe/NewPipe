/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.platform.ContentActions
import net.newpipe.app.platform.StringSetStore
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
import net.newpipe.app.preferences.ContentPreferences.KEY_APP_LANGUAGE
import net.newpipe.app.preferences.ContentPreferences.KEY_CHANNEL_TABS
import net.newpipe.app.preferences.ContentPreferences.KEY_CONTENT_COUNTRY
import net.newpipe.app.preferences.ContentPreferences.KEY_CONTENT_LANGUAGE
import net.newpipe.app.preferences.ContentPreferences.KEY_FEED_FETCH_CHANNEL_TABS
import net.newpipe.app.preferences.ContentPreferences.KEY_FEED_UPDATE_THRESHOLD
import net.newpipe.app.preferences.ContentPreferences.KEY_FEED_USE_DEDICATED_FETCH_METHOD
import net.newpipe.app.preferences.ContentPreferences.KEY_IMAGE_QUALITY
import net.newpipe.app.preferences.ContentPreferences.KEY_SEARCH_SUGGESTIONS
import net.newpipe.app.preferences.ContentPreferences.KEY_SHOW_AGE_RESTRICTED_CONTENT
import net.newpipe.app.preferences.ContentPreferences.KEY_SHOW_COMMENTS
import net.newpipe.app.preferences.ContentPreferences.KEY_SHOW_DESCRIPTION
import net.newpipe.app.preferences.ContentPreferences.KEY_SHOW_META_INFO
import net.newpipe.app.preferences.ContentPreferences.KEY_SHOW_NEXT_VIDEO
import net.newpipe.app.preferences.ContentPreferences.KEY_YOUTUBE_RESTRICTED_MODE
import net.newpipe.app.screen.settings.model.ChannelTab
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class ContentSettingsViewModel(
    private val settings: Settings,
    private val stringSetStore: StringSetStore,
    private val contentActions: ContentActions
) : ViewModel() {

    val appLanguage: StateFlow<String>
        field = stringFlow(KEY_APP_LANGUAGE, DEFAULT_LOCALIZATION)

    val contentLanguage: StateFlow<String>
        field = stringFlow(KEY_CONTENT_LANGUAGE, DEFAULT_LOCALIZATION)

    val contentCountry: StateFlow<String>
        field = stringFlow(KEY_CONTENT_COUNTRY, DEFAULT_LOCALIZATION)

    val channelTabs: StateFlow<Set<String>>
        field = stringSetFlow(KEY_CHANNEL_TABS, ChannelTab.SHOW_KEYS)

    val showAgeRestrictedContent: StateFlow<Boolean>
        field = booleanFlow(KEY_SHOW_AGE_RESTRICTED_CONTENT, DEFAULT_SHOW_AGE_RESTRICTED_CONTENT)

    val youtubeRestrictedMode: StateFlow<Boolean>
        field = booleanFlow(KEY_YOUTUBE_RESTRICTED_MODE, DEFAULT_YOUTUBE_RESTRICTED_MODE)

    val searchSuggestions: StateFlow<Set<String>>
        field = stringSetFlow(KEY_SEARCH_SUGGESTIONS, DEFAULT_SEARCH_SUGGESTIONS)

    val imageQuality: StateFlow<String>
        field = stringFlow(KEY_IMAGE_QUALITY, DEFAULT_IMAGE_QUALITY)

    val showComments: StateFlow<Boolean>
        field = booleanFlow(KEY_SHOW_COMMENTS, DEFAULT_SHOW_COMMENTS)

    val showNextVideo: StateFlow<Boolean>
        field = booleanFlow(KEY_SHOW_NEXT_VIDEO, DEFAULT_SHOW_NEXT_VIDEO)

    val showDescription: StateFlow<Boolean>
        field = booleanFlow(KEY_SHOW_DESCRIPTION, DEFAULT_SHOW_DESCRIPTION)

    val showMetaInfo: StateFlow<Boolean>
        field = booleanFlow(KEY_SHOW_META_INFO, DEFAULT_SHOW_META_INFO)

    val feedUpdateThreshold: StateFlow<String>
        field = stringFlow(KEY_FEED_UPDATE_THRESHOLD, DEFAULT_FEED_UPDATE_THRESHOLD)

    val feedUseDedicatedFetchMethod: StateFlow<Boolean>
        field = booleanFlow(KEY_FEED_USE_DEDICATED_FETCH_METHOD, DEFAULT_FEED_USE_DEDICATED_FETCH_METHOD)

    val feedFetchChannelTabs: StateFlow<Set<String>>
        field = stringSetFlow(KEY_FEED_FETCH_CHANNEL_TABS, ChannelTab.FETCH_KEYS)

    val imageCacheWiped: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setAppLanguage(value: String) {
        appLanguage.persist(KEY_APP_LANGUAGE, value)
        contentActions.applyAppLanguage(value)
    }

    fun setContentLanguage(value: String) = contentLanguage.persist(KEY_CONTENT_LANGUAGE, value)

    fun setContentCountry(value: String) = contentCountry.persist(KEY_CONTENT_COUNTRY, value)

    fun setChannelTabs(value: Set<String>) = channelTabs.persist(KEY_CHANNEL_TABS, value)

    fun setShowAgeRestrictedContent(value: Boolean) = showAgeRestrictedContent
        .persist(KEY_SHOW_AGE_RESTRICTED_CONTENT, value)

    fun setYoutubeRestrictedMode(value: Boolean) {
        youtubeRestrictedMode.persist(KEY_YOUTUBE_RESTRICTED_MODE, value)
        contentActions.applyYoutubeRestrictedMode(value)
    }

    fun setSearchSuggestions(value: Set<String>) = searchSuggestions
        .persist(KEY_SEARCH_SUGGESTIONS, value)

    fun setImageQuality(value: String) {
        imageQuality.persist(KEY_IMAGE_QUALITY, value)
        contentActions.applyImageQuality(value)
        imageCacheWiped.value = true
    }

    fun setShowComments(value: Boolean) = showComments.persist(KEY_SHOW_COMMENTS, value)

    fun setShowNextVideo(value: Boolean) = showNextVideo.persist(KEY_SHOW_NEXT_VIDEO, value)

    fun setShowDescription(value: Boolean) = showDescription.persist(KEY_SHOW_DESCRIPTION, value)

    fun setShowMetaInfo(value: Boolean) = showMetaInfo.persist(KEY_SHOW_META_INFO, value)

    fun setFeedUpdateThreshold(value: String) = feedUpdateThreshold
        .persist(KEY_FEED_UPDATE_THRESHOLD, value)

    fun setFeedUseDedicatedFetchMethod(value: Boolean) = feedUseDedicatedFetchMethod
        .persist(KEY_FEED_USE_DEDICATED_FETCH_METHOD, value)

    fun setFeedFetchChannelTabs(value: Set<String>) = feedFetchChannelTabs
        .persist(KEY_FEED_FETCH_CHANNEL_TABS, value)

    fun onImageCacheWipedShown() {
        imageCacheWiped.value = false
    }

    override fun onCleared() = contentActions.applyContentLocalization()

    private fun stringFlow(key: String, defaultValue: String) = MutableStateFlow(
        settings.getString(key, defaultValue)
    )

    private fun booleanFlow(key: String, defaultValue: Boolean) = MutableStateFlow(
        settings.getBoolean(key, defaultValue)
    )

    private fun stringSetFlow(key: String, defaultValue: Set<String>) = MutableStateFlow(
        stringSetStore.get(key, defaultValue)
    )

    private fun MutableStateFlow<String>.persist(key: String, newValue: String) {
        settings.putString(key, newValue)
        value = newValue
    }

    private fun MutableStateFlow<Boolean>.persist(key: String, newValue: Boolean) {
        settings.putBoolean(key, newValue)
        value = newValue
    }

    private fun MutableStateFlow<Set<String>>.persist(key: String, newValue: Set<String>) {
        stringSetStore.put(key, newValue)
        value = newValue
    }
}
