package org.schabi.newpipe.playList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.schabi.newpipe.ChannelActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.search_fragment.SearchInfoItemFragment;

import java.util.ArrayList;
import java.util.Collections;

public class PlayListActivity extends AppCompatActivity {

    public static final String TAG = PlayListActivity.class.getName();
    private ListView mPlayListListView;
    private Button mPlayListYTBtn;
    private PlayListDataSource dataSource;
    private ArrayAdapter<String> mAdapter;
    private Activity activity = this;
    private SparseArray<String> playListsSparseArray;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);
        mPlayListListView = (ListView) findViewById(R.id.list_playlist);
        mPlayListYTBtn = (Button) findViewById(R.id.add_youtube_playlist);
        mPlayListYTBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                lunchPlayListFromDialogYoutube();
            }
        });
        dataSource = new PlayListDataSource(activity);
        updateUI();
    }

    public void deletePlayList(final View view) {
        final View parent = (View) view.getParent();
        final TextView textView = (TextView) parent.findViewById(R.id.playlist_title);
        final String playListName = String.valueOf(textView.getText());

        final AlertDialog builder = new AlertDialog.Builder(this)
                .setMessage(String.format(getString(R.string.config_delete), playListName))
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        new AsyncTask<Void, Void, Void>() {
                            @Override
                            protected Void doInBackground(final Void... voids) {
                                final int playlistId = getPlayListId(playListName);
                                if(playlistId >= 0) {
                                    dataSource.deletePlayList(playlistId);
                                }
                                return null;
                            }

                            @Override
                            protected void onPostExecute(Void aVoid) {
                                updateUI();
                            }
                        }.execute();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        builder.show();
    }

    private int getPlayListId(final String playListName) {
        if (playListName == null) {
            return -1;
        }
        final int index = playListsSparseArray.indexOfValue(playListName);
        return index >= 0 ? playListsSparseArray.keyAt(index) : -1;
    }

    private void updateUI() {
        new AsyncTask<Void, Void, SparseArray<String>>() {
            @Override
            protected SparseArray<String> doInBackground(final Void... voids) {
                final boolean includeSystemPlaylist = false;
                return dataSource.getAllPlayList(includeSystemPlaylist);
            }

            @Override
            protected void onPostExecute(final SparseArray<String> stringSparseArray) {
                playListsSparseArray = stringSparseArray;
                final String[] playLists = PlayListDialog.getPlayListName(stringSparseArray);
                if (mAdapter == null) {
                    ArrayList<String> list = new ArrayList<>();
                    Collections.addAll(list, playLists);
                    mAdapter = new ArrayAdapter<>(activity,
                            R.layout.item_playlist,
                            R.id.playlist_title,
                            list);
                    mPlayListListView.setAdapter(mAdapter);
                } else {
                    mAdapter.clear();
                    mAdapter.addAll(playLists);
                    mAdapter.notifyDataSetChanged();
                }
            }
        }.execute();
    }

    private void lunchPlayListFromDialogYoutube() {
        PlayListDialog playListDialog = new PlayListDialog(this);
        playListDialog.createDialogToRetrieveYoutubePlayList();
    }

    public void lunchPlayList(final View view) {
        final View parent = (View) view.getParent();
        final TextView textView = (TextView) parent.findViewById(R.id.playlist_title);
        final String playListName = String.valueOf(textView.getText());
        final int playlistId = getPlayListId(playListName);
        if(playlistId >= 0) {
            Intent intent = new Intent(this, ChannelActivity.class);
            intent.putExtra(SearchInfoItemFragment.PLAYLIST_ID, playlistId);
            intent.putExtra(ChannelActivity.CHANNEL_URL, "");
            try {
                intent.putExtra(ChannelActivity.SERVICE_ID, NewPipe.getIdOfService("Youtube"));
            } catch (ExtractionException e) {

            }
            this.startActivity(intent);
        }
    }
}
