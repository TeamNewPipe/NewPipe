package org.schabi.newpipe.local.playlist

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import icepick.State
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleSource
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.databinding.DialogEditTextBinding
import org.schabi.newpipe.databinding.LocalPlaylistHeaderBinding
import org.schabi.newpipe.databinding.PlaylistControlBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.fragments.MainFragment.SelectedTabsPagerAdapter
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry
import org.schabi.newpipe.info_list.dialog.StreamDialogEntry.StreamDialogEntryAction
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.local.BaseLocalListFragment
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.PlayButtonHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.debounce.DebounceSavable
import org.schabi.newpipe.util.debounce.DebounceSaver
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Predicate
import java.util.stream.Collectors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

class LocalPlaylistFragment() : BaseLocalListFragment<List<PlaylistStreamEntry?>?, Void?>(), PlaylistControlViewHolder, DebounceSavable {
    @State
    protected var playlistId: Long? = null

    @State
    protected var name: String? = null

    @State
    var itemsListState: Parcelable? = null
    private var headerBinding: LocalPlaylistHeaderBinding? = null
    private var playlistControlBinding: PlaylistControlBinding? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var playlistManager: LocalPlaylistManager? = null
    private var databaseSubscription: Subscription? = null
    private var disposables: CompositeDisposable? = null

    /** Whether the playlist has been fully loaded from db.  */
    private var isLoadingComplete: AtomicBoolean? = null

    /** Used to debounce saving playlist edits to disk.  */
    private var debounceSaver: DebounceSaver? = null

    /** Flag to prevent simultaneous rewrites of the playlist.  */
    private var isRewritingPlaylist: Boolean = false

    /**
     * The pager adapter that the fragment is created from when it is used as frontpage, i.e.
     * [.useAsFrontPage] is [true].
     */
    private var tabsPagerAdapter: SelectedTabsPagerAdapter? = null

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlistManager = LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()))
        disposables = CompositeDisposable()
        isLoadingComplete = AtomicBoolean()
        debounceSaver = DebounceSaver(this)
    }

    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playlist, container, false)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Lifecycle - Views
    ///////////////////////////////////////////////////////////////////////////
    public override fun setTitle(title: String?) {
        super.setTitle(title)
        if (headerBinding != null) {
            headerBinding!!.playlistTitleView.setText(title)
        }
    }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        setTitle(name)
    }

    protected override val listHeader: ViewBinding?
        protected get() {
            headerBinding = LocalPlaylistHeaderBinding.inflate(activity!!.getLayoutInflater(), itemsList,
                    false)
            playlistControlBinding = headerBinding!!.playlistControl
            headerBinding!!.playlistTitleView.setSelected(true)
            return headerBinding
        }

    override fun initListeners() {
        super.initListeners()
        headerBinding!!.playlistTitleView.setOnClickListener(View.OnClickListener({ view: View? -> createRenameDialog() }))
        itemTouchHelper = ItemTouchHelper(itemTouchCallback)
        itemTouchHelper!!.attachToRecyclerView(itemsList)
        itemListAdapter!!.setSelectedListener(object : OnClickGesture<LocalItem?> {
            public override fun selected(selectedItem: LocalItem?) {
                if (selectedItem is PlaylistStreamEntry) {
                    val item: StreamEntity = selectedItem.streamEntity
                    NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                            item.serviceId, item.url, item.title, null, false)
                }
            }

            public override fun held(selectedItem: LocalItem?) {
                if (selectedItem is PlaylistStreamEntry) {
                    showInfoItemDialog(selectedItem)
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
    // Fragment Lifecycle - Loading
    ///////////////////////////////////////////////////////////////////////////
    public override fun showLoading() {
        super.showLoading()
        if (headerBinding != null) {
            headerBinding!!.getRoot().animate(false, 200)
            playlistControlBinding!!.getRoot().animate(false, 200)
        }
    }

    public override fun hideLoading() {
        super.hideLoading()
        if (headerBinding != null) {
            headerBinding!!.getRoot().animate(true, 200)
            playlistControlBinding!!.getRoot().animate(true, 200)
        }
    }

    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        if (disposables != null) {
            disposables!!.clear()
        }
        if (debounceSaver != null) {
            disposables!!.add(debounceSaver.getDebouncedSaver())
            debounceSaver!!.setNoChangesToSave()
        }
        isLoadingComplete!!.set(false)
        playlistManager!!.getPlaylistStreams((playlistId)!!)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playlistObserver)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Lifecycle - Destruction
    ///////////////////////////////////////////////////////////////////////////
    public override fun onPause() {
        super.onPause()
        itemsListState = itemsList!!.getLayoutManager()!!.onSaveInstanceState()

        // Save on exit
        saveImmediate()
    }

    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]"))
        }
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_local_playlist, menu)
    }

    public override fun onDestroyView() {
        super.onDestroyView()
        if (itemListAdapter != null) {
            itemListAdapter!!.unsetSelectedListener()
        }
        headerBinding = null
        playlistControlBinding = null
        if (databaseSubscription != null) {
            databaseSubscription!!.cancel()
        }
        if (disposables != null) {
            disposables!!.clear()
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
        if (tabsPagerAdapter != null) {
            tabsPagerAdapter!!.getLocalPlaylistFragments().remove(this)
        }
        debounceSaver = null
        playlistManager = null
        disposables = null
        isLoadingComplete = null
    }

    private val playlistObserver: Subscriber<List<PlaylistStreamEntry?>?>
        ///////////////////////////////////////////////////////////////////////////
        private get() {
            return object : Subscriber<List<PlaylistStreamEntry?>?> {
                public override fun onSubscribe(s: Subscription) {
                    showLoading()
                    isLoadingComplete!!.set(false)
                    if (databaseSubscription != null) {
                        databaseSubscription!!.cancel()
                    }
                    databaseSubscription = s
                    databaseSubscription!!.request(1)
                }

                public override fun onNext(streams: List<PlaylistStreamEntry?>?) {
                    // Skip handling the result after it has been modified
                    if (debounceSaver == null || !debounceSaver!!.getIsModified()) {
                        handleResult(streams)
                        isLoadingComplete!!.set(true)
                    }
                    if (databaseSubscription != null) {
                        databaseSubscription!!.request(1)
                    }
                }

                public override fun onError(exception: Throwable) {
                    showError(ErrorInfo(exception, UserAction.REQUESTED_BOOKMARK,
                            "Loading local playlist"))
                }

                public override fun onComplete() {}
            }
        }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.menu_item_share_playlist) {
            createShareConfirmationDialog()
        } else if (item.getItemId() == R.id.menu_item_rename_playlist) {
            createRenameDialog()
        } else if (item.getItemId() == R.id.menu_item_remove_watched) {
            if (!isRewritingPlaylist) {
                AlertDialog.Builder(requireContext())
                        .setMessage(R.string.remove_watched_popup_warning)
                        .setTitle(R.string.remove_watched_popup_title)
                        .setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ d: DialogInterface?, id: Int -> removeWatchedStreams(false) }))
                        .setNeutralButton(
                                R.string.remove_watched_popup_yes_and_partially_watched_videos,
                                DialogInterface.OnClickListener({ d: DialogInterface?, id: Int -> removeWatchedStreams(true) }))
                        .setNegativeButton(R.string.cancel,
                                DialogInterface.OnClickListener({ d: DialogInterface, id: Int -> d.cancel() }))
                        .show()
            }
        } else if (item.getItemId() == R.id.menu_item_remove_duplicates) {
            if (!isRewritingPlaylist) {
                openRemoveDuplicatesDialog()
            }
        } else {
            return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Shares the playlist as a list of stream URLs if `shouldSharePlaylistDetails` is
     * set to `false`. Shares the playlist name along with a list of video titles and URLs
     * if `shouldSharePlaylistDetails` is set to `true`.
     *
     * @param shouldSharePlaylistDetails Whether the playlist details should be included in the
     * shared content.
     */
    private fun sharePlaylist(shouldSharePlaylistDetails: Boolean) {
        val context: Context = requireContext()
        disposables!!.add(playlistManager!!.getPlaylistStreams((playlistId)!!)
                .flatMapSingle<String>(io.reactivex.rxjava3.functions.Function<List<PlaylistStreamEntry?>, SingleSource<out String?>>({ playlist: List<PlaylistStreamEntry?> ->
                    Single.just<String?>(playlist.stream()
                            .map<StreamEntity>(PlaylistStreamEntry::streamEntity)
                            .map<String>(java.util.function.Function<StreamEntity, String>({ streamEntity: StreamEntity ->
                                if (shouldSharePlaylistDetails) {
                                    return@map context.getString(R.string.video_details_list_item,
                                            streamEntity.title, streamEntity.url)
                                } else {
                                    return@map streamEntity.url
                                }
                            }))
                            .collect(Collectors.joining("\n")))
                }))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer<String>({ urlsText: String? ->
                    shareText(
                            context, name, if (shouldSharePlaylistDetails) context.getString(R.string.share_playlist_content_details,
                            name, urlsText) else urlsText)
                }),
                        Consumer<Throwable>({ throwable: Throwable? -> showUiErrorSnackbar(this, "Sharing playlist", (throwable)!!) })))
    }

    fun removeWatchedStreams(removePartiallyWatched: Boolean) {
        if (isRewritingPlaylist) {
            return
        }
        isRewritingPlaylist = true
        showLoading()
        val recordManager: HistoryRecordManager = HistoryRecordManager(getContext())
        val historyIdsMaybe: Maybe<List<Long>> = recordManager.getStreamHistorySortedById()
                .firstElement() // already sorted by ^ getStreamHistorySortedById(), binary search can be used
                .map(io.reactivex.rxjava3.functions.Function<List<StreamHistoryEntry?>?, List<Long>>({ historyList: List<StreamHistoryEntry?>? ->
                    historyList!!.stream().map<Long>(StreamHistoryEntry::streamId)
                            .collect(Collectors.toList())
                }))
        val streamsMaybe: Maybe<Pair<List<PlaylistStreamEntry?>, Boolean>> = playlistManager!!.getPlaylistStreams((playlistId)!!)
                .firstElement()
                .zipWith(historyIdsMaybe, BiFunction<List<PlaylistStreamEntry?>?, List<Long>, Pair<List<PlaylistStreamEntry?>, Boolean>>({ playlist: List<PlaylistStreamEntry?>?, historyStreamIds: List<Long>? ->
                    // Remove Watched, Functionality data
                    val itemsToKeep: MutableList<PlaylistStreamEntry?> = ArrayList()
                    val isThumbnailPermanent: Boolean = playlistManager
                            .getIsPlaylistThumbnailPermanent((playlistId)!!)
                    var thumbnailVideoRemoved: Boolean = false
                    if (removePartiallyWatched) {
                        for (playlistItem: PlaylistStreamEntry? in playlist!!) {
                            val indexInHistory: Int = Collections.binarySearch(historyStreamIds,
                                    playlistItem!!.streamId)
                            if (indexInHistory < 0) {
                                itemsToKeep.add(playlistItem)
                            } else if ((!isThumbnailPermanent && !thumbnailVideoRemoved
                                            && ((playlistManager!!.getPlaylistThumbnailStreamId((playlistId)!!)
                                            == playlistItem.streamEntity.uid)))) {
                                thumbnailVideoRemoved = true
                            }
                        }
                    } else {
                        val streamStates: List<StreamStateEntity?>? = recordManager
                                .loadLocalStreamStateBatch(playlist).blockingGet()
                        for (i in playlist!!.indices) {
                            val playlistItem: PlaylistStreamEntry? = playlist.get(i)
                            val streamStateEntity: StreamStateEntity? = streamStates!!.get(i)
                            val indexInHistory: Int = Collections.binarySearch(historyStreamIds,
                                    playlistItem!!.streamId)
                            val duration: Long = playlistItem.toStreamInfoItem().getDuration()
                            if (indexInHistory < 0 || ((streamStateEntity != null
                                            && !streamStateEntity.isFinished(duration)))) {
                                itemsToKeep.add(playlistItem)
                            } else if ((!isThumbnailPermanent && !thumbnailVideoRemoved
                                            && ((playlistManager!!.getPlaylistThumbnailStreamId((playlistId)!!)
                                            == playlistItem.streamEntity.uid)))) {
                                thumbnailVideoRemoved = true
                            }
                        }
                    }
                    Pair(itemsToKeep, thumbnailVideoRemoved)
                }))
        disposables!!.add(streamsMaybe.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer({ flow: Pair<List<PlaylistStreamEntry?>, Boolean> ->
                    val itemsToKeep: List<PlaylistStreamEntry?> = flow.first
                    val thumbnailVideoRemoved: Boolean = flow.second
                    itemListAdapter!!.clearStreamItemList()
                    itemListAdapter!!.addItems(itemsToKeep)
                    debounceSaver!!.setHasChangesToSave()
                    if (thumbnailVideoRemoved) {
                        updateThumbnailUrl()
                    }
                    val videoCount: Long = itemListAdapter.getItemsList().size.toLong()
                    setStreamCountAndOverallDuration(itemListAdapter.getItemsList())
                    if (videoCount == 0L) {
                        showEmptyState()
                    }
                    hideLoading()
                    isRewritingPlaylist = false
                }), Consumer({ throwable: Throwable? ->
                    showError(ErrorInfo((throwable)!!, UserAction.REQUESTED_BOOKMARK,
                            "Removing watched videos, partially watched=" + removePartiallyWatched))
                })))
    }

    public override fun handleResult(result: List<PlaylistStreamEntry?>) {
        super.handleResult(result)
        if (itemListAdapter == null) {
            return
        }
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
        setStreamCountAndOverallDuration(itemListAdapter.getItemsList())
        PlayButtonHelper.initPlaylistControlClickListener((activity)!!, (playlistControlBinding)!!, this)
        hideLoading()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////
    override fun resetFragment() {
        super.resetFragment()
        if (databaseSubscription != null) {
            databaseSubscription!!.cancel()
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Metadata/Streams Manipulation
    ////////////////////////////////////////////////////////////////////////// */
    private fun createRenameDialog() {
        if ((playlistId == null) || (name == null) || (getContext() == null)) {
            return
        }
        val dialogBinding: DialogEditTextBinding = DialogEditTextBinding.inflate(getLayoutInflater())
        dialogBinding.dialogEditText.setHint(R.string.name)
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT)
        dialogBinding.dialogEditText.setSelection(dialogBinding.dialogEditText.getText()!!.length)
        dialogBinding.dialogEditText.setText(name)
        AlertDialog.Builder((getContext())!!)
                .setTitle(R.string.rename_playlist)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.rename, DialogInterface.OnClickListener({ dialogInterface: DialogInterface?, i: Int -> changePlaylistName(dialogBinding.dialogEditText.getText().toString()) }))
                .show()
    }

    private fun changePlaylistName(title: String) {
        if (playlistManager == null) {
            return
        }
        name = title
        setTitle(title)
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("Updating playlist id=[" + playlistId + "] "
                    + "with new title=[" + title + "] items"))
        }
        val disposable: Disposable = playlistManager!!.renamePlaylist((playlistId)!!, title)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer<Int?>({ longs: Int? -> }), Consumer({ throwable: Throwable? ->
                    showError(ErrorInfo((throwable)!!, UserAction.REQUESTED_BOOKMARK,
                            "Renaming playlist"))
                }))
        disposables!!.add(disposable)
    }

    private fun changeThumbnailStreamId(thumbnailStreamId: Long, isPermanent: Boolean) {
        if (playlistManager == null || (!isPermanent && playlistManager!!
                        .getIsPlaylistThumbnailPermanent((playlistId)!!))) {
            return
        }
        val successToast: Toast = Toast.makeText(getActivity(),
                R.string.playlist_thumbnail_change_success,
                Toast.LENGTH_SHORT)
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("Updating playlist id=[" + playlistId + "] "
                    + "with new thumbnail stream id=[" + thumbnailStreamId + "]"))
        }
        val disposable: Disposable = playlistManager!!
                .changePlaylistThumbnail((playlistId)!!, thumbnailStreamId, isPermanent)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer<Int?>({ ignore: Int? -> successToast.show() }), Consumer({ throwable: Throwable? ->
                    showError(ErrorInfo((throwable)!!, UserAction.REQUESTED_BOOKMARK,
                            "Changing playlist thumbnail"))
                }))
        disposables!!.add(disposable)
    }

    private fun updateThumbnailUrl() {
        if (playlistManager!!.getIsPlaylistThumbnailPermanent((playlistId)!!)) {
            return
        }
        val thumbnailStreamId: Long
        if (!itemListAdapter.getItemsList().isEmpty()) {
            thumbnailStreamId = (itemListAdapter.getItemsList().get(0) as PlaylistStreamEntry?)
                    .streamEntity.uid
        } else {
            thumbnailStreamId = PlaylistEntity.Companion.DEFAULT_THUMBNAIL_ID
        }
        changeThumbnailStreamId(thumbnailStreamId, false)
    }

    private fun openRemoveDuplicatesDialog() {
        AlertDialog.Builder((getActivity())!!)
                .setTitle(R.string.remove_duplicates_title)
                .setMessage(R.string.remove_duplicates_message)
                .setPositiveButton(R.string.ok, DialogInterface.OnClickListener({ dialog: DialogInterface?, i: Int -> removeDuplicatesInPlaylist() }))
                .setNeutralButton(R.string.cancel, null)
                .show()
    }

    private fun removeDuplicatesInPlaylist() {
        if (isRewritingPlaylist) {
            return
        }
        isRewritingPlaylist = true
        showLoading()
        val streamsMaybe: Maybe<List<PlaylistStreamEntry?>?> = playlistManager
                .getDistinctPlaylistStreams((playlistId)!!).firstElement()
        disposables!!.add(streamsMaybe.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer({ itemsToKeep: List<PlaylistStreamEntry?>? ->
                    itemListAdapter!!.clearStreamItemList()
                    itemListAdapter!!.addItems(itemsToKeep)
                    setStreamCountAndOverallDuration(itemListAdapter.getItemsList())
                    debounceSaver!!.setHasChangesToSave()
                    hideLoading()
                    isRewritingPlaylist = false
                }), Consumer({ throwable: Throwable? ->
                    showError(ErrorInfo((throwable)!!, UserAction.REQUESTED_BOOKMARK,
                            "Removing duplicated streams"))
                })))
    }

    private fun deleteItem(item: PlaylistStreamEntry) {
        if (itemListAdapter == null) {
            return
        }
        itemListAdapter!!.removeItem(item)
        if (playlistManager!!.getPlaylistThumbnailStreamId((playlistId)!!) == item.streamId) {
            updateThumbnailUrl()
        }
        setStreamCountAndOverallDuration(itemListAdapter.getItemsList())
        debounceSaver!!.setHasChangesToSave()
    }

    /**
     *
     * Commit changes immediately if the playlist has been modified.
     * Delete operations and other modifications will be committed to ensure that the database
     * is up to date, e.g. when the user adds the just deleted stream from another fragment.
     */
    public override fun saveImmediate() {
        if (playlistManager == null || itemListAdapter == null) {
            return
        }

        // List must be loaded and modified in order to save
        if ((isLoadingComplete == null) || (debounceSaver == null
                        ) || !isLoadingComplete!!.get() || !debounceSaver!!.getIsModified()) {
            return
        }
        val items: List<LocalItem?>? = itemListAdapter.getItemsList()
        val streamIds: MutableList<Long> = ArrayList(items!!.size)
        for (item: LocalItem? in items) {
            if (item is PlaylistStreamEntry) {
                streamIds.add(item.streamId)
            }
        }
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("Updating playlist id=[" + playlistId + "] "
                    + "with [" + streamIds.size + "] items"))
        }
        val disposable: Disposable = playlistManager!!.updateJoin((playlistId)!!, streamIds)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        Action({
                            if (debounceSaver != null) {
                                debounceSaver!!.setNoChangesToSave()
                            }
                        }),
                        Consumer({ throwable: Throwable? ->
                            showError(ErrorInfo((throwable)!!,
                                    UserAction.REQUESTED_BOOKMARK, "Saving playlist"))
                        })
                )
        disposables!!.add(disposable)
    }

    private val itemTouchCallback: ItemTouchHelper.SimpleCallback
        private get() {
            var directions: Int = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            if (ThemeHelper.shouldUseGridLayout(requireContext())) {
                directions = directions or (ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
            }
            return object : ItemTouchHelper.SimpleCallback(directions,
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
                    if ((source.getItemViewType() != target.getItemViewType()
                                    || itemListAdapter == null)) {
                        return false
                    }
                    val sourceIndex: Int = source.getBindingAdapterPosition()
                    val targetIndex: Int = target.getBindingAdapterPosition()
                    val isSwapped: Boolean = itemListAdapter!!.swapItems(sourceIndex, targetIndex)
                    if (isSwapped) {
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
                }
            }
        }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun getPlayQueueStartingAt(infoItem: PlaylistStreamEntry): PlayQueue {
        return getPlayQueue(max(itemListAdapter.getItemsList().indexOf(infoItem).toDouble(), 0.0).toInt())
    }

    protected fun showInfoItemDialog(item: PlaylistStreamEntry) {
        val infoItem: StreamInfoItem = item.toStreamInfoItem()
        try {
            val context: Context? = getContext()
            val dialogBuilder: InfoItemDialog.Builder = InfoItemDialog.Builder((getActivity())!!, (context)!!, this, infoItem)

            // add entries in the middle
            dialogBuilder.addAllEntries(
                    StreamDialogDefaultEntry.SET_AS_PLAYLIST_THUMBNAIL,
                    StreamDialogDefaultEntry.DELETE
            )

            // set custom actions
            // all entries modified below have already been added within the builder
            dialogBuilder
                    .setAction(
                            StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND,
                            StreamDialogEntryAction({ f: Fragment?, i: StreamInfoItem? ->
                                NavigationHelper.playOnBackgroundPlayer(
                                        context, getPlayQueueStartingAt(item), true)
                            }))
                    .setAction(
                            StreamDialogDefaultEntry.SET_AS_PLAYLIST_THUMBNAIL,
                            StreamDialogEntryAction({ f: Fragment?, i: StreamInfoItem? ->
                                changeThumbnailStreamId(item.streamEntity.uid,
                                        true)
                            }))
                    .setAction(
                            StreamDialogDefaultEntry.DELETE,
                            StreamDialogEntryAction({ f: Fragment?, i: StreamInfoItem? -> deleteItem(item) }))
                    .create()
                    .show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.Companion.reportErrorDuringInitialization(e, infoItem)
        }
    }

    private fun setInitialData(pid: Long, title: String?) {
        playlistId = pid
        name = if (!TextUtils.isEmpty(title)) title else ""
    }

    private fun setStreamCountAndOverallDuration(itemsList: ArrayList<LocalItem?>?) {
        if (activity != null && headerBinding != null) {
            val streamCount: Long = itemsList!!.size.toLong()
            val playlistOverallDurationSeconds: Long = itemsList.stream()
                    .filter(Predicate({ obj: LocalItem? -> PlaylistStreamEntry::class.java.isInstance(obj) }))
                    .map(java.util.function.Function({ obj: LocalItem? -> PlaylistStreamEntry::class.java.cast(obj) }))
                    .map(PlaylistStreamEntry::streamEntity)
                    .mapToLong(StreamEntity::duration)
                    .sum()
            headerBinding!!.playlistStreamCount.setText(
                    Localization.concatenateStrings(
                            Localization.localizeStreamCount(activity!!, streamCount),
                            Localization.getDurationString(playlistOverallDurationSeconds))
            )
        }
    }

    override val playQueue: PlayQueue
        get() {
            return getPlayQueue(0)
        }

    private fun getPlayQueue(index: Int): PlayQueue {
        if (itemListAdapter == null) {
            return SinglePlayQueue(emptyList(), 0)
        }
        val infoItems: List<LocalItem?>? = itemListAdapter.getItemsList()
        val streamInfoItems: MutableList<StreamInfoItem> = ArrayList(infoItems!!.size)
        for (item: LocalItem? in infoItems) {
            if (item is PlaylistStreamEntry) {
                streamInfoItems.add(item.toStreamInfoItem())
            }
        }
        return SinglePlayQueue(streamInfoItems, index)
    }

    /**
     * Creates a dialog to confirm whether the user wants to share the playlist
     * with the playlist details or just the list of stream URLs.
     * After the user has made a choice, the playlist is shared.
     */
    private fun createShareConfirmationDialog() {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.share_playlist)
                .setMessage(R.string.share_playlist_with_titles_message)
                .setCancelable(true)
                .setPositiveButton(R.string.share_playlist_with_titles, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int -> sharePlaylist( /* shouldSharePlaylistDetails= */true) })
                )
                .setNegativeButton(R.string.share_playlist_with_list, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int -> sharePlaylist( /* shouldSharePlaylistDetails= */false) })
                )
                .show()
    }

    fun setTabsPagerAdapter(
            tabsPagerAdapter: SelectedTabsPagerAdapter?) {
        this.tabsPagerAdapter = tabsPagerAdapter
    }

    companion object {
        private val MINIMUM_INITIAL_DRAG_VELOCITY: Int = 12
        fun getInstance(playlistId: Long, name: String?): LocalPlaylistFragment {
            val instance: LocalPlaylistFragment = LocalPlaylistFragment()
            instance.setInitialData(playlistId, name)
            return instance
        }
    }
}
