package org.schabi.newpipe.local.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.openActivity
import org.schabi.newpipe.error.UserAction
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
        val context = requireActivity()
        (context as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.title_activity_history)

        val recordManager = HistoryRecordManager(context)
        context.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_history, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when (menuItem.itemId) {
                        R.id.action_history_clear -> {
                            AlertDialog.Builder(context)
                                .setTitle(R.string.delete_view_history_alert)
                                .setNegativeButton(R.string.cancel) { dialog, which -> dialog.dismiss() }
                                .setPositiveButton(R.string.delete) { dialog, which ->
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        launch(getExceptionHandler("Delete playback states")) {
                                            recordManager.deleteCompleteStreamStateHistory().await()
                                            Toast
                                                .makeText(context, R.string.watch_history_states_deleted, Toast.LENGTH_SHORT)
                                                .show()
                                        }

                                        launch(getExceptionHandler("Delete watch history")) {
                                            recordManager.deleteWholeStreamHistory().await()
                                            Toast.makeText(context, R.string.watch_history_deleted, Toast.LENGTH_SHORT)
                                                .show()
                                        }

                                        launch(getExceptionHandler("Clear orphaned records")) {
                                            recordManager.removeOrphanedRecords().await()
                                        }
                                    }
                                }
                                .show()
                        }
                    }
                    return true
                }
            },
            viewLifecycleOwner
        )
    }

    private fun getExceptionHandler(action: String) = CoroutineExceptionHandler { _, throwable ->
        openActivity(requireContext(), ErrorInfo(throwable, UserAction.DELETE_FROM_HISTORY, action))
    }
}
