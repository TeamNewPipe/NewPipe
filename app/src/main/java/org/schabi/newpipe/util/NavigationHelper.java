package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.about.AboutActivity;
import org.schabi.newpipe.download.DownloadActivity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.MainFragment;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.fragments.list.channel.ChannelFragment;
import org.schabi.newpipe.fragments.list.feed.FeedFragment;
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment;
import org.schabi.newpipe.fragments.list.search.SearchFragment;
import org.schabi.newpipe.history.HistoryActivity;
import org.schabi.newpipe.player.BackgroundPlayer;
import org.schabi.newpipe.player.BackgroundPlayerActivity;
import org.schabi.newpipe.player.BasePlayer;
import org.schabi.newpipe.player.MainVideoPlayer;
import org.schabi.newpipe.player.PopupVideoPlayer;
import org.schabi.newpipe.player.PopupVideoPlayerActivity;
import org.schabi.newpipe.player.VideoPlayer;
import org.schabi.newpipe.player.old.PlayVideoActivity;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.settings.SettingsActivity;

import java.util.ArrayList;

@SuppressWarnings({"unused", "WeakerAccess"})
public class NavigationHelper {
    public static final String MAIN_FRAGMENT_TAG = "main_fragment_tag";

    /*//////////////////////////////////////////////////////////////////////////
    // Players
    //////////////////////////////////////////////////////////////////////////*/

    public static Intent getPlayerIntent(final Context context,
                                         final Class targetClazz,
                                         final PlayQueue playQueue,
                                         final String quality) {
        Intent intent = new Intent(context, targetClazz)
                .putExtra(VideoPlayer.PLAY_QUEUE, playQueue);
        if (quality != null) intent.putExtra(VideoPlayer.PLAYBACK_QUALITY, quality);

        return intent;
    }

    public static Intent getPlayerIntent(final Context context,
                                         final Class targetClazz,
                                         final PlayQueue playQueue) {
        return getPlayerIntent(context, targetClazz, playQueue, null);
    }

    public static Intent getPlayerEnqueueIntent(final Context context,
                                                final Class targetClazz,
                                                final PlayQueue playQueue,
                                                final boolean selectOnAppend) {
        return getPlayerIntent(context, targetClazz, playQueue)
                .putExtra(BasePlayer.APPEND_ONLY, true)
                .putExtra(BasePlayer.SELECT_ON_APPEND, selectOnAppend);
    }

    public static Intent getPlayerIntent(final Context context,
                                         final Class targetClazz,
                                         final PlayQueue playQueue,
                                         final int repeatMode,
                                         final float playbackSpeed,
                                         final float playbackPitch,
                                         final String playbackQuality) {
        return getPlayerIntent(context, targetClazz, playQueue, playbackQuality)
                .putExtra(BasePlayer.REPEAT_MODE, repeatMode)
                .putExtra(BasePlayer.PLAYBACK_SPEED, playbackSpeed)
                .putExtra(BasePlayer.PLAYBACK_PITCH, playbackPitch);
    }

    public static void playOnMainPlayer(final Context context, final PlayQueue queue) {
        final Intent playerIntent = getPlayerIntent(context, MainVideoPlayer.class, queue);
        playerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(playerIntent);
    }

    public static void playOnOldVideoPlayer(Context context, StreamInfo info) {
        ArrayList<VideoStream> videoStreamsList = new ArrayList<>(ListHelper.getSortedStreamVideosList(context, info.getVideoStreams(), null, false));
        int index = ListHelper.getDefaultResolutionIndex(context, videoStreamsList);

        if (index == -1) {
            Toast.makeText(context, R.string.video_streams_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        VideoStream videoStream = videoStreamsList.get(index);
        Intent intent = new Intent(context, PlayVideoActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(PlayVideoActivity.VIDEO_TITLE, info.getName())
                .putExtra(PlayVideoActivity.STREAM_URL, videoStream.getUrl())
                .putExtra(PlayVideoActivity.VIDEO_URL, info.getUrl())
                .putExtra(PlayVideoActivity.START_POSITION, info.getStartPosition());

        context.startActivity(intent);
    }

    public static void playOnPopupPlayer(final Context context, final PlayQueue queue) {
        if (!PermissionHelper.isPopupEnabled(context)) {
            PermissionHelper.showPopupEnablementToast(context);
            return;
        }

        Toast.makeText(context, R.string.popup_playing_toast, Toast.LENGTH_SHORT).show();
        context.startService(getPlayerIntent(context, PopupVideoPlayer.class, queue));
    }

    public static void playOnBackgroundPlayer(final Context context, final PlayQueue queue) {
        Toast.makeText(context, R.string.background_player_playing_toast, Toast.LENGTH_SHORT).show();
        context.startService(getPlayerIntent(context, BackgroundPlayer.class, queue));
    }

    public static void enqueueOnPopupPlayer(final Context context, final PlayQueue queue) {
        enqueueOnPopupPlayer(context, queue, false);
    }

    public static void enqueueOnPopupPlayer(final Context context, final PlayQueue queue, boolean selectOnAppend) {
        if (!PermissionHelper.isPopupEnabled(context)) {
            PermissionHelper.showPopupEnablementToast(context);
            return;
        }

        Toast.makeText(context, R.string.popup_playing_append, Toast.LENGTH_SHORT).show();
        context.startService(getPlayerEnqueueIntent(context, PopupVideoPlayer.class, queue, selectOnAppend));
    }

    public static void enqueueOnBackgroundPlayer(final Context context, final PlayQueue queue) {
        enqueueOnBackgroundPlayer(context, queue, false);
    }

    public static void enqueueOnBackgroundPlayer(final Context context, final PlayQueue queue, boolean selectOnAppend) {
        Toast.makeText(context, R.string.background_player_append, Toast.LENGTH_SHORT).show();
        context.startService(getPlayerEnqueueIntent(context, BackgroundPlayer.class, queue, selectOnAppend));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // External Players
    //////////////////////////////////////////////////////////////////////////*/

    public static void playOnExternalAudioPlayer(Context context, StreamInfo info) {
        final int index = ListHelper.getDefaultAudioFormat(context, info.getAudioStreams());

        if (index == -1) {
            Toast.makeText(context, R.string.audio_streams_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        AudioStream audioStream = info.getAudioStreams().get(index);
        playOnExternalPlayer(context, info.getName(), info.getUploaderName(), audioStream);
    }

    public static void playOnExternalVideoPlayer(Context context, StreamInfo info) {
        ArrayList<VideoStream> videoStreamsList = new ArrayList<>(ListHelper.getSortedStreamVideosList(context, info.getVideoStreams(), null, false));
        int index = ListHelper.getDefaultResolutionIndex(context, videoStreamsList);

        if (index == -1) {
            Toast.makeText(context, R.string.video_streams_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        VideoStream videoStream = videoStreamsList.get(index);
        playOnExternalPlayer(context, info.getName(), info.getUploaderName(), videoStream);
    }

    public static void playOnExternalPlayer(Context context, String name, String artist, Stream stream) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(stream.getUrl()), stream.getFormat().getMimeType());
        intent.putExtra(Intent.EXTRA_TITLE, name);
        intent.putExtra("title", name);
        intent.putExtra("artist", artist);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        resolveActivityOrAskToInstall(context, intent);
    }

    public static void resolveActivityOrAskToInstall(Context context, Intent intent) {
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            if (context instanceof Activity) {
                new AlertDialog.Builder(context)
                        .setMessage(R.string.no_player_found)
                        .setPositiveButton(R.string.install, (dialog, which) -> {
                            Intent i = new Intent();
                            i.setAction(Intent.ACTION_VIEW);
                            i.setData(Uri.parse(context.getString(R.string.fdroid_vlc_url)));
                            context.startActivity(i);
                        })
                        .setNegativeButton(R.string.cancel, (dialog, which) -> Log.i("NavigationHelper", "You unlocked a secret unicorn."))
                        .show();
                //Log.e("NavigationHelper", "Either no Streaming player for audio was installed, or something important crashed:");
            } else {
                Toast.makeText(context, R.string.no_player_found_toast, Toast.LENGTH_LONG).show();
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Through FragmentManager
    //////////////////////////////////////////////////////////////////////////*/

    public static void gotoMainFragment(FragmentManager fragmentManager) {
        ImageLoader.getInstance().clearMemoryCache();

        boolean popped = fragmentManager.popBackStackImmediate(MAIN_FRAGMENT_TAG, 0);
        if (!popped) openMainFragment(fragmentManager);
    }

    public static void openMainFragment(FragmentManager fragmentManager) {
        InfoCache.getInstance().trimCache();

        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out, R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.fragment_holder, new MainFragment())
                .addToBackStack(MAIN_FRAGMENT_TAG)
                .commit();
    }

    public static void openSearchFragment(FragmentManager fragmentManager, int serviceId, String query) {
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out, R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.fragment_holder, SearchFragment.getInstance(serviceId, query))
                .addToBackStack(null)
                .commit();
    }

    public static void openVideoDetailFragment(FragmentManager fragmentManager, int serviceId, String url, String title) {
        openVideoDetailFragment(fragmentManager, serviceId, url, title, false);
    }

    public static void openVideoDetailFragment(FragmentManager fragmentManager, int serviceId, String url, String title, boolean autoPlay) {
        Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_holder);
        if (title == null) title = "";

        if (fragment instanceof VideoDetailFragment && fragment.isVisible()) {
            VideoDetailFragment detailFragment = (VideoDetailFragment) fragment;
            detailFragment.setAutoplay(autoPlay);
            detailFragment.selectAndLoadVideo(serviceId, url, title);
            return;
        }

        VideoDetailFragment instance = VideoDetailFragment.getInstance(serviceId, url, title);
        instance.setAutoplay(autoPlay);

        fragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out, R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.fragment_holder, instance)
                .addToBackStack(null)
                .commit();
    }

    public static void openChannelFragment(FragmentManager fragmentManager, int serviceId, String url, String name) {
        if (name == null) name = "";
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out, R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.fragment_holder, ChannelFragment.getInstance(serviceId, url, name))
                .addToBackStack(null)
                .commit();
    }

    public static void openPlaylistFragment(FragmentManager fragmentManager, int serviceId, String url, String name) {
        if (name == null) name = "";
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out, R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.fragment_holder, PlaylistFragment.getInstance(serviceId, url, name))
                .addToBackStack(null)
                .commit();
    }

    public static void openWhatsNewFragment(FragmentManager fragmentManager) {
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out, R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.fragment_holder, new FeedFragment())
                .addToBackStack(null)
                .commit();
    }

    public static void openKioskFragment(FragmentManager fragmentManager, int serviceId, String kioskId)
        throws ExtractionException {
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out, R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.fragment_holder, KioskFragment.getInstance(serviceId, kioskId))
                .addToBackStack(null)
                .commit();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Through Intents
    //////////////////////////////////////////////////////////////////////////*/

    public static void openSearch(Context context, int serviceId, String query) {
        Intent mIntent = new Intent(context, MainActivity.class);
        mIntent.putExtra(Constants.KEY_SERVICE_ID, serviceId);
        mIntent.putExtra(Constants.KEY_QUERY, query);
        mIntent.putExtra(Constants.KEY_OPEN_SEARCH, true);
        context.startActivity(mIntent);
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
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(mIntent);
    }

    public static void openAbout(Context context) {
        Intent intent = new Intent(context, AboutActivity.class);
        context.startActivity(intent);
    }

    public static void openHistory(Context context) {
        Intent intent = new Intent(context, HistoryActivity.class);
        context.startActivity(intent);
    }

    public static void openSettings(Context context) {
        Intent intent = new Intent(context, SettingsActivity.class);
        context.startActivity(intent);
    }

    public static boolean openDownloads(Activity activity) {
        if (!PermissionHelper.checkStoragePermissions(activity)) {
            return false;
        }
        Intent intent = new Intent(activity, DownloadActivity.class);
        activity.startActivity(intent);
        return true;
    }

    public static Intent getBackgroundPlayerActivityIntent(final Context context) {
        return getServicePlayerActivityIntent(context, BackgroundPlayerActivity.class);
    }

    public static Intent getPopupPlayerActivityIntent(final Context context) {
        return getServicePlayerActivityIntent(context, PopupVideoPlayerActivity.class);
    }

    private static Intent getServicePlayerActivityIntent(final Context context,
                                                         final Class activityClass) {
        Intent intent = new Intent(context, activityClass);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Link handling
    //////////////////////////////////////////////////////////////////////////*/

    private static Intent getOpenIntent(Context context, String url, int serviceId, StreamingService.LinkType type) {
        Intent mIntent = new Intent(context, MainActivity.class);
        mIntent.putExtra(Constants.KEY_SERVICE_ID, serviceId);
        mIntent.putExtra(Constants.KEY_URL, url);
        mIntent.putExtra(Constants.KEY_LINK_TYPE, type);
        return mIntent;
    }

    public static Intent getIntentByLink(Context context, String url) throws ExtractionException {
        return getIntentByLink(context, NewPipe.getServiceByUrl(url), url);
    }

    public static Intent getIntentByLink(Context context, StreamingService service, String url) throws ExtractionException {
        if (service != ServiceList.YouTube.getService()) {
            throw new ExtractionException("Service not supported at the moment");
        }

        StreamingService.LinkType linkType = service.getLinkTypeByUrl(url);

        if (linkType == StreamingService.LinkType.NONE) {
            throw new ExtractionException("Url not known to service. service=" + service + " url=" + url);
        }

        url = getCleanUrl(service, url, linkType);
        Intent rIntent = getOpenIntent(context, url, service.getServiceId(), linkType);

        switch (linkType) {
            case STREAM:
                rIntent.putExtra(VideoDetailFragment.AUTO_PLAY, PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean(context.getString(R.string.autoplay_through_intent_key), false));
                break;
        }

        return rIntent;
    }

    public static String getCleanUrl(StreamingService service, String dirtyUrl, StreamingService.LinkType linkType) throws ExtractionException {
        switch (linkType) {
            case STREAM:
                return service.getStreamUrlIdHandler().cleanUrl(dirtyUrl);
            case CHANNEL:
                return service.getChannelUrlIdHandler().cleanUrl(dirtyUrl);
            case PLAYLIST:
                return service.getPlaylistUrlIdHandler().cleanUrl(dirtyUrl);
            case NONE:
                break;
        }
        return null;
    }

    private static Uri openMarketUrl(String packageName) {
        return Uri.parse("market://details")
                .buildUpon()
                .appendQueryParameter("id", packageName)
                .build();
    }

    private static Uri getGooglePlayUrl(String packageName) {
        return Uri.parse("https://play.google.com/store/apps/details")
                .buildUpon()
                .appendQueryParameter("id", packageName)
                .build();
    }

    private static void installApp(Context context, String packageName) {
        try {
            // Try market:// scheme
            context.startActivity(new Intent(Intent.ACTION_VIEW, openMarketUrl(packageName)));
        } catch (ActivityNotFoundException e) {
            // Fall back to google play URL (don't worry F-Droid can handle it :)
            context.startActivity(new Intent(Intent.ACTION_VIEW, getGooglePlayUrl(packageName)));
        }
    }

    /**
     * Start an activity to install Kore
     * @param context the context
     */
    public static void installKore(Context context) {
        installApp(context, context.getString(R.string.kore_package));
    }

    /**
     * Start Kore app to show a video on Kodi
     *
     * For a list of supported urls see the
     * <a href="https://github.com/xbmc/Kore/blob/master/app/src/main/AndroidManifest.xml">
     *     Kore source code
     * </a>.
     *
     * @param context the context to use
     * @param videoURL the url to the video
     */
    public static void playWithKore(Context context, Uri videoURL) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setPackage(context.getString(R.string.kore_package));
        intent.setData(videoURL);
        context.startActivity(intent);
    }
}
