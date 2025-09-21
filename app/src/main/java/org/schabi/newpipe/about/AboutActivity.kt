package org.schabi.newpipe.about

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.res.stringResource
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.common.ScaffoldWithToolbar
import org.schabi.newpipe.ui.screens.AboutScreen
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.ThemeHelper

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.getTheme(this))
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                ScaffoldWithToolbar(
                    title = stringResource(R.string.title_activity_about),
                    onBackClick = { onBackPressedDispatcher.onBackPressed() }
                ) { padding ->
                    AboutScreen(padding)
                }
            }
        }
    }
}
