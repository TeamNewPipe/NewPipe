package org.schabi.newpipe.info_list.holder;

import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.NavigationHelper;

import de.hdodenhof.circleimageview.CircleImageView;

public class CommentsMiniInfoItemHolder extends InfoItemHolder {
    public final CircleImageView itemThumbnailView;
    private final TextView itemContentView;
    private final TextView itemLikesCountView;
    private final TextView itemDislikesCountView;
    private final TextView itemPublishedTime;

    private static final int commentDefaultLines = 2;
    private static final int commentExpandedLines = 1000;

    CommentsMiniInfoItemHolder(InfoItemBuilder infoItemBuilder, int layoutId, ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemLikesCountView = itemView.findViewById(R.id.detail_thumbs_up_count_view);
        itemDislikesCountView = itemView.findViewById(R.id.detail_thumbs_down_count_view);
        itemPublishedTime = itemView.findViewById(R.id.itemPublishedTime);
        itemContentView = itemView.findViewById(R.id.itemCommentContentView);
    }

    public CommentsMiniInfoItemHolder(InfoItemBuilder infoItemBuilder, ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_comments_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem) {
        if (!(infoItem instanceof CommentsInfoItem)) return;
        final CommentsInfoItem item = (CommentsInfoItem) infoItem;

        itemBuilder.getImageLoader()
                .displayImage(item.getAuthorThumbnail(),
                        itemThumbnailView,
                        ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);

        itemThumbnailView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    final AppCompatActivity activity = (AppCompatActivity) itemBuilder.getContext();
                    NavigationHelper.openChannelFragment(
                            activity.getSupportFragmentManager(),
                            item.getServiceId(),
                            item.getAuthorEndpoint(),
                            item.getAuthorName());
                } catch (Exception e) {
                    ErrorActivity.reportUiError((AppCompatActivity) itemBuilder.getContext(), e);
                }
            }
        });

        // ellipsize if not already ellipsized
        if (null == itemContentView.getEllipsize()) {
            itemContentView.setEllipsize(TextUtils.TruncateAt.END);
            itemContentView.setMaxLines(commentDefaultLines);
        }

        itemContentView.setText(item.getCommentText());
        if (null != item.getLikeCount()) {
            itemLikesCountView.setText(String.valueOf(item.getLikeCount()));
        }
        itemPublishedTime.setText(item.getPublishedTime());

        itemView.setOnClickListener(view -> {
            toggleEllipsize(item.getCommentText());
            if (itemBuilder.getOnCommentsSelectedListener() != null) {
                itemBuilder.getOnCommentsSelectedListener().selected(item);
            }
        });
    }

    private void toggleEllipsize(String text) {
        // toggle ellipsize
        if (null == itemContentView.getEllipsize()) {
            itemContentView.setEllipsize(TextUtils.TruncateAt.END);
            itemContentView.setMaxLines(commentDefaultLines);
        } else {
            itemContentView.setEllipsize(null);
            itemContentView.setMaxLines(commentExpandedLines);
        }
    }
}
