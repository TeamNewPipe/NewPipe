/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.extensions

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable

/**
 * Finds the toggleable node, like a checkbox or switch, placed next to the node with the given text
 *
 * Useful for rows where the label and the toggle are separate tap targets
 * @param text Text of the node next to the toggleable node
 */
fun SemanticsNodeInteractionsProvider.onToggleableNodeNextTo(text: String): SemanticsNodeInteraction = onNode(isToggleable() and hasAnySibling(hasText(text)))
