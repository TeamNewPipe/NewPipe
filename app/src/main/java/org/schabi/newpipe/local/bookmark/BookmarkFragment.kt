package org.schabi.newpipe.local.bookmark

import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.util.Log
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import icepick.State
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Consumer
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.LocalItem.LocalItemType
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.databinding.DialogEditTextBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.BaseLocalListFragment
import org.schabi.newpipe.local.holder.LocalBookmarkPlaylistItemHolder
import org.schabi.newpipe.local.holder.RemoteBookmarkPlaylistItemHolder
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.debounce.DebounceSavable
import org.schabi.newpipe.util.debounce.DebounceSaver
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

class BookmarkFragment() : BaseLocalListFragment<List<PlaylistLocalItem?>?, Void?>(), DebounceSavable {
    @State
    var itemsListState: Parcelable? = null
    private var databaseSubscription: Subscription? = null
    private var disposables: CompositeDisposable? = CompositeDisposable()
    private var localPlaylistManager: LocalPlaylistManager? = null
    private var remotePlaylistManager: RemotePlaylistManager? = null
    private var itemTouchHelper: ItemTouchHelper? = null

    /* Have the bookmarked playlists been fully loaded from db */
    private var isLoadingComplete: AtomicBoolean? = null

    /* Gives enough time to avoid interrupting user sorting operations */
    private var debounceSaver: DebounceSaver? = null
    private var deletedItems: MutableList<Pair<Long, LocalItemType>>? = null

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (activity == null) {
            return
        }
        val database: AppDatabase = NewPipeDatabase.getInstance(activity!!)
        localPlaylistManager = LocalPlaylistManager(database)
        remotePlaylistManager = RemotePlaylistManager(database)
        disposables = CompositeDisposable()
        isLoadingComplete = AtomicBoolean()
        debounceSaver = DebounceSaver(3000, this)
        deletedItems = ArrayList()
    }

    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        if (!useAsFrontPage) {
            setTitle(activity!!.getString(R.string.tab_bookmarks))
        }
        return inflater.inflate(R.layout.fragment_bookmarks, container, false)
    }

    public override fun onResume() {
        super.onResume()
        if (activity != null) {
            setTitle(activity!!.getString(R.string.tab_bookmarks))
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////
    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        itemListAdapter!!.setUseItemHandle(true)
    }

    override fun initListeners() {
        super.initListeners()
        itemTouchHelper = ItemTouchHelper(itemTouchCallback)
        itemTouchHelper!!.attachToRecyclerView(itemsList)
        itemListAdapter!!.setSelectedListener(object : OnClickGesture<LocalItem?> {
            public override fun selected(selectedItem: LocalItem?) {
                val fragmentManager: FragmentManager? = getFM()
                if (selectedItem is PlaylistMetadataEntry) {
                    val entry: PlaylistMetadataEntry = selectedItem
                    NavigationHelper.openLocalPlaylistFragment(fragmentManager, entry.getUid(),
                            entry.name)
                } else if (selectedItem is PlaylistRemoteEntity) {
                    val entry: PlaylistRemoteEntity = selectedItem
                    NavigationHelper.openPlaylistFragment(
                            fragmentManager,
                            entry.getServiceId(),
                            entry.getUrl(),
                            entry.getName())
                }
            }

            public override fun held(selectedItem: LocalItem?) {
                if (selectedItem is PlaylistMetadataEntry) {
                    showLocalDialog(selectedItem)
                } else if (selectedItem is PlaylistRemoteEntity) {
                    showRemoteDeleteDialog(selectedItem)
                }
            }

            public override fun drag(selectedItem: LocalItem?,
                                     viewHolder: RecyclerView.ViewHolder?) {
                if (itemTouchHelper != null) {
                    itemTouchHelper!!.startDrag((viewHolder)!!)
                }
            }
        })
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////
    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        if (debounceSaver != null) {
            disposables!!.add(debounceSaver.getDebouncedSaver())
            debounceSaver!!.setNoChangesToSave()
        }
        isLoadingComplete!!.set(false)
        MergedPlaylistManager.getMergedOrderedPlaylists(localPlaylistManager, remotePlaylistManager)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlistsSubscriber)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////
    public override fun onPause() {
        super.onPause()
        itemsListState = itemsList!!.getLayoutManager()!!.onSaveInstanceState()

        // Save on exit
        saveImmediate()
    }

    public override fun onDestroyView() {
        super.onDestroyView()
        if (disposables != null) {
            disposables!!.clear()
        }
        if (databaseSubscription != null) {
            databaseSubscription!!.cancel()
        }
        databaseSubscription = null
        itemTouchHelper = null
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (debounceSaver != null) {
            debounceSaver.getDebouncedSaveSignal().onComplete()
        }
        if (disposables != null) {
            disposables!!.dispose()
        }
        debounceSaver = null
        disposables = null
        localPlaylistManager = null
        remotePlaylistManager = null
        itemsListState = null
        isLoadingComplete = null
        deletedItems = null
    }

    private val playlistsSubscriber: Subscriber<List<PlaylistLocalItem?>?>
        ///////////////////////////////////////////////////////////////////////////
        private get() {
            return object : Subscriber<List<PlaylistLocalItem?>?> {
                public override fun onSubscribe(s: Subscription) {
                    showLoading()
                    isLoadingComplete!!.set(false)
                    if (databaseSubscription != null) {
                        databaseSubscription!!.cancel()
                    }
                    databaseSubscription = s
                    databaseSubscription!!.request(1)
                }

                public override fun onNext(subscriptions: List<PlaylistLocalItem?>?) {
                    if (debounceSaver == null || !debounceSaver!!.getIsModified()) {
                        handleResult(subscriptions)
                        isLoadingComplete!!.set(true)
                    }
                    if (databaseSubscription != null) {
                        databaseSubscription!!.request(1)
                    }
                }

                public override fun onError(exception: Throwable) {
                    showError(ErrorInfo(exception,
                            UserAction.REQUESTED_BOOKMARK, "Loading playlists"))
                }

                public override fun onComplete() {}
            }
        }

    public override fun handleResult(result: List<PlaylistLocalItem?>) {
        super.handleResult(result)
        itemListAdapter!!.clearStreamItemList()
        if (result.isEmpty()) {
            showEmptyState()
            return
        }
        itemListAdapter!!.addItems(result)
        if (itemsListState != null) {
            itemsList!!.getLayoutManager()!!.onRestoreInstanceState(itemsListState)
            itemsListState = null
        }
        hideLoading()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////
    override fun resetFragment() {
        super.resetFragment()
        if (disposables != null) {
            disposables!!.clear()
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Metadata Manipulation
    ////////////////////////////////////////////////////////////////////////// */
    private fun changeLocalPlaylistName(id: Long, name: String) {
        if (localPlaylistManager == null) {
            return
        }
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("Updating playlist id=[" + id + "] "
                    + "with new name=[" + name + "] items"))
        }
        val disposable: Disposable = localPlaylistManager!!.renamePlaylist(id, name)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer<Int?>({ longs: Int? -> }), Consumer({ throwable: Throwable? ->
                    showError(
                            ErrorInfo((throwable)!!,
                                    UserAction.REQUESTED_BOOKMARK,
                                    "Changing playlist name"))
                }))
        disposables!!.add(disposable)
    }

    private fun deleteItem(item: PlaylistLocalItem) {
        if (itemListAdapter == null) {
            return
        }
        itemListAdapter!!.removeItem(item)
        if (item is PlaylistMetadataEntry) {
            deletedItems!!.add(Pair(item.getUid(),
                    LocalItemType.PLAYLIST_LOCAL_ITEM))
        } else if (item is PlaylistRemoteEntity) {
            deletedItems!!.add(Pair(item.getUid(),
                    LocalItemType.PLAYLIST_REMOTE_ITEM))
        }
        if (debounceSaver != null) {
            debounceSaver!!.setHasChangesToSave()
            saveImmediate()
        }
    }

    public override fun saveImmediate() {
        if (itemListAdapter == null) {
            return
        }

        // List must be loaded and modified in order to save
        if ((isLoadingComplete == null) || (debounceSaver == null
                        ) || !isLoadingComplete!!.get() || !debounceSaver!!.getIsModified()) {
            return
        }
        val items: List<LocalItem?>? = itemListAdapter.getItemsList()
        val localItemsUpdate: MutableList<PlaylistMetadataEntry> = ArrayList()
        val localItemsDeleteUid: MutableList<Long> = ArrayList()
        val remoteItemsUpdate: MutableList<PlaylistRemoteEntity> = ArrayList()
        val remoteItemsDeleteUid: MutableList<Long> = ArrayList()

        // Calculate display index
        for (i in items!!.indices) {
            val item: LocalItem? = items.get(i)
            if ((item is PlaylistMetadataEntry
                            && item.getDisplayIndex() != i.toLong())) {
                item.setDisplayIndex(i.toLong())
                localItemsUpdate.add(item)
            } else if ((item is PlaylistRemoteEntity
                            && item.getDisplayIndex() != i.toLong())) {
                item.setDisplayIndex(i.toLong())
                remoteItemsUpdate.add(item)
            }
        }

        // Find deleted items
        for (item: Pair<Long, LocalItemType> in deletedItems!!) {
            if ((item.second == LocalItemType.PLAYLIST_LOCAL_ITEM)) {
                localItemsDeleteUid.add(item.first)
            } else if ((item.second == LocalItemType.PLAYLIST_REMOTE_ITEM)) {
                remoteItemsDeleteUid.add(item.first)
            }
        }
        deletedItems!!.clear()

        // 1. Update local playlists
        // 2. Update remote playlists
        // 3. Set NoChangesToSave
        disposables!!.add(localPlaylistManager!!.updatePlaylists(localItemsUpdate, localItemsDeleteUid)
                .mergeWith(remotePlaylistManager!!.updatePlaylists(
                        remoteItemsUpdate, remoteItemsDeleteUid))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Action({
                    if (debounceSaver != null) {
                        debounceSaver!!.setNoChangesToSave()
                    }
                }),
                        Consumer({ throwable: Throwable? ->
                            showError(ErrorInfo((throwable)!!,
                                    UserAction.REQUESTED_BOOKMARK, "Saving playlist"))
                        })
                ))
    }

    private val itemTouchCallback: ItemTouchHelper.SimpleCallback
        private get() {
            // if adding grid layout, also include ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
            // with an `if (shouldUseGridLayout()) ...`
            return object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    ItemTouchHelper.ACTION_STATE_IDLE) {
                public override fun interpolateOutOfBoundsScroll(recyclerView: RecyclerView,
                                                                 viewSize: Int,
                                                                 viewSizeOutOfBounds: Int,
                                                                 totalSize: Int,
                                                                 msSinceStartScroll: Long): Int {
                    val standardSpeed: Int = super.interpolateOutOfBoundsScroll(recyclerView,
                            viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll)
                    val minimumAbsVelocity: Int = max(MINIMUM_INITIAL_DRAG_VELOCITY.toDouble(), abs(standardSpeed.toDouble())).toInt()
                    return minimumAbsVelocity * sign(viewSizeOutOfBounds.toDouble()).toInt()
                }

                public override fun onMove(recyclerView: RecyclerView,
                                           source: RecyclerView.ViewHolder,
                                           target: RecyclerView.ViewHolder): Boolean {

                    // Allow swap LocalBookmarkPlaylistItemHolder and RemoteBookmarkPlaylistItemHolder.
                    if ((itemListAdapter == null
                                    || source.getItemViewType() != target.getItemViewType()
                                    && !(((source is LocalBookmarkPlaylistItemHolder)
                                    || (source is RemoteBookmarkPlaylistItemHolder))
                                    && ((target is LocalBookmarkPlaylistItemHolder)
                                    || (target is RemoteBookmarkPlaylistItemHolder))))) {
                        return false
                    }
                    val sourceIndex: Int = source.getBindingAdapterPosition()
                    val targetIndex: Int = target.getBindingAdapterPosition()
                    val isSwapped: Boolean = itemListAdapter!!.swapItems(sourceIndex, targetIndex)
                    if (isSwapped && debounceSaver != null) {
                        debounceSaver!!.setHasChangesToSave()
                    }
                    return isSwapped
                }

                public override fun isLongPressDragEnabled(): Boolean {
                    return false
                }

                public override fun isItemViewSwipeEnabled(): Boolean {
                    return false
                }

                public override fun onSwiped(viewHolder: RecyclerView.ViewHolder,
                                             swipeDir: Int) {
                    // Do nothing.
                }
            }
        }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////
    private fun showRemoteDeleteDialog(item: PlaylistRemoteEntity) {
        showDeleteDialog(item.getName(), item)
    }

    private fun showLocalDialog(selectedItem: PlaylistMetadataEntry) {
        val rename: String = getString(R.string.rename)
        val delete: String = getString(R.string.delete)
        val unsetThumbnail: String = getString(R.string.unset_playlist_thumbnail)
        val isThumbnailPermanent: Boolean = localPlaylistManager
                .getIsPlaylistThumbnailPermanent(selectedItem.getUid())
        val items: ArrayList<String> = ArrayList()
        items.add(rename)
        items.add(delete)
        if (isThumbnailPermanent) {
            items.add(unsetThumbnail)
        }
        val action: DialogInterface.OnClickListener = DialogInterface.OnClickListener({ d: DialogInterface?, index: Int ->
            if ((items.get(index) == rename)) {
                showRenameDialog(selectedItem)
            } else if ((items.get(index) == delete)) {
                showDeleteDialog(selectedItem.name, selectedItem)
            } else if (isThumbnailPermanent && (items.get(index) == unsetThumbnail)) {
                val thumbnailStreamId: Long = localPlaylistManager
                        .getAutomaticPlaylistThumbnailStreamId(selectedItem.getUid())
                localPlaylistManager
                        .changePlaylistThumbnail(selectedItem.getUid(), thumbnailStreamId, false)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe()
            }
        })
        AlertDialog.Builder((activity)!!)
                .setItems(items.toTypedArray<String>(), action)
                .show()
    }

    private fun showRenameDialog(selectedItem: PlaylistMetadataEntry) {
        val dialogBinding: DialogEditTextBinding = DialogEditTextBinding.inflate(getLayoutInflater())
        dialogBinding.dialogEditText.setHint(R.string.name)
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT)
        dialogBinding.dialogEditText.setText(selectedItem.name)
        AlertDialog.Builder((activity)!!)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.rename_playlist, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int ->
                    changeLocalPlaylistName(
                            selectedItem.getUid(),
                            dialogBinding.dialogEditText.getText().toString())
                }))
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun showDeleteDialog(name: String?, item: PlaylistLocalItem) {
        if (activity == null || disposables == null) {
            return
        }
        AlertDialog.Builder(activity!!)
                .setTitle(name)
                .setMessage(R.string.delete_playlist_prompt)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, DialogInterface.OnClickListener({ dialog: DialogInterface?, i: Int -> deleteItem(item) }))
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    companion object {
        private val MINIMUM_INITIAL_DRAG_VELOCITY: Int = 12
    }
}
