package org.schabi.newpipe.ui.components.about

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.App

class LicenseTabViewModel : ViewModel() {
    private val _state = MutableStateFlow(LicenseTabState(null, null, null))
    val state: StateFlow<LicenseTabState> = _state
    private var licenseLoadJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                loadLibraries()
            }
        }
    }

    private fun loadLibraries() {
        val context = App.instance
        val libs = Libs.Builder().withContext(context).build()
        val (teamNewPipeLibraries, thirdParty) = libs.libraries
            .toMutableList()
            .partition { it.uniqueId.startsWith("com.github.TeamNewPipe") }

        val firstParty = getFirstPartyLibraries(context, teamNewPipeLibraries)
        val allThirdParty =
            getAdditionalThirdPartyLibraries(context, teamNewPipeLibraries, libs.licenses) +
                thirdParty

        _state.update {
            it.copy(
                firstPartyLibraries = firstParty,
                thirdPartyLibraries = allThirdParty
            )
        }
    }

    fun showLicenseDialog(filename: String) {
        licenseLoadJob?.cancel()
        _state.update { it.copy(licenseDialogHtml = AnnotatedString("")) }
        licenseLoadJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val text = App.instance.assets.open(filename).bufferedReader().use { it.readText() }
                val parsedHtml = AnnotatedString.fromHtml(text)
                _state.update {
                    if (it.licenseDialogHtml != null && isActive) {
                        it.copy(licenseDialogHtml = parsedHtml)
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun closeLicenseDialog() {
        licenseLoadJob?.cancel()
        _state.update { it.copy(licenseDialogHtml = null) }
    }

    data class LicenseTabState(
        val firstPartyLibraries: List<Library>?,
        val thirdPartyLibraries: List<Library>?,
        // null if dialog closed, empty if loading, otherwise license HTML content
        val licenseDialogHtml: AnnotatedString?
    )
}
