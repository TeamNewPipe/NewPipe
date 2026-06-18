/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.model.License
import net.newpipe.app.preview.ThemePreviewProvider
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.done
import newpipe.shared.generated.resources.website_title
import org.jetbrains.compose.resources.stringResource

/**
 * Test tag for lazy column hosting license text
 */
const val TEST_TAG_LICENSE_TEXT = "TEST_TAG_LICENSE_TEXT"

/**
 * Dialog to show license and other details for a library
 * @param modifier Modifier for the dialog
 * @param license License information to display
 * @param onOpenWebsite Callback when action button to view library's website is clicked
 * @param onDismiss Callback when the dialog is dismissed
 */
@Composable
fun LicenseDialog(
    modifier: Modifier = Modifier,
    license: License,
    onOpenWebsite: (website: String) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var licenseContent by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(key1 = Unit) {
        licenseContent = Res.readBytes("files/LICENSES/${license.spdxID}.txt")
            .decodeToString()
            .lines()
    }

    AlertDialog(
        modifier = modifier,
        title = { Text(text = license.name) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TEST_TAG_LICENSE_TEXT)
            ) {
                items(items = licenseContent) { line ->
                    Text(text = line)
                }
            }
        },
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(
                onClick = { onOpenWebsite(license.website!!) },
                enabled = !license.website.isNullOrBlank()
            ) {
                Text(text = stringResource(Res.string.website_title))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.done))
            }
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun LicenseDialogPreview() {
    LicenseDialog(
        license = License(
            name = "NewPipe",
            website = "https://newpipe.net/",
            spdxID = "GPL-3.0-or-later"
        )
    )
}
