package org.schabi.newpipe.recommendations

import android.content.Context
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager

object RecommendationControls {
    @JvmStatic
    fun show(context: Context, changed: Runnable) {
        val profile = ProfileManager.getActiveProfileId(context)
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(TextView(context).apply { setText(R.string.local_recommendations_help) })
        }
        val fields = linkedMapOf<String, CheckBox>()
        val options = listOf(
            "enabled" to R.string.local_recommendations_enable,
            "paused" to R.string.local_recommendations_pause,
            "shorts" to R.string.local_recommendations_shorts,
            "autoplay" to R.string.local_recommendations_autoplay,
            "reduce_shorts" to R.string.local_recommendations_reduce_shorts,
            "reduce_short_videos" to R.string.local_recommendations_reduce_short_videos,
            "reduce_disclosed_ai" to R.string.local_recommendations_reduce_disclosed_ai
        )
        for ((key, title) in options) {
            fields[key] = CheckBox(context).apply {
                setText(title)
                isChecked = LocalRecommendations.setting(context, key)
                content.addView(this)
            }
        }
        content.addView(
            MaterialButton(context).apply {
                setText(R.string.local_recommendations_your_taste)
                setOnClickListener { showTaste(context) }
            }
        )
        content.addView(
            MaterialButton(context).apply {
                val tabsManager = TabsManager.getManager(context)
                val alreadyAdded = tabsManager.tabs.any { it is Tab.RecommendationsTab }
                setText(if (alreadyAdded) R.string.local_recommendations_home_added else R.string.local_recommendations_add_home)
                isEnabled = !alreadyAdded
                setOnClickListener {
                    val tabs = tabsManager.tabs.toMutableList()
                    if (tabs.none { it is Tab.RecommendationsTab }) {
                        tabs.add(Tab.Type.RECOMMENDATIONS.tab)
                        tabsManager.saveTabs(tabs)
                        setText(R.string.local_recommendations_home_added)
                        isEnabled = false
                        Toast.makeText(context, R.string.local_recommendations_home_added, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        content.addView(TextView(context).apply { setText(R.string.local_recommendations_topics) })
        val topics = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(LocalRecommendations.topics(context))
            content.addView(this)
        }
        MaterialAlertDialogBuilder(context).setTitle(R.string.local_recommendations_title)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.ok) { _, _ ->
                if (profile == ProfileManager.getActiveProfileId(context)) {
                    LocalRecommendations.configure(context, fields.mapValues { it.value.isChecked }, topics.text.toString())
                    changed.run()
                }
            }.setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.discovery_reset) { _, _ ->
                MaterialAlertDialogBuilder(context).setTitle(R.string.discovery_reset)
                    .setMessage(R.string.local_recommendations_reset_help)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        if (profile == ProfileManager.getActiveProfileId(context)) {
                            LocalRecommendations.reset(context)
                            changed.run()
                        }
                    }.setNegativeButton(R.string.cancel, null).show()
            }.show()
    }

    private fun showTaste(context: Context) {
        val summary = LocalRecommendations.tasteSummary(context)
        val interests = summary.interests.ifEmpty { listOf(context.getString(R.string.local_recommendations_taste_empty)) }
        val channels = summary.channels.ifEmpty { listOf(context.getString(R.string.local_recommendations_taste_empty)) }
        val message = context.getString(
            R.string.local_recommendations_taste_summary,
            interests.joinToString(" • "),
            channels.joinToString(" • ")
        )
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.local_recommendations_your_taste)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
