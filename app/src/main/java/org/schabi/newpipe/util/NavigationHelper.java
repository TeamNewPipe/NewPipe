package org.schabi.newpipe.util;

import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream_info.AudioStream;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.fragments.OnItemSelectedListener;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.player.BackgroundPlayer;
import org.schabi.newpipe.player.BasePlayer;
import org.schabi.newpipe.player.VideoPlayer;

@SuppressWarnings({"unused", "WeakerAccess"})
public class NavigationHelper {

    public static Intent getOpenVideoPlayerIntent(Context context, Class targetClazz, StreamInfo info, int selectedStreamIndex) {
        Intent mIntent = new Intent(context, targetClazz)
                .putExtra(BasePlayer.VIDEO_TITLE, info.title)
                .putExtra(BasePlayer.VIDEO_URL, info.webpage_url)
                .putExtra(BasePlayer.VIDEO_THUMBNAIL_URL, info.thumbnail_url)
                .putExtra(BasePlayer.CHANNEL_NAME, info.uploader)
                .putExtra(VideoPlayer.INDEX_SEL_VIDEO_STREAM, selectedStreamIndex)
                .putExtra(VideoPlayer.VIDEO_STREAMS_LIST, Utils.getSortedStreamVideosList(context, info.video_streams, info.video_only_streams, false))
                .putExtra(VideoPlayer.VIDEO_ONLY_AUDIO_STREAM, Utils.getHighestQualityAudio(info.audio_streams));
        if (info.start_position > 0) mIntent.putExtra(BasePlayer.START_POSITION, info.start_position * 1000);
        return mIntent;
    }


    public static Intent getOpenVideoPlayerIntent(Context context, Class targetClazz, VideoPlayer instance) {
        return new Intent(context, targetClazz)
                .putExtra(BasePlayer.VIDEO_TITLE, instance.getVideoTitle())
                .putExtra(BasePlayer.VIDEO_URL, instance.getVideoUrl())
                .putExtra(BasePlayer.VIDEO_THUMBNAIL_URL, instance.getVideoThumbnailUrl())
                .putExtra(BasePlayer.CHANNEL_NAME, instance.getChannelName())
                .putExtra(VideoPlayer.INDEX_SEL_VIDEO_STREAM, instance.getSelectedStreamIndex())
                .putExtra(VideoPlayer.VIDEO_STREAMS_LIST, instance.getVideoStreamsList())
                .putExtra(VideoPlayer.VIDEO_ONLY_AUDIO_STREAM, instance.getAudioStream())
                .putExtra(BasePlayer.START_POSITION, ((int) instance.getPlayer().getCurrentPosition()));
    }

    public static Intent getOpenBackgroundPlayerIntent(Context context, StreamInfo info) {
        return getOpenBackgroundPlayerIntent(context, info, info.audio_streams.get(Utils.getPreferredAudioFormat(context, info.audio_streams)));
    }

    public static Intent getOpenBackgroundPlayerIntent(Context context, StreamInfo info, AudioStream audioStream) {
        Intent mIntent = new Intent(context, BackgroundPlayer.class)
                .putExtra(BasePlayer.VIDEO_TITLE, info.title)
                .putExtra(BasePlayer.VIDEO_URL, info.webpage_url)
                .putExtra(BasePlayer.VIDEO_THUMBNAIL_URL, info.thumbnail_url)
                .putExtra(BasePlayer.CHANNEL_NAME, info.uploader)
                .putExtra(BasePlayer.CHANNEL_NAME, info.uploader)
                .putExtra(BackgroundPlayer.AUDIO_STREAM, audioStream);
        if (info.start_position > 0) mIntent.putExtra(BasePlayer.START_POSITION, info.start_position * 1000);
        return mIntent;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Through Interface (faster)
    //////////////////////////////////////////////////////////////////////////*/

    public static void openChannel(OnItemSelectedListener listener, int serviceId, String url) {
        openChannel(listener, serviceId, url, null);
    }

    public static void openChannel(OnItemSelectedListener listener, int serviceId, String url, String name) {
        listener.onItemSelected(StreamingService.LinkType.CHANNEL, serviceId, url, name);
    }

    public static void openVideoDetail(OnItemSelectedListener listener, int serviceId, String url) {
        openVideoDetail(listener, serviceId, url, null);
    }

    public static void openVideoDetail(OnItemSelectedListener listener, int serviceId, String url, String title) {
        listener.onItemSelected(StreamingService.LinkType.STREAM, serviceId, url, title);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Through Intents
    //////////////////////////////////////////////////////////////////////////*/

    public static void openByLink(Context context, String url) throws Exception {
        context.startActivity(getIntentByLink(context, url));
    }

    public static void openChannel(Context context, int serviceId, String url) {
        openChannel(context, serviceId, url, null);
    }

    public static void openChannel(Context context, int serviceId, String url, String name) {
        Intent openIntent = getOpenIntent(context, url, serviceId, StreamingService.LinkType.CHANNEL);
        if (name != null && !name.isEmpty()) openIntent.putExtra(Constants.KEY_TITLE, name);
        context.startActivity(openIntent);
    }

    public static void openVideoDetail(Context context, int serviceId, String url) {
        openVideoDetail(context, serviceId, url, null);
    }

    public static void openVideoDetail(Context context, int serviceId, String url, String title) {
        Intent openIntent = getOpenIntent(context, url, serviceId, StreamingService.LinkType.STREAM);
        if (title != null && !title.isEmpty()) openIntent.putExtra(Constants.KEY_TITLE, title);
        context.startActivity(openIntent);
    }

    public static void openMainActivity(Context context) {
        Intent mIntent = new Intent(context, MainActivity.class);
        context.startActivity(mIntent);
    }

    private static Intent getOpenIntent(Context context, String url, int serviceId, StreamingService.LinkType type) {
        Intent mIntent = new Intent(context, MainActivity.class);
        mIntent.putExtra(Constants.KEY_SERVICE_ID, serviceId);
        mIntent.putExtra(Constants.KEY_URL, url);
        mIntent.putExtra(Constants.KEY_LINK_TYPE, type);
        return mIntent;
    }

    private static Intent getIntentByLink(Context context, String url) throws Exception {
        StreamingService service = NewPipe.getServiceByUrl(url);
        if (service == null) throw new Exception("NewPipe.getServiceByUrl returned null for url > \"" + url + "\"");
        int serviceId = service.getServiceId();
        switch (service.getLinkTypeByUrl(url)) {
            case STREAM:
                Intent sIntent = getOpenIntent(context, url, serviceId, StreamingService.LinkType.STREAM);
                sIntent.putExtra(VideoDetailFragment.AUTO_PLAY, PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean(context.getString(R.string.autoplay_through_intent_key), false));
                return sIntent;
            case CHANNEL:
                return getOpenIntent(context, url, serviceId, StreamingService.LinkType.CHANNEL);
            case NONE:
                throw new Exception("Url not known to service. service="
                        + Integer.toString(serviceId) + " url=" + url);
        }
        return null;
    }
}
