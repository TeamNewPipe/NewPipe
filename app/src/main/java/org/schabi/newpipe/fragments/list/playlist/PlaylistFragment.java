package org.schabi.newpipe.fragments.list.playlist;

import static org.schabi.newpipe.extractor.utils.Utils.isBlank;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateHideRecyclerViewAllowingScrolling;
import static org.schabi.newpipe.util.ServiceHelper.getServiceById;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.databinding.PlaylistHeaderBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.dialog.InfoItemDialog;
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PlayButtonHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.image.PicassoHelper;
import org.schabi.newpipe.util.text.TextEllipsizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class PlaylistFragment extends BaseListInfoFragment<StreamInfoItem, PlaylistInfo>
        implements PlaylistControlViewHolder {

    private static final String PICASSO_PLAYLIST_TAG = "PICASSO_PLAYLIST_TAG";

    private CompositeDisposable disposables;
    private Subscription bookmarkReactor;
    private AtomicBoolean isBookmarkButtonReady;

    private RemotePlaylistManager remotePlaylistManager;
    private PlaylistRemoteEntity playlistEntity;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private PlaylistHeaderBinding headerBinding;
    private PlaylistControlBinding playlistControlBinding;

    private MenuItem playlistBookmarkButton;

    private long streamCount;
    private long playlistOverallDurationSeconds;

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
        remotePlaylistManager = new RemotePlaylistManager(NewPipeDatabase
                .getInstance(requireContext()));
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

        // Is mini variant still relevant?
        // Only the remote playlist screen uses it now
        infoListAdapter.setUseMiniVariant(true);
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

        playlistBookmarkButton = menu.findItem(R.id.menu_item_bookmark);
        updateBookmarkButtons();
    }

    @Override
    public void onDestroyView() {
        headerBinding = null;
        playlistControlBinding = null;

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

        bookmarkReactor = null;
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
        switch (item.getItemId()) {
            case R.id.action_settings:
                NavigationHelper.openSettings(requireContext());
                break;
            case R.id.menu_item_openInBrowser:
                ShareUtils.openUrlInBrowser(requireContext(), url);
                break;
            case R.id.menu_item_share:
                ShareUtils.shareText(requireContext(), name, url,
                        currentInfo == null ? List.of() : currentInfo.getThumbnails());
                break;
            case R.id.menu_item_bookmark:
                onBookmarkClicked();
                break;
            case R.id.menu_item_append_playlist:
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
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();
        animate(headerBinding.getRoot(), false, 200);
        animateHideRecyclerViewAllowingScrolling(itemsList);

        PicassoHelper.cancelTag(PICASSO_PLAYLIST_TAG);
        animate(headerBinding.uploaderLayout, false, 200);
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage result) {
        super.handleNextItems(result);
        setStreamCountAndOverallDuration(result.getItems(), !result.hasNextPage());
    }

    @Override
    public void handleResult(@NonNull final PlaylistInfo result) {
        super.handleResult(result);

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
            PicassoHelper.loadAvatar(result.getUploaderAvatars()).tag(PICASSO_PLAYLIST_TAG)
                    .into(headerBinding.uploaderAvatarView);
        }

        streamCount = result.getStreamCount();
        setStreamCountAndOverallDuration(result.getRelatedItems(), !result.hasNextPage());

        final Description description = result.getDescription();
        if (description != null && description != Description.EMPTY_DESCRIPTION
                && !isBlank(description.getContent())) {
            final TextEllipsizer ellipsizer = new TextEllipsizer(
                    headerBinding.playlistDescription, 5, getServiceById(result.getServiceId()));
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

        remotePlaylistManager.getPlaylist(result)
                .flatMap(lists -> getUpdateProcessor(lists, result), (lists, id) -> lists)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistBookmarkSubscriber());

        PlayButtonHelper.initPlaylistControlClickListener(activity, playlistControlBinding, this);
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
        return new PlaylistPlayQueue(
                currentInfo.getServiceId(),
                currentInfo.getUrl(),
                currentInfo.getNextPage(),
                infoItems,
                index
        );
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private Flowable<Integer> getUpdateProcessor(
            @NonNull final List<PlaylistRemoteEntity> playlists,
            @NonNull final PlaylistInfo result) {
        final Flowable<Integer> noItemToUpdate = Flowable.just(/*noItemToUpdate=*/-1);
        if (playlists.isEmpty()) {
            return noItemToUpdate;
        }

        final PlaylistRemoteEntity playlistRemoteEntity = playlists.get(0);
        if (playlistRemoteEntity.isIdenticalTo(result)) {
            return noItemToUpdate;
        }

        return remotePlaylistManager.onUpdate(playlists.get(0).getUid(), result).toFlowable();
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

                updateBookmarkButtons();
                isBookmarkButtonReady.set(true);

                if (bookmarkReactor != null) {
                    bookmarkReactor.request(1);
                }
            }

            @Override
            public void onError(final Throwable throwable) {
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
                || remotePlaylistManager == null) {
            return;
        }

        final Disposable action;

        if (currentInfo != null && playlistEntity == null) {
            action = remotePlaylistManager.onBookmark(currentInfo)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ignored -> { /* Do nothing */ }, throwable ->
                            showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                                    "Adding playlist bookmark")));
        } else if (playlistEntity != null) {
            action = remotePlaylistManager.deletePlaylist(playlistEntity.getUid())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally(() -> playlistEntity = null)
                    .subscribe(ignored -> { /* Do nothing */ }, throwable ->
                            showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                                    "Deleting playlist bookmark")));
        } else {
            action = Disposable.empty();
        }

        disposables.add(action);
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
