/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import net.newpipe.app.model.Developer
import net.newpipe.app.model.Library

/**
 * Preview provider for composable working with [Library]
 */
class LibraryPreviewProvider : PreviewParameterProvider<Library> {

    override val values: Sequence<Library>
        get() = sequenceOf(
            Library(
                id = "net.newpipe.extractor",
                name = "NewPipe Extractor",
                developers = listOf(
                    Developer(
                        name = "Team NewPipe",
                        organisationUrl = "https://newpipe.net/"
                    )
                ),
                licenses = listOf("GPL-3.0-or-later"),
                website = "https://github.com/TeamNewPipe/NewPipeExtractor/"
            )
        )
}
