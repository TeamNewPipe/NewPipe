/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.model.PeertubeInstance
import net.newpipe.app.platform.ContentActions
import net.newpipe.app.preferences.PeertubePreferences
import net.newpipe.app.preferences.PeertubePreferences.DEFAULT_INSTANCE
import net.newpipe.app.preferences.PeertubePreferences.KEY_INSTANCE_LIST
import net.newpipe.app.preferences.PeertubePreferences.KEY_MAIN_PAGE_CHANGE
import net.newpipe.app.preferences.PeertubePreferences.KEY_SELECTED_INSTANCE
import net.newpipe.app.screen.settings.model.PeertubeInstanceMessage
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class PeertubeInstanceSettingsViewModel(
    private val settings: Settings,
    private val contentActions: ContentActions
) : ViewModel() {

    val selectedInstance: StateFlow<PeertubeInstance>
        field = MutableStateFlow(
            settings.getStringOrNull(KEY_SELECTED_INSTANCE)
                ?.let(PeertubePreferences::decodeInstance)
                ?: DEFAULT_INSTANCE
        )

    // Without a saved list only the selected instance is shown, like the legacy screen
    val instances: StateFlow<List<PeertubeInstance>>
        field = MutableStateFlow(
            settings.getStringOrNull(KEY_INSTANCE_LIST)
                ?.let(PeertubePreferences::decodeInstanceList)
                ?: listOf(selectedInstance.value)
        )

    val isAdding: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val message: StateFlow<PeertubeInstanceMessage?>
        field = MutableStateFlow<PeertubeInstanceMessage?>(null)

    fun select(instance: PeertubeInstance) {
        settings.putString(KEY_SELECTED_INSTANCE, PeertubePreferences.encodeInstance(instance))
        settings.putBoolean(KEY_MAIN_PAGE_CHANGE, true)
        contentActions.applyPeertubeInstance(instance)
        selectedInstance.value = instance
    }

    fun add(input: String) {
        val url = PeertubePreferences.cleanInstanceUrl(input)
        when {
            url == null -> message.value = PeertubeInstanceMessage.HTTPS_ONLY

            instances.value.any { instance -> instance.url == url } -> {
                message.value = PeertubeInstanceMessage.ALREADY_EXISTS
            }

            else -> viewModelScope.launch {
                isAdding.value = true
                val name = contentActions.fetchPeertubeInstanceName(url)
                isAdding.value = false
                when {
                    name == null -> message.value = PeertubeInstanceMessage.ADD_FAILED

                    // Checked again, as the same URL may have been added while this one loaded
                    instances.value.none { instance -> instance.url == url } -> {
                        saveInstances(instances.value + PeertubeInstance(name = name, url = url))
                    }
                }
            }
        }
    }

    fun remove(instance: PeertubeInstance) {
        if (instance.url == selectedInstance.value.url) {
            return
        }
        saveInstances((instances.value - instance).ifEmpty { listOf(selectedInstance.value) })
    }

    fun move(fromUrl: String, toUrl: String) {
        val reordered = instances.value.toMutableList()
        val from = reordered.indexOfFirst { instance -> instance.url == fromUrl }
        val to = reordered.indexOfFirst { instance -> instance.url == toUrl }
        if (from < 0 || to < 0) {
            return
        }
        reordered.add(to, reordered.removeAt(from))
        saveInstances(reordered)
    }

    fun restoreDefaults() {
        settings.remove(KEY_INSTANCE_LIST)
        select(DEFAULT_INSTANCE)
        instances.value = listOf(DEFAULT_INSTANCE)
    }

    fun onMessageShown() {
        message.value = null
    }

    private fun saveInstances(newInstances: List<PeertubeInstance>) {
        settings.putString(KEY_INSTANCE_LIST, PeertubePreferences.encodeInstanceList(newInstances))
        instances.value = newInstances
    }
}
