/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.newpipe.app.model.AboutLibraries
import net.newpipe.app.model.Library
import net.newpipe.app.platform.ResourceHandler
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AboutViewModel(
    private val json: Json,
    private val resourceHandler: ResourceHandler
) : ViewModel() {

    private val _libraries = MutableStateFlow<List<Library>>(emptyList())
    val libraries = _libraries.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            parseLibraries()
        }
    }

    private fun parseLibraries() {
        try {
            val aboutLibraries = json.decodeFromString<AboutLibraries>(
                resourceHandler.readResourceToString(PATH_BOM)
            )
            _libraries.value = aboutLibraries.libraries
        } catch (exception: Exception) {
            Logger.e(messageString = "Failed to parse BOM", throwable = exception)
        }
    }

    companion object {
        private const val PATH_BOM = "aboutlibraries.json"
    }
}
