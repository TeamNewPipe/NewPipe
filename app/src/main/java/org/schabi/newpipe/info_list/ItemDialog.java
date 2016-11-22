package org.schabi.newpipe.info_list;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;
import org.schabi.newpipe.playList.PlayListDataSource;
import org.schabi.newpipe.playList.PlayListDataSource.PLAYLIST_SYSTEM;
import org.schabi.newpipe.playList.PlayListDialog;
import org.schabi.newpipe.playList.QueueManager;
import org.schabi.newpipe.player.BackgroundPlayer;

import java.util.Collections;

public class ItemDialog {
    private final Context context;

    public ItemDialog(Context context) {
        this.context = context;
    }

    public void showSettingDialog(final View view, final StreamPreviewInfo info,
                                  final int playListId, final Runnable callback) {
        final boolean userHasCreatedAPlaylist = userHasCreatedAPlaylist();
        final boolean isOnPlayList = PLAYLIST_SYSTEM.NOT_IN_PLAYLIST_ID != playListId;
        final PopupMenu popup = new PopupMenu(context, view);
        popup.inflate(R.menu.menu_stream);

        final Menu menu = popup.getMenu();
        // only show if we are inside a playlist
        final MenuItem removePlayList = menu.findItem(R.id.remove_to_playlist);
        removePlayList.setVisible(isOnPlayList);
        // only show when user has created playlist
        final MenuItem existingPlayListItemMenu = menu.findItem(R.id.menu_add_item_existing_playlist);
        existingPlayListItemMenu.setVisible(userHasCreatedAPlaylist);
        // not show on queue view
        final MenuItem queueItemMenu = menu.findItem(R.id.queue);
        queueItemMenu.setVisible(PLAYLIST_SYSTEM.QUEUE_ID != playListId);

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                final int id = item.getItemId();
                if (id == R.id.menu_add_item_new_playlist) {
                    final PlayListDialog playListDialog = new PlayListDialog(context);
                    playListDialog.createDialogAddToNewPlayList(info);
                    return true;
                } else if (id == R.id.menu_add_item_existing_playlist) {
                    final PlayListDialog playListDialog = new PlayListDialog(context);
                    playListDialog.createDialogAddToExistingPlayList(info);
                    return true;
                } else if (id == R.id.remove_to_playlist) {
                    removeItemFromPlayList(playListId, info, callback);
                    return true;
                } else if (id == R.id.append_queue) {
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            new QueueManager(context).addToQueue(info);
                            return null;
                        }
                    }.execute();
                    return true;
                } else if (id == R.id.replace_queue) {
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            Intent intent = new Intent(BackgroundPlayer.ACTION_STOP);
                            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                            context.sendBroadcast(intent);
                            new QueueManager(context).replaceQueue(Collections.singletonList(info));
                            return null;
                        }
                    }.execute();
                    return true;
                } else {
                    return false;
                }
            }
        });

        popup.show();
    }

    private boolean userHasCreatedAPlaylist() {
        final PlayListDataSource playListDataSource = new PlayListDataSource(context);
        return playListDataSource.hasPersonalPlayList();
    }

    private void removeItemFromPlayList(final int playListId, final StreamPreviewInfo info, final Runnable callback) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... voids) {
                final PlayListDataSource playListDataSource = new PlayListDataSource(context);
                playListDataSource.deleteEntryFromPlayList(playListId, info.position);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                Toast.makeText(context, String.format(context.getString(R.string.delete_from_playlist),
                        info.webpage_url), Toast.LENGTH_SHORT).show();
                if(callback != null) {
                    callback.run();
                }
            }
        }.execute();
    }

}
