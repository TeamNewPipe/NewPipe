package org.schabi.newpipe.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.bookmark.MergedPlaylistManager
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.settings.SelectPlaylistFragment.SelectPlaylistAdapter.SelectPlaylistItemHolder
import org.schabi.newpipe.util.image.PicassoHelper
import java.util.Vector

class SelectPlaylistFragment() : DialogFragment() {
    private var onSelectedListener: OnSelectedListener? = null
    private var progressBar: ProgressBar? = null
    private var emptyView: TextView? = null
    private var recyclerView: RecyclerView? = null
    private var disposable: Disposable? = null
    private var playlists: List<PlaylistLocalItem?>? = Vector()
    fun setOnSelectedListener(listener: OnSelectedListener?) {
        onSelectedListener = listener
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        val v: View = inflater.inflate(R.layout.select_playlist_fragment, container, false)
        progressBar = v.findViewById(R.id.progressBar)
        recyclerView = v.findViewById(R.id.items_list)
        emptyView = v.findViewById(R.id.empty_state_view)
        recyclerView.setLayoutManager(LinearLayoutManager(getContext()))
        val playlistAdapter: SelectPlaylistAdapter = SelectPlaylistAdapter()
        recyclerView.setAdapter(playlistAdapter)
        loadPlaylists()
        return v
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and display playlists
    ////////////////////////////////////////////////////////////////////////// */
    private fun loadPlaylists() {
        progressBar!!.setVisibility(View.VISIBLE)
        recyclerView!!.setVisibility(View.GONE)
        emptyView!!.setVisibility(View.GONE)
        val database: AppDatabase = NewPipeDatabase.getInstance(requireContext())
        val localPlaylistManager: LocalPlaylistManager = LocalPlaylistManager(database)
        val remotePlaylistManager: RemotePlaylistManager = RemotePlaylistManager(database)
        disposable = MergedPlaylistManager.getMergedOrderedPlaylists(localPlaylistManager, remotePlaylistManager)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer<List<PlaylistLocalItem?>?>({ newPlaylists: List<PlaylistLocalItem?>? -> displayPlaylists(newPlaylists) }), Consumer({ e: Throwable? -> onError(e) }))
    }

    private fun displayPlaylists(newPlaylists: List<PlaylistLocalItem?>?) {
        playlists = newPlaylists
        progressBar!!.setVisibility(View.GONE)
        emptyView!!.setVisibility(if (newPlaylists!!.isEmpty()) View.VISIBLE else View.GONE)
        recyclerView!!.setVisibility(if (newPlaylists.isEmpty()) View.GONE else View.VISIBLE)
    }

    protected fun onError(e: Throwable?) {
        showSnackbar(requireActivity(), ErrorInfo((e)!!,
                UserAction.UI_ERROR, "Loading playlists"))
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    ////////////////////////////////////////////////////////////////////////// */
    private fun clickedItem(position: Int) {
        if (onSelectedListener != null) {
            val selectedItem: LocalItem? = playlists!!.get(position)
            if (selectedItem is PlaylistMetadataEntry) {
                val entry: PlaylistMetadataEntry = selectedItem
                onSelectedListener!!.onLocalPlaylistSelected(entry.getUid(), entry.name)
            } else if (selectedItem is PlaylistRemoteEntity) {
                val entry: PlaylistRemoteEntity = selectedItem
                onSelectedListener!!.onRemotePlaylistSelected(
                        entry.getServiceId(), entry.getUrl(), entry.getName())
            }
        }
        dismiss()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    ////////////////////////////////////////////////////////////////////////// */
    open interface OnSelectedListener {
        fun onLocalPlaylistSelected(id: Long, name: String?)
        fun onRemotePlaylistSelected(serviceId: Int, url: String?, name: String?)
    }

    private inner class SelectPlaylistAdapter() : RecyclerView.Adapter<SelectPlaylistItemHolder>() {
        public override fun onCreateViewHolder(parent: ViewGroup,
                                               viewType: Int): SelectPlaylistItemHolder {
            val item: View = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_playlist_mini_item, parent, false)
            return SelectPlaylistItemHolder(item)
        }

        public override fun onBindViewHolder(holder: SelectPlaylistItemHolder,
                                             position: Int) {
            val selectedItem: PlaylistLocalItem? = playlists!!.get(position)
            if (selectedItem is PlaylistMetadataEntry) {
                val entry: PlaylistMetadataEntry = selectedItem
                holder.titleView.setText(entry.name)
                holder.view.setOnClickListener(View.OnClickListener({ view: View? -> clickedItem(position) }))
                PicassoHelper.loadPlaylistThumbnail(entry.thumbnailUrl).into(holder.thumbnailView)
            } else if (selectedItem is PlaylistRemoteEntity) {
                val entry: PlaylistRemoteEntity = selectedItem
                holder.titleView.setText(entry.getName())
                holder.view.setOnClickListener(View.OnClickListener({ view: View? -> clickedItem(position) }))
                PicassoHelper.loadPlaylistThumbnail(entry.getThumbnailUrl())
                        .into(holder.thumbnailView)
            }
        }

        public override fun getItemCount(): Int {
            return playlists!!.size
        }

        inner class SelectPlaylistItemHolder internal constructor(val view: View) : RecyclerView.ViewHolder(v) {
            val thumbnailView: ImageView
            val titleView: TextView

            init {
                thumbnailView = v.findViewById<ImageView>(R.id.itemThumbnailView)
                titleView = v.findViewById<TextView>(R.id.itemTitleView)
            }
        }
    }
}
