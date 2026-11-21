package org.schabi.newpipe.info_list.holder;

import static org.schabi.newpipe.util.ServiceHelper.getServiceById;
import static org.schabi.newpipe.util.text.TouchUtils.getOffsetForHorizontalLine;

import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.CommentTextSizeHelper;
import org.schabi.newpipe.util.CommentTranslationProvider;
import org.schabi.newpipe.util.image.CommentImageViewerDialog;
import org.schabi.newpipe.fragments.list.comments.CommentDatePresentation;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.text.TextEllipsizer;

public class CommentInfoItemHolder extends InfoItemHolder {

    private static final int COMMENT_DEFAULT_LINES = 2;
    private final int commentHorizontalPadding;
    private final int commentVerticalPadding;

    private final RelativeLayout itemRoot;
    private final ImageView itemThumbnailView;
    private final TextView itemContentView;
    private final TextView itemLikesCountView;
    private final TextView itemTitleView;
    private final ImageView itemHeartView;
    private final ImageView commentImagePreview;
    private final Button commentImagesButton;
    private final ImageView itemPinnedView;
    private final Button translateButton;
    private final Button repliesButton;

    @NonNull
    private final TextEllipsizer textEllipsizer;

    private CommentsInfoItem boundComment;
    private String originalCommentText;
    private String translatedCommentText;
    private boolean showingTranslatedComment;
    private int translationGeneration;

    public CommentInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                 final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_comment_item, parent);

        itemRoot = itemView.findViewById(R.id.itemRoot);
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemContentView = itemView.findViewById(R.id.itemCommentContentView);
        itemLikesCountView = itemView.findViewById(R.id.detail_thumbs_up_count_view);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemHeartView = itemView.findViewById(R.id.detail_heart_image_view);
        commentImagePreview = itemView.findViewById(R.id.comment_image_preview);
        commentImagesButton = itemView.findViewById(R.id.comment_images_button);
        itemPinnedView = itemView.findViewById(R.id.detail_pinned_view);
        translateButton = itemView.findViewById(R.id.translate_button);
        repliesButton = itemView.findViewById(R.id.replies_button);

        commentHorizontalPadding = (int) infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_horizontal_padding);
        commentVerticalPadding = (int) infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_vertical_padding);

        textEllipsizer = new TextEllipsizer(itemContentView, COMMENT_DEFAULT_LINES, null);
        textEllipsizer.setStateChangeListener(isEllipsized -> {
            if (Boolean.TRUE.equals(isEllipsized)) {
                denyLinkFocus();
            } else {
                determineMovementMethod();
            }
        });
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof CommentsInfoItem item)) {
            return;
        }

        CommentTextSizeHelper.applyCommentTextSize(itemContentView);

        // load the author avatar
        CoilHelper.INSTANCE.loadCommentAvatar(itemThumbnailView, item.getUploaderAvatarUrl());
        if (ImageStrategy.shouldLoadImages()) {
            itemThumbnailView.setVisibility(View.VISIBLE);
            itemRoot.setPadding(commentVerticalPadding, commentVerticalPadding,
                    commentVerticalPadding, commentVerticalPadding);
        } else {
            itemThumbnailView.setVisibility(View.GONE);
            itemRoot.setPadding(commentHorizontalPadding, commentVerticalPadding,
                    commentHorizontalPadding, commentVerticalPadding);
        }
        itemThumbnailView.setOnClickListener(view -> openCommentAuthor(item));

        // setup the top row, with pinned icon, author name and comment date
        itemPinnedView.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);
        final String uploaderName = Localization.localizeUserName(item.getUploaderName());
        itemTitleView.setText(Localization.concatenateStrings(
                uploaderName,
                CommentDatePresentation.label(
                        itemBuilder.getContext(), item)));

        // setup bottom row, with likes, heart and replies button
        itemLikesCountView.setText(
                Localization.likeCount(itemBuilder.getContext(), item.getLikeCount()));

        itemHeartView.setVisibility(item.isHeartedByUploader() ? View.VISIBLE : View.GONE);

        final boolean hasReplies = item.getReplies() != null;
        repliesButton.setOnClickListener(hasReplies ? v -> openCommentReplies(item) : null);
        repliesButton.setVisibility(hasReplies ? View.VISIBLE : View.GONE);
        repliesButton.setText(hasReplies
                ? Localization.replyCount(itemBuilder.getContext(), item.getReplyCount()) : "");
        setupTranslation(item);

        // setup comment content and click listeners to expand/ellipsize it
        textEllipsizer.setStreamingService(getServiceById(item.getServiceId()));
        textEllipsizer.setStreamUrl(item.getUrl());
        showCommentText(item.getCommentText());
        bindCommentPictures(item);

        //noinspection ClickableViewAccessibility
        itemContentView.setOnTouchListener((v, event) -> {
            final CharSequence text = itemContentView.getText();
            if (text instanceof Spanned buffer) {
                final int action = event.getAction();

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                    final int offset = getOffsetForHorizontalLine(itemContentView, event);
                    final var links = buffer.getSpans(offset, offset, ClickableSpan.class);

                    if (links.length != 0) {
                        if (action == MotionEvent.ACTION_UP) {
                            links[0].onClick(itemContentView);
                        }
                        // we handle events that intersect links, so return true
                        return true;
                    }
                }
            }
            return false;
        });

        itemView.setOnClickListener(view -> {
            textEllipsizer.toggle();
            if (itemBuilder.getOnCommentsSelectedListener() != null) {
                itemBuilder.getOnCommentsSelectedListener().selected(item);
            }
        });

        itemView.setOnLongClickListener(view -> {
            if (DeviceUtils.isTv(itemBuilder.getContext())) {
                openCommentAuthor(item);
            } else {
                final CharSequence text = itemContentView.getText();
                if (text != null) {
                    ShareUtils.copyToClipboard(itemBuilder.getContext(), text.toString());
                }
            }
            return true;
        });
    }

    @Override
    public void recycle() {
        translationGeneration++;
        boundComment = null;
        originalCommentText = null;
        translatedCommentText = null;
        showingTranslatedComment = false;
        translateButton.setOnClickListener(null);
        translateButton.setVisibility(View.GONE);
        CoilHelper.INSTANCE.clearAvatar(itemThumbnailView);
        CoilHelper.INSTANCE.clearImage(commentImagePreview);
        commentImagePreview.setOnClickListener(null);
        commentImagesButton.setOnClickListener(null);
        commentImagePreview.setVisibility(View.GONE);
        commentImagesButton.setVisibility(View.GONE);
    }

    private void bindCommentPictures(@NonNull final CommentsInfoItem item) {
        final java.util.List<Image> pictures = new java.util.ArrayList<>(item.getPictures());
        if (!ImageStrategy.shouldLoadImages() || pictures.isEmpty()) {
            CoilHelper.INSTANCE.clearImage(commentImagePreview);
            commentImagePreview.setVisibility(View.GONE);
            commentImagesButton.setVisibility(View.GONE);
            commentImagePreview.setOnClickListener(null);
            commentImagesButton.setOnClickListener(null);
            return;
        }
        commentImagePreview.setVisibility(View.VISIBLE);
        commentImagesButton.setVisibility(View.VISIBLE);
        commentImagesButton.setText(itemBuilder.getContext().getString(
                R.string.comment_view_images, pictures.size()));
        CoilHelper.INSTANCE.loadThumbnail(commentImagePreview,
                ImageStrategy.choosePreferredImage(pictures));
        final View.OnClickListener openViewer = view ->
                CommentImageViewerDialog.show((FragmentActivity) itemBuilder.getContext(),
                        pictures);
        commentImagePreview.setOnClickListener(openViewer);
        commentImagesButton.setOnClickListener(openViewer);
    }

    private void setupTranslation(@NonNull final CommentsInfoItem item) {
        translationGeneration++;
        boundComment = item;
        originalCommentText = item.getCommentText();
        translatedCommentText = null;
        showingTranslatedComment = false;

        translateButton.setVisibility(View.GONE);
        translateButton.setEnabled(false);
        translateButton.setText(R.string.comment_translate);
        translateButton.setOnClickListener(null);

        if (!CommentTranslationProvider.isAvailableOnPlatform()
                || originalCommentText == null
                || originalCommentText.trim().isEmpty()) {
            return;
        }

        final int generation = translationGeneration;
        CommentTranslationProvider.checkAvailability(
                itemBuilder.getContext(),
                originalCommentText,
                available -> {
                    if (!isCurrentTranslationRequest(item, generation)) {
                        return;
                    }
                    translateButton.setVisibility(available ? View.VISIBLE : View.GONE);
                    translateButton.setEnabled(available);
                    translateButton.setOnClickListener(
                            available ? view -> onTranslateClicked(item) : null);
                });
    }

    private void onTranslateClicked(@NonNull final CommentsInfoItem item) {
        if (translatedCommentText != null) {
            showingTranslatedComment = !showingTranslatedComment;
            showCommentText(showingTranslatedComment
                    ? translatedCommentText : originalCommentText);
            translateButton.setText(showingTranslatedComment
                    ? R.string.comment_show_original : R.string.comment_show_translation);
            return;
        }

        translateButton.setEnabled(false);
        translateButton.setText(R.string.comment_translating);
        final int generation = translationGeneration;
        CommentTranslationProvider.translate(
                itemBuilder.getContext(),
                originalCommentText,
                new CommentTranslationProvider.Callback() {
                    @Override
                    public void onSuccess(@NonNull final String translatedText) {
                        if (!isCurrentTranslationRequest(item, generation)) {
                            return;
                        }
                        translatedCommentText = translatedText;
                        showingTranslatedComment = true;
                        showCommentText(translatedText);
                        translateButton.setEnabled(true);
                        translateButton.setText(R.string.comment_show_original);
                    }

                    @Override
                    public void onFailure(
                            @NonNull final CommentTranslationProvider.Failure failure) {
                        if (!isCurrentTranslationRequest(item, generation)) {
                            return;
                        }
                        translateButton.setEnabled(true);
                        translateButton.setText(R.string.comment_translate);
                        showTranslationFailure(failure);
                    }
                });
    }

    private boolean isCurrentTranslationRequest(@NonNull final CommentsInfoItem item,
                                                final int generation) {
        return boundComment == item && translationGeneration == generation;
    }

    private void showCommentText(final String text) {
        textEllipsizer.setContent(new Description(text, Description.PLAIN_TEXT));
        textEllipsizer.ellipsize();
    }

    private void showTranslationFailure(
            @NonNull final CommentTranslationProvider.Failure failure) {
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
        Toast.makeText(itemBuilder.getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void openCommentAuthor(@NonNull final CommentsInfoItem item) {
        NavigationHelper.openCommentAuthorIfPresent((FragmentActivity) itemBuilder.getContext(),
                item);
    }

    private void openCommentReplies(@NonNull final CommentsInfoItem item) {
        NavigationHelper.openCommentRepliesFragment((FragmentActivity) itemBuilder.getContext(),
                item);
    }

    private void allowLinkFocus() {
        itemContentView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void denyLinkFocus() {
        itemContentView.setMovementMethod(null);
    }

    private boolean shouldFocusLinks() {
        if (itemView.isInTouchMode()) {
            return false;
        }

        final URLSpan[] urls = itemContentView.getUrls();

        return urls != null && urls.length != 0;
    }

    private void determineMovementMethod() {
        if (shouldFocusLinks()) {
            allowLinkFocus();
        } else {
            denyLinkFocus();
        }
    }
}
