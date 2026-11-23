package org.schabi.newpipe.fragments.list.comments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;
import java.util.Queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.button.MaterialButton;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.comments.CommentArchive;
import org.schabi.newpipe.local.comments.CommentArchiveRequest;
import org.schabi.newpipe.local.comments.CachedCommentsDialog;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentSortOrder;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.local.history.ListFastScroller;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.ExtractorHelper;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class CommentsFragment extends BaseListInfoFragment<CommentsInfoItem, CommentsInfo> {
    private static final String SORT_PREFERENCE = "comment_sort_newest";
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final CommentSearchController commentSearch = new CommentSearchController();
    private TextView emptyStateDesc;
    private ListFastScroller fastScroller;
    private CommentSortOrder sortOrder = CommentSortOrder.TOP;

    public static CommentsFragment getInstance(final int serviceId, final String url,
                                               final String name) {
        final CommentsFragment instance = new CommentsFragment();
        instance.setInitialData(serviceId, url, name);
        return instance;
    }

    public CommentsFragment() {
        super(UserAction.REQUESTED_COMMENTS);
    }

    @NonNull
    @Override
    protected ContentBlockingHelper.Target getContentBlockingTarget() {
        return ContentBlockingHelper.Target.COMMENTS;
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        fastScroller = ListFastScroller.attach(itemsList);

        emptyStateDesc = rootView.findViewById(R.id.empty_state_desc);
        if (savedInstanceState != null) {
            commentSearch.setQuery(savedInstanceState.getString("comment_query", ""));
            commentSearch.setExpanded(savedInstanceState.getBoolean("comment_search_expanded"));
            commentSearch.setPinnedOnly(savedInstanceState.getBoolean("comments_pinned_only"));
        }
        commentSearch.bind(rootView, this::applyCommentSearch, this::loadMoreItems);
        final View cachedAction = rootView.findViewById(R.id.comments_show_cached);
        cachedAction.setVisibility(CommentArchive.isEnabled(requireContext())
                ? View.VISIBLE : View.GONE);
        cachedAction.setOnClickListener(v -> CachedCommentsDialog.show(
                requireContext(), serviceId, url));
        final Chip pinnedOnly = rootView.findViewById(R.id.comments_pinned_only);
        pinnedOnly.setChecked(commentSearch.getPinnedOnly());
        pinnedOnly.setOnCheckedChangeListener((button, checked) -> {
            commentSearch.setPinnedOnly(checked);
            applyCommentSearch();
        });
        final MaterialButton dateToggle = rootView.findViewById(R.id.comments_date_toggle);
        final Runnable updateDateToggle = () -> dateToggle.setText(
                CommentDatePresentation.isExact(requireContext())
                        ? R.string.comments_date_exact : R.string.comments_date_relative);
        updateDateToggle.run();
        dateToggle.setOnClickListener(view -> {
            CommentDatePresentation.setExact(requireContext(),
                    !CommentDatePresentation.isExact(requireContext()));
            updateDateToggle.run();
            if (infoListAdapter != null) {
                infoListAdapter.notifyDataSetChanged();
            }
        });
        if (getParentFragment() instanceof VideoDetailFragment detail) {
            detail.getCommentsMiniPlayerAction().bind(rootView, getViewLifecycleOwner());
        }
        final boolean supportsSorting = serviceId == ServiceList.YouTube.getServiceId();
        final boolean newest = savedInstanceState != null
                ? savedInstanceState.getBoolean("comments_newest", false)
                : PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .getBoolean(SORT_PREFERENCE, false);
        sortOrder = supportsSorting
                ? currentInfo != null ? currentInfo.getSortOrder()
                        : newest ? CommentSortOrder.NEWEST : CommentSortOrder.TOP
                : CommentSortOrder.TOP;
        final ChipGroup sorting = rootView.findViewById(R.id.comment_sort);
        sorting.setVisibility(supportsSorting ? View.VISIBLE : View.GONE);
        sorting.check(sortOrder == CommentSortOrder.NEWEST
                ? R.id.comments_newest : R.id.comments_top);
        sorting.setOnCheckedStateChangeListener((group, checkedIds) -> {
            final CommentSortOrder selected = checkedIds.contains(R.id.comments_newest)
                    ? CommentSortOrder.NEWEST : CommentSortOrder.TOP;
            if (sortOrder != selected) {
                sortOrder = selected;
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                        .putBoolean(SORT_PREFERENCE, selected == CommentSortOrder.NEWEST).apply();
                currentNextPage = null;
                startLoading(true);
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_comments, container, false);
    }

    @Override
    public void onDestroyView() {
        if (fastScroller != null) {
            fastScroller.close();
            fastScroller = null;
        }
        commentSearch.unbind();
        super.onDestroyView();
    }

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(commentSearch.snapshot());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        final Object comments = savedObjects.poll();
        if (comments instanceof List) {
            commentSearch.restore((List<CommentsInfoItem>) comments);
        }
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        commentSearch.clearItems();
        commentSearch.updatePagination(false, true);
        super.startLoading(forceLoad);
    }

    @Override
    protected void loadMoreItems() {
        if (isLoading.get() || currentInfo == null || !hasMoreItems()) {
            return;
        }
        commentSearch.updatePagination(hasMoreItems(), true);
        super.loadMoreItems();
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage<CommentsInfoItem> result) {
        commentSearch.append(result.getItems());
        super.handleNextItems(result);
        applyCommentSearch();
    }

    private void applyCommentSearch() {
        if (infoListAdapter == null || currentInfo == null || getView() == null) {
            return;
        }
        infoListAdapter.clearStreamItemList();
        infoListAdapter.addInfoItemList(commentSearch.matches());
        showListFooter(hasMoreItems() && commentSearch.getQuery().trim().isEmpty());
        commentSearch.updatePagination(hasMoreItems(), isLoading.get());
        if (infoListAdapter.getItemsList().isEmpty()) {
            showEmptyState();
            emptyStateDesc.setText(currentInfo.isCommentsDisabled()
                    ? R.string.comments_are_disabled
                    : commentSearch.getQuery().trim().isEmpty()
                            && !commentSearch.getPinnedOnly()
                    ? R.string.no_comments : R.string.comments_search_no_matches);
        } else {
            hideLoading();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("comments_newest", sortOrder == CommentSortOrder.NEWEST);
        state.putBoolean("comments_pinned_only", commentSearch.getPinnedOnly());
        state.putString("comment_query", commentSearch.getQuery());
        state.putBoolean("comment_search_expanded", commentSearch.getExpanded());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<ListExtractor.InfoItemsPage<CommentsInfoItem>> loadMoreItemsLogic() {
        final CommentArchiveRequest archiveRequest =
                CommentArchiveRequest.capture(requireContext());
        return ExtractorHelper.getMoreCommentItems(serviceId, currentInfo, currentNextPage)
                .doOnSuccess(page -> archiveRequest.record(serviceId, url, page.getItems(), null))
                .doFinally(() -> {
                    archiveRequest.close();
                    commentSearch.paginationFinished(hasMoreItems());
                });
    }

    @Override
    protected Single<CommentsInfo> loadResult(final boolean forceLoad) {
        final CommentArchiveRequest archiveRequest =
                CommentArchiveRequest.capture(requireContext());
        return ExtractorHelper.getCommentsInfo(serviceId, url, forceLoad, sortOrder)
                .doOnSuccess(info -> archiveRequest.record(
                        serviceId, url, info.getRelatedItems(), null))
                .doFinally(archiveRequest::close);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final CommentsInfo result) {
        commentSearch.initial(result.getRelatedItems());
        super.handleResult(result);

        emptyStateDesc.setText(
                result.isCommentsDisabled()
                        ? R.string.comments_are_disabled
                        : R.string.no_comments);

        applyCommentSearch();
        ViewUtils.slideUp(requireView(), 120, 150, 0.06f);
        disposables.clear();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(final String title) { }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) { }

    @Override
    protected ItemViewMode getItemViewMode() {
        return ItemViewMode.LIST;
    }

    public boolean scrollToComment(final CommentsInfoItem comment) {
        if (infoListAdapter == null || itemsList == null) {
            return false;
        }

        final int position = infoListAdapter.getItemsList().indexOf(comment);
        if (position < 0) {
            return false;
        }

        itemsList.scrollToPosition(position);
        return true;
    }
}
