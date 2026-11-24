package org.schabi.newpipe.fragments.list.playlist;

import static org.schabi.newpipe.extractor.utils.Utils.isBlank;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateHideRecyclerViewAllowingScrolling;
import static org.schabi.newpipe.extractor.ServiceList.getById;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.evernote.android.state.State;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.learning.LearningPlaylistContext;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.learning.LearningDifficulty;
import org.schabi.newpipe.learning.LearningReviewDialog;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.databinding.FragmentPlaylistBinding;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.databinding.PlaylistHeaderBinding;
import org.schabi.newpipe.download.BulkDownloadDialog;
import org.schabi.newpipe.download.BulkDownloadItem;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.dialog.InfoItemDialog;
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry;
import org.schabi.newpipe.learning.LearningContentManager;
import org.schabi.newpipe.learning.LearningMode;
import org.schabi.newpipe.learning.LearningPlaylistNotesDialog;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.local.search.ContextualSearchHelper;
import org.schabi.newpipe.local.search.ContextualSearchable;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.ExpandableSearchViewHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PlayButtonHelper;
import org.schabi.newpipe.util.StreamFilterControls;
import org.schabi.newpipe.util.StreamFilterSelection;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ExtractorImageCompat;
import org.schabi.newpipe.util.text.TextEllipsizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import coil3.util.CoilUtils;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PlaylistFragment extends BaseListInfoFragment<StreamInfoItem, PlaylistInfo>
        implements PlaylistControlViewHolder, ContextualSearchable {

    @NonNull
    @Override
    protected ContentBlockingHelper.Target getContentBlockingTarget() {
        return ContentBlockingHelper.Target.REMOTE_PLAYLISTS;
    }

    private CompositeDisposable disposables;
    private Subscription bookmarkReactor;
    private AtomicBoolean isBookmarkButtonReady;
    private final BookmarkActionGuard bookmarkActionGuard = new BookmarkActionGuard();
    private Disposable streamStateWorker;
    private Disposable bookmarkMetadataUpdater;

    private RemotePlaylistManager remotePlaylistManager;
    private LearningContentManager learningContentManager;
    private PlaylistRemoteEntity playlistEntity;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private PlaylistHeaderBinding headerBinding;
    private PlaylistControlBinding playlistControlBinding;
    private FragmentPlaylistBinding binding;

    private MenuItem playlistBookmarkButton;

    private long streamCount;
    private long playlistOverallDurationSeconds;
    @State
    protected String selectedStreamFilters = "";
    @State
    protected PlaylistSortOrder selectedPlaylistSort = PlaylistSortOrder.PLAYLIST_ORDER;
    @State
    protected boolean bulkDownloadPending;
    @State
    protected String contextualSearchQuery = "";
    @State
    protected boolean standaloneSearchExpanded;
    private final List<StreamInfoItem> unfilteredItems = new ArrayList<>();
    private final Map<String, StreamStateEntity> streamStates = new HashMap<>();
    private HistoryRecordManager historyRecordManager;

    public static PlaylistFragment getInstance(final int serviceId, final String url,
                                               final String name) {
        final PlaylistFragment instance = new PlaylistFragment();
        instance.setInitialData(serviceId, url, name);
        return instance;
    }

    public PlaylistFragment() {
        super(UserAction.REQUESTED_PLAYLIST);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        disposables = new CompositeDisposable();
        isBookmarkButtonReady = new AtomicBoolean(false);
        remotePlaylistManager = new RemotePlaylistManager(
                NewPipeDatabase.getInstance(requireContext()),
                ProfileManager.getActiveProfileId(requireContext()));
        learningContentManager = LearningContentManager.getInstance(requireContext());
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        headerBinding = PlaylistHeaderBinding
                .inflate(activity.getLayoutInflater(), itemsList, false);
        playlistControlBinding = headerBinding.playlistControl;

        return headerBinding::getRoot;
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        binding = FragmentPlaylistBinding.bind(rootView);
        historyRecordManager = new HistoryRecordManager(requireContext());
        StreamFilterControls.bind(binding.streamFilterChips, selectedStreamFilters,
                true, selection -> {
                    selectedStreamFilters = selection;
                    refreshStreamStates();
                });
        updatePlaylistSortButton();
        binding.playlistSortButton.setOnClickListener(ignored -> showPlaylistSortDialog());

        // Is mini variant still relevant?
        // Only the remote playlist screen uses it now
        infoListAdapter.setUseMiniVariant(true);

        observeBookmark();
    }

    @Override
    protected PlayQueue getPlayQueueForSelection(final StreamInfoItem item) {
        return isLearningPlaylist() ? getPlayQueueStartingAt(item) : null;
    }

    private boolean isLearningPlaylist() {
        return LearningMode.isEnabled(requireContext())
                && learningContentManager.isRemotePlaylistMarked(serviceId, url);
    }

    private PlayQueue getPlayQueueStartingAt(final StreamInfoItem infoItem) {
        return getPlayQueue(Math.max(infoListAdapter.getItemsList().indexOf(infoItem), 0));
    }

    @Override
    protected void showInfoItemDialog(final StreamInfoItem item) {
        final Context context = getContext();
        try {
            final InfoItemDialog.Builder dialogBuilder =
                    new InfoItemDialog.Builder(getActivity(), context, this, item);

            dialogBuilder
                    .setAction(
                            StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND,
                            (f, infoItem) -> NavigationHelper.playOnBackgroundPlayer(
                                    context, getPlayQueueStartingAt(infoItem), true))
                    .create()
                    .show();
        } catch (final IllegalArgumentException e) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, item);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_playlist, menu);

        final MenuItem searchItem = menu.findItem(R.id.menu_item_search_content);
        searchItem.setVisible(!useAsFrontPage);
        if (!useAsFrontPage) {
            ExpandableSearchViewHelper.configure(
                    searchItem,
                    getString(R.string.contextual_search_hint, name),
                    contextualSearchQuery,
                    standaloneSearchExpanded,
                    this::setContextualSearchQuery,
                    expanded -> standaloneSearchExpanded = expanded);
        }

        playlistBookmarkButton = menu.findItem(R.id.menu_item_bookmark);
        updateBookmarkButtons();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_item_learning_review).setVisible(
                LearningDifficulty.isEnabled(requireContext())
                        && learningContentManager.isRemotePlaylistMarked(serviceId, url));
        menu.findItem(R.id.menu_item_learning_notes).setVisible(isLearningPlaylist());
        final MenuItem learningItem = menu.findItem(R.id.menu_item_learning_content);
        if (learningItem == null) {
            return;
        }
        final boolean visible = LearningMode.isEnabled(requireContext()) && currentInfo != null;
        learningItem.setVisible(visible);
        if (visible) {
            learningItem.setTitle(learningContentManager.isRemotePlaylistMarked(serviceId, url)
                    ? R.string.learning_remove_content : R.string.learning_mark_content);
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        headerBinding = null;
        playlistControlBinding = null;
        playlistBookmarkButton = null;

        super.onDestroyView();
        if (isBookmarkButtonReady != null) {
            isBookmarkButtonReady.set(false);
        }

        if (disposables != null) {
            disposables.clear();
        }
        if (bookmarkReactor != null) {
            bookmarkReactor.cancel();
        }
        if (streamStateWorker != null) {
            streamStateWorker.dispose();
        }

        bookmarkReactor = null;
        streamStateWorker = null;
        bookmarkMetadataUpdater = null;
        bookmarkActionGuard.finish();
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        unfilteredItems.clear();
        streamStates.clear();
        if (streamStateWorker != null) {
            streamStateWorker.dispose();
            streamStateWorker = null;
        }
        super.startLoading(forceLoad);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (disposables != null) {
            disposables.dispose();
        }

        disposables = null;
        remotePlaylistManager = null;
        playlistEntity = null;
        isBookmarkButtonReady = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<ListExtractor.InfoItemsPage<StreamInfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMorePlaylistItems(serviceId, url, currentNextPage);
    }

    @Override
    protected Single<PlaylistInfo> loadResult(final boolean forceLoad) {
        return ExtractorHelper.getPlaylistInfo(serviceId, url, forceLoad);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_item_learning_review) {
            LearningReviewDialog.show(getParentFragmentManager(),
                    LearningContentManager.remotePlaylistSourceId(serviceId, url),
                    ProfileManager.getActiveProfileId(requireContext()), false);
            return true;
        }
        final int itemId = item.getItemId();
        if (itemId == R.id.menu_item_learning_notes) {
            LearningPlaylistNotesDialog.show(getParentFragmentManager(),
                    LearningContentManager.remotePlaylistSourceId(serviceId, url),
                    ProfileManager.getActiveProfileId(requireContext()));
            return true;
        }
        if (itemId == R.id.action_settings) {
            NavigationHelper.openSettings(requireContext());
        } else if (itemId == R.id.menu_item_openInBrowser) {
            ShareUtils.openUrlInBrowser(requireContext(), url);
        } else if (itemId == R.id.menu_item_share) {
            ShareUtils.shareText(requireContext(), name, url, currentInfo == null
                    ? List.of() : ExtractorImageCompat.thumbnailImages(currentInfo));
        } else if (itemId == R.id.menu_item_bookmark) {
            onBookmarkClicked();
        } else if (itemId == R.id.menu_item_append_playlist) {
            if (currentInfo != null) {
                disposables.add(PlaylistDialog.createCorrespondingDialog(
                        getContext(),
                        getPlayQueue()
                                .getStreams()
                                .stream()
                                .map(StreamEntity::new)
                                .collect(Collectors.toList()),
                        dialog -> dialog.show(getFM(), TAG)
                ));
            }
        } else if (itemId == R.id.menu_item_download_playlist) {
            beginBulkDownload();
        } else if (itemId == R.id.menu_item_learning_content) {
            toggleLearningPlaylist();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void toggleLearningPlaylist() {
        if (currentInfo == null) {
            return;
        }
        final boolean marked = learningContentManager.isRemotePlaylistMarked(serviceId, url);
        final List<StreamEntity> streams = unfilteredItems.stream()
                .map(StreamEntity::new)
                .collect(Collectors.toList());
        disposables.add(learningContentManager.setRemotePlaylistMarked(
                        serviceId, url, currentInfo.getName(),
                        new PlaylistRemoteEntity(currentInfo).getThumbnailUrl(),
                        streams, !marked)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            requireActivity().invalidateOptionsMenu();
                            Toast.makeText(requireContext(), marked
                                            ? R.string.learning_content_removed
                                            : R.string.learning_content_added,
                                    Toast.LENGTH_SHORT).show();
                            if (!marked) {
                                indexRemainingLearningPlaylistItems(currentInfo.getNextPage());
                            }
                        },
                        error -> Toast.makeText(requireContext(),
                                R.string.learning_content_update_error,
                                Toast.LENGTH_SHORT).show()
                ));
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();
        animate(headerBinding.getRoot(), false, 200);
        animateHideRecyclerViewAllowingScrolling(itemsList);

        CoilUtils.dispose(headerBinding.uploaderAvatarView);
        animate(headerBinding.uploaderLayout, false, 200);
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage<StreamInfoItem> result) {
        super.handleNextItems(result);
        unfilteredItems.addAll(result.getItems());
        indexLearningPlaylistItems(result.getItems());
        refreshStreamStatesAfterPageLoad();
        setStreamCountAndOverallDuration(result.getItems(), !result.hasNextPage());
        continueLoadingPlaylistForSorting();
        continueBulkDownloadAfterPageLoad();
    }

    @Override
    public void handleResult(@NonNull final PlaylistInfo result) {
        super.handleResult(result);
        unfilteredItems.clear();
        unfilteredItems.addAll(result.getRelatedItems());
        indexLearningPlaylistItems(result.getRelatedItems());

        animate(headerBinding.getRoot(), true, 100);
        animate(headerBinding.uploaderLayout, true, 300);
        headerBinding.uploaderLayout.setOnClickListener(null);
        // If we have an uploader put them into the UI
        if (!TextUtils.isEmpty(result.getUploaderName())) {
            headerBinding.uploaderName.setText(result.getUploaderName());
            if (!TextUtils.isEmpty(result.getUploaderUrl())) {
                headerBinding.uploaderLayout.setOnClickListener(v -> {
                    try {
                        NavigationHelper.openChannelFragment(getFM(), result.getServiceId(),
                                result.getUploaderUrl(), result.getUploaderName());
                    } catch (final Exception e) {
                        ErrorUtil.showUiErrorSnackbar(this, "Opening channel fragment", e);
                    }
                });
            }
        } else { // Otherwise say we have no uploader
            headerBinding.uploaderName.setText(R.string.playlist_no_uploader);
        }

        playlistControlBinding.getRoot().setVisibility(View.VISIBLE);

        if (result.getServiceId() == ServiceList.YouTube.getServiceId()
                && (YoutubeParsingHelper.isYoutubeMixId(result.getId())
                || YoutubeParsingHelper.isYoutubeMusicMixId(result.getId()))) {
            // this is an auto-generated playlist (e.g. Youtube mix), so a radio is shown
            final ShapeAppearanceModel model = ShapeAppearanceModel.builder()
                    .setAllCorners(CornerFamily.ROUNDED, 0f)
                    .build(); // this turns the image back into a square
            headerBinding.uploaderAvatarView.setShapeAppearanceModel(model);
            headerBinding.uploaderAvatarView.setStrokeColor(AppCompatResources
                    .getColorStateList(requireContext(), R.color.transparent_background_color));
            headerBinding.uploaderAvatarView.setImageDrawable(
                    AppCompatResources.getDrawable(requireContext(),
                    R.drawable.ic_radio)
            );
        } else {
            CoilHelper.INSTANCE.loadAvatar(headerBinding.uploaderAvatarView,
                    ExtractorImageCompat.uploaderAvatarImages(result));
        }

        streamCount = result.getStreamCount();
        setStreamCountAndOverallDuration(result.getRelatedItems(), !result.hasNextPage());
        final boolean isSortableYoutubePlaylist =
                result.getServiceId() == ServiceList.YouTube.getServiceId()
                        && !YoutubeParsingHelper.isYoutubeMixId(result.getId())
                        && !YoutubeParsingHelper.isYoutubeMusicMixId(result.getId());
        binding.playlistSortButton.setVisibility(
                isSortableYoutubePlaylist ? View.VISIBLE : View.GONE);
        updatePlaylistSortButton();

        final Description description = Description.EMPTY_DESCRIPTION;
        if (description != null && description != Description.EMPTY_DESCRIPTION
                && !isBlank(description.getContent())) {
            final TextEllipsizer ellipsizer = new TextEllipsizer(
                    headerBinding.playlistDescription, 5, getById(result.getServiceId()));
            ellipsizer.setStateChangeListener(isEllipsized ->
                headerBinding.playlistDescriptionReadMore.setText(
                        Boolean.TRUE.equals(isEllipsized) ? R.string.show_more : R.string.show_less
                ));
            ellipsizer.setOnContentChanged(canBeEllipsized -> {
                headerBinding.playlistDescriptionReadMore.setVisibility(
                        Boolean.TRUE.equals(canBeEllipsized) ? View.VISIBLE : View.GONE);
                if (Boolean.TRUE.equals(canBeEllipsized)) {
                    ellipsizer.ellipsize();
                }
            });
            ellipsizer.setContent(description);
            headerBinding.playlistDescriptionReadMore.setOnClickListener(v -> ellipsizer.toggle());
            headerBinding.playlistDescription.setOnClickListener(v -> ellipsizer.toggle());
        } else {
            headerBinding.playlistDescription.setVisibility(View.GONE);
            headerBinding.playlistDescriptionReadMore.setVisibility(View.GONE);
        }

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(new ErrorInfo(result.getErrors(), UserAction.REQUESTED_PLAYLIST,
                    result.getUrl(), result));
        }

        updateBookmarkMetadataIfNeeded();
        updateBookmarkButtons();
        requireActivity().invalidateOptionsMenu();

        PlayButtonHelper.initPlaylistControlClickListener(activity, playlistControlBinding, this);
        refreshStreamStatesAfterPageLoad();
        continueLoadingPlaylistForSorting();
    }

    private void beginBulkDownload() {
        if (currentInfo == null || unfilteredItems.isEmpty()) {
            Toast.makeText(requireContext(), R.string.bulk_download_empty,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (hasMoreItems()) {
            bulkDownloadPending = true;
            Toast.makeText(requireContext(), R.string.bulk_download_loading_playlist,
                    Toast.LENGTH_SHORT).show();
            if (!isLoading.get()) {
                loadMoreItems();
            }
            return;
        }
        showBulkDownloadDialog();
    }

    private void continueBulkDownloadAfterPageLoad() {
        if (!bulkDownloadPending) {
            return;
        }
        if (hasMoreItems()) {
            if (!isLoading.get()) {
                loadMoreItems();
            }
            return;
        }
        bulkDownloadPending = false;
        showBulkDownloadDialog();
    }

    private void showBulkDownloadDialog() {
        final List<BulkDownloadItem> downloadItems = unfilteredItems.stream()
                .map(BulkDownloadItem::from)
                .collect(Collectors.toList());
        if (downloadItems.isEmpty()) {
            Toast.makeText(requireContext(), R.string.bulk_download_empty,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        BulkDownloadDialog.newInstance(downloadItems)
                .show(getChildFragmentManager(), "bulkDownloadDialog");
    }

    private void indexLearningPlaylistItems(final List<StreamInfoItem> items) {
        if (!learningContentManager.isRemotePlaylistMarked(serviceId, url) || items.isEmpty()) {
            return;
        }
        disposables.add(learningContentManager.addRemotePlaylistStreams(
                        serviceId,
                        url,
                        items.stream().map(StreamEntity::new).collect(Collectors.toList()))
                .subscribe(
                        () -> { },
                        error -> Log.w(TAG, "Unable to index Learning playlist items", error)
                ));
    }

    private void indexRemainingLearningPlaylistItems(@Nullable final Page nextPage) {
        if (nextPage == null
                || !learningContentManager.isRemotePlaylistMarked(serviceId, url)) {
            return;
        }
        disposables.add(ExtractorHelper.getMorePlaylistItems(serviceId, url, nextPage)
                .subscribeOn(Schedulers.io())
                .flatMap(page -> learningContentManager.addRemotePlaylistStreams(
                                serviceId,
                                url,
                                page.getItems().stream()
                                        .map(StreamEntity::new)
                                        .collect(Collectors.toList()))
                        .andThen(Single.just(page)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        page -> indexRemainingLearningPlaylistItems(
                                page.hasNextPage() ? page.getNextPage() : null),
                        error -> Log.w(TAG, "Unable to finish indexing Learning playlist", error)
                ));
    }

    private void refreshStreamStatesAfterPageLoad() {
        if (!StreamFilterSelection.parse(selectedStreamFilters).isActive()
                && selectedPlaylistSort == PlaylistSortOrder.PLAYLIST_ORDER) {
            if (ContextualSearchHelper.isActive(contextualSearchQuery)) {
                applyStreamFilter();
            }
            return;
        }
        refreshStreamStates();
    }

    private void refreshStreamStates() {
        applyStreamFilter();
        if (!filterNeedsStreamStates()) {
            streamStates.clear();
            if (streamStateWorker != null) {
                streamStateWorker.dispose();
                streamStateWorker = null;
            }
            return;
        }

        final List<StreamInfoItem> snapshot = new ArrayList<>(unfilteredItems);
        if (streamStateWorker != null) {
            streamStateWorker.dispose();
        }
        streamStateWorker = historyRecordManager.loadStreamStateBatch(snapshot)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(states -> {
                    if (!unfilteredItems.equals(snapshot)) {
                        return;
                    }
                    streamStates.clear();
                    for (int i = 0; i < snapshot.size(); i++) {
                        streamStates.put(snapshot.get(i).getUrl(), states.get(i));
                    }
                    applyStreamFilter();
                }, throwable -> Log.w(TAG, "Unable to load playlist stream states", throwable));
    }

    private boolean filterNeedsStreamStates() {
        return StreamFilterSelection.parse(selectedStreamFilters).getNeedsStreamStates();
    }

    private void applyStreamFilter() {
        if (infoListAdapter == null || currentInfo == null || playlistControlBinding == null) {
            return;
        }
        final List<StreamInfoItem> sortedItems = PlaylistSortHelper.itemsForDisplay(
                unfilteredItems, selectedStreamFilters, streamStates, selectedPlaylistSort);
        final List<StreamInfoItem> displayedItems = ContextualSearchHelper.filter(
                sortedItems, contextualSearchQuery,
                item -> new String[]{item.getName(), item.getUploaderName()});
        infoListAdapter.clearStreamItemList();
        infoListAdapter.addInfoItemList(displayedItems);
        showListFooter(hasMoreItems());
        final boolean isEmpty = infoListAdapter.getItemsList().isEmpty();
        playlistControlBinding.getRoot().setVisibility(
                isEmpty ? View.GONE : View.VISIBLE);
        if (isEmpty && !hasMoreItems()) {
            showEmptyState();
            if (ContextualSearchHelper.isActive(contextualSearchQuery)) {
                setEmptyStateMessage(R.string.search_no_results);
            }
        } else {
            hideLoading();
        }
    }

    @Override
    public void setContextualSearchQuery(@NonNull final String query) {
        contextualSearchQuery = ContextualSearchHelper.normalizeQuery(query);
        applyStreamFilter();
    }

    private void showPlaylistSortDialog() {
        final PlaylistSortOrder[] sortOrders = PlaylistSortOrder.values();
        final String[] labels = new String[sortOrders.length];
        for (int i = 0; i < sortOrders.length; i++) {
            labels[i] = getString(sortOrders[i].getLabel());
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sort)
                .setSingleChoiceItems(labels, selectedPlaylistSort.ordinal(), (dialog, which) -> {
                    dialog.dismiss();
                    applyPlaylistSort(sortOrders[which]);
                })
                .show();
    }

    private void applyPlaylistSort(final PlaylistSortOrder sortOrder) {
        if (sortOrder == selectedPlaylistSort) {
            return;
        }

        selectedPlaylistSort = sortOrder;
        updatePlaylistSortButton();
        itemsList.scrollToPosition(0);
        applyStreamFilter();
        continueLoadingPlaylistForSorting();
    }

    private void updatePlaylistSortButton() {
        if (binding == null) {
            return;
        }
        final String label = getString(selectedPlaylistSort.getLabel());
        binding.playlistSortButton.setText(label);
        binding.playlistSortButton.setContentDescription(
                getString(R.string.playlist_sort_content_description, label));
    }

    private void continueLoadingPlaylistForSorting() {
        if (binding == null || selectedPlaylistSort == PlaylistSortOrder.PLAYLIST_ORDER) {
            return;
        }

        if (hasMoreItems()) {
            binding.playlistSortButton.setEnabled(false);
            if (!isLoading.get()) {
                loadMoreItems();
            }
        } else {
            binding.playlistSortButton.setEnabled(true);
        }
    }

    public PlayQueue getPlayQueue() {
        return getPlayQueue(0);
    }

    private PlayQueue getPlayQueue(final int index) {
        final List<StreamInfoItem> infoItems = new ArrayList<>();
        for (final InfoItem i : infoListAdapter.getItemsList()) {
            if (i instanceof StreamInfoItem) {
                infoItems.add((StreamInfoItem) i);
            }
        }
        final boolean learning = isLearningPlaylist();
        final int selectedIndex = learning && index >= 0 && index < infoItems.size()
                ? Math.max(0, unfilteredItems.indexOf(infoItems.get(index))) : index;
        final PlayQueue queue = new PlaylistPlayQueue(
                currentInfo.getServiceId(),
                currentInfo.getUrl(),
                learning || selectedPlaylistSort == PlaylistSortOrder.PLAYLIST_ORDER
                        && !ContextualSearchHelper.isActive(contextualSearchQuery)
                        ? currentNextPage : null,
                learning ? unfilteredItems : infoItems,
                selectedIndex
        );
        if (learning) {
            queue.setLearningPlaylistContext(new LearningPlaylistContext(
                    LearningContentManager.remotePlaylistSourceId(serviceId, url),
                    currentInfo.getName(),
                    ProfileManager.getActiveProfileId(requireContext())));
        }
        return queue;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void observeBookmark() {
        if (remotePlaylistManager == null || url == null || isBookmarkButtonReady == null) {
            return;
        }

        isBookmarkButtonReady.set(false);
        updateBookmarkButtons();
        remotePlaylistManager.getPlaylist(serviceId, url)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistBookmarkSubscriber());
    }

    private void updateBookmarkMetadataIfNeeded() {
        if (remotePlaylistManager == null || currentInfo == null || playlistEntity == null
                || playlistEntity.isIdenticalTo(currentInfo)
                || bookmarkMetadataUpdater != null && !bookmarkMetadataUpdater.isDisposed()) {
            return;
        }

        final long playlistUid = playlistEntity.getUid();
        bookmarkMetadataUpdater = remotePlaylistManager.onUpdate(
                playlistUid, currentInfo, playlistEntity.getThumbnailUrl())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> { /* The database observer refreshes the entity. */ },
                        throwable -> showError(new ErrorInfo(throwable,
                                UserAction.REQUESTED_BOOKMARK,
                                "Updating playlist bookmark")));
        disposables.add(bookmarkMetadataUpdater);
    }

    private Subscriber<List<PlaylistRemoteEntity>> getPlaylistBookmarkSubscriber() {
        return new Subscriber<>() {
            @Override
            public void onSubscribe(final Subscription s) {
                if (bookmarkReactor != null) {
                    bookmarkReactor.cancel();
                }
                bookmarkReactor = s;
                bookmarkReactor.request(1);
            }

            @Override
            public void onNext(final List<PlaylistRemoteEntity> playlist) {
                playlistEntity = playlist.isEmpty() ? null : playlist.get(0);

                isBookmarkButtonReady.set(true);
                updateBookmarkMetadataIfNeeded();
                updateBookmarkButtons();

                if (bookmarkReactor != null) {
                    bookmarkReactor.request(1);
                }
            }

            @Override
            public void onError(final Throwable throwable) {
                isBookmarkButtonReady.set(false);
                updateBookmarkButtons();
                showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                        "Get playlist bookmarks"));
            }

            @Override
            public void onComplete() { }
        };
    }

    @Override
    public void setTitle(final String title) {
        super.setTitle(title);
        if (headerBinding != null) {
            headerBinding.playlistTitleView.setText(title);
        }
    }

    private void onBookmarkClicked() {
        if (isBookmarkButtonReady == null || !isBookmarkButtonReady.get()
                || remotePlaylistManager == null
                || (playlistEntity == null && currentInfo == null)
                || !bookmarkActionGuard.tryStart()) {
            return;
        }

        updateBookmarkButtons();
        final Disposable action;

        if (currentInfo != null && playlistEntity == null) {
            action = remotePlaylistManager.onBookmark(currentInfo)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally(this::finishBookmarkAction)
                    .subscribe(ignored -> { /* Do nothing */ }, throwable ->
                            showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                                    "Adding playlist bookmark")));
        } else if (playlistEntity != null) {
            final boolean returnToBookmarks = cancelPlaylistLoadingForRemoval();
            action = remotePlaylistManager.deletePlaylist(playlistEntity.getUid())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally(this::finishBookmarkAction)
                    .subscribe(ignored -> {
                        playlistEntity = null;
                        updateBookmarkButtons();
                        if (returnToBookmarks && isAdded()) {
                            getFM().popBackStack();
                        }
                    }, throwable ->
                            showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                                    "Deleting playlist bookmark")));
        } else {
            bookmarkActionGuard.finish();
            action = Disposable.empty();
        }

        disposables.add(action);
    }

    /**
     * Stops an expensive playlist extraction before deleting its small database bookmark row.
     * This makes removal available even when the remote playlist itself cannot be opened within
     * the process heap limit.
     *
     * @return whether the initial playlist load was cancelled and the fragment should return to
     *         the bookmarks screen after a successful deletion
     */
    private boolean cancelPlaylistLoadingForRemoval() {
        final boolean initialLoadWasRunning = currentInfo == null && currentWorker != null;
        if (currentWorker != null) {
            currentWorker.dispose();
            currentWorker = null;
        }
        if (streamStateWorker != null) {
            streamStateWorker.dispose();
            streamStateWorker = null;
        }
        isLoading.set(false);
        return initialLoadWasRunning;
    }

    private void finishBookmarkAction() {
        bookmarkActionGuard.finish();
        updateBookmarkButtons();
    }

    private void updateBookmarkButtons() {
        if (playlistBookmarkButton == null || activity == null) {
            return;
        }

        final int drawable = playlistEntity == null
                ? R.drawable.ic_playlist_add : R.drawable.ic_playlist_add_check;

        final int titleRes = playlistEntity == null
                ? R.string.bookmark_playlist : R.string.unbookmark_playlist;

        playlistBookmarkButton.setIcon(drawable);
        playlistBookmarkButton.setTitle(titleRes);
        playlistBookmarkButton.setEnabled(BookmarkButtonState.isEnabled(
                isBookmarkButtonReady != null && isBookmarkButtonReady.get(),
                playlistEntity != null,
                currentInfo != null,
                bookmarkActionGuard.isRunning()));
    }

    private void setStreamCountAndOverallDuration(final List<StreamInfoItem> list,
                                                  final boolean isDurationComplete) {
        if (activity != null && headerBinding != null) {
            playlistOverallDurationSeconds += list.stream()
                    .mapToLong(x -> x.getDuration())
                    .sum();
            headerBinding.playlistStreamCount.setText(
                Localization.concatenateStrings(
                    Localization.localizeStreamCount(activity, streamCount),
                    Localization.getDurationString(playlistOverallDurationSeconds,
                            isDurationComplete, true))
            );
        }
    }

}
