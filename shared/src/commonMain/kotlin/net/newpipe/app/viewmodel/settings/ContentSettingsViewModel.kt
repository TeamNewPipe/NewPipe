/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.platform.ContentActions
import net.newpipe.app.preferences.ContentPreferences
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_IMAGE_QUALITY
import net.newpipe.app.preferences.ContentPreferences.DEFAULT_YOUTUBE_RESTRICTED_MODE_ENABLED
import net.newpipe.app.preferences.ContentPreferences.KEY_IMAGE_QUALITY
import net.newpipe.app.preferences.ContentPreferences.KEY_YOUTUBE_RESTRICTED_MODE_ENABLED
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class ContentSettingsViewModel(
    private val settings: Settings,
    private val contentActions: ContentActions,
) : ViewModel() {

    private val _imageCacheWiped = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val imageCacheWiped: SharedFlow<Unit> = _imageCacheWiped

    val imageQuality: StateFlow<String>
        field = MutableStateFlow(
            settings.getString(
                KEY_IMAGE_QUALITY,
                DEFAULT_IMAGE_QUALITY,
            )
        )

    val youtubeRestrictedModeEnabled: StateFlow<Boolean>
        field = MutableStateFlow(
            settings.getBoolean(
                KEY_YOUTUBE_RESTRICTED_MODE_ENABLED,
                DEFAULT_YOUTUBE_RESTRICTED_MODE_ENABLED,
            )
        )

    fun setImageQuality(value: String) {
        settings.putString(KEY_IMAGE_QUALITY, value)
        imageQuality.value = value
        // Changing the image quality clears both in-memory and on-disk image caches
        // (mirrors the legacy screen) and notifies the user with a snackbar.
        contentActions.wipeImageCaches()
        _imageCacheWiped.tryEmit(Unit)
    }

    fun setYoutubeRestrictedModeEnabled(value: Boolean) {
        settings.putBoolean(KEY_YOUTUBE_RESTRICTED_MODE_ENABLED, value)
        youtubeRestrictedModeEnabled.value = value
        contentActions.updateYoutubeRestrictedModeCookies()
    }

    fun onAppLanguageClicked() = contentActions.openAppLanguageSettings()

    fun refreshAppLanguageLabel() {
        appLanguageLabel.value = contentActions.appLanguageLabel()
    }

    val appLanguageLabel: StateFlow<String?>
        field = MutableStateFlow(contentActions.appLanguageLabel())
}