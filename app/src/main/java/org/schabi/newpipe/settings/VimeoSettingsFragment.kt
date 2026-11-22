/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.services.vimeo.VimeoPublicApi
import org.schabi.newpipe.util.external_communication.ShareUtils

class VimeoSettingsFragment : BasePreferenceFragment() {
    private var tokenDialog: AlertDialog? = null
    private var request: Job? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()
        findPreference<Preference>(getString(R.string.vimeo_api_set_key))!!.setOnPreferenceClickListener {
            showTokenEntry()
            true
        }
        findPreference<Preference>(getString(R.string.vimeo_api_remove_key))!!.setOnPreferenceClickListener {
            removeToken()
            true
        }
        findPreference<Preference>(getString(R.string.vimeo_api_help_key))!!.setOnPreferenceClickListener {
            ShareUtils.openUrlInBrowser(requireContext(), "https://help.vimeo.com/hc/en-us/articles/12427789081745-How-to-generate-a-personal-access-token")
            true
        }
        refresh()
    }

    private fun refresh(busy: Boolean = false) {
        val configured = VimeoPublicApi.isConfigured()
        findPreference<Preference>(getString(R.string.vimeo_api_set_key))!!.apply {
            isEnabled = !busy
            setSummary(if (configured) R.string.vimeo_api_configured else R.string.vimeo_api_not_configured)
        }
        findPreference<Preference>(getString(R.string.vimeo_api_remove_key))!!.isEnabled = configured && !busy
    }

    private fun showTokenEntry() {
        if (tokenDialog != null) return
        val context = requireContext()
        val input = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            isSaveEnabled = false
            setSingleLine()
        }
        val field = TextInputLayout(context).apply {
            hint = getString(R.string.vimeo_api_token_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(input)
        }
        val container = LinearLayout(context).apply {
            val inset = (24 * resources.displayMetrics.density).toInt()
            setPadding(inset, inset / 2, inset, 0)
            addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.vimeo_api_set_title)
            .setMessage(R.string.vimeo_api_token_message)
            .setView(container)
            .setPositiveButton(R.string.vimeo_api_verify, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        tokenDialog = dialog
        dialog.setOnDismissListener {
            request?.cancel()
            request = null
            input.text?.clear()
            tokenDialog = null
            if (isAdded && view != null) refresh()
        }
        dialog.show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val candidate = try {
                VimeoPublicApi.normalizeToken(input.text?.toString().orEmpty())
            } catch (_: IllegalArgumentException) {
                field.error = getString(R.string.vimeo_api_invalid_token)
                return@setOnClickListener
            }
            field.error = null
            input.isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            refresh(busy = true)
            val appContext = context.applicationContext
            request = viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val verified = withContext(Dispatchers.IO) { VimeoPublicApi.verifyPublicToken(candidate) }
                    val saved = withContext(Dispatchers.IO) { VimeoApiSettings.saveVerified(appContext, verified) }
                    if (!saved) {
                        field.error = getString(R.string.vimeo_api_storage_error)
                    } else {
                        Toast.makeText(context, R.string.vimeo_api_saved, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    field.error = getString(R.string.vimeo_api_verification_error)
                } finally {
                    if (dialog.isShowing) {
                        input.isEnabled = true
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                    if (isAdded && view != null) refresh()
                }
            }
        }
    }

    private fun removeToken() {
        val context = requireContext().applicationContext
        refresh(busy = true)
        request = viewLifecycleOwner.lifecycleScope.launch {
            val removed = withContext(Dispatchers.IO) { VimeoApiSettings.clear(context) }
            if (isAdded) {
                refresh()
                Toast.makeText(context, if (removed) R.string.vimeo_api_removed else R.string.vimeo_api_storage_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        request?.cancel()
        tokenDialog?.dismiss()
        tokenDialog = null
        super.onDestroyView()
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "org.schabi.newpipe.settings.OPEN_VIMEO_SETTINGS"

        @JvmStatic
        fun initialOrDefault(intent: Intent, defaultFragment: Fragment): Fragment = if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) VimeoSettingsFragment() else defaultFragment
    }
}
