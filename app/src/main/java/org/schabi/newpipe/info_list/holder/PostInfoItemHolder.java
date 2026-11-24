package org.schabi.newpipe.info_list.holder;

import static org.schabi.newpipe.extractor.ServiceList.getById;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.card.MaterialCardView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.post.PostInfoItem;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.fragments.list.comments.PostCommentsNavigation;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.text.TextEllipsizer;

import java.util.List;
import java.util.Locale;

public final class PostInfoItemHolder extends InfoItemHolder {
    private static final int POST_DEFAULT_LINES = 4;

    private final ImageView avatarView;
    private final TextView authorView;
    private final TextView dateView;
    private final ImageView pinnedView;
    private final TextView contentView;
    private final LinearLayout imagesContainer;
    private final LinearLayout pollContainer;
    private final MaterialCardView attachmentCard;
    private final ImageView attachmentThumbnail;
    private final TextView attachmentTitle;
    private final TextView attachmentDescription;
    private final TextView statsView;
    private final Button openOriginalButton;
    private final TextEllipsizer textEllipsizer;

    public PostInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                              final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_post_item, parent);
        avatarView = itemView.findViewById(R.id.itemAvatarView);
        authorView = itemView.findViewById(R.id.itemAuthorView);
        dateView = itemView.findViewById(R.id.itemDateView);
        pinnedView = itemView.findViewById(R.id.itemPinnedView);
        contentView = itemView.findViewById(R.id.itemPostContentView);
        imagesContainer = itemView.findViewById(R.id.itemImagesContainer);
        pollContainer = itemView.findViewById(R.id.itemPollContainer);
        attachmentCard = itemView.findViewById(R.id.itemAttachmentCard);
        attachmentThumbnail = itemView.findViewById(R.id.itemAttachmentThumbnail);
        attachmentTitle = itemView.findViewById(R.id.itemAttachmentTitle);
        attachmentDescription = itemView.findViewById(R.id.itemAttachmentDescription);
        statsView = itemView.findViewById(R.id.itemStatsView);
        openOriginalButton = itemView.findViewById(R.id.itemOpenOriginalButton);
        textEllipsizer = new TextEllipsizer(contentView, POST_DEFAULT_LINES, null);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof PostInfoItem post)) {
            return;
        }

        CoilHelper.INSTANCE.loadAvatar(avatarView, post.getUploaderAvatars());
        avatarView.setOnClickListener(view -> openAuthor(post));
        authorView.setText(Localization.localizeUserName(post.getUploaderName()));
        String date = Localization.relativeTimeOrTextual(
                itemBuilder.getContext(), post.getUploadDate(), post.getTextualUploadDate());
        if (post.isEdited() && !date.toLowerCase(Locale.ROOT).contains("edited")) {
            date = itemBuilder.getContext().getString(R.string.post_edited, date);
        }
        dateView.setText(date);
        pinnedView.setVisibility(post.isPinned() ? View.VISIBLE : View.GONE);

        final boolean hasContent = !TextUtils.isEmpty(post.getContent());
        contentView.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        if (hasContent) {
            textEllipsizer.setStreamingService(getById(post.getServiceId()));
            textEllipsizer.setStreamUrl(post.getUrl());
            textEllipsizer.setContent(new Description(post.getContent(), Description.PLAIN_TEXT));
            textEllipsizer.ellipsize();
        }

        bindImages(post.getImages());
        bindPoll(post.getPoll());
        bindAttachment(post);
        statsView.setText(itemBuilder.getContext().getString(
                R.string.post_stats,
                labelCount(displayCount(post.getTextualLikeCount(), post.getLikeCount()),
                        R.string.post_likes_count, "like"),
                labelCount(displayCount(post.getTextualCommentCount(), post.getCommentCount()),
                        R.string.post_comments_count, "comment")));

        openOriginalButton.setOnClickListener(view ->
                ShareUtils.openUrlInBrowser(itemBuilder.getContext(), post.getUrl()));
        PostCommentsNavigation.bind(itemView.findViewById(R.id.itemPostCommentsButton),
                post, itemBuilder.getContext());
        itemView.setOnClickListener(view -> {
            if (hasContent) {
                textEllipsizer.toggle();
            }
        });
        itemView.setOnLongClickListener(view -> {
            ShareUtils.copyToClipboard(itemBuilder.getContext(),
                    post.getContent() + "\n" + post.getUrl());
            return true;
        });
    }

    private void bindImages(@NonNull final List<Image> images) {
        imagesContainer.removeAllViews();
        if (images.isEmpty() || !ImageStrategy.shouldLoadImages()) {
            imagesContainer.setVisibility(View.GONE);
            return;
        }
        imagesContainer.setVisibility(View.VISIBLE);
        for (int index = 0; index < images.size(); index += 2) {
            final LinearLayout row = new LinearLayout(itemBuilder.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            final int rowHeight = DeviceUtils.dpToPx(images.size() == 1 ? 240 : 170,
                    itemBuilder.getContext());
            imagesContainer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));
            addImage(row, images.get(index), index, images.size());
            if (index + 1 < images.size()) {
                addImage(row, images.get(index + 1), index + 1, images.size());
            } else if (images.size() > 1) {
                final View spacer = new View(itemBuilder.getContext());
                row.addView(spacer, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
        }
    }

    private void addImage(@NonNull final LinearLayout row,
                          @NonNull final Image image,
                          final int index,
                          final int total) {
        final ImageView imageView = new ImageView(itemBuilder.getContext());
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setContentDescription(itemBuilder.getContext().getString(
                R.string.post_image_content_description, index + 1, total));
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        final int gap = DeviceUtils.dpToPx(2, itemBuilder.getContext());
        params.setMargins(index % 2 == 0 ? 0 : gap, gap, index % 2 == 0 ? gap : 0, 0);
        row.addView(imageView, params);
        CoilHelper.INSTANCE.loadThumbnail(imageView, image.getUrl());
        imageView.setOnClickListener(view ->
                ShareUtils.openUrlInBrowser(itemBuilder.getContext(), image.getUrl()));
    }

    private void bindPoll(final PostInfoItem.Poll poll) {
        pollContainer.removeAllViews();
        if (poll == null) {
            pollContainer.setVisibility(View.GONE);
            return;
        }
        pollContainer.setVisibility(View.VISIBLE);
        for (final PostInfoItem.PollChoice choice : poll.getChoices()) {
            if (!choice.getImages().isEmpty() && ImageStrategy.shouldLoadImages()) {
                final ImageView image = new ImageView(itemBuilder.getContext());
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setContentDescription(choice.getText());
                pollContainer.addView(image, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        DeviceUtils.dpToPx(170, itemBuilder.getContext())));
                CoilHelper.INSTANCE.loadThumbnail(image, choice.getImages());
            }
            final TextView choiceView = new TextView(itemBuilder.getContext());
            choiceView.setGravity(Gravity.CENTER_VERTICAL);
            choiceView.setMinHeight(DeviceUtils.dpToPx(48, itemBuilder.getContext()));
            choiceView.setPadding(DeviceUtils.dpToPx(12, itemBuilder.getContext()), 0,
                    DeviceUtils.dpToPx(12, itemBuilder.getContext()), 0);
            choiceView.setText(TextUtils.isEmpty(choice.getVotePercentage())
                    ? choice.getText() : itemBuilder.getContext().getString(
                            R.string.post_poll_choice, choice.getText(),
                            choice.getVotePercentage()));
            pollContainer.addView(choiceView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (!TextUtils.isEmpty(poll.getTotalVotes())) {
            final TextView totalView = new TextView(itemBuilder.getContext());
            totalView.setText(poll.getTotalVotes());
            totalView.setPadding(DeviceUtils.dpToPx(12, itemBuilder.getContext()),
                    DeviceUtils.dpToPx(4, itemBuilder.getContext()), 0, 0);
            pollContainer.addView(totalView);
        }
    }

    private void bindAttachment(@NonNull final PostInfoItem post) {
        final PostInfoItem.Attachment attachment = post.getAttachment();
        if (attachment == null) {
            attachmentCard.setVisibility(View.GONE);
            return;
        }
        attachmentCard.setVisibility(View.VISIBLE);
        attachmentTitle.setText(attachment.getTitle());
        attachmentDescription.setText(attachment.getDescription());
        attachmentDescription.setVisibility(TextUtils.isEmpty(attachment.getDescription())
                ? View.GONE : View.VISIBLE);
        if (attachment.getThumbnails().isEmpty()) {
            attachmentThumbnail.setVisibility(View.GONE);
        } else {
            attachmentThumbnail.setVisibility(View.VISIBLE);
            CoilHelper.INSTANCE.loadThumbnail(attachmentThumbnail, attachment.getThumbnails());
        }
        attachmentCard.setOnClickListener(view -> openAttachment(post, attachment));
    }

    private void openAttachment(@NonNull final PostInfoItem post,
                                @NonNull final PostInfoItem.Attachment attachment) {
        if (TextUtils.isEmpty(attachment.getUrl())) {
            return;
        }
        if (!(itemBuilder.getContext() instanceof FragmentActivity activity)) {
            ShareUtils.openUrlInApp(itemBuilder.getContext(), attachment.getUrl());
            return;
        }
        if (attachment.getType() == PostInfoItem.AttachmentType.VIDEO) {
            NavigationHelper.openVideoDetailFragment(activity,
                    activity.getSupportFragmentManager(), post.getServiceId(),
                    attachment.getUrl(), attachment.getTitle(), null, false);
        } else if (attachment.getType() == PostInfoItem.AttachmentType.PLAYLIST) {
            NavigationHelper.openPlaylistFragment(activity.getSupportFragmentManager(),
                    post.getServiceId(), attachment.getUrl(), attachment.getTitle());
        } else {
            ShareUtils.openUrlInApp(activity, attachment.getUrl());
        }
    }

    private void openAuthor(@NonNull final PostInfoItem post) {
        if (itemBuilder.getContext() instanceof FragmentActivity activity
                && !TextUtils.isEmpty(post.getUploaderUrl())) {
            NavigationHelper.openChannelFragment(activity.getSupportFragmentManager(),
                    post.getServiceId(), post.getUploaderUrl(), post.getUploaderName());
        }
    }

    @NonNull
    private String displayCount(final String textualCount, final long count) {
        if (!TextUtils.isEmpty(textualCount)) {
            return textualCount;
        }
        return count == PostInfoItem.UNKNOWN_COUNT
                ? itemBuilder.getContext().getString(R.string.unknown_count)
                : Localization.shortCount(itemBuilder.getContext(), count);
    }

    @NonNull
    private String labelCount(@NonNull final String count,
                              final int labelResource,
                              @NonNull final String existingLabel) {
        return count.toLowerCase(Locale.ROOT).contains(existingLabel)
                ? count : itemBuilder.getContext().getString(labelResource, count);
    }
}
