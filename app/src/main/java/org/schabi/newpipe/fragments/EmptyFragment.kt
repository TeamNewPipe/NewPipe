package org.schabi.newpipe.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.schabi.newpipe.ui.emptystate.EmptyStateComposable
import org.schabi.newpipe.ui.emptystate.EmptyStateSpec
import org.schabi.newpipe.ui.theme.AppTheme

class EmptyFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = content {
        AppTheme {
            Surface {
                if (arguments?.getBoolean(SHOW_MESSAGE) == true) {
                    EmptyStateComposable(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopCenter),
                        spec = EmptyStateSpec.GenericError
                    )
                }
            }
        }
    }

    companion object {
        private const val SHOW_MESSAGE = "SHOW_MESSAGE"

        @JvmStatic
        fun newInstance(showMessage: Boolean) = EmptyFragment().apply {
            arguments = Bundle().apply {
                putBoolean(SHOW_MESSAGE, showMessage)
            }
        }
    }
}
