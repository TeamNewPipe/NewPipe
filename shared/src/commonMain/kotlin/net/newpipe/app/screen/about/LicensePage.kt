/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.random.Random
import net.newpipe.app.composable.about.LibraryListItem
import net.newpipe.app.model.Library
import net.newpipe.app.model.License
import net.newpipe.app.platform.ShareHandler
import net.newpipe.app.preview.LibraryPreviewProvider
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.spaceMedium
import net.newpipe.app.theme.spaceXSmall
import net.newpipe.app.theme.spaceXXSmall
import net.newpipe.app.viewmodel.about.AboutViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.app_license
import newpipe.shared.generated.resources.app_license_title
import newpipe.shared.generated.resources.read_full_license
import newpipe.shared.generated.resources.title_licenses
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LicensePage(
    viewModel: AboutViewModel = koinViewModel(),
    shareHandler: ShareHandler = koinInject()
) {
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()

    LicensePageContent(
        libraries = libraries,
        onOpenUrl = { url -> shareHandler.openUrlInBrowser(url) }
    )
}

@Composable
fun LicensePageContent(
    libraries: List<Library> = emptyList(),
    onOpenUrl: (url: String) -> Unit = {}
) {
    var shouldShowLicenseDialog by rememberSaveable { mutableStateOf(false) }
    var licenseToShow by rememberSerializable { mutableStateOf(License()) }

    if (shouldShowLicenseDialog && licenseToShow.spdxID.isNotBlank()) {
        LicenseDialog(
            license = licenseToShow,
            onOpenWebsite = { website -> onOpenUrl(website) },
            onDismiss = { shouldShowLicenseDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(spaceXXSmall)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spaceMedium),
                verticalArrangement = Arrangement.spacedBy(spaceXSmall)
            ) {
                Text(
                    text = stringResource(Res.string.app_license_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(Res.string.app_license),
                    style = MaterialTheme.typography.bodyMedium
                )

                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.End),
                    onClick = {
                        licenseToShow = License(
                            name = "NewPipe",
                            website = "https://newpipe.net/",
                            spdxID = "GPL-3.0-or-later"
                        )
                        shouldShowLicenseDialog = true
                    }
                ) {
                    Text(text = stringResource(Res.string.read_full_license))
                }

                Text(
                    text = stringResource(Res.string.title_licenses),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Third-party libraries and licenses
        items(items = libraries, key = { library -> library.id }) { library ->
            LibraryListItem(
                library = library,
                onClick = {
                    licenseToShow = License(
                        name = library.name,
                        website = library.website,
                        spdxID = library.licenses.first()
                    )
                    shouldShowLicenseDialog = true
                }
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun LicensePagePreview(
    @PreviewParameter(LibraryPreviewProvider::class) library: Library
) {
    val libraries = List(10) {
        library.copy(id = Random.nextInt().toString())
    }
    LicensePageContent(libraries = libraries)
}
