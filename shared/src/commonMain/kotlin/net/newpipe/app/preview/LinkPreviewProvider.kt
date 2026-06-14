/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import net.newpipe.app.model.Link

/**
 * Preview provider for composable working with [Link]
 */
class LinkPreviewProvider : PreviewParameterProvider<Link> {

    override val values: Sequence<Link>
        get() = sequenceOf(
            Link(
                title = "About NewPipe e.V.",
                description = "A non-profit association founded in Germany in 2022, mostly by TeamNewPipe members.",
                action = "Join the association",
                url = "https://newpipe-ev.de/join/"
            )
        )
}
