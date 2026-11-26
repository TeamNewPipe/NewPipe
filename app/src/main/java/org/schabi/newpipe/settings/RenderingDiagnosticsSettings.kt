package org.schabi.newpipe.settings

import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.views.DetailRenderingDiagnostics

object RenderingDiagnosticsSettings {
    @JvmStatic
    fun bind(fragment: DebugSettingsFragment) {
        val capture = requireNotNull(fragment.findPreference<Preference>(fragment.getString(R.string.rendering_diagnostics_capture_key)))
        val report = requireNotNull(fragment.findPreference<Preference>(fragment.getString(R.string.rendering_diagnostics_report_key)))
        if (!DetailRenderingDiagnostics.isAvailable()) {
            fragment.preferenceScreen.removePreference(capture)
            fragment.preferenceScreen.removePreference(report)
            return
        }
        report.isEnabled = DetailRenderingDiagnostics.report() != null
        capture.setOnPreferenceClickListener {
            if (DetailRenderingDiagnostics.startCapture(fragment.requireContext())) {
                report.isEnabled = true
                Toast.makeText(fragment.requireContext(), R.string.rendering_diagnostics_started, Toast.LENGTH_LONG).show()
            }
            true
        }
        report.setOnPreferenceClickListener {
            val text = DetailRenderingDiagnostics.report()
            if (text != null) {
                val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(R.string.rendering_diagnostics_report_title)
                    .setMessage(text)
                    .setPositiveButton(android.R.string.copy) { _, _ -> ShareUtils.copyToClipboard(fragment.requireContext(), text) }
                    .setNegativeButton(R.string.rendering_diagnostics_clear) { _, _ ->
                        DetailRenderingDiagnostics.clear()
                        report.isEnabled = false
                    }
                    .setNeutralButton(android.R.string.cancel, null)
                    .create()
                val owner = fragment.viewLifecycleOwner
                val observer = object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) = dialog.dismiss()
                }
                owner.lifecycle.addObserver(observer)
                dialog.setOnDismissListener { owner.lifecycle.removeObserver(observer) }
                dialog.show()
            }
            true
        }
    }
}
