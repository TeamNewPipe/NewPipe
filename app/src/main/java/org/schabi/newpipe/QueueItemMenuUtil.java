package org.schabi.newpipe;

import static org.schabi.newpipe.util.external_communication.ShareUtils.shareText;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.PopupMenu;

import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.local.dialog.PlaylistCreationDialog;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.Collections;

public final class QueueItemMenuUtil {
    public static void openPopupMenu(final PlayQueue playQueue,
                                     final PlayQueueItem item,
                                     final View view,
                                     final boolean hideDetails,
                                     final FragmentManager fragmentManager,
                                     final Context context) {
        final ContextThemeWrapper themeWrapper =
                new ContextThemeWrapper(context, R.style.DarkPopupMenu);

        final PopupMenu popupMenu = new PopupMenu(themeWrapper, view);
        popupMenu.inflate(R.menu.menu_play_queue_item);

        if (hideDetails) {
            popupMenu.getMenu().findItem(R.id.menu_item_details).setVisible(false);
        }

        popupMenu.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case R.id.menu_item_remove:
                    final int index = playQueue.indexOf(item);
                    playQueue.remove(index);
                    return true;
                case R.id.menu_item_details:
                    // playQueue is null since we don't want any queue change
                    NavigationHelper.openVideoDetail(context, item.getServiceId(),
                            item.getUrl(), item.getTitle(), null,
                            false);
                    return true;
                case R.id.menu_item_append_playlist:
                    final PlaylistAppendDialog d = PlaylistAppendDialog.fromPlayQueueItems(
                            Collections.singletonList(item)
                    );
                    PlaylistAppendDialog.onPlaylistFound(context,
                            () -> d.show(fragmentManager, "QueueItemMenuUtil@append_playlist"),
                            () -> PlaylistCreationDialog.newInstance(d)
                                    .show(fragmentManager, "QueueItemMenuUtil@append_playlist"));
                    return true;
                case R.id.menu_item_share:
                    shareText(context, item.getTitle(), item.getUrl(),
                            item.getThumbnailUrl());
                    return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private QueueItemMenuUtil() { }
}
