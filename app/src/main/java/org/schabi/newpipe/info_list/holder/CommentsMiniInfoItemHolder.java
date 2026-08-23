package org.schabi.newpipe.info_list.holder;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.util.LinkifyCompat;

import androidx.preference.PreferenceManager;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.CommentTextOnTouchListener;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.external_communication.TimestampExtractor;

import java.util.Objects;

public class CommentsMiniInfoItemHolder extends InfoItemHolder {
    private static final String TAG = "CommentsMiniIIHolder";

    private static final int COMMENT_DEFAULT_LINES = 2;
    private static final int COMMENT_EXPANDED_LINES = 1000;

    private final int commentHorizontalPadding;
    private final int commentVerticalPadding;

    private final RelativeLayout itemRoot;
    private final ImageView itemThumbnailView;
    private final TextView itemContentView;
    private final TextView itemLikesCountView;
    private final TextView itemPublishedTime;
    private final Button itemContentReplyButton;

    private String commentText;
    private String streamUrl;

    private boolean shouldEllipsize = false;

    CommentsMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                               final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemRoot = itemView.findViewById(R.id.itemRoot);
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemLikesCountView = itemView.findViewById(R.id.detail_thumbs_up_count_view);
        itemPublishedTime = itemView.findViewById(R.id.itemPublishedTime);
        itemContentView = itemView.findViewById(R.id.itemCommentContentView);
        itemContentReplyButton = itemView.findViewById(R.id.itemContentReplyButton);

        commentHorizontalPadding = (int) infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_horizontal_padding);
        commentVerticalPadding = (int) infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_vertical_padding);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(parent.getContext());
        shouldEllipsize = pref.getBoolean(parent.getContext().getString(R.string.auto_ellipsize_key), false);
    }

    public CommentsMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                      final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_comments_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof CommentsInfoItem)) {
            return;
        }
        final CommentsInfoItem item = (CommentsInfoItem) infoItem;

        PicassoHelper.loadAvatar(item.getUploaderAvatarUrl()).into(itemThumbnailView);
        if (PicassoHelper.getShouldLoadImages()) {
            itemThumbnailView.setVisibility(View.VISIBLE);
            itemRoot.setPadding(commentVerticalPadding, commentVerticalPadding,
                    commentVerticalPadding, commentVerticalPadding);
        } else {
            itemThumbnailView.setVisibility(View.GONE);
            itemRoot.setPadding(commentHorizontalPadding, commentVerticalPadding,
                    commentHorizontalPadding, commentVerticalPadding);
        }


        itemThumbnailView.setOnClickListener(view -> openCommentAuthor(item));

        streamUrl = item.getUrl();

        itemContentView.setMinLines(COMMENT_DEFAULT_LINES);
        commentText = item.getCommentText();
        itemContentView.setText(commentText, TextView.BufferType.SPANNABLE);
        itemContentView.setOnTouchListener(CommentTextOnTouchListener.INSTANCE);

        if (shouldEllipsize) {
            if (itemContentView.getLineCount() == 0) {
                itemContentView.post(this::ellipsize);
            } else {
                ellipsize();
            }
        }
        linkify();


        if (item.getLikeCount() >= 0) {
            itemLikesCountView.setText(
                    Localization.shortCount(
                            itemBuilder.getContext(),
                            item.getLikeCount()));
        } else {
            itemLikesCountView.setText("-");
        }

        if (item.getUploadDate() != null) {
            itemPublishedTime.setText(Localization.relativeTime(item.getUploadDate()
                    .offsetDateTime()));
        } else {
            itemPublishedTime.setText(item.getTextualUploadDate());
        }

        if (item.getReplies() != null) {
            itemContentReplyButton.setVisibility(View.VISIBLE);
            itemContentReplyButton.setOnClickListener(
                    view -> itemBuilder.getOnCommentsReplyListener().selected(item)
            );
            final int replyCount = item.getReplyCount();
            itemContentReplyButton.setText(
                    itemView.getContext().getResources().getQuantityString(
                            R.plurals.replies, replyCount, replyCount
                    ));
        } else {
            itemContentReplyButton.setVisibility(View.GONE);
        }

        itemView.setOnClickListener(view -> {
            toggleEllipsize();
            if (itemBuilder.getOnCommentsSelectedListener() != null) {
                itemBuilder.getOnCommentsSelectedListener().selected(item);
            }
        });


        itemView.setOnLongClickListener(view -> {
            if (DeviceUtils.isTv(itemBuilder.getContext())) {
                openCommentAuthor(item);
            } else {
                ShareUtils.copyToClipboard(itemBuilder.getContext(), commentText);
            }
            return true;
        });
    }

    private void openCommentAuthor(final CommentsInfoItem item) {
        if (TextUtils.isEmpty(item.getUploaderUrl())) {
            return;
        }
        final AppCompatActivity activity = (AppCompatActivity) itemBuilder.getContext();
        try {
            NavigationHelper.openChannelFragment(
                    activity.getSupportFragmentManager(),
                    item.getServiceId(),
                    item.getUploaderUrl(),
                    item.getUploaderName());
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(activity, "Opening channel fragment", e);
        }
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

    private void determineLinkFocus() {
        if (shouldFocusLinks()) {
            allowLinkFocus();
        } else {
            denyLinkFocus();
        }
    }

    private void ellipsize() {
        boolean hasEllipsis = false;

        if (itemContentView.getLineCount() > COMMENT_DEFAULT_LINES) {
            final int endOfLastLine = itemContentView
                    .getLayout()
                    .getLineEnd(COMMENT_DEFAULT_LINES - 1);
            int end = itemContentView.getText().toString().lastIndexOf(' ', endOfLastLine - 2);
            if (end == -1) {
                end = Math.max(endOfLastLine - 2, 0);
            }
            final String newVal = itemContentView.getText().subSequence(0, end) + " …";
            itemContentView.setText(newVal);
            hasEllipsis = true;
        }

        linkify();

        if (hasEllipsis) {
            denyLinkFocus();
        } else {
            determineLinkFocus();
        }
    }

    private void toggleEllipsize() {
        if (itemContentView.getText().toString().equals(commentText)) {
            if (itemContentView.getLineCount() > COMMENT_DEFAULT_LINES) {
                ellipsize();
            }
        } else {
            expand();
        }
    }

    private void expand() {
        itemContentView.setMaxLines(COMMENT_EXPANDED_LINES);
        itemContentView.setText(commentText);
        linkify();
        determineLinkFocus();
    }

    private void linkify() {
        LinkifyCompat.addLinks(itemContentView, Linkify.WEB_URLS);
        LinkifyCompat.addLinks(itemContentView, TimestampExtractor.TIMESTAMPS_PATTERN, null, null,
                (match, url) -> {
                    try { //here url is like 15:38 or 01:00:00
                        final var timestampMatch = TimestampExtractor
                                .getTimestampFromMatcher(match, commentText);
                        if (timestampMatch == null) {
                            return url;
                        }
                        return "internal://timestamp/" + timestampMatch.seconds();
                    } catch (final Exception ex) {
                        Log.e(TAG, "Unable to process url='" + url + "' as timestampLink", ex);
                        return url;
                    }
                });
    }
}
