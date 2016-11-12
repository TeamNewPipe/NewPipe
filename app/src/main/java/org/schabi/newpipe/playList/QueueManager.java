package org.schabi.newpipe.playList;

import android.content.Context;
import android.os.AsyncTask;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;

import java.util.Collections;
import java.util.List;

public class QueueManager {

    private static final String TAG = QueueManager.class.getName();

    public static final int PLAY_ALL_ITEM = R.drawable.ic_repeat_white_24dp;
    public static final int PLAY_ONE_ITEM = R.drawable.ic_repeat_one_white_24dp;
    public static final int PLAY_RANDOM = R.drawable.ic_shuffle_white_24dp;

    private final PlayListDataSource playListDataSource;

    public QueueManager(Context context) {
        this.playListDataSource = new PlayListDataSource(context);
    }

    public void clearQueue() {
        playListDataSource.deleteAllEntryFromPlayList(PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID);
    }

    public boolean isEmptyQueue() {
        return playListDataSource.getNumberOfEntriesOnPlayList(PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID) < 1;
    }

    public void replaceQueue(final List<StreamPreviewInfo> streams) {
        if(streams != null) {
            clearQueue();
            addToQueue(streams);
        }
    }

    public void addToQueue(final List<StreamPreviewInfo> streams) {
        if(streams != null) {
            playListDataSource.addEntriesToPlayList(PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID, streams);
        }
    }
    public void addToQueue(final StreamPreviewInfo streams) {
        if(streams != null) {
            playListDataSource.addEntriesToPlayList(PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID, Collections.singletonList(streams));
        }
    }

    public StreamPreviewInfo getStreamAt(final int indexInQueue) {
        return playListDataSource.getEntryForItems(PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID, indexInQueue);
    }

    public StreamPreviewInfo getEntriesFor(final int position) {
        return playListDataSource.getEntryFromPlayList(PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID, position);
    }

    public StreamPreviewInfo getRandomItem() {
        return playListDataSource.getRandomItem(PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID);
    }

    public StreamPreviewInfo getNextEntries(final int position) {
        return playListDataSource.getNextEntryForItems(PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID, position);
    }

    public StreamPreviewInfo getPreviousEntries(final int position) {
        return playListDataSource.getPreviousEntryForItems(PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID, position);
    }

    public void remoteItemAt(final int position) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                playListDataSource.deleteEntryFromPlayList(PlayListDataSource.PLAYLIST_SYSTEM.QUEUE_ID, position);
                return null;
            }
        }.execute();
    }
}
