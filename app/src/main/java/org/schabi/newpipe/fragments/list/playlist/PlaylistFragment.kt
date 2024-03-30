package org.schabi.newpipe.fragments.list.playlist

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.BiFunction
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.databinding.PlaylistControlBinding
import org.schabi.newpipe.databinding.PlaylistHeaderBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry
import org.schabi.newpipe.info_list.dialog.StreamDialogEntry.StreamDialogEntryAction
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.animateHideRecyclerViewAllowingScrolling
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.PlayButtonHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.image.PicassoHelper
import org.schabi.newpipe.util.text.TextEllipsizer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import java.util.function.ToLongFunction
import java.util.stream.Collectors
import kotlin.math.max

class PlaylistFragment() : BaseListInfoFragment<StreamInfoItem?, PlaylistInfo>(UserAction.REQUESTED_PLAYLIST), PlaylistControlViewHolder {
    private var disposables: CompositeDisposable? = null
    private var bookmarkReactor: Subscription? = null
    private var isBookmarkButtonReady: AtomicBoolean? = null
    private var remotePlaylistManager: RemotePlaylistManager? = null
    private var playlistEntity: PlaylistRemoteEntity? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////// */
    private var headerBinding: PlaylistHeaderBinding? = null
    private var playlistControlBinding: PlaylistControlBinding? = null
    private var playlistBookmarkButton: MenuItem? = null
    private var streamCount: Long = 0
    private var playlistOverallDurationSeconds: Long = 0

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disposables = CompositeDisposable()
        isBookmarkButtonReady = AtomicBoolean(false)
        remotePlaylistManager = RemotePlaylistManager(NewPipeDatabase.getInstance(requireContext()))
    }

    public override fun onCreateView(inflater: LayoutInflater,
                                     container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playlist, container, false)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */
    override fun getListHeaderSupplier(): Supplier<View>? {
        headerBinding = PlaylistHeaderBinding
                .inflate(activity!!.getLayoutInflater(), itemsList, false)
        playlistControlBinding = headerBinding!!.playlistControl
        return Supplier({ headerBinding!!.getRoot() })
    }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)

        // Is mini variant still relevant?
        // Only the remote playlist screen uses it now
        infoListAdapter!!.setUseMiniVariant(true)
    }

    private fun getPlayQueueStartingAt(infoItem: StreamInfoItem): PlayQueue {
        return getPlayQueue(max(infoListAdapter!!.getItemsList().indexOf(infoItem).toDouble(), 0.0).toInt())
    }

    override fun showInfoItemDialog(item: StreamInfoItem) {
        val context: Context? = getContext()
        try {
            val dialogBuilder: InfoItemDialog.Builder = InfoItemDialog.Builder((getActivity())!!, (context)!!, this, item)
            dialogBuilder
                    .setAction(
                            StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND,
                            StreamDialogEntryAction({ f: Fragment?, infoItem: StreamInfoItem ->
                                NavigationHelper.playOnBackgroundPlayer(
                                        context, getPlayQueueStartingAt(infoItem), true)
                            }))
                    .create()
                    .show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.Companion.reportErrorDuringInitialization(e, item)
        }
    }

    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]"))
        }
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_playlist, menu)
        playlistBookmarkButton = menu.findItem(R.id.menu_item_bookmark)
        updateBookmarkButtons()
    }

    public override fun onDestroyView() {
        headerBinding = null
        playlistControlBinding = null
        super.onDestroyView()
        if (isBookmarkButtonReady != null) {
            isBookmarkButtonReady!!.set(false)
        }
        if (disposables != null) {
            disposables!!.clear()
        }
        if (bookmarkReactor != null) {
            bookmarkReactor!!.cancel()
        }
        bookmarkReactor = null
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (disposables != null) {
            disposables!!.dispose()
        }
        disposables = null
        remotePlaylistManager = null
        playlistEntity = null
        isBookmarkButtonReady = null
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    ////////////////////////////////////////////////////////////////////////// */
    override fun loadMoreItemsLogic(): Single<InfoItemsPage<StreamInfoItem?>?>? {
        return ExtractorHelper.getMorePlaylistItems(serviceId, url, currentNextPage)
    }

    override fun loadResult(forceLoad: Boolean): Single<PlaylistInfo>? {
        return ExtractorHelper.getPlaylistInfo(serviceId, url, forceLoad)
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.action_settings -> NavigationHelper.openSettings(requireContext())
            R.id.menu_item_openInBrowser -> ShareUtils.openUrlInBrowser(requireContext(), url)
            R.id.menu_item_share -> ShareUtils.shareText(requireContext(), (name)!!, url,
                    if (currentInfo == null) listOf() else currentInfo!!.getThumbnails())

            R.id.menu_item_bookmark -> onBookmarkClicked()
            R.id.menu_item_append_playlist -> if (currentInfo != null) {
                disposables!!.add(PlaylistDialog.Companion.createCorrespondingDialog(
                        getContext(),
                        getPlayQueue()
                                .getStreams()
                                .stream()
                                .map<StreamEntity?>(java.util.function.Function<PlayQueueItem?, StreamEntity?>({ item: PlayQueueItem? -> StreamEntity((item)!!) }))
                                .collect(Collectors.toList<StreamEntity?>()),
                        java.util.function.Consumer<PlaylistDialog>({ dialog: PlaylistDialog -> dialog.show(getFM(), TAG) })
                ))
            }

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    public override fun showLoading() {
        super.showLoading()
        headerBinding!!.getRoot().animate(false, 200)
        itemsList!!.animateHideRecyclerViewAllowingScrolling()
        PicassoHelper.cancelTag(PICASSO_PLAYLIST_TAG)
        headerBinding!!.uploaderLayout.animate(false, 200)
    }

    public override fun handleNextItems(result: InfoItemsPage<*>) {
        super.handleNextItems(result)
        setStreamCountAndOverallDuration(result.getItems(), !result.hasNextPage())
    }

    public override fun handleResult(result: PlaylistInfo) {
        super.handleResult(result)
        headerBinding!!.getRoot().animate(true, 100)
        headerBinding!!.uploaderLayout.animate(true, 300)
        headerBinding!!.uploaderLayout.setOnClickListener(null)
        // If we have an uploader put them into the UI
        if (!TextUtils.isEmpty(result.getUploaderName())) {
            headerBinding!!.uploaderName.setText(result.getUploaderName())
            if (!TextUtils.isEmpty(result.getUploaderUrl())) {
                headerBinding!!.uploaderLayout.setOnClickListener(View.OnClickListener({ v: View? ->
                    try {
                        NavigationHelper.openChannelFragment(getFM(), result.getServiceId(),
                                result.getUploaderUrl(), result.getUploaderName())
                    } catch (e: Exception) {
                        showUiErrorSnackbar(this, "Opening channel fragment", e)
                    }
                }))
            }
        } else { // Otherwise say we have no uploader
            headerBinding!!.uploaderName.setText(R.string.playlist_no_uploader)
        }
        playlistControlBinding!!.getRoot().setVisibility(View.VISIBLE)
        if ((result.getServiceId() == ServiceList.YouTube.getServiceId()
                        && (YoutubeParsingHelper.isYoutubeMixId(result.getId())
                        || YoutubeParsingHelper.isYoutubeMusicMixId(result.getId())))) {
            // this is an auto-generated playlist (e.g. Youtube mix), so a radio is shown
            val model: ShapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCorners(CornerFamily.ROUNDED, 0f)
                    .build() // this turns the image back into a square
            headerBinding!!.uploaderAvatarView.setShapeAppearanceModel(model)
            headerBinding!!.uploaderAvatarView.setStrokeColor(AppCompatResources
                    .getColorStateList(requireContext(), R.color.transparent_background_color))
            headerBinding!!.uploaderAvatarView.setImageDrawable(
                    AppCompatResources.getDrawable(requireContext(),
                            R.drawable.ic_radio)
            )
        } else {
            PicassoHelper.loadAvatar(result.getUploaderAvatars()).tag(PICASSO_PLAYLIST_TAG)
                    .into(headerBinding!!.uploaderAvatarView)
        }
        streamCount = result.getStreamCount()
        setStreamCountAndOverallDuration(result.getRelatedItems(), !result.hasNextPage())
        val description: Description? = result.getDescription()
        if ((description != null) && (description !== Description.EMPTY_DESCRIPTION
                        ) && !Utils.isBlank(description.getContent())) {
            val ellipsizer: TextEllipsizer = TextEllipsizer(
                    headerBinding!!.playlistDescription, 5, ServiceHelper.getServiceById(result.getServiceId()))
            ellipsizer.setStateChangeListener(java.util.function.Consumer({ isEllipsized: Boolean ->
                headerBinding!!.playlistDescriptionReadMore.setText(
                        if ((java.lang.Boolean.TRUE == isEllipsized)) R.string.show_more else R.string.show_less
                )
            }))
            ellipsizer.setOnContentChanged(java.util.function.Consumer({ canBeEllipsized: Boolean ->
                headerBinding!!.playlistDescriptionReadMore.setVisibility(
                        if ((java.lang.Boolean.TRUE == canBeEllipsized)) View.VISIBLE else View.GONE)
                if ((java.lang.Boolean.TRUE == canBeEllipsized)) {
                    ellipsizer.ellipsize()
                }
            }))
            ellipsizer.setContent(description)
            headerBinding!!.playlistDescriptionReadMore.setOnClickListener(View.OnClickListener({ v: View? -> ellipsizer.toggle() }))
        } else {
            headerBinding!!.playlistDescription.setVisibility(View.GONE)
            headerBinding!!.playlistDescriptionReadMore.setVisibility(View.GONE)
        }
        if (!result.getErrors().isEmpty()) {
            showSnackBarError(ErrorInfo(result.getErrors(), UserAction.REQUESTED_PLAYLIST,
                    result.getUrl(), result))
        }
        remotePlaylistManager!!.getPlaylist(result)
                .flatMap<Int, List<PlaylistRemoteEntity>>(io.reactivex.rxjava3.functions.Function<List<PlaylistRemoteEntity>, Publisher<out Int?>>({ lists: List<PlaylistRemoteEntity> -> getUpdateProcessor(lists, result) }), BiFunction<List<PlaylistRemoteEntity?>, Int, List<PlaylistRemoteEntity?>?>({ lists: List<PlaylistRemoteEntity?>?, id: Int? -> lists }))
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistBookmarkSubscriber())
        PlayButtonHelper.initPlaylistControlClickListener((activity)!!, (playlistControlBinding)!!, this)
    }

    public override fun getPlayQueue(): PlayQueue {
        return getPlayQueue(0)
    }

    private fun getPlayQueue(index: Int): PlayQueue {
        val infoItems: MutableList<StreamInfoItem> = ArrayList()
        for (i: InfoItem? in infoListAdapter!!.getItemsList()) {
            if (i is StreamInfoItem) {
                infoItems.add(i)
            }
        }
        return PlaylistPlayQueue(
                currentInfo!!.getServiceId(),
                currentInfo!!.getUrl(),
                currentInfo!!.getNextPage(),
                infoItems,
                index
        )
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun getUpdateProcessor(
            playlists: List<PlaylistRemoteEntity>,
            result: PlaylistInfo): Flowable<Int?> {
        val noItemToUpdate: Flowable<Int?> = Flowable.just( /*noItemToUpdate=*/-1)
        if (playlists.isEmpty()) {
            return noItemToUpdate
        }
        val playlistRemoteEntity: PlaylistRemoteEntity = playlists.get(0)
        if (playlistRemoteEntity.isIdenticalTo(result)) {
            return noItemToUpdate
        }
        return remotePlaylistManager!!.onUpdate(playlists.get(0).getUid(), result).toFlowable()
    }

    private fun getPlaylistBookmarkSubscriber(): Subscriber<List<PlaylistRemoteEntity>> {
        return object : Subscriber<List<PlaylistRemoteEntity?>> {
            public override fun onSubscribe(s: Subscription) {
                if (bookmarkReactor != null) {
                    bookmarkReactor!!.cancel()
                }
                bookmarkReactor = s
                bookmarkReactor!!.request(1)
            }

            public override fun onNext(playlist: List<PlaylistRemoteEntity?>) {
                playlistEntity = if (playlist.isEmpty()) null else playlist.get(0)
                updateBookmarkButtons()
                isBookmarkButtonReady!!.set(true)
                if (bookmarkReactor != null) {
                    bookmarkReactor!!.request(1)
                }
            }

            public override fun onError(throwable: Throwable) {
                showError(ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                        "Get playlist bookmarks"))
            }

            public override fun onComplete() {}
        }
    }

    public override fun setTitle(title: String?) {
        super.setTitle(title)
        if (headerBinding != null) {
            headerBinding!!.playlistTitleView.setText(title)
        }
    }

    private fun onBookmarkClicked() {
        if (((isBookmarkButtonReady == null) || !isBookmarkButtonReady!!.get()
                        || (remotePlaylistManager == null))) {
            return
        }
        val action: Disposable
        if (currentInfo != null && playlistEntity == null) {
            action = remotePlaylistManager!!.onBookmark(currentInfo!!)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(io.reactivex.rxjava3.functions.Consumer<Long?>({ ignored: Long? -> }), io.reactivex.rxjava3.functions.Consumer({ throwable: Throwable? ->
                        showError(ErrorInfo((throwable)!!, UserAction.REQUESTED_BOOKMARK,
                                "Adding playlist bookmark"))
                    }))
        } else if (playlistEntity != null) {
            action = remotePlaylistManager!!.deletePlaylist(playlistEntity!!.getUid())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally(Action({ playlistEntity = null }))
                    .subscribe(io.reactivex.rxjava3.functions.Consumer<Int?>({ ignored: Int? -> }), io.reactivex.rxjava3.functions.Consumer({ throwable: Throwable? ->
                        showError(ErrorInfo((throwable)!!, UserAction.REQUESTED_BOOKMARK,
                                "Deleting playlist bookmark"))
                    }))
        } else {
            action = Disposable.empty()
        }
        disposables!!.add(action)
    }

    private fun updateBookmarkButtons() {
        if (playlistBookmarkButton == null || activity == null) {
            return
        }
        val drawable: Int = if (playlistEntity == null) R.drawable.ic_playlist_add else R.drawable.ic_playlist_add_check
        val titleRes: Int = if (playlistEntity == null) R.string.bookmark_playlist else R.string.unbookmark_playlist
        playlistBookmarkButton!!.setIcon(drawable)
        playlistBookmarkButton!!.setTitle(titleRes)
    }

    private fun setStreamCountAndOverallDuration(list: List<StreamInfoItem>,
                                                 isDurationComplete: Boolean) {
        if (activity != null && headerBinding != null) {
            playlistOverallDurationSeconds += list.stream()
                    .mapToLong(ToLongFunction({ x: StreamInfoItem -> x.getDuration() }))
                    .sum()
            headerBinding!!.playlistStreamCount.setText(
                    Localization.concatenateStrings(
                            Localization.localizeStreamCount(activity!!, streamCount),
                            Localization.getDurationString(playlistOverallDurationSeconds,
                                    isDurationComplete))
            )
        }
    }

    companion object {
        private val PICASSO_PLAYLIST_TAG: String = "PICASSO_PLAYLIST_TAG"
        fun getInstance(serviceId: Int, url: String?,
                        name: String?): PlaylistFragment {
            val instance: PlaylistFragment = PlaylistFragment()
            instance.setInitialData(serviceId, url, name)
            return instance
        }
    }
}
