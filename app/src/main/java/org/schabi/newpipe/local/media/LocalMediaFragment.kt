/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.Locale
import java.util.Optional
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.download.DownloadActivity
import org.schabi.newpipe.download.DownloadPlayback
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.learning.LearningContentManager
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.util.GridLayoutManagerHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import us.shandian.giga.get.FinishedMission

@LayoutRes
internal fun localMediaItemLayout(itemViewMode: ItemViewMode): Int = when (itemViewMode) {
    ItemViewMode.GRID -> R.layout.list_stream_grid_item
    ItemViewMode.CARD -> R.layout.list_stream_card_item
    else -> R.layout.list_stream_item
}

class LocalMediaFragment : Fragment() {
    private companion object {
        const val ARG_OPEN_AUDIO_TRACKS = "open_audio_tracks"
        const val STATE_QUERY = "local_media_query"
        const val STATE_SEARCH_EXPANDED = "local_media_search_expanded"
        const val STATE_FILTER = "local_media_filter"

        @JvmStatic
        fun newAudioTracksInstance() = LocalMediaFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_OPEN_AUDIO_TRACKS, true) }
        }
    }

    private enum class Filter { ALL, AUDIO, VIDEO, BROWSE }
    private enum class Sort { TITLE, ARTIST, ALBUM, FOLDER, RECENT }

    private val disposables = CompositeDisposable()
    private var allItems = emptyList<LocalMediaItem>()
    private var shownItems = emptyList<LocalMediaItem>()
    private var filter = Filter.ALL
    private var sort = Sort.TITLE
    private var query = ""
    private var searchExpanded = false
    private var audioCategory = LocalMediaAudioCategory.TRACKS
    private var videoCategory = LocalMediaVideoCategory.VIDEOS
    private var activeGroup: LocalMediaGroup? = null
    private lateinit var adapter: LocalMediaAdapter
    private lateinit var groupAdapter: LocalMediaGroupAdapter
    private lateinit var documentAdapter: LocalMediaDocumentAdapter
    private lateinit var list: RecyclerView
    private lateinit var viewModel: LocalMediaViewModel
    private lateinit var browserViewModel: LocalMediaBrowserViewModel
    private var browserState = LocalMediaBrowserState()
    private var searchField: TextInputEditText? = null
    private val exclusionStore by lazy { LocalMediaExclusionStore(requireContext()) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { loadOrExplainPermission() }

    private val folderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.onSuccess {
            browserViewModel.addRoot(uri)
            selectFilter(Filter.BROWSE)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setHasOptionsMenu(true)
        query = state?.getString(STATE_QUERY).orEmpty()
        searchExpanded = state?.getBoolean(STATE_SEARCH_EXPANDED) ?: false
        filter = state?.getString(STATE_FILTER)
            ?.let { saved -> Filter.entries.firstOrNull { it.name == saved } }
            ?: if (arguments?.getBoolean(ARG_OPEN_AUDIO_TRACKS) == true) {
                Filter.AUDIO
            } else {
                Filter.ALL
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_local_media, container, false)
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        list = view.findViewById(R.id.localMediaList)
        viewModel = ViewModelProvider(this)[LocalMediaViewModel::class.java]
        browserViewModel = ViewModelProvider(this)[LocalMediaBrowserViewModel::class.java]
        adapter = LocalMediaAdapter(::play, ::showActions)
        groupAdapter = LocalMediaGroupAdapter(::openGroup)
        documentAdapter = LocalMediaDocumentAdapter(::openDocument, ::showDocumentActions)
        searchField = view.findViewById(R.id.localMediaSearch)
        list.adapter = adapter
        applyItemViewMode()

        view.findViewById<View>(R.id.localMediaGrantAccess).setOnClickListener {
            permissionLauncher.launch(requiredPermissions())
        }
        view.findViewById<Chip>(R.id.localMediaAll).setOnClickListener {
            selectFilter(Filter.ALL)
        }
        view.findViewById<Chip>(R.id.localMediaAudio).setOnClickListener {
            selectFilter(Filter.AUDIO)
        }
        view.findViewById<Chip>(R.id.localMediaVideo).setOnClickListener {
            selectFilter(Filter.VIDEO)
        }
        view.findViewById<Chip>(R.id.localMediaBrowse).setOnClickListener {
            selectFilter(Filter.BROWSE)
        }
        view.findViewById<Chip>(R.id.localMediaSort).setOnClickListener { showSortDialog() }
        view.findViewById<Chip>(R.id.localMediaAudioTracks).setOnClickListener {
            selectAudioCategory(LocalMediaAudioCategory.TRACKS)
        }
        view.findViewById<Chip>(R.id.localMediaAudioArtists).setOnClickListener {
            selectAudioCategory(LocalMediaAudioCategory.ARTISTS)
        }
        view.findViewById<Chip>(R.id.localMediaAudioAlbums).setOnClickListener {
            selectAudioCategory(LocalMediaAudioCategory.ALBUMS)
        }
        view.findViewById<Chip>(R.id.localMediaAudioGenres).setOnClickListener {
            selectAudioCategory(LocalMediaAudioCategory.GENRES)
        }
        view.findViewById<Chip>(R.id.localMediaAudioPlaylists).setOnClickListener {
            NavigationHelper.openBookmarksFragment(parentFragmentManager)
        }
        view.findViewById<Chip>(R.id.localMediaVideoVideos).setOnClickListener {
            selectVideoCategory(LocalMediaVideoCategory.VIDEOS)
        }
        view.findViewById<Chip>(R.id.localMediaVideoFolders).setOnClickListener {
            selectVideoCategory(LocalMediaVideoCategory.FOLDERS)
        }
        view.findViewById<Chip>(R.id.localMediaVideoPlaylists).setOnClickListener {
            NavigationHelper.openBookmarksFragment(parentFragmentManager)
        }
        view.findViewById<View>(R.id.localMediaGroupBack).setOnClickListener {
            activeGroup = null
            updateList()
        }
        view.findViewById<View>(R.id.localMediaGroupPlay).setOnClickListener {
            playActiveGroup(shuffle = false)
        }
        view.findViewById<View>(R.id.localMediaGroupShuffle).setOnClickListener {
            playActiveGroup(shuffle = true)
        }
        view.findViewById<View>(R.id.localMediaGroupMore).setOnClickListener {
            showActiveGroupActions()
        }
        view.findViewById<View>(R.id.localMediaBrowseBack).setOnClickListener {
            browserViewModel.goBack()
        }
        view.findViewById<View>(R.id.localMediaBrowseAdd).setOnClickListener {
            folderLauncher.launch(null)
        }
        view.findViewById<View>(R.id.localMediaBrowseMore).setOnClickListener {
            browserState.location?.let { showFolderActions(it, browserState.title, false) }
        }
        searchField?.apply {
            if (query.isNotEmpty()) setText(query)
            addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        query = s?.toString().orEmpty()
                        updateList()
                    }

                    override fun afterTextChanged(s: Editable?) = Unit
                }
            )
        }
        viewModel.state.observe(viewLifecycleOwner, ::renderState)
        browserViewModel.state.observe(viewLifecycleOwner, ::renderBrowserState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (filter == Filter.BROWSE && browserViewModel.goBack()) return
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        )
        view.findViewById<Chip>(filterChipId(filter)).isChecked = true
        selectFilter(filter)
        updateSearchVisibility()
        loadOrExplainPermission()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (view != null && ::adapter.isInitialized) {
            GridLayoutManagerHelper.preserveScrollPosition(list) { applyItemViewMode() }
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeHelper.setTitleToAppCompatActivity(activity, getString(R.string.local_media))
        if (::adapter.isInitialized) applyItemViewMode()
        if (::viewModel.isInitialized) viewModel.refresh()
        if (::browserViewModel.isInitialized && filter == Filter.BROWSE) {
            browserViewModel.refresh()
        }
        requireActivity().invalidateOptionsMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_QUERY, query)
        outState.putBoolean(STATE_SEARCH_EXPANDED, searchExpanded)
        outState.putString(STATE_FILTER, filter.name)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_local_media, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.menu_item_search_content)?.isVisible = filter != Filter.BROWSE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_search_content -> {
                toggleSearch()
                true
            }

            R.id.menu_item_local_media_refresh -> {
                refreshLocalMedia()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        searchField = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    private fun requiredPermissions(): Array<String> = LocalMediaPermissionPolicy
        .requiredPermissions()

    private fun loadOrExplainPermission() {
        val access = LocalMediaPermissionPolicy.access(requireContext())
        if (!access.hasAnyAccess) {
            showMessage(R.string.local_media_permission_description, true)
            return
        }
        view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
        viewModel.load(access)
    }

    private fun refreshLocalMedia() {
        if (!::viewModel.isInitialized || !::browserViewModel.isInitialized) return
        val access = LocalMediaPermissionPolicy.access(requireContext())
        if (!access.hasAnyAccess) {
            showMessage(R.string.local_media_permission_description, true)
            return
        }
        view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
        viewModel.load(access, force = true)
        if (filter == Filter.BROWSE) browserViewModel.refresh()
    }

    private fun toggleSearch() {
        if (filter == Filter.BROWSE) return
        searchExpanded = !searchExpanded
        if (!searchExpanded) searchField?.text?.clear()
        updateSearchVisibility(focus = searchExpanded)
    }

    private fun updateSearchVisibility(focus: Boolean = false) {
        val shouldShow = searchExpanded && filter != Filter.BROWSE
        view?.findViewById<View>(R.id.localMediaSearchLayout)?.visibility =
            if (shouldShow) View.VISIBLE else View.GONE

        val field = searchField ?: return
        if (shouldShow) {
            if (focus) {
                field.requestFocus()
                field.post {
                    val inputMethodManager = requireContext().getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as InputMethodManager
                    inputMethodManager.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        } else {
            field.clearFocus()
            val inputMethodManager = requireContext().getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(field.windowToken, 0)
        }
    }

    private fun renderState(state: LocalMediaLibraryState) {
        if (!isAdded || view == null) return
        view?.findViewById<View>(R.id.localMediaProgress)?.visibility =
            if (state.isLoading) View.VISIBLE else View.GONE
        allItems = state.library.allItems
        if (!state.isLoading && filter != Filter.BROWSE) updateList()
    }

    private fun selectFilter(selected: Filter) {
        filter = selected
        activeGroup = null
        view?.findViewById<View>(R.id.localMediaAudioNavigation)?.visibility =
            if (selected == Filter.AUDIO) View.VISIBLE else View.GONE
        view?.findViewById<View>(R.id.localMediaVideoNavigation)?.visibility =
            if (selected == Filter.VIDEO) View.VISIBLE else View.GONE
        view?.findViewById<View>(R.id.localMediaBrowseNavigation)?.visibility =
            if (selected == Filter.BROWSE) View.VISIBLE else View.GONE
        updateSearchVisibility()
        view?.findViewById<View>(R.id.localMediaSort)?.visibility =
            if (selected == Filter.BROWSE) View.GONE else View.VISIBLE
        view?.findViewById<View>(R.id.localMediaGroupHeader)?.visibility = View.GONE
        requireActivity().invalidateOptionsMenu()
        if (selected == Filter.BROWSE) renderBrowserState(browserState) else updateList()
    }

    private fun filterChipId(selected: Filter): Int = when (selected) {
        Filter.ALL -> R.id.localMediaAll
        Filter.AUDIO -> R.id.localMediaAudio
        Filter.VIDEO -> R.id.localMediaVideo
        Filter.BROWSE -> R.id.localMediaBrowse
    }

    private fun selectAudioCategory(selected: LocalMediaAudioCategory) {
        audioCategory = selected
        activeGroup = null
        updateList()
    }

    private fun selectVideoCategory(selected: LocalMediaVideoCategory) {
        videoCategory = selected
        activeGroup = null
        updateList()
    }

    private fun openGroup(group: LocalMediaGroup) {
        activeGroup = group
        updateList()
    }

    private fun updateList() {
        if (!::adapter.isInitialized || filter == Filter.BROWSE) return
        val term = query.trim().lowercase(Locale.getDefault())
        if (
            filter == Filter.ALL &&
            groupAllMediaByFolder() &&
            activeGroup == null
        ) {
            showAllMediaGroups(term)
            return
        }
        if (
            filter == Filter.AUDIO &&
            audioCategory != LocalMediaAudioCategory.TRACKS &&
            activeGroup == null
        ) {
            showAudioGroups(term)
            return
        }
        if (
            filter == Filter.VIDEO &&
            videoCategory == LocalMediaVideoCategory.FOLDERS &&
            activeGroup == null
        ) {
            showVideoGroups(term)
            return
        }
        val comparator: Comparator<LocalMediaItem> = if (
            activeGroup?.kind == LocalMediaGroupKind.ALBUM
        ) {
            compareBy(
                LocalMediaItem::discNumber,
                LocalMediaItem::trackNumber,
                LocalMediaItem::title
            )
        } else {
            when (sort) {
                Sort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::title)
                Sort.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::artist)
                Sort.ALBUM -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::album)
                Sort.FOLDER -> compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::folder)
                Sort.RECENT -> compareByDescending(LocalMediaItem::addedAtSeconds)
            }
        }
        val sourceItems = activeGroup?.items ?: allItems
        shownItems = sourceItems.asSequence()
            .filter {
                filter == Filter.ALL ||
                    filter == Filter.VIDEO && it.isVideo ||
                    filter == Filter.AUDIO && !it.isVideo
            }
            .filter {
                term.isEmpty() || listOf(it.title, it.artist, it.album, it.folder).any { value ->
                    value.lowercase(Locale.getDefault()).contains(term)
                }
            }
            .sortedWith(comparator)
            .toList()
        list.adapter = adapter
        applyItemViewMode()
        renderGroupHeader()
        adapter.submit(shownItems)
        if (shownItems.isEmpty()) {
            showMessage(R.string.local_media_empty, false)
        } else {
            view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
        }
    }

    private fun groupAllMediaByFolder(): Boolean = PreferenceManager
        .getDefaultSharedPreferences(requireContext())
        .getBoolean(
            getString(R.string.local_media_group_all_by_folder_key),
            false
        )

    private fun showAllMediaGroups(term: String) {
        val groups = LocalMediaVideoIndex.folders(
            allItems,
            getString(R.string.local_media_unknown_folder),
            LocalMediaGroupKind.MEDIA_FOLDER
        ).filter { group ->
            term.isEmpty() || listOf(group.title, group.subtitle).any { value ->
                value.lowercase(Locale.getDefault()).contains(term)
            }
        }
        renderGroups(groups)
    }

    private fun showAudioGroups(term: String) {
        val groups = LocalMediaAudioIndex.groups(
            items = allItems.filter(LocalMediaItem::isAudio),
            category = audioCategory,
            unknownArtist = getString(R.string.local_media_unknown_artist),
            unknownAlbum = getString(R.string.local_media_unknown_album),
            unknownGenre = getString(R.string.local_media_unknown_genre)
        ).filter { group ->
            term.isEmpty() || listOf(group.title, group.subtitle).any { value ->
                value.lowercase(Locale.getDefault()).contains(term)
            }
        }
        renderGroups(groups)
    }

    private fun showVideoGroups(term: String) {
        val groups = LocalMediaVideoIndex.folders(
            allItems.filter(LocalMediaItem::isVideo),
            getString(R.string.local_media_unknown_folder)
        ).filter { group ->
            term.isEmpty() || listOf(group.title, group.subtitle).any { value ->
                value.lowercase(Locale.getDefault()).contains(term)
            }
        }
        renderGroups(groups)
    }

    private fun renderBrowserState(state: LocalMediaBrowserState) {
        browserState = state
        if (!::documentAdapter.isInitialized || filter != Filter.BROWSE || view == null) return
        view?.findViewById<View>(R.id.localMediaProgress)?.visibility =
            if (state.isLoading) View.VISIBLE else View.GONE
        view?.findViewById<View>(R.id.localMediaBrowseBack)?.visibility =
            if (state.location == null) View.GONE else View.VISIBLE
        view?.findViewById<View>(R.id.localMediaBrowseMore)?.visibility =
            if (state.location == null) View.GONE else View.VISIBLE
        view?.findViewById<TextView>(R.id.localMediaBrowseTitle)?.text = state.title.ifBlank {
            getString(R.string.local_media_storage_locations)
        }
        list.adapter = documentAdapter
        list.layoutManager = LinearLayoutManager(requireContext())
        documentAdapter.submit(state.entries)
        if (state.isLoading) {
            view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
        } else {
            when {
                state.isUnavailable -> showMessage(R.string.local_media_folder_unavailable, false)

                state.entries.isNotEmpty() -> {
                    view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
                }

                state.location == null -> showAddFolderMessage()

                else -> showMessage(R.string.local_media_folder_empty, false)
            }
        }
    }

    private fun openDocument(entry: LocalMediaDocumentEntry) {
        if (entry.isDirectory) {
            browserViewModel.open(entry)
            return
        }
        resolveDocumentMediaItem(entry) { item ->
            item ?: return@resolveDocumentMediaItem
            val queue = LocalMediaPlayQueue(listOf(item.toPlayQueueItem()), 0)
            NavigationHelper.playOnMainPlayer(requireActivity() as AppCompatActivity, queue)
        }
    }

    private fun showDocumentActions(entry: LocalMediaDocumentEntry) {
        if (entry.isDirectory) {
            showFolderActions(entry.location, entry.name, entry.isRoot)
        } else {
            resolveDocumentMediaItem(entry) { item -> item?.let(::showActions) }
        }
    }

    private fun resolveDocumentMediaItem(
        entry: LocalMediaDocumentEntry,
        onReady: (LocalMediaItem?) -> Unit
    ) {
        view?.findViewById<View>(R.id.localMediaProgress)?.visibility = View.VISIBLE
        browserViewModel.resolveMediaItem(entry) { item ->
            if (!isAdded || view == null) return@resolveMediaItem
            view?.findViewById<View>(R.id.localMediaProgress)?.visibility = View.GONE
            onReady(item)
        }
    }

    private fun showFolderActions(
        location: LocalMediaDocumentLocation,
        title: String,
        allowRemoval: Boolean
    ) {
        val actions = mutableListOf(
            R.string.local_media_play_all,
            R.string.local_media_shuffle_all,
            R.string.local_media_play_background,
            R.string.enqueue,
            R.string.local_media_enqueue_next,
            R.string.add_to_playlist
        )
        if (location.path.isNotEmpty()) {
            actions += R.string.local_media_ignore_folder
        }
        if (allowRemoval || location.path.isEmpty()) {
            actions += R.string.local_media_remove_folder
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(actions.map(::getString).toTypedArray()) { _, which ->
                when (actions[which]) {
                    R.string.local_media_remove_folder -> {
                        browserViewModel.removeRoot(location.rootUri)
                    }

                    R.string.local_media_ignore_folder -> {
                        exclusionStore.ignoreDocumentLocation(location)
                        if (browserState.location == location) {
                            browserViewModel.goBack()
                        } else {
                            browserViewModel.refresh()
                        }
                        showIgnoredConfirmation()
                    }

                    else -> collectFolderForAction(location, title, actions[which])
                }
            }.show()
    }

    private fun collectFolderForAction(
        location: LocalMediaDocumentLocation,
        title: String,
        action: Int
    ) {
        view?.findViewById<View>(R.id.localMediaProgress)?.visibility = View.VISIBLE
        browserViewModel.collect(location) { items ->
            if (!isAdded || view == null) return@collect
            view?.findViewById<View>(R.id.localMediaProgress)?.visibility = View.GONE
            if (items.isEmpty()) {
                showMessage(R.string.local_media_folder_empty, false)
                return@collect
            }
            val group = LocalMediaGroup(
                stableKey = "document:${location.rootUri}:${location.path.joinToString("/")}",
                title = title,
                subtitle = "",
                items = items,
                thumbnailUri = items.firstNotNullOfOrNull(LocalMediaItem::thumbnailUri),
                kind = LocalMediaGroupKind.VIDEO_FOLDER
            )
            val queue = LocalMediaGroupQueueBuilder.queue(
                group,
                shuffle = action == R.string.local_media_shuffle_all
            )
            when (action) {
                R.string.local_media_play_all,
                R.string.local_media_shuffle_all -> NavigationHelper.playOnMainPlayer(
                    requireActivity() as AppCompatActivity,
                    queue
                )

                R.string.local_media_play_background -> {
                    NavigationHelper.playOnBackgroundPlayer(requireContext(), queue, true)
                }

                R.string.enqueue -> NavigationHelper.enqueueOnPlayer(requireContext(), queue)

                R.string.local_media_enqueue_next -> {
                    NavigationHelper.enqueueNextOnPlayer(requireContext(), queue)
                }

                R.string.add_to_playlist -> addGroupToPlaylist(group)
            }
        }
    }

    private fun renderGroups(groups: List<LocalMediaGroup>) {
        list.adapter = groupAdapter
        list.layoutManager = LinearLayoutManager(requireContext())
        view?.findViewById<View>(R.id.localMediaGroupHeader)?.visibility = View.GONE
        groupAdapter.submit(groups)
        if (groups.isEmpty()) {
            showMessage(R.string.local_media_empty, false)
        } else {
            view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.GONE
        }
    }

    private fun renderGroupHeader() {
        val group = activeGroup
        view?.findViewById<View>(R.id.localMediaGroupHeader)?.visibility =
            if (group == null) View.GONE else View.VISIBLE
        view?.findViewById<TextView>(R.id.localMediaGroupTitle)?.text = group?.title
    }

    private fun applyItemViewMode() {
        val mode = ThemeHelper.getItemViewMode(requireContext())
        list.adapter = adapter
        list.layoutManager = if (mode == ItemViewMode.GRID) {
            val minimumItemWidth = resources.getDimensionPixelSize(
                R.dimen.video_item_grid_thumbnail_image_width
            ) + resources.getDimensionPixelSize(R.dimen.video_item_search_padding) * 2
            GridLayoutManagerHelper.create(
                list,
                minimumItemWidth,
                GridLayoutManagerHelper.getPreferredSpanCount(requireContext()),
                null
            )
        } else {
            LinearLayoutManager(requireContext())
        }
        adapter.setItemViewMode(mode)
    }

    private fun showMessage(message: Int, showButton: Boolean) {
        view?.findViewById<TextView>(R.id.localMediaMessage)?.setText(message)
        view?.findViewById<View>(R.id.localMediaGrantAccess)?.apply {
            visibility = if (showButton) View.VISIBLE else View.GONE
            if (showButton) {
                (this as TextView).setText(R.string.local_media_grant_access)
                setOnClickListener { permissionLauncher.launch(requiredPermissions()) }
            }
        }
        view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.localMediaProgress)?.visibility = View.GONE
    }

    private fun showAddFolderMessage() {
        view?.findViewById<TextView>(R.id.localMediaMessage)?.setText(
            R.string.local_media_browse_folders
        )
        view?.findViewById<View>(R.id.localMediaGrantAccess)?.apply {
            visibility = View.VISIBLE
            (this as TextView).setText(R.string.local_media_add_folder)
            setOnClickListener { folderLauncher.launch(null) }
        }
        view?.findViewById<View>(R.id.localMediaMessagePanel)?.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.localMediaProgress)?.visibility = View.GONE
    }

    private fun play(item: LocalMediaItem) {
        val index = shownItems.indexOf(item).coerceAtLeast(0)
        val queue = LocalMediaPlayQueue(shownItems.map(LocalMediaItem::toPlayQueueItem), index)
        NavigationHelper.playOnMainPlayer(requireActivity() as AppCompatActivity, queue)
    }

    private fun playActiveGroup(shuffle: Boolean) {
        val group = activeGroup ?: return
        val queue = LocalMediaGroupQueueBuilder.queue(group, shuffle)
        NavigationHelper.playOnMainPlayer(requireActivity() as AppCompatActivity, queue)
    }

    private fun showActiveGroupActions() {
        val group = activeGroup ?: return
        val actions = mutableListOf(
            R.string.local_media_play_background,
            R.string.enqueue,
            R.string.local_media_enqueue_next,
            R.string.add_to_playlist
        )
        if (
            group.kind == LocalMediaGroupKind.VIDEO_FOLDER ||
            group.kind == LocalMediaGroupKind.MEDIA_FOLDER
        ) {
            actions += R.string.local_media_ignore_folder
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(group.title)
            .setItems(actions.map(::getString).toTypedArray()) { _, which ->
                when (actions[which]) {
                    R.string.local_media_ignore_folder -> {
                        group.items.firstOrNull()?.let(exclusionStore::ignoreMediaFolder)
                        activeGroup = null
                        viewModel.refresh()
                        showIgnoredConfirmation()
                    }

                    else -> {
                        val queue = LocalMediaGroupQueueBuilder.queue(group, shuffle = false)
                        when (actions[which]) {
                            R.string.local_media_play_background -> {
                                NavigationHelper.playOnBackgroundPlayer(
                                    requireContext(),
                                    queue,
                                    true
                                )
                            }

                            R.string.enqueue -> {
                                NavigationHelper.enqueueOnPlayer(requireContext(), queue)
                            }

                            R.string.local_media_enqueue_next -> {
                                NavigationHelper.enqueueNextOnPlayer(requireContext(), queue)
                            }

                            R.string.add_to_playlist -> addGroupToPlaylist(group)
                        }
                    }
                }
            }.show()
    }

    private fun addGroupToPlaylist(group: LocalMediaGroup) {
        disposables.add(
            PlaylistDialog.createCorrespondingDialog(
                requireContext(),
                group.items.map { StreamEntity(it.toPlayQueueItem()) }
            ) { dialog ->
                dialog.show(parentFragmentManager, "LocalMediaGroupPlaylist")
            }
        )
    }

    private fun showActions(item: LocalMediaItem) {
        val context = requireContext().applicationContext
        disposables.add(
            Single.fromCallable { Optional.ofNullable(DownloadPlayback.find(context, item.contentUri)) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { download -> if (view != null && isAdded) showActions(item, download.orElse(null)) },
                    { if (view != null && isAdded) showActions(item, null) }
                )
        )
    }

    private fun showActions(item: LocalMediaItem, download: FinishedMission?) {
        val context = requireContext()
        val queueItem = item.toPlayQueueItem()
        val learning = LearningContentManager.getInstance(context)
        val markedAsLearning = learning.isStreamSourceMarked(queueItem.serviceId, queueItem.url)
        val actions = mutableListOf(
            R.string.play,
            R.string.local_media_play_background,
            R.string.enqueue,
            R.string.local_media_enqueue_next,
            R.string.add_to_playlist,
            R.string.copy_video_title,
            R.string.local_media_ignore_item
        )
        if (item.isVideo) {
            actions.add(2, R.string.open_in_popup_mode)
        }
        if (!IncognitoMode.isEnabled(context)) {
            actions += R.string.mark_as_watched
            actions += R.string.mark_as_unwatched
            actions += if (markedAsLearning) R.string.learning_remove_content else R.string.learning_mark_content
        }
        if (download != null) {
            actions += R.string.local_media_open_in_downloads
        }
        if (item.mediaStoreId >= 0 && item.relativePath.isNotBlank()) {
            actions += R.string.local_media_ignore_folder
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.title)
            .setItems(actions.map(::getString).toTypedArray()) { _, which ->
                val queue = LocalMediaPlayQueue(listOf(queueItem), 0)
                when (actions[which]) {
                    R.string.play -> play(item)

                    R.string.open_in_popup_mode -> NavigationHelper.playOnPopupPlayer(context, queue, true)

                    R.string.copy_video_title -> ShareUtils.copyToClipboard(context, item.title)

                    R.string.mark_as_watched -> updateLocalMediaState(
                        HistoryRecordManager(context).markAsWatched(queueItem).ignoreElement()
                    )

                    R.string.mark_as_unwatched -> updateLocalMediaState(
                        HistoryRecordManager(context).markLocalMediaAsUnwatched(queueItem)
                    )

                    R.string.learning_mark_content, R.string.learning_remove_content -> updateLocalMediaState(
                        learning.setStreamMarked(StreamEntity(queueItem), !markedAsLearning)
                    )

                    R.string.local_media_open_in_downloads -> {
                        val name = download?.displayName?.takeIf { it.isNotBlank() }
                            ?: download?.title?.takeIf { it.isNotBlank() }
                            ?: item.title
                        startActivity(
                            Intent(context, DownloadActivity::class.java)
                                .putExtra(DownloadActivity.EXTRA_DOWNLOAD_SEARCH, name)
                        )
                    }

                    R.string.local_media_play_background -> {
                        NavigationHelper.playOnBackgroundPlayer(requireContext(), queue, true)
                    }

                    R.string.enqueue -> NavigationHelper.enqueueOnPlayer(requireContext(), queue)

                    R.string.local_media_enqueue_next -> {
                        NavigationHelper.enqueueNextOnPlayer(requireContext(), queue)
                    }

                    R.string.add_to_playlist -> disposables.add(
                        PlaylistDialog.createCorrespondingDialog(
                            requireContext(),
                            listOf(StreamEntity(item.toPlayQueueItem()))
                        ) { dialog ->
                            dialog.show(parentFragmentManager, "LocalMediaPlaylist")
                        }
                    )

                    R.string.local_media_ignore_item -> {
                        exclusionStore.ignoreItem(item)
                        viewModel.refresh()
                        browserViewModel.refresh()
                        showIgnoredConfirmation()
                    }

                    R.string.local_media_ignore_folder -> {
                        exclusionStore.ignoreMediaFolder(item)
                        activeGroup = null
                        viewModel.refresh()
                        showIgnoredConfirmation()
                    }
                }
            }.show()
    }

    private fun updateLocalMediaState(action: Completable) {
        val context = requireContext()
        disposables.add(
            action.observeOn(AndroidSchedulers.mainThread())
                .subscribe({}, { Toast.makeText(context, R.string.general_error, Toast.LENGTH_SHORT).show() })
        )
    }

    private fun showIgnoredConfirmation() {
        Toast.makeText(
            requireContext(),
            R.string.local_media_ignored_confirmation,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.local_media_sort_title),
            getString(R.string.local_media_sort_artist),
            getString(R.string.local_media_sort_album),
            getString(R.string.local_media_sort_folder),
            getString(R.string.local_media_sort_recent)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.local_media_sort)
            .setSingleChoiceItems(options, sort.ordinal) { dialog, which ->
                sort = Sort.values()[which]
                view?.findViewById<Chip>(R.id.localMediaSort)?.text = options[which]
                updateList()
                dialog.dismiss()
            }.show()
    }
}

private class LocalMediaGroupAdapter(
    private val onClick: (LocalMediaGroup) -> Unit
) : RecyclerView.Adapter<LocalMediaGroupAdapter.Holder>() {
    private var items = emptyList<LocalMediaGroup>()

    fun submit(updated: List<LocalMediaGroup>) {
        items = updated
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.list_local_media_group_item,
            parent,
            false
        )
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun onViewRecycled(holder: Holder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon = view.findViewById<ImageView>(R.id.localMediaGroupIcon)
        private val title = view.findViewById<TextView>(R.id.localMediaGroupItemTitle)
        private val subtitle = view.findViewById<TextView>(R.id.localMediaGroupItemSubtitle)

        fun bind(group: LocalMediaGroup) {
            val countPlural = when (group.kind) {
                LocalMediaGroupKind.VIDEO_FOLDER -> R.plurals.videos
                LocalMediaGroupKind.MEDIA_FOLDER -> R.plurals.local_media_item_count
                else -> R.plurals.local_media_track_count
            }
            val count = itemView.resources.getQuantityString(
                countPlural,
                group.items.size,
                group.items.size
            )
            group.items.firstOrNull()?.let { LocalMediaThumbnailLoader.load(icon, it) }
                ?: icon.setImageResource(R.drawable.ic_music_note)
            title.text = group.title
            subtitle.text = listOf(group.subtitle, count)
                .filter(String::isNotBlank)
                .joinToString(" • ")
            itemView.setOnClickListener { onClick(group) }
        }

        fun recycle() {
            LocalMediaThumbnailLoader.clear(icon)
        }
    }
}

private class LocalMediaAdapter(
    private val onClick: (LocalMediaItem) -> Unit,
    private val onLongClick: (LocalMediaItem) -> Unit
) : RecyclerView.Adapter<LocalMediaAdapter.Holder>() {
    private var items = emptyList<LocalMediaItem>()
    private var itemViewMode = ItemViewMode.LIST

    fun setItemViewMode(updated: ItemViewMode) {
        if (itemViewMode == updated) return
        itemViewMode = updated
        notifyDataSetChanged()
    }

    fun submit(updated: List<LocalMediaItem>) {
        items = updated
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = itemViewMode.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = localMediaItemLayout(ItemViewMode.values()[viewType])
        return Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    override fun onViewRecycled(holder: Holder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail = view.findViewById<ImageView>(R.id.itemThumbnailView)
        private val duration = view.findViewById<TextView>(R.id.itemDurationView)
        private val title = view.findViewById<TextView>(R.id.itemVideoTitleView)
        private val uploader = view.findViewById<TextView>(R.id.itemUploaderView)
        private val details = view.findViewById<TextView>(R.id.itemAdditionalDetails)

        fun bind(item: LocalMediaItem) {
            title.text = item.title
            uploader.text = item.artist.ifBlank { item.album.ifBlank { item.folder } }
            details.setText(R.string.local_media_on_device)
            duration.text = Localization.getDurationString(item.durationSeconds)
            duration.visibility = if (item.durationSeconds > 0) View.VISIBLE else View.GONE
            itemView.findViewById<View>(R.id.itemUploaderAvatarView).visibility = View.GONE
            itemView.findViewById<View>(R.id.itemMembersOnlyView).visibility = View.GONE
            itemView.findViewById<View>(R.id.itemProgressView).visibility = View.GONE
            LocalMediaThumbnailLoader.load(thumbnail, item)
            itemView.setOnClickListener { onClick(item) }
            itemView.findViewById<View>(R.id.itemUploaderRoot).apply {
                setOnClickListener { onClick(item) }
                setOnLongClickListener {
                    onLongClick(item)
                    true
                }
            }
            itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }

        fun recycle() {
            LocalMediaThumbnailLoader.clear(thumbnail)
        }
    }
}
