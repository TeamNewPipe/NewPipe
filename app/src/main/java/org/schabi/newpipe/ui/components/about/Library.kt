@file:OptIn(ExperimentalLayoutApi::class)

package org.schabi.newpipe.ui.components.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Developer
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.entity.Organization
import com.mikepenz.aboutlibraries.entity.Scm
import com.mikepenz.aboutlibraries.ui.compose.m3.util.author
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.external_communication.ShareUtils

@Composable
fun Library(
    @PreviewParameter(LibraryProvider::class) library: Library,
    showLicenseDialog: (licenseFilename: String) -> Unit,
    descriptionMaxLines: Int
) {
    val spdxLicense = library.licenses.firstOrNull()?.spdxId?.takeIf { it.isNotBlank() }
    val licenseAssetPath = spdxLicense?.let { SPDX_ID_TO_ASSET_PATH[it] }
    val context = LocalContext.current

    Column(
        modifier = (
            if (licenseAssetPath != null) {
                Modifier.clickable {
                    showLicenseDialog(licenseAssetPath)
                }
            } else if (spdxLicense != null) {
                Modifier.clickable {
                    ShareUtils.openUrlInBrowser(context, "https://spdx.org/licenses/$spdxLicense.html")
                }
            } else {
                Modifier
            }
            )
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = library.name,
                modifier = Modifier.weight(0.75f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val version = library.artifactVersion
            if (!version.isNullOrBlank()) {
                Text(
                    version,
                    modifier = if (version.length > 12) {
                        // limit the version size if it's too many characters (can happen e.g. if
                        // the version is a commit hash)
                        Modifier.weight(0.25f)
                    } else {
                        Modifier
                    }.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val author = library.author
        if (author.isNotBlank()) {
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val description = library.description
        if (!description.isNullOrBlank() && description != library.name) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = descriptionMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (library.licenses.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                library.licenses.forEach {
                    Badge {
                        Text(text = it.spdxId?.takeIf { it.isNotBlank() } ?: it.name)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LibraryPreview(@PreviewParameter(LibraryProvider::class) library: Library) {
    AppTheme {
        Library(library, {}, 2)
    }
}

private class LibraryProvider : CollectionPreviewParameterProvider<Library>(
    listOf(
        Library(
            uniqueId = "org.schabi.newpipe.extractor",
            artifactVersion = "v0.24.3",
            name = "NewPipeExtractor",
            description = "NewPipe Extractor is a library for extracting things from streaming sites. It is a core component of NewPipe, but could be used independently.",
            website = "https://newpipe.net",
            developers = listOf(Developer("TeamNewPipe", "https://newpipe.net")).toImmutableList(),
            organization = Organization("TeamNewPipe", "https://newpipe.net"),
            scm = Scm(null, null, "https://github.com/TeamNewPipe/NewPipeExtractor"),
            licenses = setOf(
                License(
                    name = "GNU General Public License v3.0",
                    url = "https://api.github.com/licenses/gpl-3.0",
                    year = null,
                    spdxId = "GPL-3.0-only",
                    licenseContent = LoremIpsum().values.first(),
                    hash = "1234"
                ),
                License(
                    name = "GNU General Public License v3.0",
                    url = "https://api.github.com/licenses/gpl-3.0",
                    year = null,
                    spdxId = "GPL-3.0-only",
                    licenseContent = LoremIpsum().values.first(),
                    hash = "4321"
                )
            ).toImmutableSet()
        ),
        Library(
            uniqueId = "org.schabi.newpipe.extractor",
            artifactVersion = "v0.24.3",
            name = "NewPipeExtractor",
            description = "NewPipe Extractor is a library for extracting things from streaming sites. It is a core component of NewPipe, but could be used independently.",
            website = null,
            developers = listOf<Developer>().toImmutableList(),
            organization = null,
            scm = null,
            licenses = setOf(
                License(
                    name = "GNU General Public License v3.0",
                    url = "https://api.github.com/licenses/gpl-3.0",
                    year = null,
                    spdxId = "GPL-3.0-only",
                    licenseContent = LoremIpsum().values.first(),
                    hash = "1234"
                )
            ).toImmutableSet()
        )
    )
)
