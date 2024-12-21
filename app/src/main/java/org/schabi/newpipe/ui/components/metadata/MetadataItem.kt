package org.schabi.newpipe.ui.components.metadata

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun MetadataItem(@StringRes title: Int, value: String) {
    MetadataItem(title = title, value = AnnotatedString(value))
}

@Composable
fun MetadataItem(@StringRes title: Int, value: AnnotatedString) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.width(96.dp),
            textAlign = TextAlign.End,
            text = stringResource(title).uppercase(),
            style = MaterialTheme.typography.titleSmall
        )

        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MetadataItemPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                MetadataItem(title = R.string.metadata_category, value = "Entertainment")
                MetadataItem(title = R.string.metadata_age_limit, value = "18")
            }
        }
    }
}
