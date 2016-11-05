package org.schabi.newpipe.info_list;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.widget.ListAdapter;
import android.widget.Toast;

import org.schabi.newpipe.IntentRunner;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;
import org.schabi.newpipe.playList.PlayListDataSource;
import org.schabi.newpipe.playList.PlayListDialog;

public class ItemDialog {
    private final Context context;

    public ItemDialog(Context context) {
        this.context = context;
    }

    public void showSettingDialog(final StreamPreviewInfo info, final int playListId,
                                  final int positionInPlayList, final Runnable callback) {
        final String[] items = getItems(playListId);
        final Integer[] icons = getItemsIcons(playListId);

        final ListAdapter adapter = new ArrayAdapterWithIcon(context, items, icons);
        new AlertDialog.Builder(context)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialogInterface, final int item) {
                        switch (item) {
                            case 0: // play
                                IntentRunner.lunchIntentVideoDetail(context, info.webpage_url, info.service_id, playListId, positionInPlayList);
                                break;
                            case 1: // share
                                IntentRunner.lunchIntentShareStream(context, info.webpage_url);
                                break;
                            case 2: // add playlist
                                addToPlayList(info);
                                break;
                            case 3: // remove playlist from play list
                                if (playListId >= -1) {
                                    removeItemFromPlayList(playListId, info, callback);
                                }
                                break;
                        }
                    }
                }).show();
    }

    @NonNull
    private String[] getItems(int playListId) {
        return playListId >= 0 ? new String[]{
                "Lire",
                "Share",
                "Add from PlayList",
                "Remove from playList"
        } : new String[]{
                "Lire",
                "Share",
                "Add from PlayList"
        };
    }

    @NonNull
    private Integer[] getItemsIcons(int playListId) {
        return playListId >= 0 ? new Integer[]{
                android.R.drawable.ic_media_play,
                android.R.drawable.ic_menu_share,
                android.R.drawable.ic_menu_add,
                android.R.drawable.ic_menu_delete
        } : new Integer[]{
                android.R.drawable.ic_media_play,
                android.R.drawable.ic_menu_share,
                android.R.drawable.ic_menu_add
        };
    }

    private void addToPlayList(final StreamPreviewInfo info) {
        final PlayListDialog playListDialog = new PlayListDialog(context);
        playListDialog.createDialogAddToPlayList(info);
    }

    private void removeItemFromPlayList(final int playListId, final StreamPreviewInfo info, final Runnable callback) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... voids) {
                final PlayListDataSource playListDataSource = new PlayListDataSource(context);
                playListDataSource.deleteEntryFromPlayList(playListId, info.id);

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
