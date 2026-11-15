/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.DateFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogDevicePairingBinding
import org.schabi.newpipe.sync.DeviceSyncAttempt
import org.schabi.newpipe.sync.DeviceSyncBackgroundScheduler
import org.schabi.newpipe.sync.DeviceSyncListenerService
import org.schabi.newpipe.sync.DeviceSyncLogCategory
import org.schabi.newpipe.sync.DeviceSyncLogCategoryResult
import org.schabi.newpipe.sync.DeviceSyncLogDirection
import org.schabi.newpipe.sync.DeviceSyncLogEntry
import org.schabi.newpipe.sync.DeviceSyncLogStatus
import org.schabi.newpipe.sync.DeviceSyncManager
import org.schabi.newpipe.sync.DeviceSyncSummary
import org.schabi.newpipe.sync.StructuredPreferenceCategory
import org.schabi.newpipe.sync.SyncAppVersion
import org.schabi.newpipe.sync.TrustedPeer

class DeviceSyncSettingsFragment : BasePreferenceFragment() {
    private val scanPairingCode = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(::pairWithCode)
    }

    private val syncManager: DeviceSyncManager
        get() = DeviceSyncManager.get(requireContext())

    private lateinit var identityPreference: Preference
    private lateinit var statusPreference: Preference
    private lateinit var syncNowPreference: Preference
    private lateinit var syncLogPreference: Preference
    private lateinit var backgroundSyncPreference: SwitchPreferenceCompat
    private lateinit var trustedDevicesCategory: PreferenceCategory
    private lateinit var showPairingCodePreference: Preference
    private lateinit var scanPairingCodePreference: Preference
    private val trustedDevicePreferences = mutableListOf<Preference>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()

        identityPreference = requirePreference(R.string.device_sync_identity_key)
        statusPreference = requirePreference(R.string.device_sync_status_key)
        syncNowPreference = requirePreference(R.string.device_sync_sync_now_key)
        syncLogPreference = requirePreference(R.string.device_sync_log_key)
        backgroundSyncPreference = requirePreference(R.string.device_sync_background_key)
        trustedDevicesCategory =
            requirePreference(R.string.device_sync_trusted_devices_category_key)
        showPairingCodePreference = requirePreference(R.string.device_sync_show_code_key)
        scanPairingCodePreference = requirePreference(R.string.device_sync_scan_code_key)

        syncNowPreference.setOnPreferenceClickListener {
            syncData()
            true
        }
        syncLogPreference.setOnPreferenceClickListener {
            showSyncLog()
            true
        }
        backgroundSyncPreference.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            val hasTrustedPeers = syncManager.trustedPeers.isNotEmpty()
            DeviceSyncBackgroundScheduler.setEnabled(
                requireContext(),
                enabled = enabled,
                hasTrustedPeers = hasTrustedPeers
            )
            DeviceSyncListenerService.update(
                requireContext(),
                backgroundSyncEnabled = enabled,
                hasTrustedPeers = hasTrustedPeers
            )
            true
        }
        requirePreference<SwitchPreferenceCompat>(R.string.device_sync_charging_key)
            .setOnPreferenceChangeListener { _, newValue ->
                DeviceSyncBackgroundScheduler.setEnabled(
                    requireContext(),
                    enabled = backgroundSyncPreference.isChecked,
                    hasTrustedPeers = syncManager.trustedPeers.isNotEmpty(),
                    onlyWhileCharging = newValue as Boolean
                )
                true
            }
        showPairingCodePreference.setOnPreferenceClickListener {
            createPairingCode()
            true
        }
        scanPairingCodePreference.setOnPreferenceClickListener {
            launchScanner()
            true
        }
        requirePreference<Preference>(R.string.device_sync_clear_devices_key)
            .setOnPreferenceClickListener {
                confirmClearTrustedDevices()
                true
            }
    }

    override fun onResume() {
        super.onResume()
        updateState()
        DeviceSyncListenerService.startIfEnabled(requireContext())
        startListening()
    }

    override fun onPause() {
        if (!backgroundSyncPreference.isChecked) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                syncManager.stopListening()
            }
        }
        super.onPause()
    }

    private fun updateState() {
        identityPreference.summary = syncManager.peerId
        val peers = syncManager.trustedPeers
        syncNowPreference.isEnabled = peers.isNotEmpty()
        statusPreference.summary = statusSummary(peers)
        val logEntries = syncManager.syncLogEntries
        syncLogPreference.summary = if (logEntries.isEmpty()) {
            getString(R.string.device_sync_log_empty)
        } else {
            getString(
                R.string.device_sync_log_summary,
                logEntries.size,
                relativeTime(logEntries.first().timestampEpochMillis)
            )
        }
        updateTrustedDevices(peers)
    }

    private fun updateTrustedDevices(peers: List<TrustedPeer>) {
        trustedDevicePreferences.forEach(trustedDevicesCategory::removePreference)
        trustedDevicePreferences.clear()
        val displayedPeers = if (peers.isEmpty()) {
            listOf(
                Preference(requireContext()).apply {
                    title = getString(R.string.device_sync_no_trusted_devices)
                    order = 0
                    isSelectable = false
                    isIconSpaceReserved = false
                }
            )
        } else {
            peers.mapIndexed { index, peer ->
                Preference(requireContext()).apply {
                    title = peer.deviceName
                    summary = abbreviatePeerId(peer.peerId)
                    order = index
                    isSelectable = false
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                }
            }
        }
        displayedPeers.forEach { preference ->
            trustedDevicesCategory.addPreference(preference)
            trustedDevicePreferences.add(preference)
        }
    }

    private fun startListening() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    syncManager.startListening()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                statusPreference.summary = error.message ?: getString(R.string.general_error)
            }
        }
    }

    private fun syncData() {
        setActionsEnabled(false)
        syncNowPreference.summary = getString(R.string.device_sync_sync_in_progress)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    syncManager.sync()
                }
                updateState()
                showSyncSummary(summary)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(R.string.device_sync_sync_failed, error)
            }
            syncNowPreference.setSummary(R.string.device_sync_sync_now_summary)
            setActionsEnabled(true)
            updateState()
        }
    }

    private fun createPairingCode() {
        setActionsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val code = withContext(Dispatchers.IO) {
                    syncManager.createPairingCode()
                }
                showPairingCodeDialog(code)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(R.string.device_sync_pairing_failed, error)
            }
            setActionsEnabled(true)
        }
    }

    private fun launchScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(getString(R.string.device_sync_scan_prompt))
            .setBeepEnabled(false)
            .setCaptureActivity(DeviceSyncCaptureActivity::class.java)
            .setOrientationLocked(false)
        scanPairingCode.launch(options)
    }

    private fun pairWithCode(code: String) {
        setActionsEnabled(false)
        scanPairingCodePreference.summary = getString(R.string.device_sync_pairing_in_progress)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val peer = withContext(Dispatchers.IO) {
                    syncManager.pair(code)
                }
                updateState()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.device_sync_pairing_succeeded, peer.deviceName),
                    Toast.LENGTH_LONG
                ).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(R.string.device_sync_pairing_failed, error)
            }
            scanPairingCodePreference.setSummary(R.string.device_sync_scan_code_summary)
            setActionsEnabled(true)
        }
    }

    private fun showPairingCodeDialog(code: String) {
        val binding = DialogDevicePairingBinding.inflate(layoutInflater)
        val qrSize = minOf(
            resources.displayMetrics.widthPixels -
                (PAIRING_DIALOG_HORIZONTAL_MARGIN_DP * resources.displayMetrics.density).toInt(),
            (MAX_QR_SIZE_DP * resources.displayMetrics.density).toInt()
        )
        binding.devicePairingQr.setImageBitmap(
            BarcodeEncoder().encodeBitmap(
                code,
                BarcodeFormat.QR_CODE,
                qrSize,
                qrSize
            )
        )
        binding.devicePairingQr.setBackgroundColor(Color.WHITE)
        binding.devicePairingPeerId.text = getString(
            R.string.device_sync_pairing_peer_id,
            abbreviatePeerId(syncManager.peerId)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_sync_show_code_title)
            .setView(binding.root)
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun confirmClearTrustedDevices() {
        if (syncManager.trustedPeers.isEmpty()) {
            Toast.makeText(
                requireContext(),
                R.string.device_sync_no_trusted_devices,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_sync_clear_devices_title)
            .setMessage(R.string.device_sync_clear_devices_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                clearTrustedDevices()
            }
            .show()
    }

    private fun clearTrustedDevices() {
        setActionsEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    syncManager.clearTrustedPeers()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(R.string.device_sync_clear_devices_failed, error)
            }
            setActionsEnabled(true)
            updateState()
        }
    }

    private fun showSyncSummary(summary: DeviceSyncSummary) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_sync_sync_complete_title)
            .setView(scrollableTextView(buildSyncReport(summary)))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun buildSyncReport(summary: DeviceSyncSummary): CharSequence {
        return SpannableStringBuilder().apply {
            appendBold(getString(R.string.device_sync_report_overview))
            append("\n")
            append(
                getString(
                    R.string.device_sync_report_devices,
                    summary.succeeded,
                    summary.failed
                )
            )
            append("\n")
            append(
                getString(
                    R.string.device_sync_report_changes,
                    summary.sentChanges,
                    summary.receivedChanges
                )
            )
            summary.attempts.forEach { attempt ->
                append("\n\n")
                appendBold(attempt.peer.deviceName)
                syncCategories(attempt).forEach { category ->
                    append("\n")
                    append(category.status.symbol)
                    append(" ")
                    appendBold(category.title)
                    append("\n   ")
                    append(category.detail)
                }
            }
        }
    }

    private fun syncCategories(attempt: DeviceSyncAttempt): List<SyncCategoryDisplay> {
        return buildList {
            add(
                syncCategory(
                    getString(R.string.device_sync_category_profiles),
                    attempt.profileResult?.sentChanges,
                    attempt.profileResult?.receivedChanges,
                    attempt.profileError
                )
            )
            add(
                syncCategory(
                    getString(R.string.device_sync_category_subscriptions),
                    attempt.result?.sentChanges,
                    attempt.result?.receivedChanges,
                    attempt.error,
                    attempt.subscriptionSkipped
                )
            )
            add(
                syncCategory(
                    getString(R.string.device_sync_category_playlists),
                    attempt.playlistResult?.sentChanges,
                    attempt.playlistResult?.receivedChanges,
                    attempt.playlistError,
                    attempt.playlistSkipped
                )
            )
            add(
                syncCategory(
                    getString(R.string.device_sync_category_watch_history),
                    attempt.watchHistoryResult?.sentChanges,
                    attempt.watchHistoryResult?.receivedChanges,
                    attempt.watchHistoryError,
                    attempt.watchHistorySkipped
                )
            )
            add(
                syncCategory(
                    getString(R.string.device_sync_category_search_history),
                    attempt.searchHistoryResult?.sentChanges,
                    attempt.searchHistoryResult?.receivedChanges,
                    attempt.searchHistoryError,
                    attempt.searchHistorySkipped
                )
            )
            add(
                syncCategory(
                    getString(R.string.device_sync_category_learning_notes),
                    attempt.learningNotesResult?.sentChanges,
                    attempt.learningNotesResult?.receivedChanges,
                    attempt.learningNotesError,
                    attempt.learningNotesSkipped
                )
            )
            StructuredPreferenceCategory.entries.forEach { category ->
                val result = attempt.structuredPreferenceResults[category]
                add(
                    syncCategory(
                        structuredPreferenceCategoryName(category),
                        result?.sentChanges,
                        result?.receivedChanges,
                        attempt.structuredPreferenceErrors[category],
                        category in attempt.structuredPreferenceSkipped
                    )
                )
            }
        }
    }

    private fun syncCategory(
        category: String,
        sentChanges: Int?,
        receivedChanges: Int?,
        error: String?,
        disabled: Boolean = false
    ): SyncCategoryDisplay {
        return when {
            disabled -> SyncCategoryDisplay(
                category,
                SyncDisplayStatus.DISABLED,
                getString(R.string.device_sync_report_disabled)
            )

            sentChanges != null && receivedChanges != null -> SyncCategoryDisplay(
                category,
                SyncDisplayStatus.SUCCEEDED,
                getString(
                    R.string.device_sync_report_sent_received,
                    sentChanges,
                    receivedChanges
                )
            )

            else -> SyncCategoryDisplay(
                category,
                SyncDisplayStatus.FAILED,
                getString(
                    R.string.device_sync_report_failed,
                    error ?: getString(R.string.general_error)
                )
            )
        }
    }

    private fun showSyncLog() {
        val entries = syncManager.syncLogEntries
        if (entries.isEmpty()) {
            Toast.makeText(requireContext(), R.string.device_sync_log_empty, Toast.LENGTH_SHORT)
                .show()
            return
        }
        val logText = buildSyncLog(entries)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.device_sync_log_title)
            .setView(scrollableTextView(logText))
            .setNegativeButton(R.string.device_sync_log_copy) { _, _ ->
                requireContext().getSystemService<ClipboardManager>()?.setPrimaryClip(
                    ClipData.newPlainText(
                        getString(R.string.device_sync_log_title),
                        logText.toString()
                    )
                )
                Toast.makeText(
                    requireContext(),
                    R.string.device_sync_log_copied,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNeutralButton(R.string.device_sync_log_clear) { _, _ ->
                syncManager.clearSyncLog()
                updateState()
            }
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun buildSyncLog(entries: List<DeviceSyncLogEntry>): CharSequence {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
        return SpannableStringBuilder().apply {
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    append("\n\n")
                }
                appendBold(dateFormat.format(entry.timestampEpochMillis))
                append(" · ")
                append(
                    getString(
                        if (entry.direction == DeviceSyncLogDirection.INCOMING) {
                            R.string.device_sync_log_incoming
                        } else if (entry.background) {
                            R.string.device_sync_log_background
                        } else {
                            R.string.device_sync_log_manual
                        }
                    )
                )
                if (entry.direction == DeviceSyncLogDirection.OUTGOING) {
                    append("\n")
                    append(
                        getString(
                            R.string.device_sync_report_devices,
                            entry.succeededDevices,
                            entry.failedDevices
                        )
                    )
                    append("\n")
                    append(
                        getString(
                            R.string.device_sync_report_changes,
                            entry.sentChanges,
                            entry.receivedChanges
                        )
                    )
                    append("\n")
                    append(
                        if (entry.localAddresses.isEmpty()) {
                            getString(R.string.device_sync_log_no_local_addresses)
                        } else {
                            getString(
                                R.string.device_sync_log_local_addresses,
                                entry.localAddresses.joinToString()
                            )
                        }
                    )
                }
                entry.fatalError?.let { error ->
                    append("\n")
                    append(getString(R.string.device_sync_log_fatal_error, error))
                }
                entry.attempts.forEach { attempt ->
                    append("\n\n")
                    appendBold(attempt.deviceName)
                    append(" — ")
                    append(abbreviatePeerId(attempt.peerId))
                    append("\n")
                    append(
                        if (attempt.addresses.isEmpty()) {
                            getString(R.string.device_sync_log_no_addresses)
                        } else {
                            getString(
                                R.string.device_sync_log_addresses,
                                attempt.addresses.joinToString()
                            )
                        }
                    )
                    attempt.attemptId?.let { attemptId ->
                        append("\n")
                        append(getString(R.string.device_sync_log_attempt_id, attemptId))
                        append("\n")
                        append(
                            getString(
                                R.string.device_sync_log_local_version,
                                attempt.localAppVersion?.displayValue()
                                    ?: getString(R.string.device_sync_log_unknown)
                            )
                        )
                        append("\n")
                        append(
                            getString(
                                R.string.device_sync_log_remote_version,
                                attempt.remoteAppVersion?.displayValue()
                                    ?: getString(R.string.device_sync_log_unknown)
                            )
                        )
                        append("\n")
                        append(
                            getString(
                                R.string.device_sync_log_protocol_versions,
                                attempt.localSyncProtocolVersion?.toString()
                                    ?: getString(R.string.device_sync_log_unknown),
                                attempt.remoteSyncProtocolVersion?.toString()
                                    ?: getString(R.string.device_sync_log_unknown)
                            )
                        )
                    }
                    attempt.categories.forEach { category ->
                        append("\n")
                        val display = category.toDisplay()
                        append(display.status.symbol)
                        append(" ")
                        append(display.title)
                        append(" — ")
                        append(display.detail)
                    }
                }
            }
        }
    }

    private fun SyncAppVersion.displayValue(): String {
        return getString(R.string.device_sync_log_version_value, name, code)
    }

    private fun DeviceSyncLogCategoryResult.toDisplay(): SyncCategoryDisplay {
        val title = deviceSyncLogCategoryName(category)
        val display = when (status) {
            DeviceSyncLogStatus.SUCCEEDED -> SyncCategoryDisplay(
                title,
                SyncDisplayStatus.SUCCEEDED,
                getString(
                    R.string.device_sync_report_sent_received,
                    sentChanges,
                    receivedChanges
                )
            )

            DeviceSyncLogStatus.FAILED -> SyncCategoryDisplay(
                title,
                SyncDisplayStatus.FAILED,
                getString(
                    R.string.device_sync_report_failed,
                    error ?: getString(R.string.general_error)
                )
            )

            DeviceSyncLogStatus.DISABLED -> SyncCategoryDisplay(
                title,
                SyncDisplayStatus.DISABLED,
                getString(R.string.device_sync_report_disabled)
            )
        }
        val retry = retryDiagnostic?.takeIf(String::isNotBlank) ?: return display
        return display.copy(detail = display.detail + " · " + retry)
    }

    private fun deviceSyncLogCategoryName(category: DeviceSyncLogCategory): String {
        return getString(
            when (category) {
                DeviceSyncLogCategory.RADIO_FILTERS ->
                    R.string.device_sync_category_radio_filters

                DeviceSyncLogCategory.LESSON_DIFFICULTY ->
                    R.string.device_sync_category_lesson_difficulty

                DeviceSyncLogCategory.PROFILES -> R.string.device_sync_category_profiles

                DeviceSyncLogCategory.SUBSCRIPTIONS ->
                    R.string.device_sync_category_subscriptions

                DeviceSyncLogCategory.PLAYLISTS -> R.string.device_sync_category_playlists

                DeviceSyncLogCategory.WATCH_HISTORY ->
                    R.string.device_sync_category_watch_history

                DeviceSyncLogCategory.SEARCH_HISTORY ->
                    R.string.device_sync_category_search_history

                DeviceSyncLogCategory.LEARNING_NOTES ->
                    R.string.device_sync_category_learning_notes

                DeviceSyncLogCategory.FEED_GROUPS -> R.string.device_sync_category_feed_groups

                DeviceSyncLogCategory.HOME_TABS -> R.string.device_sync_category_home_tabs

                DeviceSyncLogCategory.CHANNEL_PROFILES ->
                    R.string.device_sync_category_channel_profiles

                DeviceSyncLogCategory.FILTERS -> R.string.device_sync_category_filters

                DeviceSyncLogCategory.CONTENT_BLOCKING ->
                    R.string.device_sync_category_blocked_content

                DeviceSyncLogCategory.SETTINGS -> R.string.device_sync_category_settings

                DeviceSyncLogCategory.COMPLETED_DOWNLOADS ->
                    R.string.device_sync_category_completed_downloads
            }
        )
    }

    private fun scrollableTextView(text: CharSequence): NestedScrollView {
        val textView = TextView(requireContext()).apply {
            this.text = text
            setTextIsSelectable(true)
            setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
            )
            setLineSpacing(0f, REPORT_LINE_SPACING)
        }
        return NestedScrollView(requireContext()).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(
                REPORT_HORIZONTAL_PADDING_DP.dp,
                0,
                REPORT_HORIZONTAL_PADDING_DP.dp,
                REPORT_BOTTOM_PADDING_DP.dp
            )
            addView(
                textView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * REPORT_MAX_HEIGHT_RATIO).toInt()
            )
        }
    }

    private fun SpannableStringBuilder.appendBold(value: CharSequence) {
        val start = length
        append(value)
        setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun structuredPreferenceCategoryName(
        category: StructuredPreferenceCategory
    ): String {
        return getString(
            when (category) {
                StructuredPreferenceCategory.RADIO_FILTERS ->
                    R.string.device_sync_category_radio_filters

                StructuredPreferenceCategory.LESSON_DIFFICULTY ->
                    R.string.device_sync_category_lesson_difficulty

                StructuredPreferenceCategory.FEED_GROUPS ->
                    R.string.device_sync_category_feed_groups

                StructuredPreferenceCategory.HOME_TABS ->
                    R.string.device_sync_category_home_tabs

                StructuredPreferenceCategory.CHANNEL_PROFILES ->
                    R.string.device_sync_category_channel_profiles

                StructuredPreferenceCategory.FILTERS ->
                    R.string.device_sync_category_filters

                StructuredPreferenceCategory.CONTENT_BLOCKING ->
                    R.string.device_sync_category_blocked_content

                StructuredPreferenceCategory.SETTINGS ->
                    R.string.device_sync_category_settings

                StructuredPreferenceCategory.COMPLETED_DOWNLOADS ->
                    R.string.device_sync_category_completed_downloads
            }
        )
    }

    private fun statusSummary(peers: List<TrustedPeer>): CharSequence {
        if (peers.isEmpty()) {
            return getString(R.string.device_sync_status_no_devices)
        }
        val errors = peers.count { it.lastSyncError != null }
        if (errors > 0) {
            return getString(R.string.device_sync_status_errors, errors)
        }
        val latestSync = peers.mapNotNull(TrustedPeer::lastSyncAtEpochMillis).maxOrNull()
        return if (latestSync == null) {
            getString(R.string.device_sync_status_ready, peers.size)
        } else {
            getString(
                R.string.device_sync_status_last_sync,
                relativeTime(latestSync)
            )
        }
    }

    private fun relativeTime(epochMillis: Long): CharSequence {
        return DateUtils.getRelativeTimeSpanString(
            epochMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
    }

    private fun setActionsEnabled(enabled: Boolean) {
        showPairingCodePreference.isEnabled = enabled
        scanPairingCodePreference.isEnabled = enabled
        syncNowPreference.isEnabled = enabled && syncManager.trustedPeers.isNotEmpty()
    }

    private fun showError(title: Int, error: Exception) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(error.message ?: getString(R.string.general_error))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun abbreviatePeerId(peerId: String): String {
        if (peerId.length <= ABBREVIATED_PEER_ID_LENGTH) {
            return peerId
        }
        return "${peerId.take(PEER_ID_EDGE_LENGTH)}…${peerId.takeLast(PEER_ID_EDGE_LENGTH)}"
    }

    companion object {
        private const val MAX_QR_SIZE_DP = 420
        private const val PAIRING_DIALOG_HORIZONTAL_MARGIN_DP = 64
        private const val PEER_ID_EDGE_LENGTH = 8
        private const val ABBREVIATED_PEER_ID_LENGTH = PEER_ID_EDGE_LENGTH * 2
        private const val REPORT_HORIZONTAL_PADDING_DP = 24
        private const val REPORT_BOTTOM_PADDING_DP = 16
        private const val REPORT_MAX_HEIGHT_RATIO = 0.58f
        private const val REPORT_LINE_SPACING = 1.08f
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private data class SyncCategoryDisplay(
        val title: String,
        val status: SyncDisplayStatus,
        val detail: String
    )

    private enum class SyncDisplayStatus(val symbol: String) {
        SUCCEEDED("✓"),
        FAILED("✕"),
        DISABLED("–")
    }
}
