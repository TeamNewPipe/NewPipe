package org.schabi.newpipe.info_list;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.NavigationHelper;

public abstract class ItemHolderWithToolbar<ItemType> extends ItemHolder {
    private static final int TOGGLE_ITEM_TOOLBAR_ANIMATION_DURATION = 70; // ms

    private static PlayQueue queueFromObject(Object itemObject) {
        /*  */ if (itemObject instanceof StreamInfoItem) {
            return new SinglePlayQueue((StreamInfoItem) itemObject);
        } /*else if (itemObject instanceof PlaylistInfoItem) {
            return new PlaylistPlayQueue((PlaylistInfoItem) itemObject);
        } else if (itemObject instanceof ChannelInfoItem) {

        }*/ else {
            throw new IllegalArgumentException("invalid item object: " + itemObject);
        }
    }

    private final Class<ItemType> itemClass;
    @NonNull
    private LinearLayout itemToolbarView;

    public ItemHolderWithToolbar(final Class<ItemType> itemClass,
                                 final ItemHandler itemHandler,
                                 final int layoutId,
                                 final ViewGroup parent) {

        super(itemHandler, layoutId, parent);
        this.itemClass = itemClass;

        itemToolbarView = itemView.findViewById(R.id.toolbarBelowItem);
        if (itemToolbarView == null) {
            itemToolbarView = itemView.findViewById(R.id.toolbarOverlayItem);
            if (itemToolbarView == null) {
                throw new IllegalArgumentException(
                        "getToolbarViewFromItem(): no item toolbars found for itemView");
            }
        }
    }


    public abstract void updateFromItem(ItemType item,
                                        HistoryRecordManager historyRecordManager);

    public void updateStateFromItem(final ItemType item,
                                    final HistoryRecordManager historyRecordManager) {
    }

    @Override
    public void updateFromObject(final Object itemObject,
                                 final HistoryRecordManager historyRecordManager) {
        resetItemToolbar();
        itemView.setOnClickListener(view -> {
            if (itemToolbarView.getVisibility() == View.VISIBLE) {
                NavigationHelper.playOnMainPlayer(
                        itemHandler.getContext(), queueFromObject(itemObject), true);
            } else {
                showItemToolbar();
            }
        });

        if (itemClass.isAssignableFrom(itemObject.getClass())) {
            updateFromItem(itemClass.cast(itemObject), historyRecordManager);
        }
    }

    @Override
    public void updateStateFromObject(final Object itemObject,
                                      final HistoryRecordManager historyRecordManager) {
        if (itemClass.isAssignableFrom(itemObject.getClass())) {
            updateStateFromItem(itemClass.cast(itemObject), historyRecordManager);
        }
    }


    private void resetItemToolbar() {
        switch (itemToolbarView.getId()) {
            case R.id.toolbarOverlayItem:
                ToolbarOverlayItemAnimation.resetToolbarOverlayItem(itemToolbarView);
                break;
            case R.id.toolbarBelowItem:
                ToolbarBelowItemAnimation.resetToolbarBelowItem(itemToolbarView);
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid itemToolbarView passed to resetItemToolbarView()");
        }
    }

    private void showItemToolbar() {
        final Animation animation;
        switch (itemToolbarView.getId()) {
            case R.id.toolbarOverlayItem:
                animation = new ToolbarOverlayItemAnimation(TOGGLE_ITEM_TOOLBAR_ANIMATION_DURATION,
                        itemToolbarView);
                break;
            case R.id.toolbarBelowItem:
                animation = new ToolbarBelowItemAnimation(TOGGLE_ITEM_TOOLBAR_ANIMATION_DURATION,
                        itemToolbarView);
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid itemToolbarView passed to toggleItemToolbar()");
        }
        itemToolbarView.startAnimation(animation);
    }
}
