package org.schabi.newpipe;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.widget.Toast;

import org.schabi.newpipe.detail.VideoItemDetailActivity;
import org.schabi.newpipe.detail.VideoItemDetailFragment;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.YoutubePlayListUrlIdHandler;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;
import org.schabi.newpipe.playList.NewPipeSQLiteHelper.PLAYLIST_LINK_ENTRIES;
import org.schabi.newpipe.playList.PlayListDataSource;
import org.schabi.newpipe.playList.PlayListDataSource.PLAYLIST_SYSTEM;
import org.schabi.newpipe.search_fragment.SearchInfoItemFragment;

public class IntentRunner {

    public static Intent buildItemShareStream(@NonNull final String webpage_url) {
        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, webpage_url);
        intent.setType("text/plain");
        return intent;
    }

    public static void lunchIntentShareStream(@NonNull final Context context, @NonNull final String webpage_url) {
        final Intent intent = buildItemShareStream(webpage_url);
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_dialog_title)));
    }

    public static Intent buildVideoDetail(@NonNull final Context context, @NonNull final String webpage_url, int service_id, int playlistId, int positionInPlayList) {
        final Intent i = new Intent(context, VideoItemDetailActivity.class);
        i.putExtra(VideoItemDetailFragment.STREAMING_SERVICE, service_id);
        i.putExtra(VideoItemDetailFragment.VIDEO_URL, webpage_url);
        i.putExtra(SearchInfoItemFragment.PLAYLIST_ID, playlistId);
        i.putExtra(PLAYLIST_LINK_ENTRIES.POSITION, positionInPlayList);
        return i;
    }

    public static void lunchIntentVideoDetail(@NonNull final Context context, @NonNull final String webpage_url, int service_id, int playListId, int positionInPlayList) {
        final Intent i = buildVideoDetail(context, webpage_url, service_id, playListId, positionInPlayList);
        context.startActivity(i);
    }

    public static void lunchNextStreamOnPlayList(final Context context, final int currentPlayList, final int currentPosition) {
        if(PLAYLIST_SYSTEM.NOT_IN_PLAYLIST_ID != currentPlayList) {
            final PlayListDataSource playListDataSource = new PlayListDataSource(context);
            new AsyncTask<Void, Void, StreamPreviewInfo>() {
                @Override
                protected StreamPreviewInfo doInBackground(final Void... voids) {
                    return playListDataSource.getNextEntriesForItems(currentPlayList, currentPosition);
                }

                @Override
                protected void onPostExecute(final StreamPreviewInfo nextStream) {
                    super.onPostExecute(nextStream);
                    if(nextStream != null) {
                        IntentRunner.lunchIntentVideoDetail(context, nextStream.webpage_url, nextStream.service_id,
                                currentPlayList, currentPosition + 1);
                    }
                }
            }.execute();
        }
    }

    public static void lunchYoutubePlayList(final Context context, final String playListUrl) throws ExtractionException {
        final YoutubePlayListUrlIdHandler youtubePlayListUrlIdHandler = new YoutubePlayListUrlIdHandler();
        if(youtubePlayListUrlIdHandler.acceptUrl(playListUrl)) {
            final Intent i = new Intent(context, ChannelActivity.class);
            i.putExtra(ChannelActivity.CHANNEL_URL, youtubePlayListUrlIdHandler.cleanUrl(playListUrl));
            i.putExtra(ChannelActivity.SERVICE_ID, NewPipe.getIdOfService("Youtube"));
            context.startActivity(i);
        } else {
            Toast.makeText(context, R.string.youtube_playlist_not_valid, Toast.LENGTH_LONG).show();
        }
    }
}
