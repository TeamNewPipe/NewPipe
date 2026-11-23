/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.transcript

import android.app.Dialog
import android.os.Bundle
import android.text.InputFilter
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.SerialDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.download.DownloadedStreamInfo
import org.schabi.newpipe.download.ResolvedOfflineMedia
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.learning.LearningNoteManager
import org.schabi.newpipe.learning.LearningNoteTime
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.TimestampChangeData
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/** Captions remain in memory. Fetching begins only after the user opens this panel. */
class TranscriptDialog : DialogFragment() {
    private val work = CompositeDisposable()
    private var captionRequest = SerialDisposable()
    private var info: StreamInfo? = null
    private var tracks = emptyList<SubtitlesStream>()
    private var captionIdentities = emptyMap<String, String>()
    private var lines = emptyList<TranscriptLine>()
    private var visibleLines = emptyList<TranscriptLine>()
    private var selectedLanguage = ""
    private var query = ""
    private lateinit var status: TextView
    private lateinit var language: MaterialButton
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        selectedLanguage = savedInstanceState?.getString("language").orEmpty()
        query = savedInstanceState?.getString("query").orEmpty()
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, 0)
        }
        language = MaterialButton(context).apply {
            setText(R.string.transcript_language)
            isEnabled = false
            setOnClickListener {
                MaterialAlertDialogBuilder(context).setTitle(R.string.transcript_language)
                    .setItems(tracks.map(::trackLabel).toTypedArray()) { _, index -> load(tracks[index]) }.show()
            }
        }
        content.addView(language)
        content.addView(
            SearchView(context).apply {
                setIconifiedByDefault(false)
                queryHint = getString(R.string.transcript_search)
                setQuery(this@TranscriptDialog.query, false)
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(value: String?): Boolean = true
                    override fun onQueryTextChange(value: String?): Boolean {
                        this@TranscriptDialog.query = value.orEmpty()
                        render()
                        return true
                    }
                })
            }
        )
        status = TextView(context).apply { setText(R.string.transcript_loading) }
        content.addView(status)
        adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf<String>())
        list = ListView(context).apply {
            adapter = this@TranscriptDialog.adapter
            setOnItemClickListener { _, _, index, _ -> visibleLines.getOrNull(index)?.let(::seek) }
            setOnItemLongClickListener { _, _, index, _ ->
                visibleLines.getOrNull(index)?.let(::actions)
                true
            }
        }
        val height = (resources.displayMetrics.heightPixels * 0.45).toInt()
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        return MaterialAlertDialogBuilder(context).setTitle(R.string.transcript_title)
            .setView(content).setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.transcript_copy_results) { _, _ ->
                if (valid() && visibleLines.isNotEmpty()) ShareUtils.copyToClipboard(context, visibleLines.joinToString("\n") { label(it) })
            }.create()
    }

    override fun onStart() {
        super.onStart()
        work.clear()
        captionRequest = SerialDisposable()
        work.add(captionRequest)
        info = null
        tracks = emptyList()
        captionIdentities = emptyMap()
        lines = emptyList()
        render()
        status.setText(R.string.transcript_loading)
        work.add(
            TranscriptSourceRepository.load(requireContext().applicationContext, request())
                .observeOn(AndroidSchedulers.mainThread()).subscribe({ source ->
                    if (!valid()) return@subscribe
                    info = source.info
                    tracks = source.tracks
                    captionIdentities = source.captionIdentities
                    language.isEnabled = tracks.isNotEmpty()
                    val track = tracks.firstOrNull { it.id == selectedLanguage }
                        ?: tracks.firstOrNull { it.locale.language == java.util.Locale.getDefault().language }
                        ?: tracks.firstOrNull()
                    if (track == null) status.setText(R.string.transcript_unavailable) else load(track)
                }, { if (valid()) status.setText(R.string.transcript_error) })
        )
    }

    private fun valid() = isAdded && requireArguments().getString("profile") == ProfileManager.getActiveProfileId(requireContext())

    private fun request() = TranscriptRequest(
        requireArguments().getInt("service"),
        requireArguments().getString("url")!!,
        requireArguments().getString("profile")!!,
        requireArguments().getString("local_uri"),
        requireArguments().getString("local_identity")
    )

    private fun trackLabel(track: SubtitlesStream) = track.displayLanguageName +
        if (track.isAutoGenerated) getString(R.string.transcript_automatic) else ""

    private fun load(track: SubtitlesStream) {
        selectedLanguage = track.id
        language.text = trackLabel(track)
        lines = emptyList()
        render()
        status.setText(R.string.transcript_loading)
        captionRequest.set(
            TranscriptRepository.load(requireContext().applicationContext, track, captionIdentities[track.content]).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ loaded ->
                    if (valid()) {
                        lines = loaded
                        render()
                    }
                }, { if (valid()) status.setText(R.string.transcript_error) })
        )
    }

    private fun render() {
        if (!::adapter.isInitialized) return
        visibleLines = TranscriptRepository.filter(lines, query)
        adapter.clear()
        adapter.addAll(visibleLines.map(::label))
        status.setText(if (visibleLines.isEmpty()) R.string.transcript_no_matches else R.string.transcript_help)
    }

    private fun label(line: TranscriptLine) = "${LearningNoteTime.format(line.timeMillis)}  ${line.text}"

    private fun seek(line: TranscriptLine) {
        if (!valid()) return
        requireContext().startService(
            NavigationHelper.getPlayerTimestampIntent(
                requireContext(),
                TimestampChangeData(
                    requireArguments().getInt("seek_service", requireArguments().getInt("service")),
                    requireArguments().getString("seek_url") ?: requireArguments().getString("url")!!,
                    (line.timeMillis / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    requireArguments().getString("local_uri"),
                    requireArguments().getString("local_identity"),
                    requireArguments().getString("profile"),
                    line.timeMillis
                )
            )
        )
    }

    private fun actions(line: TranscriptLine) {
        if (!valid()) return
        MaterialAlertDialogBuilder(requireContext()).setItems(
            if (info == null) arrayOf(getString(R.string.transcript_copy)) else arrayOf(getString(R.string.transcript_copy), getString(R.string.transcript_save_note))
        ) { _, index ->
            if (!valid()) return@setItems
            if (index == 0) ShareUtils.copyToClipboard(requireContext(), label(line)) else saveNote(line)
        }.show()
    }

    private fun saveNote(line: TranscriptLine) {
        val stream = info ?: return
        if (IncognitoMode.isEnabled(requireContext())) {
            Toast.makeText(requireContext(), R.string.transcript_private_note, Toast.LENGTH_LONG).show()
            return
        }
        val input = TextInputEditText(requireContext()).apply {
            setText(line.text)
            filters = arrayOf(InputFilter.LengthFilter(LearningNoteManager.MAX_NOTE_LENGTH))
        }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.transcript_save_note).setView(input)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save) { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (valid() && !IncognitoMode.isEnabled(requireContext()) && text.isNotEmpty()) {
                    work.add(
                        LearningNoteManager(requireContext()).create(stream, line.timeMillis, text)
                            .observeOn(AndroidSchedulers.mainThread()).subscribe({
                                if (valid()) Toast.makeText(requireContext(), R.string.transcript_note_saved, Toast.LENGTH_SHORT).show()
                            }, { if (valid()) Toast.makeText(requireContext(), R.string.general_error, Toast.LENGTH_LONG).show() })
                    )
                }
            }.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("language", selectedLanguage)
        outState.putString("query", query)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        work.clear()
        info = null
        tracks = emptyList()
        captionIdentities = emptyMap()
        lines = emptyList()
        visibleLines = emptyList()
        super.onDestroyView()
    }

    companion object {
        @JvmStatic
        fun addButton(parent: ViewGroup, fragment: Fragment, info: StreamInfo) {
            if (info.subtitles.none(TranscriptRepository::supports)) return
            val arguments = bundleOf("service" to info.serviceId, "url" to info.url)
            if (info is DownloadedStreamInfo) {
                arguments.putString("local_uri", info.copyUri)
                arguments.putString("local_identity", info.localIdentity)
            }
            addButton(parent, fragment, arguments)
        }

        fun addLocalButton(parent: ViewGroup, fragment: Fragment, item: PlayQueueItem, local: ResolvedOfflineMedia) {
            if (local.subtitles.none(TranscriptRepository::supports)) return
            val source = local.sourceInfo ?: return
            addButton(
                parent,
                fragment,
                bundleOf(
                    "service" to source.serviceId,
                    "url" to source.url,
                    "local_uri" to item.url,
                    "local_identity" to local.identity,
                    "seek_service" to PlayQueueItem.LOCAL_SERVICE_ID,
                    "seek_url" to item.url
                )
            )
        }

        private fun addButton(parent: ViewGroup, fragment: Fragment, source: Bundle) {
            parent.addView(
                MaterialButton(parent.context).apply {
                    setText(R.string.transcript_title)
                    setOnClickListener {
                        val manager = fragment.parentFragmentManager
                        if (!manager.isStateSaved && manager.findFragmentByTag("transcript") == null) {
                            TranscriptDialog().apply {
                                arguments = Bundle(source).apply { putString("profile", ProfileManager.getActiveProfileId(parent.context)) }
                            }.show(manager, "transcript")
                        }
                    }
                },
                0
            )
        }
    }
}
