package org.schabi.newpipe.settings

import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.appearance.CustomThemeColor
import org.schabi.newpipe.themes.CustomAccentColors
import org.schabi.newpipe.themes.ThemeDraftStore
import org.schabi.newpipe.themes.ThemePack
import org.schabi.newpipe.themes.ThemePacks
import org.schabi.newpipe.util.ThemeHelper

class ThemePackSettingsFragment : BasePreferenceFragment() {
    private var pending: ThemePack? = null
    private var exportText: String? = null
    private var pendingId: String? = null
    private var exportId: String? = null
    private var previewDialog: androidx.appcompat.app.AlertDialog? = null
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolver = requireContext().contentResolver
            lifecycleScope.launch {
                try {
                    val pack = withContext(Dispatchers.IO) {
                        resolver.openInputStream(uri)?.use(ThemePack::read) ?: error("Cannot read file")
                    }
                    if (isResumed) preview(pack) else pending = pack
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failure()
                }
            }
        }
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = exportText
        exportText = null
        ThemeDraftStore.remove(requireContext(), exportId)
        exportId = null
        if (uri != null && text != null) {
            val resolver = requireContext().contentResolver
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                            ?: error("Cannot write file")
                    }
                    if (isAdded) Toast.makeText(requireContext(), R.string.theme_pack_exported, Toast.LENGTH_LONG).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failure()
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResourceRegistry()
        pendingId = savedInstanceState?.getString("pendingId")
        exportId = savedInstanceState?.getString("exportId")
        pending = ThemeDraftStore.read(requireContext(), pendingId)
        exportText = ThemeDraftStore.read(requireContext(), exportId)?.encode()
        findPreference<Preference>("theme_pack_status")!!.summary = ThemePacks.active(requireContext())?.let {
            getString(R.string.theme_pack_author, it.name, it.author)
        } ?: getString(R.string.theme_pack_default)
        findPreference<Preference>("theme_pack_import")!!.setOnPreferenceClickListener {
            try {
                importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            } catch (_: ActivityNotFoundException) {
                failure()
            }
            true
        }
        findPreference<Preference>("theme_pack_export")!!.setOnPreferenceClickListener {
            try {
                exportText = ThemePacks.export(requireContext()).encode()
                exportFile.launch("WizeStream-theme.json")
            } catch (_: Exception) {
                exportText = null
                failure()
            }
            true
        }
        findPreference<Preference>("theme_pack_restore")!!.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.theme_pack_restore_message)
                .setPositiveButton(R.string.theme_pack_restore) { _, _ ->
                    ThemePacks.restore(requireContext())
                    refreshTheme()
                }.setNegativeButton(android.R.string.cancel, null).show()
            true
        }
        findPreference<Preference>("theme_pack_presets")!!.setOnPreferenceClickListener {
            val files = listOf("forest.json", "ocean.json", "midnight.json", "glass-record.json", "glass.json")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.theme_pack_presets)
                .setItems(R.array.theme_pack_presets) { _, which ->
                    try {
                        preview(requireContext().assets.open("themes/${files[which]}").use(ThemePack::read))
                    } catch (_: Exception) {
                        failure()
                    }
                }.show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        pending?.let { if (previewDialog == null) preview(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            pendingId = ThemeDraftStore.save(requireContext(), pendingId, pending?.encode())
            exportId = ThemeDraftStore.save(requireContext(), exportId, exportText)
            outState.putString("pendingId", pendingId)
            outState.putString("exportId", exportId)
        } catch (_: java.io.IOException) {
            // Losing a preview on cache eviction/storage failure must not crash settings.
        }
    }

    override fun onDestroyView() {
        previewDialog?.dismiss()
        previewDialog = null
        super.onDestroyView()
    }

    private fun preview(pack: ThemePack) {
        previewDialog?.dismiss()
        pending = pack
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        content.addView(
            TextView(context).apply {
                text = getString(R.string.theme_pack_preview_description, pack.name, pack.author)
            }
        )
        val modes = if (pack.mode == "system") listOf("light", pack.night) else listOf(pack.mode)
        for (mode in modes) {
            val light = mode == "light"
            val config = Configuration(resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (light) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
            }
            val style = when (mode) {
                "light" -> R.style.LightTheme
                "black" -> R.style.BlackTheme
                "eye_comfort" -> R.style.EyeComfortTheme
                else -> R.style.DarkTheme
            }
            var palette: android.content.Context = ContextThemeWrapper(context.createConfigurationContext(config), style)
            val accent = if (light) pack.lightAccent else pack.darkAccent
            if (mode != "eye_comfort") {
                val customSeed = CustomThemeColor.normalize(accent)
                if (customSeed != null) {
                    palette = CustomAccentColors.wrap(palette, CustomThemeColor.parse(customSeed), light, mode == "black")
                } else if (accent == "follow_system") {
                    palette = DynamicColors.wrapContextIfAvailable(palette)
                    if (mode == "black") palette.theme.applyStyle(R.style.ThemeOverlay_wizestream_BlackSurfaces, true)
                } else {
                    palette.theme.applyStyle(ThemeHelper.getThemeColorOverlay(accent), true)
                }
            }
            val card = LinearLayout(palette).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                setBackgroundColor(ThemeHelper.resolveColorFromAttr(palette, com.google.android.material.R.attr.colorSurface))
            }
            card.addView(
                TextView(palette).apply {
                    text = getString(if (light) R.string.theme_pack_day_preview else R.string.theme_pack_night_preview)
                    setTextColor(ThemeHelper.resolveColorFromAttr(palette, com.google.android.material.R.attr.colorOnSurface))
                }
            )
            card.addView(
                TextView(palette).apply {
                    text = getString(R.string.theme_pack_accent_preview)
                    val pad = (12 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                    setTextColor(ThemeHelper.resolveColorFromAttr(palette, com.google.android.material.R.attr.colorOnPrimaryContainer))
                    setBackgroundColor(ThemeHelper.resolveColorFromAttr(palette, com.google.android.material.R.attr.colorPrimaryContainer))
                }
            )
            if (pack.version >= 2) {
                val sample = org.schabi.newpipe.themes.ThemePlayerView(palette).apply { this.pack = pack }
                val frame = android.widget.FrameLayout(palette)
                frame.addView(sample, android.widget.FrameLayout.LayoutParams(-1, (220 * resources.displayMetrics.density).toInt()))
                val caption = TextView(palette).apply {
                    text = getString(R.string.theme_pack_style_preview, pack.player.layout, pack.assets.size)
                    setTextColor(ThemeHelper.resolveColorFromAttr(palette, com.google.android.material.R.attr.colorOnSurface))
                    setPadding(16, 16, 16, 16)
                    background = org.schabi.newpipe.themes.GlassBackdrop(this, sample, pack)
                }
                frame.addView(caption, android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
                card.addView(frame)
                if (pack.icons.isNotEmpty()) {
                    val row = LinearLayout(palette)
                    for ((_, assetId) in pack.icons) {
                        row.addView(
                            android.widget.ImageView(palette).apply {
                                setImageBitmap(pack.assets.getValue(assetId).bitmap)
                                imageTintList = android.content.res.ColorStateList.valueOf(ThemeHelper.resolveColorFromAttr(palette, com.google.android.material.R.attr.colorOnSurface))
                                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            },
                            LinearLayout.LayoutParams(0, (40 * resources.displayMetrics.density).toInt(), 1f)
                        )
                    }
                    card.addView(row)
                }
                if (pack.glass != null) card.addView(TextView(palette).apply { setText(R.string.theme_pack_glass_scope) })
            }
            content.addView(card)
        }
        previewDialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.theme_pack_preview)
            .setView(android.widget.ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.theme_pack_apply) { _, _ ->
                ThemePacks.apply(context, pack)
                pending = null
                ThemeDraftStore.remove(context, pendingId)
                refreshTheme()
            }.setNegativeButton(android.R.string.cancel) { _, _ -> pending = null }
            .setOnCancelListener { pending = null }
            .create().also { it.show() }
    }

    private fun refreshTheme() {
        ThemeHelper.setDayNightMode(requireContext())
        requireActivity().recreate()
    }

    private fun failure() {
        if (isAdded) Toast.makeText(requireContext(), R.string.theme_pack_failed, Toast.LENGTH_LONG).show()
    }
}
