package org.schabi.newpipe.local.holder;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.local.LocalItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;

import java.time.format.DateTimeFormatter;

public class RemoteBookmarkPlaylistItemHolder extends RemotePlaylistItemHolder {
    private final View itemHandleView;

    public RemoteBookmarkPlaylistItemHolder(final LocalItemBuilder infoItemBuilder,
                                            final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_playlist_bookmark_item, parent);
    }

    RemoteBookmarkPlaylistItemHolder(final LocalItemBuilder infoItemBuilder, final int layoutId,
                                     final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
        itemHandleView = itemView.findViewById(R.id.itemHandle);
    }

    @Override
    public void updateFromItem(final LocalItem localItem,
                               final HistoryRecordManager historyRecordManager,
                               final DateTimeFormatter dateTimeFormatter) {
        if (!(localItem instanceof PlaylistRemoteEntity)) {
            return;
        }
        final PlaylistRemoteEntity item = (PlaylistRemoteEntity) localItem;

        itemHandleView.setOnTouchListener(getOnTouchListener(item));

        super.updateFromItem(localItem, historyRecordManager, dateTimeFormatter);

        if (itemHandleView != null
                && itemHandleView.getLayoutParams()
                instanceof android.widget.RelativeLayout.LayoutParams) {
            final android.widget.RelativeLayout.LayoutParams params =
                    (android.widget.RelativeLayout.LayoutParams) itemHandleView
                            .getLayoutParams();
            if (itemUploaderView == null
                    || android.text.TextUtils.isEmpty(itemUploaderView.getText())
                    || itemUploaderView.getVisibility() != View.VISIBLE) {
                params.removeRule(android.widget.RelativeLayout.ALIGN_TOP);
                params.removeRule(android.widget.RelativeLayout.ALIGN_BOTTOM);
                params.addRule(android.widget.RelativeLayout.ALIGN_TOP, R.id.itemTitleView);
                params.addRule(android.widget.RelativeLayout.ALIGN_BOTTOM, R.id.itemTitleView);
            } else {
                params.removeRule(android.widget.RelativeLayout.ALIGN_TOP);
                params.removeRule(android.widget.RelativeLayout.ALIGN_BOTTOM);
                params.addRule(android.widget.RelativeLayout.ALIGN_TOP, R.id.itemUploaderView);
                params.addRule(android.widget.RelativeLayout.ALIGN_BOTTOM,
                        R.id.itemUploaderView);
            }
            itemHandleView.setLayoutParams(params);
        }
    }

    private View.OnTouchListener getOnTouchListener(final PlaylistRemoteEntity item) {
        return (view, motionEvent) -> {
            view.performClick();
            if (itemBuilder != null && itemBuilder.getOnItemSelectedListener() != null
                    && motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                itemBuilder.getOnItemSelectedListener().drag(item,
                        RemoteBookmarkPlaylistItemHolder.this);
            }
            return false;
        };
    }
}
