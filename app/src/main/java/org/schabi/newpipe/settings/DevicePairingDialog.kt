/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.PersistableBundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogDevicePairingBinding
import org.schabi.newpipe.databinding.DialogDevicePairingInputBinding
import org.schabi.newpipe.sync.PairingCodeInput
import org.schabi.newpipe.sync.PairingException

internal object DevicePairingDialog {
    fun showCode(context: Context, code: String, peerId: String): AlertDialog {
        val binding = DialogDevicePairingBinding.inflate(LayoutInflater.from(context))
        val metrics = context.resources.displayMetrics
        val qrSize = minOf(
            metrics.widthPixels - (HORIZONTAL_MARGIN_DP * metrics.density).toInt(),
            (MAX_QR_SIZE_DP * metrics.density).toInt()
        )
        try {
            binding.devicePairingQr.setImageBitmap(
                BarcodeEncoder().encodeBitmap(code, BarcodeFormat.QR_CODE, qrSize, qrSize)
            )
        } catch (_: WriterException) {
            // An invitation with many addresses may exceed QR capacity but still be copyable.
            binding.devicePairingQr.visibility = View.GONE
            binding.devicePairingQrError.visibility = View.VISIBLE
        }
        binding.devicePairingQr.setBackgroundColor(Color.WHITE)
        binding.devicePairingPeerId.text = context.getString(
            R.string.device_sync_pairing_peer_id,
            peerId
        )
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.device_sync_show_code_title)
            .setView(binding.root)
            .setNeutralButton(R.string.device_sync_pairing_copy, null)
            .setNegativeButton(R.string.close, null)
            .show()
        // Copying must keep the invitation screen open while the other device connects.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val clipboard = context.getSystemService<ClipboardManager>()
            if (clipboard == null) {
                Toast.makeText(context, R.string.device_sync_pairing_copy_failed, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            val clip = ClipData.newPlainText(context.getString(R.string.device_sync_pairing_code_hint), code)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, R.string.device_sync_pairing_copied, Toast.LENGTH_LONG).show()
        }
        return dialog
    }

    fun showEntry(context: Context, onPair: (String) -> Unit): AlertDialog {
        val binding = DialogDevicePairingInputBinding.inflate(LayoutInflater.from(context))
        val input = binding.devicePairingInput
        input.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.root.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            input.imeOptions = input.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        input.doAfterTextChanged { binding.devicePairingInputLayout.error = null }

        fun codeOrError(value: CharSequence): String? {
            return try {
                PairingCodeInput.normalize(value)
            } catch (_: PairingException) {
                binding.devicePairingInputLayout.error = context.getString(
                    if (value.isBlank()) {
                        R.string.device_sync_pairing_code_empty
                    } else {
                        R.string.device_sync_pairing_code_too_large
                    }
                )
                null
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.device_sync_enter_code_title)
            .setView(binding.root)
            .setPositiveButton(R.string.device_sync_pairing_pair, null)
            .setNeutralButton(R.string.device_sync_pairing_paste, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
        dialog.setOnDismissListener { input.text?.clear() }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            // Read only after an explicit Paste; do not dereference clipboard URIs.
            val clip = context.getSystemService<ClipboardManager>()?.primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text else null
            if (text.isNullOrBlank()) {
                binding.devicePairingInputLayout.error =
                    context.getString(R.string.device_sync_pairing_clipboard_empty)
            } else {
                codeOrError(text)?.let { code ->
                    input.setText(code)
                    input.setSelection(code.length)
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val code = codeOrError(input.text ?: "") ?: return@setOnClickListener
            dialog.dismiss()
            onPair(code)
        }
        return dialog
    }

    private const val MAX_QR_SIZE_DP = 420
    private const val HORIZONTAL_MARGIN_DP = 64
}
