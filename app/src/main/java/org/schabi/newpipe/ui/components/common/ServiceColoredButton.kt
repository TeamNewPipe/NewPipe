package org.schabi.newpipe.ui.components.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingMedium
import org.schabi.newpipe.ui.theme.SizeTokens.SpacingSmall

@Composable
fun ServiceColoredButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable() RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.wrapContentWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        contentPadding = PaddingValues(horizontal = SpacingMedium, vertical = SpacingSmall),
        shape = RectangleShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,

        ),
    ) {
        content()
    }
}

@Preview
@Composable
fun ServiceColoredButtonPreview() {
    AppTheme {
        ServiceColoredButton(
            onClick = {},
            content = {
                Text("Button")
            }
        )
    }
}
