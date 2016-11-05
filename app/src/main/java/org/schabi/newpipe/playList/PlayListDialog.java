package org.schabi.newpipe.playList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.SparseArray;
import android.widget.EditText;

import org.schabi.newpipe.IntentRunner;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;

import java.util.HashSet;

public class PlayListDialog {

    private final Context context;

    public PlayListDialog(final Context context) {
        this.context = context;
    }

    public void createDialogToRetrieveYoutubePlayList() {
        final EditText taskEditText = new EditText(context);
        final AlertDialog builder = new AlertDialog.Builder(context)
                .setTitle(R.string.type_youtube_playlist_url)
                .setView(taskEditText)
                .setCancelable(true)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        final String playListUrl = String.valueOf(taskEditText.getText());
                        try {
                            IntentRunner.lunchYoutubePlayList(context, playListUrl);
                        } catch (ExtractionException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        builder.show();
    }

    public void createDialogAddToPlayList(final StreamPreviewInfo info) {
        final PlayListDataSource playListDataSource = new PlayListDataSource(context);
        new AsyncTask<Void, Void, SparseArray<String>>() {
            @Override
            protected SparseArray<String> doInBackground(Void... voids) {
                return playListDataSource.getAllPlayList(false);
            }

            @Override
            protected void onPostExecute(final SparseArray<String> playListMap) {
                final String[] playListName = getPlayListName(playListMap);

                final HashSet<String> addToPlayList = new HashSet<>();

                final AlertDialog builder = new AlertDialog.Builder(context)
                        .setTitle(R.string.add_stream_to_playlist)
                        .setMultiChoiceItems(playListName, null, new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which, boolean isChecked) {
                                if (isChecked) {
                                    addToPlayList.add(playListName[which]);
                                } else if (addToPlayList.contains(playListName[which])) {
                                    addToPlayList.remove(playListName[which]);
                                }
                            }
                        })
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialogInterface, final int i) {
                                new AsyncTask<Void, Void, Void>() {
                                    @Override
                                    protected Void doInBackground(Void... voids) {
                                        for (final String playList : addToPlayList) {
                                            final int indexOfValue = playListMap.indexOfValue(playList);
                                            final int playListId = playListMap.keyAt(indexOfValue);
                                            playListDataSource.addEntryFromPlayList(playListId, info);
                                        }
                                        return null;
                                    }
                                }.execute();
                            }
                        })
                        .setNegativeButton(R.string.new_playlist, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialogInterface, final int i) {
                                createDialogForMadePlayList(playListDataSource, info);
                            }
                        }).create();
                builder.show();
            }
        }.execute();
    }

    public static String[] getPlayListName(final SparseArray<String> playListMap) {
        final String[] playListName = new String[playListMap.size()];
        for (int i = 0; i < playListMap.size(); i++) {
            playListName[i] = playListMap.get(playListMap.keyAt(i));
        }
        return playListName;
    }

    private void createDialogForMadePlayList(final PlayListDataSource playListDataSource, final StreamPreviewInfo info) {
        final EditText taskEditText = new EditText(context);
        final AlertDialog builder = new AlertDialog.Builder(context)
                .setTitle(R.string.create_a_new_playlist)
                .setView(taskEditText)
                .setCancelable(true)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        final String playListName = String.valueOf(taskEditText.getText());
                        new AsyncTask<Void, Void, Void>() {
                            @Override
                            protected Void doInBackground(Void... voids) {
                                final PlayList playList = playListDataSource.createPlayList(playListName);
                                playListDataSource.addEntryFromPlayList(playList.get_id(), info);
                                return null;
                            }
                        }.execute();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        builder.show();
    }
}
