package org.schabi.newpipe.local.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.screens.HistoryScreen
import org.schabi.newpipe.ui.theme.AppTheme

class HistoryFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        AppTheme {
            Surface {
                HistoryScreen()
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        (activity as AppCompatActivity).supportActionBar?.setTitle(R.string.title_activity_history)
    }
}
