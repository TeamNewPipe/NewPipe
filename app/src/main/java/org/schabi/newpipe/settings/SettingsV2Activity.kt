package org.schabi.newpipe.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.Toolbar
import org.schabi.newpipe.ui.theme.AppTheme

class SettingsV2Activity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Scaffold(topBar = {
                    Toolbar(
                        title = stringResource(id = R.string.settings),
                        hasSearch = true,
                        onSearchQueryChange = null // TODO: Add suggestions logic
                    )
                }) { padding ->
                    Text(
                        text = "Settings",
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}
