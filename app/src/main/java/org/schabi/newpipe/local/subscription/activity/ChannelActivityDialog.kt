/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription.activity

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogChannelActivityBinding
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.views.NewPipeTextView

/** A local, read-only snapshot. Opening this dialog never triggers extraction or a feed refresh. */
class ChannelActivityDialog : DialogFragment() {
    private var request: Disposable? = null
    private var weekly = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogChannelActivityBinding.inflate(layoutInflater)
        val context = requireContext()
        val app = context.applicationContext
        val args = requireArguments()
        val zone = ZoneId.systemDefault()
        weekly = savedInstanceState?.getBoolean("weekly") ?: false
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.channel_activity_title)
            .setView(binding.root)
            .setPositiveButton(R.string.ok, null)
            .create()
        request = Single.fromCallable {
            val snapshot = NewPipeDatabase.getInstance(app).channelActivityDAO().snapshot(
                requireNotNull(args.getString("profile")),
                args.getInt("service"),
                requireNotNull(args.getString("channel"))
            )
            ChannelActivityStatistics.from(snapshot, FeedDatabaseManager.oldestAllowedDate(app).toInstant(), Instant.now(), zone)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ statistics ->
                binding.activitySummary.text = summary(statistics, zone)
                binding.activityPeriod.isVisible = statistics.uploadDates.isNotEmpty()
                binding.activityPeriod.check(if (weekly) R.id.activity_by_week else R.id.activity_by_month)
                binding.activityPeriod.addOnButtonCheckedListener { _, id, checked ->
                    if (checked) {
                        weekly = id == R.id.activity_by_week
                        showBuckets(binding.activityBuckets, statistics)
                    }
                }
                showBuckets(binding.activityBuckets, statistics)
            }, {
                binding.activitySummary.setText(R.string.channel_activity_error)
            })
        return dialog
    }

    private fun summary(statistics: ChannelActivityStatistics, zone: ZoneId): String {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        val numbers = NumberFormat.getIntegerInstance()
        return buildList {
            add(getString(R.string.channel_activity_coverage, date.format(statistics.windowStart), date.format(statistics.windowEnd), numbers.format(statistics.uploadDates.size)))
            if (statistics.uploadDates.isEmpty()) add(getString(R.string.channel_activity_empty))
            statistics.latestKnownUpload?.let { add(getString(R.string.channel_activity_latest, date.format(it))) }
            statistics.oldestObservedUpload?.let { add(getString(R.string.channel_activity_oldest, date.format(it))) }
            statistics.lastUpdated?.let {
                val timestamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(zone)
                add(getString(R.string.channel_activity_refresh, timestamp.format(it)))
            }
            if (statistics.approximateDates > 0 || statistics.undatedItems > 0) {
                add(getString(R.string.channel_activity_dates, numbers.format(statistics.approximateDates), numbers.format(statistics.undatedItems)))
            }
        }.joinToString("\n\n")
    }

    private fun showBuckets(container: LinearLayout, statistics: ChannelActivityStatistics) {
        container.removeAllViews()
        if (statistics.uploadDates.isEmpty()) return
        val buckets = statistics.buckets(weekly)
        val max = buckets.maxOf { it.count }.coerceAtLeast(1)
        val context = container.context
        val numbers = NumberFormat.getIntegerInstance()
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault())
        val month = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
        for (bucket in buckets) {
            val period = if (weekly) getString(R.string.channel_activity_week, date.format(bucket.start), date.format(bucket.end)) else month.format(bucket.start)
            val label = getString(R.string.channel_activity_bucket, period, numbers.format(bucket.count))
            container.addView(
                NewPipeTextView(context).apply {
                    text = label
                    setPadding(0, DeviceUtils.dpToPx(12, context), 0, DeviceUtils.dpToPx(4, context))
                }
            )
            container.addView(
                LinearProgressIndicator(context).apply {
                    isIndeterminate = false
                    this.max = max
                    progress = bucket.count
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, DeviceUtils.dpToPx(8, context))
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("weekly", weekly)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        request?.dispose()
        request = null
        super.onDestroyView()
    }

    companion object {
        @JvmStatic
        fun addButton(fragment: Fragment, parent: LinearLayout, info: ChannelInfo) {
            parent.addView(
                MaterialButton(parent.context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    setText(R.string.channel_activity_open)
                    isSingleLine = false
                    maxLines = 2
                    setOnClickListener {
                        val manager = fragment.childFragmentManager
                        if (!manager.isStateSaved && manager.findFragmentByTag("channel_activity") == null) {
                            ChannelActivityDialog().apply {
                                arguments = Bundle().apply {
                                    putString("profile", ProfileManager.getActiveProfileId(parent.context))
                                    putInt("service", info.serviceId)
                                    putString("channel", info.url)
                                }
                            }.show(manager, "channel_activity")
                        }
                    }
                }
            )
        }
    }
}
