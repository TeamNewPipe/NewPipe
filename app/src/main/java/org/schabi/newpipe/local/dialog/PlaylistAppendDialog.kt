package org.schabi.newpipe.local.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.local.LocalItemListAdapter
import org.schabi.newpipe.local.feed.FeedFragment.Companion.newInstance
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.util.OnClickGesture
import java.util.function.Predicate

class PlaylistAppendDialog() : PlaylistDialog() {
    private var playlistRecyclerView: RecyclerView? = null
    private var playlistAdapter: LocalItemListAdapter? = null
    private var playlistDuplicateIndicator: TextView? = null
    private val playlistDisposables: CompositeDisposable = CompositeDisposable()

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Creation
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_playlists, container)
    }

    public override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val playlistManager: LocalPlaylistManager = LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()))
        playlistAdapter = LocalItemListAdapter(getActivity())
        playlistAdapter!!.setSelectedListener(OnClickGesture({ selectedItem: LocalItem? ->
            val entities: List<StreamEntity?>? = getStreamEntities()
            if (selectedItem is PlaylistDuplicatesEntry && entities != null) {
                onPlaylistSelected(playlistManager,
                        selectedItem, entities)
            }
        }))
        playlistRecyclerView = view.findViewById(R.id.playlist_list)
        playlistRecyclerView.setLayoutManager(LinearLayoutManager(requireContext()))
        playlistRecyclerView.setAdapter(playlistAdapter)
        playlistDuplicateIndicator = view.findViewById(R.id.playlist_duplicate)
        val newPlaylistButton: View = view.findViewById(R.id.newPlaylist)
        newPlaylistButton.setOnClickListener(View.OnClickListener({ ignored: View? -> openCreatePlaylistDialog() }))
        playlistDisposables.add(playlistManager
                .getPlaylistDuplicates(getStreamEntities().get(0).url)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer<List<PlaylistDuplicatesEntry?>?>({ playlists: List<PlaylistDuplicatesEntry?> -> onPlaylistsReceived(playlists) })))
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Destruction
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onDestroyView() {
        super.onDestroyView()
        playlistDisposables.dispose()
        if (playlistAdapter != null) {
            playlistAdapter!!.unsetSelectedListener()
        }
        playlistDisposables.clear()
        playlistRecyclerView = null
        playlistAdapter = null
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    ////////////////////////////////////////////////////////////////////////// */
    /** Display create playlist dialog.  */
    fun openCreatePlaylistDialog() {
        if (getStreamEntities() == null || !isAdded()) {
            return
        }
        val playlistCreationDialog: PlaylistCreationDialog = PlaylistCreationDialog.Companion.newInstance(getStreamEntities())
        // Move the dismissListener to the new dialog.
        playlistCreationDialog.setOnDismissListener(getOnDismissListener())
        setOnDismissListener(null)
        playlistCreationDialog.show(getParentFragmentManager(), TAG)
        requireDialog().dismiss()
    }

    private fun onPlaylistsReceived(playlists: List<PlaylistDuplicatesEntry?>) {
        if ((playlistAdapter != null
                        ) && (playlistRecyclerView != null
                        ) && (playlistDuplicateIndicator != null)) {
            playlistAdapter!!.clearStreamItemList()
            playlistAdapter!!.addItems(playlists)
            playlistRecyclerView!!.setVisibility(View.VISIBLE)
            playlistDuplicateIndicator!!.setVisibility(
                    if (anyPlaylistContainsDuplicates(playlists)) View.VISIBLE else View.GONE)
        }
    }

    private fun anyPlaylistContainsDuplicates(playlists: List<PlaylistDuplicatesEntry?>): Boolean {
        return playlists.stream()
                .anyMatch(Predicate({ playlist: PlaylistDuplicatesEntry? -> playlist!!.timesStreamIsContained > 0 }))
    }

    private fun onPlaylistSelected(manager: LocalPlaylistManager,
                                   playlist: PlaylistDuplicatesEntry,
                                   streams: List<StreamEntity?>) {
        val toastText: String
        if (playlist.timesStreamIsContained > 0) {
            toastText = getString(R.string.playlist_add_stream_success_duplicate,
                    playlist.timesStreamIsContained)
        } else {
            toastText = getString(R.string.playlist_add_stream_success)
        }
        val successToast: Toast = Toast.makeText(getContext(), toastText, Toast.LENGTH_SHORT)
        playlistDisposables.add(manager.appendToPlaylist(playlist.getUid(), streams)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer<List<Long?>?>({ ignored: List<Long?>? ->
                    successToast.show()
                    if ((playlist.thumbnailUrl == PlaylistEntity.Companion.DEFAULT_THUMBNAIL)) {
                        playlistDisposables.add(manager
                                .changePlaylistThumbnail(playlist.getUid(), streams.get(0)!!.uid,
                                        false)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(Consumer<Int?>({ ignore: Int? -> successToast.show() })))
                    }
                })))
        requireDialog().dismiss()
    }

    companion object {
        private val TAG: String = PlaylistAppendDialog::class.java.getCanonicalName()

        /**
         * Create a new instance of [PlaylistAppendDialog].
         *
         * @param streamEntities    a list of [StreamEntity] to be added to playlists
         * @return a new instance of [PlaylistAppendDialog]
         */
        fun newInstance(streamEntities: List<StreamEntity?>?): PlaylistAppendDialog {
            val dialog: PlaylistAppendDialog = PlaylistAppendDialog()
            dialog.setStreamEntities(streamEntities)
            return dialog
        }
    }
}
