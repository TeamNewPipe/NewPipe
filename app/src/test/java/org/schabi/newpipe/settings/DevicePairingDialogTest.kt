/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import com.google.android.material.textfield.TextInputLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.sync.DeviceSyncSetupDiagnostic
import org.schabi.newpipe.sync.DeviceSyncSetupFailure
import org.schabi.newpipe.sync.DeviceSyncSetupOperation
import org.schabi.newpipe.sync.PairingSecurity
import org.schabi.newpipe.sync.SyncAppVersion

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w400dp-h800dp")
class DevicePairingDialogTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private var applicationFlags = 0
    private val dialogs = mutableListOf<AlertDialog>()

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java)
        activity = controller.get()
        // The plain test Application must opt into RTL before Android resolves text direction.
        applicationFlags = activity.applicationInfo.flags
        activity.applicationInfo.flags = applicationFlags or ApplicationInfo.FLAG_SUPPORTS_RTL
        activity.setTheme(R.style.LightTheme)
        controller.setup().visible()
    }

    @After
    fun tearDown() {
        try {
            dialogs.forEach(AlertDialog::dismiss)
            idle()
            if (::controller.isInitialized) {
                controller.pause().stop().destroy()
            }
        } finally {
            if (::activity.isInitialized) {
                activity.applicationInfo.flags = applicationFlags
            }
        }
    }

    @Test
    @Config(sdk = [23, 28])
    fun `copy keeps the QR screen open and marks the clipboard sensitive where supported`() {
        val code = "wizestream://pair/signed-invitation"
        val dialog = DevicePairingDialog.showCode(activity, code, "peer-id").also(dialogs::add)

        click(dialog, AlertDialog.BUTTON_NEUTRAL)

        assertTrue(dialog.isShowing)
        assertNotNull(dialog.findViewById<View>(R.id.device_pairing_qr))
        val clip = activity.getSystemService<ClipboardManager>()!!.primaryClip!!
        assertEquals(code, clip.getItemAt(0).text.toString())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            assertTrue(clip.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
        }
    }

    @Test
    fun `entry reads the clipboard only after Paste and submits the complete normalized code`() {
        val code = "wizestream://pair/signed-invitation"
        activity.getSystemService<ClipboardManager>()!!.setPrimaryClip(ClipData.newPlainText("code", "\n $code \n"))
        val submitted = mutableListOf<String>()
        val dialog = showEntry(submitted::add)
        val input = input(dialog)
        assertTrue(input.text.isEmpty())

        click(dialog, AlertDialog.BUTTON_NEUTRAL)

        assertTrue(dialog.isShowing)
        assertEquals(code, input.text.toString())
        assertTrue(submitted.isEmpty())
        click(dialog, AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf(code), submitted)
        assertFalse(dialog.isShowing)
        assertTrue(input.text.isEmpty())
    }

    @Test
    fun `invitation can still be copied when it exceeds QR capacity`() {
        val code = "wizestream://pair/" + "x".repeat(5000)
        val dialog = DevicePairingDialog.showCode(activity, code, "peer-id").also(dialogs::add)

        assertEquals(View.GONE, dialog.findViewById<View>(R.id.device_pairing_qr)!!.visibility)
        assertEquals(View.VISIBLE, dialog.findViewById<View>(R.id.device_pairing_qr_error)!!.visibility)
        click(dialog, AlertDialog.BUTTON_NEUTRAL)
        assertTrue(dialog.isShowing)
        assertEquals(code, activity.getSystemService<ClipboardManager>()!!.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun `blank and oversized input keep the dialog open and allow correction`() {
        val submitted = mutableListOf<String>()
        val dialog = showEntry(submitted::add)
        val input = input(dialog)

        input.setText(" \n ")
        click(dialog, AlertDialog.BUTTON_POSITIVE)
        assertEquals(activity.getString(R.string.device_sync_pairing_code_empty), field(dialog).error.toString())
        input.setText("x".repeat(PairingSecurity.MAX_PAIRING_URI_LENGTH + 1))
        click(dialog, AlertDialog.BUTTON_POSITIVE)
        assertEquals(activity.getString(R.string.device_sync_pairing_code_too_large), field(dialog).error.toString())
        assertTrue(dialog.isShowing)
        assertTrue(submitted.isEmpty())

        input.setText("wizestream://pair/new-code")
        click(dialog, AlertDialog.BUTTON_POSITIVE)
        assertEquals(listOf("wizestream://pair/new-code"), submitted)
    }

    @Test
    fun `empty clipboard does not erase typed input or submit it`() {
        activity.getSystemService<ClipboardManager>()!!.setPrimaryClip(ClipData.newPlainText("empty", ""))
        val submitted = mutableListOf<String>()
        val dialog = showEntry(submitted::add)
        input(dialog).setText("typed-code")

        click(dialog, AlertDialog.BUTTON_NEUTRAL)

        assertEquals("typed-code", input(dialog).text.toString())
        assertEquals(activity.getString(R.string.device_sync_pairing_clipboard_empty), field(dialog).error.toString())
        assertTrue(submitted.isEmpty())
        assertTrue(dialog.isShowing)
    }

    @Test
    fun `cancelling clears the invitation without pairing`() {
        val submitted = mutableListOf<String>()
        val dialog = showEntry(submitted::add)
        val input = input(dialog)
        input.setText("wizestream://pair/private-invitation")

        click(dialog, AlertDialog.BUTTON_NEGATIVE)

        assertTrue(input.text.isEmpty())
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `invitation is not restored with dialog saved state`() {
        val dialog = showEntry { }
        input(dialog).setText("wizestream://pair/private-invitation")
        val savedState = dialog.onSaveInstanceState()
        dialog.dismiss()
        idle()

        val restored = showEntry { }
        restored.onRestoreInstanceState(savedState)
        idle()

        assertTrue(input(restored).text.isEmpty())
        assertFalse(input(restored).isSaveEnabled)
        assertFalse(field(restored).isSaveEnabled)
    }

    @Test
    @Config(qualifiers = "ldrtl-w320dp-h480dp")
    fun `entry has an accessible label and keeps the opaque code left to right`() {
        val dialog = showEntry { }
        // Apply the RTL fixture to the dialog window as well as the Activity's resources.
        val decor = dialog.window!!.decorView
        decor.layoutDirection = View.LAYOUT_DIRECTION_RTL
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(activity.resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(activity.resources.displayMetrics.heightPixels, View.MeasureSpec.AT_MOST)
        )
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        idle()

        assertEquals(activity.getString(R.string.device_sync_pairing_code_hint), field(dialog).hint.toString())
        assertEquals(View.LAYOUT_DIRECTION_RTL, field(dialog).layoutDirection)
        assertEquals(View.TEXT_DIRECTION_LTR, input(dialog).textDirection)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled)
    }

    @Test
    fun `setup display identifies failure and version without zero sync counters`() {
        val setup = DeviceSyncSetupDiagnostic(
            DeviceSyncSetupOperation.PAIR,
            false,
            DeviceSyncSetupFailure.EXPIRED_OR_USED,
            SyncAppVersion("test", 1)
        )

        val text = setup.displayText(activity)

        assertTrue(text.contains(activity.getString(R.string.device_sync_setup_pair)))
        assertTrue(text.contains(activity.getString(R.string.device_sync_setup_expired)))
        assertTrue(text.contains(activity.getString(R.string.device_sync_log_version_value, "test", 1L)))
        assertFalse(text.contains(activity.getString(R.string.device_sync_report_devices, 0, 0)))
        assertFalse(text.contains(activity.getString(R.string.device_sync_report_changes, 0, 0)))
    }

    private fun showEntry(onPair: (String) -> Unit): AlertDialog {
        return DevicePairingDialog.showEntry(activity, onPair).also {
            dialogs.add(it)
            idle()
        }
    }

    private fun input(dialog: AlertDialog): EditText = dialog.findViewById(R.id.device_pairing_input)!!

    private fun field(dialog: AlertDialog): TextInputLayout = dialog.findViewById(R.id.device_pairing_input_layout)!!

    private fun click(dialog: AlertDialog, button: Int) {
        dialog.getButton(button).performClick()
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }
}
