/*
 * SPDX-FileCopyrightText: 2015-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.error

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jakewharton.processphoenix.ProcessPhoenix
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ActivityRecoveryBinding
import org.schabi.newpipe.settings.BackupRestoreSettingsFragment
import org.schabi.newpipe.util.EdgeToEdgeHelper

/** Minimal recovery UI that remains usable when a normal preference causes startup to fail. */
class RecoveryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecoveryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.LightSettingsTheme)
        super.onCreate(savedInstanceState)

        if (!CrashRecoveryManager.isSafeModeActive(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        EdgeToEdgeHelper.enable(this)
        binding = ActivityRecoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeHelper.applySystemBarPadding(binding.root)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.crash_recovery_title)
            setDisplayHomeAsUpEnabled(false)
        }

        binding.crashRecoveryOpenApp.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.crashRecoveryBackupRestore.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.crash_recovery_content, BackupRestoreSettingsFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.crashRecoveryRestoreSettings.setOnClickListener {
            confirmRestoreOriginalPreferences()
        }
        binding.crashRecoveryKeepDefaults.setOnClickListener {
            confirmKeepCurrentPreferences()
        }
    }

    private fun confirmRestoreOriginalPreferences() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_recovery_restore_title)
            .setMessage(R.string.crash_recovery_restore_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.crash_recovery_restore_action) { _, _ ->
                restartNormally()
            }
            .show()
    }

    private fun confirmKeepCurrentPreferences() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_recovery_keep_title)
            .setMessage(R.string.crash_recovery_keep_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.crash_recovery_keep_action) { _, _ ->
                if (CrashRecoveryManager.keepCurrentPreferences(this)) {
                    restartNormally()
                }
            }
            .show()
    }

    private fun restartNormally() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ProcessPhoenix.triggerRebirth(this, intent)
    }
}
