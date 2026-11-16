/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.newpipe.app.extensions.settingsModule
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.model.ErrorReport
import net.newpipe.app.model.SettingsFormat
import net.newpipe.app.screen.settings.model.BackupRestoreConfirmation
import net.newpipe.app.screen.settings.model.BackupRestoreMessage
import net.newpipe.app.screen.settings.model.SettingsImport
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.cancel
import newpipe.shared.generated.resources.error_snackbar_action
import newpipe.shared.generated.resources.import_settings_vulnerable_format
import newpipe.shared.generated.resources.no_appropriate_file_manager_message_android_10
import newpipe.shared.generated.resources.ok
import newpipe.shared.generated.resources.reset_all_settings
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class BackupRestoreSettingsScreenTest {

    @Test
    fun confirmingTheResetPassesItOnAndClosesTheDialog() = runComposeUiTest {
        var confirmed: BackupRestoreConfirmation? = null
        var dismissed = false
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                BackupRestoreSettingsScreenContent(
                    pendingConfirmation = BackupRestoreConfirmation.ResetSettings,
                    onConfirm = { confirmation -> confirmed = confirmation },
                    onDismissConfirmation = { dismissed = true }
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.reset_all_settings)).assertIsDisplayed()
                onNodeWithText(getString(Res.string.ok)).performClick()
                assertEquals(BackupRestoreConfirmation.ResetSettings, confirmed)
                assertTrue(dismissed)
            }
        )
    }

    @Test
    fun oldSettingsFormatWarnsAndCancellingStillFinishesTheImport() = runComposeUiTest {
        val settingsImport = SettingsImport(file = "backup.zip", format = SettingsFormat.SERIALIZED)
        var skipped: SettingsImport? = null
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                BackupRestoreSettingsScreenContent(
                    pendingSettingsImport = settingsImport,
                    onSkipSettingsImport = { skip -> skipped = skip }
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.import_settings_vulnerable_format)).assertIsDisplayed()
                onNodeWithText(getString(Res.string.cancel)).performClick()
                assertEquals(settingsImport, skipped)
            }
        )
    }

    @Test
    fun failuresOfferToReportTheError() = runComposeUiTest {
        var reported = false
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                BackupRestoreSettingsScreenContent(
                    message = BackupRestoreMessage.Failed(ErrorReport(message = "Boom", open = { reported = true }))
                )
            },
            onContent = {
                onNodeWithText("Boom").assertIsDisplayed()
                onNodeWithText(getString(Res.string.error_snackbar_action)).performClick()
                waitForIdle()
                assertTrue(reported)
            }
        )
    }

    @Test
    fun missingFileManagerExplainsWhatToInstall() = runComposeUiTest {
        withKoin(
            modules = listOf(settingsModule()),
            content = { BackupRestoreSettingsScreenContent(noFileManager = true) },
            onContent = {
                onNodeWithText(getString(Res.string.no_appropriate_file_manager_message_android_10))
                    .assertIsDisplayed()
            }
        )
    }
}
