package org.schabi.newpipe.playList;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;
import org.schabi.newpipe.playList.PlayListDataSource.PLAYLIST_SYSTEM;
import org.schabi.newpipe.player.BackgroundPlayer;
import org.schabi.newpipe.player.LunchAudioTrack;

import java.util.Collections;
import java.util.List;

public class QueueManager {

    private static final String TAG = QueueManager.class.getName();

    public static final int PLAY_ALL_ITEM = R.drawable.ic_repeat_white_24dp;
    public static final int PLAY_ONE_ITEM = R.drawable.ic_repeat_one_white_24dp;
    public static final int PLAY_RANDOM = R.drawable.ic_shuffle_white_24dp;

    private final PlayListDataSource playListDataSource;
    private final Context context;
    public QueueManager(Context context) {
        this.playListDataSource = new PlayListDataSource(context);
        this.context = context;
    }

    public void lunchInBackgroundQueue() {
        final StreamPreviewInfo firstItem = getFirstItem();
        LunchAudioTrack track = new LunchAudioTrack(context, firstItem, PLAYLIST_SYSTEM.QUEUE_ID);
        track.process(true);
    }

    public void clearQueue() {
        playListDataSource.deleteAllEntryFromPlayList(PLAYLIST_SYSTEM.QUEUE_ID);
    }

    public boolean isEmptyQueue() {
        return playListDataSource.getNumberOfEntriesOnPlayList(PLAYLIST_SYSTEM.QUEUE_ID) < 1;
    }

    public void replaceQueue(final List<StreamPreviewInfo> streams) {
        if(streams != null) {
            clearQueue();
            addToQueue(streams);
            // stop current queue
            final Intent intent = new Intent(BackgroundPlayer.ACTION_STOP);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent);
        }
    }

    public void addToQueue(final int playlistId) {
        playListDataSource.duplicatePlayListAOnPlaylistB(playlistId, PLAYLIST_SYSTEM.QUEUE_ID);
    }

    public void replaceQueue(final int playlistId) {
        clearQueue();
        addToQueue(playlistId);
        // stop current queue
        final Intent intent = new Intent(BackgroundPlayer.ACTION_STOP);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(intent);
    }

    public void addToQueue(final List<StreamPreviewInfo> streams) {
        if(streams != null) {
            playListDataSource.addEntriesToPlayList(PLAYLIST_SYSTEM.QUEUE_ID, streams);
        }
    }
    public void addToQueue(final StreamPreviewInfo streams) {
        if(streams != null) {
            playListDataSource.addEntriesToPlayList(PLAYLIST_SYSTEM.QUEUE_ID, Collections.singletonList(streams));
        }
    }

    public StreamPreviewInfo getStreamAt(final int indexInQueue) {
        return playListDataSource.getEntryForItems(PLAYLIST_SYSTEM.QUEUE_ID, indexInQueue);
    }

    public StreamPreviewInfo getEntriesFor(final int position) {
        return playListDataSource.getEntryFromPlayList(PLAYLIST_SYSTEM.QUEUE_ID, position);
    }

    public StreamPreviewInfo getRandomItem() {
        return playListDataSource.getRandomItem(PLAYLIST_SYSTEM.QUEUE_ID);
    }

    public StreamPreviewInfo getFirstItem() {
        return playListDataSource.getFirstEntryForPlayList(PLAYLIST_SYSTEM.QUEUE_ID);
    }

    public StreamPreviewInfo getLastItem() {
        return playListDataSource.getLastEntryForPlayList(PLAYLIST_SYSTEM.QUEUE_ID);
    }

    public StreamPreviewInfo getNextEntries(final int position) {
        final StreamPreviewInfo nextEntry = playListDataSource.getNextEntryForItems(PLAYLIST_SYSTEM.QUEUE_ID, position);
        return nextEntry != null ? nextEntry : getFirstItem();
    }

    public StreamPreviewInfo getPreviousEntries(final int position) {
        final StreamPreviewInfo previousEntry = playListDataSource.getPreviousEntryForItems(PLAYLIST_SYSTEM.QUEUE_ID, position);
        return previousEntry != null ? previousEntry : getLastItem();
    }

    public void remoteItemAt(final int position) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                playListDataSource.deleteEntryFromPlayList(PLAYLIST_SYSTEM.QUEUE_ID, position);
                return null;
            }
        }.execute();
    }
}
