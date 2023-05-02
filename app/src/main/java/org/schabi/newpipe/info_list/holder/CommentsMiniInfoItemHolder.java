package org.schabi.newpipe.info_list.holder;

import static android.text.TextUtils.isEmpty;

import android.graphics.Paint;
import android.text.Layout;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.image.PicassoHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.text.CommentTextOnTouchListener;
import org.schabi.newpipe.util.text.TextLinkifier;

import java.util.function.Consumer;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class CommentsMiniInfoItemHolder extends InfoItemHolder {
    private static final String TAG = "CommentsMiniIIHolder";
    private static final String ELLIPSIS = "…";

    private static final int COMMENT_DEFAULT_LINES = 2;
    private static final int COMMENT_EXPANDED_LINES = 1000;

    private final int commentHorizontalPadding;
    private final int commentVerticalPadding;

    private final Paint paintAtContentSize;
    private final float ellipsisWidthPx;

    private final RelativeLayout itemRoot;
    private final ImageView itemThumbnailView;
    private final TextView itemContentView;
    private final TextView itemLikesCountView;
    private final TextView itemPublishedTime;

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable private Description commentText;
    @Nullable private StreamingService streamService;
    @Nullable private String streamUrl;

    CommentsMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                               final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemRoot = itemView.findViewById(R.id.itemRoot);
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemLikesCountView = itemView.findViewById(R.id.detail_thumbs_up_count_view);
        itemPublishedTime = itemView.findViewById(R.id.itemPublishedTime);
        itemContentView = itemView.findViewById(R.id.itemCommentContentView);

        commentHorizontalPadding = (int) infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_horizontal_padding);
        commentVerticalPadding = (int) infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_vertical_padding);

        paintAtContentSize = new Paint();
        paintAtContentSize.setTextSize(itemContentView.getTextSize());
        ellipsisWidthPx = paintAtContentSize.measureText(ELLIPSIS);
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

        PicassoHelper.loadAvatar(item.getUploaderAvatars()).into(itemThumbnailView);
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

        try {
            streamService = NewPipe.getService(item.getServiceId());
        } catch (final ExtractionException e) {
            // should never happen
            ErrorUtil.showUiErrorSnackbar(itemBuilder.getContext(), "Getting StreamingService", e);
            Log.w(TAG, "Cannot obtain service from comment service id, defaulting to YouTube", e);
            streamService = ServiceList.YouTube;
        }
        streamUrl = item.getUrl();
        commentText = item.getCommentText();
        ellipsize();

        //noinspection ClickableViewAccessibility
        itemContentView.setOnTouchListener(CommentTextOnTouchListener.INSTANCE);

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
                final CharSequence text = itemContentView.getText();
                if (text != null) {
                    ShareUtils.copyToClipboard(itemBuilder.getContext(), text.toString());
                }
            }
            return true;
        });
    }

    private void openCommentAuthor(final CommentsInfoItem item) {
        if (isEmpty(item.getUploaderUrl())) {
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

    private void determineMovementMethod() {
        if (shouldFocusLinks()) {
            allowLinkFocus();
        } else {
            denyLinkFocus();
        }
    }

    private void ellipsize() {
        itemContentView.setMaxLines(COMMENT_EXPANDED_LINES);
        linkifyCommentContentView(v -> {
            boolean hasEllipsis = false;

            final CharSequence charSeqText = itemContentView.getText();
            if (charSeqText != null && itemContentView.getLineCount() > COMMENT_DEFAULT_LINES) {
                // Note that converting to String removes spans (i.e. links), but that's something
                // we actually want since when the text is ellipsized we want all clicks on the
                // comment to expand the comment, not to open links.
                final String text = charSeqText.toString();

                final Layout layout = itemContentView.getLayout();
                final float lineWidth = layout.getLineWidth(COMMENT_DEFAULT_LINES - 1);
                final float layoutWidth = layout.getWidth();
                final int lineStart = layout.getLineStart(COMMENT_DEFAULT_LINES - 1);
                final int lineEnd = layout.getLineEnd(COMMENT_DEFAULT_LINES - 1);

                // remove characters up until there is enough space for the ellipsis
                // (also summing 2 more pixels, just to be sure to avoid float rounding errors)
                int end = lineEnd;
                float removedCharactersWidth = 0.0f;
                while (lineWidth - removedCharactersWidth + ellipsisWidthPx + 2.0f > layoutWidth
                        && end >= lineStart) {
                    end -= 1;
                    // recalculate each time to account for ligatures or other similar things
                    removedCharactersWidth = paintAtContentSize.measureText(
                            text.substring(end, lineEnd));
                }

                // remove trailing spaces and newlines
                while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
                    end -= 1;
                }

                final String newVal = text.substring(0, end) + ELLIPSIS;
                itemContentView.setText(newVal);
                hasEllipsis = true;
            }

            itemContentView.setMaxLines(COMMENT_DEFAULT_LINES);
            if (hasEllipsis) {
                denyLinkFocus();
            } else {
                determineMovementMethod();
            }
        });
    }

    private void toggleEllipsize() {
        final CharSequence text = itemContentView.getText();
        if (!isEmpty(text) && text.charAt(text.length() - 1) == ELLIPSIS.charAt(0)) {
            expand();
        } else if (itemContentView.getLineCount() > COMMENT_DEFAULT_LINES) {
            ellipsize();
        }
    }

    private void expand() {
        itemContentView.setMaxLines(COMMENT_EXPANDED_LINES);
        linkifyCommentContentView(v -> determineMovementMethod());
    }

    private void linkifyCommentContentView(@Nullable final Consumer<TextView> onCompletion) {
        disposables.clear();
        if (commentText != null) {
            TextLinkifier.fromDescription(itemContentView, commentText,
                    HtmlCompat.FROM_HTML_MODE_LEGACY, streamService, streamUrl, disposables,
                    onCompletion);
        }
    }
}
