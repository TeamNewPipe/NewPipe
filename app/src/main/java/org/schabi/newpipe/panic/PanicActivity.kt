/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.panic

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import info.guardianproject.panic.Panic
import info.guardianproject.panic.PanicResponder
import net.newpipe.app.AppKoinContext
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.screen.settings.PanicSettingsScreen
import net.newpipe.app.viewmodel.settings.PanicSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.R

/**
 * The PanicActivity responds to Intents from Panic apps and allows them to connect
 * (see [Panic.ACTION_CONNECT]) and disconnect (see [Panic.ACTION_DISCONNECT]).
 *
 * When a panic app connects to NewPipe, the user is present with actions that can be performed
 * when receiving the panic trigger in [PanicResponderActivity].
 */
class PanicActivity : AppCompatActivity() {

    companion object {
        const val TAG = "PanicActivity"
    }

    private var showPanicDialog by mutableStateOf(false)
    private var panicAppName by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkIntent()
        enableEdgeToEdge()

        setContent {
            AppKoinContext {
                val viewModel: PanicSettingsViewModel = koinViewModel()

                if (showPanicDialog) {
                    PanicConfirmationDialog(
                        panicAppName = panicAppName,
                        onConfirm = {
                            val pkg = getCallerPackageName()
                            if (pkg != null) {
                                PanicResponder.setTriggerPackageName(this@PanicActivity, pkg)
                                val prefs = getSharedPreferences("${packageName}_preferences", MODE_PRIVATE)
                                prefs.edit { putString(PanicResponder.PREF_TRIGGER_PACKAGE_NAME, pkg) }
                                viewModel.setTriggerPackageName(pkg)
                            } else {
                                // it's not clear what can be done in this state, so currently it's a no-op
                            }
                            setResult(RESULT_OK)
                            showPanicDialog = false
                        },
                        onDismiss = {
                            setResult(RESULT_CANCELED)
                            showPanicDialog = false
                            finish()
                        }
                    )
                }
                PanicSettingsScreen(
                    viewModel = viewModel,
                    onBackClicked = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }

    private fun checkIntent() {
        // check intent to see if this was triggered by the panic app settings ui
        val intent = getIntent()
        if (intent != null) {
            if (Panic.ACTION_CONNECT == intent.action) {
                // then check settings to see if we're coming from a new app
                val prefs = getSharedPreferences("${packageName}_preferences", MODE_PRIVATE)
                val packagePref: String =
                    prefs.getString(PanicResponder.PREF_TRIGGER_PACKAGE_NAME, null) ?: Panic.PACKAGE_NAME_NONE
                val callerPackageName: String? = getCallerPackageName()
                if (callerPackageName != packagePref) {
                    showPanicDialog = true

                    // get app name if possible
                    if (callerPackageName != null) {
                        try {
                            panicAppName =
                                packageManager.getApplicationLabel(
                                    packageManager.getApplicationInfo(callerPackageName, 0)
                                ) as String
                        } catch (e: PackageManager.NameNotFoundException) {
                            Log.e(TAG, "Panic app label not found: ", e)
                            panicAppName = getString(R.string.panic_app_unknown_app)
                        }
                    } else {
                        panicAppName = getString(R.string.panic_app_unknown_app)
                    }
                }
            } else if (Panic.ACTION_DISCONNECT == intent.action) {
                // action not supported by previous version
                Log.w(TAG, "Panic app disconnect action not supported ")
            }
        }
    }

    private fun getCallerPackageName(): String? {
        // attempt various methods of determining the package that started this activity
        if (callingPackage != null) {
            return callingPackage
        }
        if (callingActivity?.packageName != null) {
            return callingActivity?.packageName
        }
        if (intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) != null) {
            return intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        }
        return null
    }
}

@Composable
fun PanicConfirmationDialog(
    panicAppName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
        title = { Text(stringResource(R.string.panic_app_dialog_title)) },
        text = { Text(stringResource(R.string.panic_app_dialog_message, panicAppName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
fun PanicConfirmationDialogPreview() {
    PanicConfirmationDialog(
        panicAppName = "Ripple",
        onConfirm = {},
        onDismiss = {}
    )
}
