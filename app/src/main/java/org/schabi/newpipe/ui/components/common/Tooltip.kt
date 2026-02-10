@file:OptIn(ExperimentalMaterial3Api::class)

package org.schabi.newpipe.ui.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Useful to show a descriptive popup tooltip when something (e.g. a button) is long pressed. This
 * happens by default on XML Views buttons, but needs to be done manually in Compose.
 *
 * @param text the text to show in the tooltip
 * @param modifier The [TooltipBox] implementation does not handle modifiers well, since it wraps
 * [content] in a [Box], rendering some [content] modifiers useless. Therefore we have to wrap the
 * [TooltipBox] in yet another [Box] with its own modifier, passed as a parameter here.
 * @param content the content that will show a tooltip when long pressed (e.g. a button)
 */
@Composable
fun SimpleTooltipBox(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(text) } },
            state = rememberTooltipState(),
            content = content
        )
    }
}

/**
 * An [IconButton] that shows a descriptive popup tooltip when it is long pressed.
 *
 * @param onClick handles clicks on the button
 * @param icon the icon to show inside the button
 * @param contentDescription the text to use as content description for the button,
 * and also to show in the tooltip
 * @param modifier as described in [SimpleTooltipBox]
 * @param buttonModifier a modifier for the internal [IconButton]
 * @param iconModifier a modifier for the internal [Icon]
 * @param tint the color of the icon
 */
@Composable
fun TooltipIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    SimpleTooltipBox(
        text = contentDescription,
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = buttonModifier
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = iconModifier
            )
        }
    }
}
