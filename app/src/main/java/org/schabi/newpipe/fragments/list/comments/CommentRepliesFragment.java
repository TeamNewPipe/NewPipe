package org.schabi.newpipe.fragments.list.comments;

import static org.schabi.newpipe.util.ServiceHelper.getServiceById;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.text.HtmlCompat;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.CommentRepliesHeaderBinding;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.comments.CommentArchiveRequest;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.util.CommentTextSizeHelper;
import org.schabi.newpipe.util.CommentTranslationProvider;
import org.schabi.newpipe.util.ContentBlockingHelper;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.text.LongPressLinkMovementMethod;
import org.schabi.newpipe.util.text.TextLinkifier;

import java.util.Queue;
import java.util.function.Supplier;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class CommentRepliesFragment
        extends BaseListInfoFragment<CommentsInfoItem, CommentRepliesInfo> {

    public static final String TAG = CommentRepliesFragment.class.getSimpleName();

    @State
    CommentsInfoItem commentsInfoItem; // the comment to show replies of
    private final CompositeDisposable disposables = new CompositeDisposable();
    private CommentRepliesHeaderBinding headerBinding;
    private String translatedHeaderComment;
    private boolean showingTranslatedHeaderComment;
    private int headerTranslationGeneration;


    /*//////////////////////////////////////////////////////////////////////////
    // Constructors and lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    // only called by the Android framework, after which readFrom is called and restores all data
    public CommentRepliesFragment() {
        super(UserAction.REQUESTED_COMMENT_REPLIES);
    }

    public CommentRepliesFragment(@NonNull final CommentsInfoItem commentsInfoItem) {
        this();
        this.commentsInfoItem = commentsInfoItem;
        // setting "" as title since the title will be properly set right after
        setInitialData(commentsInfoItem.getServiceId(), commentsInfoItem.getUrl(), "");
    }

    @NonNull
    @Override
    protected ContentBlockingHelper.Target getContentBlockingTarget() {
        return ContentBlockingHelper.Target.COMMENTS;
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
        headerTranslationGeneration++;
        translatedHeaderComment = null;
        showingTranslatedHeaderComment = false;
        disposables.clear();
        headerBinding = null;
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (headerBinding != null) {
            CommentTextSizeHelper.applyCommentTextSize(headerBinding.commentContent);
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (getString(R.string.comment_text_size_key).equals(key)
                && isResumed() && headerBinding != null) {
            CommentTextSizeHelper.applyCommentTextSize(headerBinding.commentContent);
        }
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        return () -> {
            headerBinding = CommentRepliesHeaderBinding
                    .inflate(activity.getLayoutInflater(), itemsList, false);
            final CommentRepliesHeaderBinding binding = headerBinding;
            final CommentsInfoItem item = commentsInfoItem;

            // load the author avatar
            CoilHelper.INSTANCE.loadCommentAvatar(
                    binding.authorAvatar, item.getUploaderAvatarUrl());
            binding.authorAvatar.setVisibility(ImageStrategy.shouldLoadImages()
                    ? View.VISIBLE : View.GONE);

            // setup author name and comment date
            binding.authorName.setText(item.getUploaderName());
            binding.uploadDate.setText(Localization.relativeTimeOrTextual(
                    getContext(), item.getUploadDate(), item.getTextualUploadDate()));
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
            CommentTextSizeHelper.applyCommentTextSize(binding.commentContent);
            showHeaderCommentText(binding, item, item.getCommentText());
            setupHeaderTranslation(binding, item);
            return binding.getRoot();
        };
    }



    private void setupHeaderTranslation(@NonNull final CommentRepliesHeaderBinding binding,
                                        @NonNull final CommentsInfoItem item) {
        headerTranslationGeneration++;
        translatedHeaderComment = null;
        showingTranslatedHeaderComment = false;

        final String originalText = item.getCommentText();
        binding.translateButton.setVisibility(View.GONE);
        binding.translateButton.setEnabled(false);
        binding.translateButton.setText(R.string.comment_translate);
        binding.translateButton.setOnClickListener(null);

        if (!CommentTranslationProvider.isAvailableOnPlatform()
                || originalText == null
                || originalText.trim().isEmpty()) {
            return;
        }

        final int generation = headerTranslationGeneration;
        CommentTranslationProvider.checkAvailability(
                requireContext(),
                originalText,
                available -> {
                    if (!isCurrentHeaderTranslation(binding, generation)) {
                        return;
                    }
                    binding.translateButton.setVisibility(
                            available ? View.VISIBLE : View.GONE);
                    binding.translateButton.setEnabled(available);
                    binding.translateButton.setOnClickListener(
                            available
                                    ? view -> onHeaderTranslateClicked(binding, item)
                                    : null);
                });
    }

    private void onHeaderTranslateClicked(@NonNull final CommentRepliesHeaderBinding binding,
                                          @NonNull final CommentsInfoItem item) {
        if (translatedHeaderComment != null) {
            showingTranslatedHeaderComment = !showingTranslatedHeaderComment;
            showHeaderCommentText(
                    binding,
                    item,
                    showingTranslatedHeaderComment
                            ? translatedHeaderComment : item.getCommentText());
            binding.translateButton.setText(showingTranslatedHeaderComment
                    ? R.string.comment_show_original : R.string.comment_show_translation);
            return;
        }

        binding.translateButton.setEnabled(false);
        binding.translateButton.setText(R.string.comment_translating);
        final int generation = headerTranslationGeneration;
        CommentTranslationProvider.translate(
                requireContext(),
                item.getCommentText(),
                new CommentTranslationProvider.Callback() {
                    @Override
                    public void onSuccess(@NonNull final String translatedText) {
                        if (!isCurrentHeaderTranslation(binding, generation)) {
                            return;
                        }
                        translatedHeaderComment = translatedText;
                        showingTranslatedHeaderComment = true;
                        showHeaderCommentText(binding, item, translatedText);
                        binding.translateButton.setEnabled(true);
                        binding.translateButton.setText(R.string.comment_show_original);
                    }

                    @Override
                    public void onFailure(
                            @NonNull final CommentTranslationProvider.Failure failure) {
                        if (!isCurrentHeaderTranslation(binding, generation)) {
                            return;
                        }
                        binding.translateButton.setEnabled(true);
                        binding.translateButton.setText(R.string.comment_translate);
                        showTranslationFailure(failure);
                    }
                });
    }

    private boolean isCurrentHeaderTranslation(
            @NonNull final CommentRepliesHeaderBinding binding,
            final int generation) {
        return headerBinding == binding && headerTranslationGeneration == generation;
    }

    private void showHeaderCommentText(@NonNull final CommentRepliesHeaderBinding binding,
                                       @NonNull final CommentsInfoItem item,
                                       final String text) {
        // Cancel a previous linkification before switching between translated and original text.
        // This prevents a late asynchronous result from restoring stale text.
        disposables.clear();
        TextLinkifier.fromDescription(
                binding.commentContent,
                new Description(text, Description.PLAIN_TEXT),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
                getServiceById(item.getServiceId()),
                item.getUrl(),
                disposables,
                null);
        binding.commentContent.setMovementMethod(LongPressLinkMovementMethod.getInstance());
    }

    private void showTranslationFailure(
            @NonNull final CommentTranslationProvider.Failure failure) {
        if (getContext() == null) {
            return;
        }

        final int message;
        switch (failure) {
            case ALREADY_TARGET_LANGUAGE:
                message = R.string.comment_translation_same_language;
                break;
            case LANGUAGE_UNDETECTED:
                message = R.string.comment_translation_language_undetected;
                break;
            case ON_DEVICE_UNAVAILABLE:
                message = R.string.comment_translation_unavailable;
                break;
            case TRANSLATION_FAILED:
            default:
                message = R.string.comment_translation_failed;
                break;
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(commentsInfoItem);
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        commentsInfoItem = (CommentsInfoItem) savedObjects.poll();
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Data loading
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<CommentRepliesInfo> loadResult(final boolean forceLoad) {
        return Single.fromCallable(() -> new CommentRepliesInfo(commentsInfoItem,
                // the reply count string will be shown as the activity title
                Localization.replyCount(requireContext(), commentsInfoItem.getReplyCount())));
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<CommentsInfoItem>> loadMoreItemsLogic() {
        // commentsInfoItem.getUrl() should contain the url of the original
        // ListInfo<CommentsInfoItem>, which is the stream or canonical post URL.
        final CommentArchiveRequest archiveRequest =
                CommentArchiveRequest.capture(requireContext());
        return ExtractorHelper.getMoreCommentItems(
                serviceId, commentsInfoItem.getUrl(), currentNextPage)
                .doOnSuccess(page -> archiveRequest.record(
                        serviceId, commentsInfoItem.getUrl(), page.getItems(),
                        commentsInfoItem.getCommentId()))
                .doFinally(archiveRequest::close);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected ItemViewMode getItemViewMode() {
        return ItemViewMode.LIST;
    }

    /**
     * @return the comment to which the replies are shown
     */
    public CommentsInfoItem getCommentsInfoItem() {
        return commentsInfoItem;
    }
}
