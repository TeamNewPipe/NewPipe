package org.schabi.newpipe.fragments.list.comments;

import static org.schabi.newpipe.util.ServiceHelper.getServiceById;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.text.HtmlCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.CommentRepliesHeaderBinding;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.image.PicassoHelper;
import org.schabi.newpipe.util.text.TextLinkifier;

import java.util.Queue;
import java.util.function.Supplier;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class CommentRepliesFragment
        extends BaseListInfoFragment<CommentsInfoItem, CommentRepliesInfo> {

    // the original comments info loaded alongside the stream
    private CommentsInfo commentsInfo;
    // the comment to show replies of
    private CommentsInfoItem commentsInfoItem;
    private final CompositeDisposable disposables = new CompositeDisposable();


    /*//////////////////////////////////////////////////////////////////////////
    // Constructors and lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    public CommentRepliesFragment() {
        super(UserAction.REQUESTED_COMMENT_REPLIES);
    }

    public CommentRepliesFragment(final CommentsInfo commentsInfo,
                                  final CommentsInfoItem commentsInfoItem) {
        this();
        this.commentsInfo = commentsInfo;
        this.commentsInfoItem = commentsInfoItem;
        setInitialData(commentsInfo.getServiceId(), commentsInfo.getUrl(), commentsInfo.getName());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_comments, container, false);
    }

    @Override
    public void onDestroyView() {
        disposables.clear();
        super.onDestroyView();
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        return () -> {
            final CommentRepliesHeaderBinding binding = CommentRepliesHeaderBinding
                    .inflate(activity.getLayoutInflater(), itemsList, false);
            final CommentsInfoItem item = commentsInfoItem;

            // load the author avatar
            PicassoHelper.loadAvatar(item.getUploaderAvatars()).into(binding.authorAvatar);
            binding.authorAvatar.setVisibility(ImageStrategy.shouldLoadImages()
                    ? View.VISIBLE : View.GONE);

            // setup author name and comment date
            binding.authorName.setText(item.getUploaderName());
            binding.uploadDate.setText(Localization.relativeTimeOrTextual(
                    item.getUploadDate(), item.getTextualUploadDate(), getContext()));
            binding.authorTouchArea.setOnClickListener(
                    v -> NavigationHelper.openCommentAuthorIfPresent(requireActivity(), item));

            // setup like count, hearted and pinned
            binding.thumbsUpCount.setText(
                    Localization.likeCount(requireContext(), item.getLikeCount()));
            // for heartImage goneMarginEnd was used, but there is no way to tell ConstraintLayout
            // not to use a different margin only when both the next two views are gone
            ((ConstraintLayout.LayoutParams) binding.thumbsUpCount.getLayoutParams())
                    .setMarginEnd(DeviceUtils.dpToPx(
                            (item.isHeartedByUploader() || item.isPinned() ? 8 : 16),
                            requireContext()));
            binding.heartImage.setVisibility(item.isHeartedByUploader() ? View.VISIBLE : View.GONE);
            binding.pinnedImage.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);

            // setup comment content
            TextLinkifier.fromDescription(binding.commentContent, item.getCommentText(),
                    HtmlCompat.FROM_HTML_MODE_LEGACY, getServiceById(item.getServiceId()),
                    item.getUrl(), disposables, null);

            return binding.getRoot();
        };
    }


    /*//////////////////////////////////////////////////////////////////////////
    // State saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(commentsInfo);
        objectsToSave.add(commentsInfoItem);
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        commentsInfo = (CommentsInfo) savedObjects.poll();
        commentsInfoItem = (CommentsInfoItem) savedObjects.poll();
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Data loading
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<CommentRepliesInfo> loadResult(final boolean forceLoad) {
        return Single.fromCallable(() -> CommentRepliesInfo.getInfo(commentsInfoItem,
                // the reply count string will be shown as the activity title
                Localization.replyCount(requireContext(), commentsInfoItem.getReplyCount())));
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<CommentsInfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreCommentItems(serviceId, commentsInfo, currentNextPage);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected ItemViewMode getItemViewMode() {
        return ItemViewMode.LIST;
    }
}
