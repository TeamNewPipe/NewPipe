package org.schabi.newpipe.local.history

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import icepick.State
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Consumer
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.databinding.PlaylistControlBinding
import org.schabi.newpipe.databinding.StatisticPlaylistControlBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry
import org.schabi.newpipe.info_list.dialog.StreamDialogEntry.StreamDialogEntryAction
import org.schabi.newpipe.local.BaseLocalListFragment
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.settings.HistorySettingsFragment
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.PlayButtonHelper
import java.util.Collections
import java.util.Objects
import kotlin.math.max

class StatisticsPlaylistFragment() : BaseLocalListFragment<List<StreamStatisticsEntry?>?, Void?>(), PlaylistControlViewHolder {
    private val disposables: CompositeDisposable = CompositeDisposable()

    @State
    var itemsListState: Parcelable? = null
    private var sortMode: StatisticSortMode = StatisticSortMode.LAST_PLAYED
    private var headerBinding: StatisticPlaylistControlBinding? = null
    private var playlistControlBinding: PlaylistControlBinding? = null

    /* Used for independent events */
    private var databaseSubscription: Subscription? = null
    private var recordManager: HistoryRecordManager? = null
    private fun processResult(results: List<StreamStatisticsEntry?>): List<StreamStatisticsEntry?>? {
        val comparator: Comparator<StreamStatisticsEntry?>
        when (sortMode) {
            StatisticSortMode.LAST_PLAYED -> comparator = Comparator.comparing(StreamStatisticsEntry::latestAccessDate)
            StatisticSortMode.MOST_PLAYED -> comparator = Comparator.comparingLong(StreamStatisticsEntry::watchCount)
            else -> return null
        }
        Collections.sort(results, comparator.reversed())
        return results
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordManager = HistoryRecordManager(getContext())
    }

    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playlist, container, false)
    }

    public override fun onResume() {
        super.onResume()
        if (activity != null) {
            setTitle(activity!!.getString(R.string.title_activity_history))
        }
    }

    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_history, menu)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////
    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        if (!useAsFrontPage) {
            setTitle(getString(R.string.title_last_played))
        }
    }

    protected override val listHeader: ViewBinding?
        protected get() {
            headerBinding = StatisticPlaylistControlBinding.inflate(activity!!.getLayoutInflater(),
                    itemsList, false)
            playlistControlBinding = headerBinding!!.playlistControl
            return headerBinding
        }

    override fun initListeners() {
        super.initListeners()
        itemListAdapter!!.setSelectedListener(object : OnClickGesture<LocalItem?> {
            public override fun selected(selectedItem: LocalItem?) {
                if (selectedItem is StreamStatisticsEntry) {
                    val item: StreamEntity = selectedItem.streamEntity
                    NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                            item.serviceId, item.url, item.title, null, false)
                }
            }

            public override fun held(selectedItem: LocalItem?) {
                if (selectedItem is StreamStatisticsEntry) {
                    showInfoItemDialog(selectedItem)
                }
            }
        })
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.action_history_clear) {
            HistorySettingsFragment.Companion.openDeleteWatchHistoryDialog(requireContext(), recordManager, disposables)
        } else {
            return super.onOptionsItemSelected(item)
        }
        return true
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////
    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        recordManager.getStreamStatistics()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(historyObserver)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////
    public override fun onPause() {
        super.onPause()
        itemsListState = Objects.requireNonNull(itemsList!!.getLayoutManager()).onSaveInstanceState()
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
        databaseSubscription = null
    }

    public override fun onDestroy() {
        super.onDestroy()
        recordManager = null
        itemsListState = null
    }

    private val historyObserver: Subscriber<List<StreamStatisticsEntry?>?>
        ///////////////////////////////////////////////////////////////////////////
        private get() {
            return object : Subscriber<List<StreamStatisticsEntry?>?> {
                public override fun onSubscribe(s: Subscription) {
                    showLoading()
                    if (databaseSubscription != null) {
                        databaseSubscription!!.cancel()
                    }
                    databaseSubscription = s
                    databaseSubscription!!.request(1)
                }

                public override fun onNext(streams: List<StreamStatisticsEntry?>?) {
                    handleResult(streams)
                    if (databaseSubscription != null) {
                        databaseSubscription!!.request(1)
                    }
                }

                public override fun onError(exception: Throwable) {
                    showError(
                            ErrorInfo(exception, UserAction.SOMETHING_ELSE, "History Statistics"))
                }

                public override fun onComplete() {}
            }
        }

    public override fun handleResult(result: List<StreamStatisticsEntry?>) {
        super.handleResult(result)
        if (itemListAdapter == null) {
            return
        }
        playlistControlBinding!!.getRoot().setVisibility(View.VISIBLE)
        itemListAdapter!!.clearStreamItemList()
        if (result.isEmpty()) {
            showEmptyState()
            return
        }
        itemListAdapter!!.addItems(processResult(result))
        if (itemsListState != null && itemsList!!.getLayoutManager() != null) {
            itemsList!!.getLayoutManager()!!.onRestoreInstanceState(itemsListState)
            itemsListState = null
        }
        PlayButtonHelper.initPlaylistControlClickListener((activity)!!, (playlistControlBinding)!!, this)
        headerBinding!!.sortButton.setOnClickListener(View.OnClickListener({ view: View? -> toggleSortMode() }))
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
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun toggleSortMode() {
        if (sortMode == StatisticSortMode.LAST_PLAYED) {
            sortMode = StatisticSortMode.MOST_PLAYED
            setTitle(getString(R.string.title_most_played))
            headerBinding!!.sortButtonIcon.setImageResource(R.drawable.ic_history)
            headerBinding!!.sortButtonText.setText(R.string.title_last_played)
        } else {
            sortMode = StatisticSortMode.LAST_PLAYED
            setTitle(getString(R.string.title_last_played))
            headerBinding!!.sortButtonIcon.setImageResource(
                    R.drawable.ic_filter_list)
            headerBinding!!.sortButtonText.setText(R.string.title_most_played)
        }
        startLoading(true)
    }

    private fun getPlayQueueStartingAt(infoItem: StreamStatisticsEntry): PlayQueue {
        return getPlayQueue(max(itemListAdapter.getItemsList().indexOf(infoItem).toDouble(), 0.0).toInt())
    }

    private fun showInfoItemDialog(item: StreamStatisticsEntry) {
        val context: Context? = getContext()
        val infoItem: StreamInfoItem = item.toStreamInfoItem()
        try {
            val dialogBuilder: InfoItemDialog.Builder = InfoItemDialog.Builder((getActivity())!!, (context)!!, this, infoItem)

            // set entries in the middle; the others are added automatically
            dialogBuilder
                    .addEntry(StreamDialogDefaultEntry.DELETE)
                    .setAction(
                            StreamDialogDefaultEntry.DELETE,
                            StreamDialogEntryAction({ f: Fragment?, i: StreamInfoItem? -> deleteEntry(max(itemListAdapter.getItemsList().indexOf(item).toDouble(), 0.0).toInt()) }))
                    .setAction(
                            StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND,
                            StreamDialogEntryAction({ f: Fragment?, i: StreamInfoItem? ->
                                NavigationHelper.playOnBackgroundPlayer(
                                        context, getPlayQueueStartingAt(item), true)
                            }))
                    .create()
                    .show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.Companion.reportErrorDuringInitialization(e, infoItem)
        }
    }

    private fun deleteEntry(index: Int) {
        val infoItem: LocalItem? = itemListAdapter.getItemsList().get(index)
        if (infoItem is StreamStatisticsEntry) {
            val onDelete: Disposable = recordManager
                    .deleteStreamHistoryAndState(infoItem.streamId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            Action({
                                if (getView() != null) {
                                    Snackbar.make((getView())!!, R.string.one_item_deleted,
                                            Snackbar.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(getContext(),
                                            R.string.one_item_deleted,
                                            Toast.LENGTH_SHORT).show()
                                }
                            }),
                            Consumer({ throwable: Throwable? ->
                                showSnackBarError(ErrorInfo((throwable)!!,
                                        UserAction.DELETE_FROM_HISTORY, "Deleting item"))
                            }))
            disposables.add(onDelete)
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
            if (item is StreamStatisticsEntry) {
                streamInfoItems.add(item.toStreamInfoItem())
            }
        }
        return SinglePlayQueue(streamInfoItems, index)
    }

    private enum class StatisticSortMode {
        LAST_PLAYED,
        MOST_PLAYED
    }
}
