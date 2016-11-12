package org.schabi.newpipe.player;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream_info.AudioStream;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;
import org.schabi.newpipe.extractor.stream_info.VideoStream;
import org.schabi.newpipe.playList.NewPipeSQLiteHelper;
import org.schabi.newpipe.settings.NetworkHelper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.schabi.newpipe.search_fragment.SearchInfoItemFragment.PLAYLIST_ID;

public class LunchAudioTrack {

    private final static String TAG = LunchAudioTrack.class.getName();

    private final Context activity;
    private StreamPreviewInfo info = null;
    private Bitmap bitmap = null;
    private AudioStream audioStream = null;
    private final int playListId;
    private boolean hasLoadBitmap = false;
    private boolean hasAudioStream = false;

    public LunchAudioTrack(final Context activity, @NonNull final StreamPreviewInfo info, final int playListId) {
        this.activity = activity;
        this.info = info;
        this.playListId = playListId;
    }

    public void retrieveBitmap(final Runnable callback) {
        hasLoadBitmap = true;
        if (!TextUtils.isEmpty(info.thumbnail_url)) {
            ImageLoader.getInstance().loadImage(info.thumbnail_url, new ImageLoadingListener() {
                @Override
                public void onLoadingStarted(String imageUri, View view) {

                }

                @Override
                public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                    Log.e(TAG, String.format("FAILED to load bitmap at %s", imageUri), failReason.getCause());
                }

                @Override
                public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
                    Log.d(TAG, String.format("SUCCESS to load bitmap at %s", imageUri));
                    bitmap = loadedImage;
                    if(callback != null) {
                        callback.run();
                    }
                }

                @Override
                public void onLoadingCancelled(String imageUri, View view) {

                }
            });
        }
    }

    public void retrieveInfoFromService(final Runnable callback) {
        hasAudioStream = true;
        new AsyncTask<Void, Void, AudioStream>() {
            @Override
            protected AudioStream doInBackground(Void... voids) {
                if(info != null) {
                    try {
                        final StreamExtractor extractor = NewPipe.getService(info.service_id)
                                    .getExtractorInstance(info.webpage_url);
                        try {
                            final List<AudioStream> audioStreams = extractor.getAudioStreams();
                            Log.d(TAG, String.format("Found %d audio track for song at %s",
                                    audioStreams.size(), info.webpage_url));

                            final AudioStream audioStream = audioStreams
                                    .get(getPreferredAudioStreamId(audioStreams));
                            Log.d(TAG, String.format("Url for track at %s\r\n%s", info.webpage_url,
                                    audioStream));
                            return audioStream;
                        } catch (ParsingException e) {
                            // fail back convert video to audio
                            final List<VideoStream> videoStreams = extractor.getVideoStreams();
                            VideoStream selectedVideoStreamsBest = null;
                            VideoStream selectedVideoStreamsSmall = null;
                            int previousResolution = -1;
                            for (final VideoStream videoStream : videoStreams) {
                                if ("360p".equals(videoStream.resolution)) {
                                    selectedVideoStreamsBest = videoStream;
                                }
                                final int resolution = extractNumberPositiveInteger(videoStream.resolution);
                                if (previousResolution == -1 || resolution < previousResolution) {
                                    previousResolution = resolution;
                                    selectedVideoStreamsSmall = videoStream;
                                }
                            }
                            // check if we use wifi or not for avoid big download data on mobile network
                            final boolean isConnectedByWifi = NetworkHelper.isOnlineByWifi(activity);
                            final VideoStream videoStream = isConnectedByWifi &&
                                    selectedVideoStreamsBest != null ?
                                            selectedVideoStreamsBest : selectedVideoStreamsSmall;

                            if(videoStream == null) {
                                return null;
                            }
                            Log.w(TAG, String.format("No audio track found, use fallback process " +
                                            "convert to AudioStream the video item (%s - %s)",
                                    MediaFormat.getMimeById(videoStream.format),
                                    videoStream.resolution));
                            return new AudioStream(videoStream.url, videoStream.format, -1, -1);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "FAILED to found a proper audioStream value!", e);
                        return null;
                    }
                } else {
                    return null;
                }
            }

            private int extractNumberPositiveInteger(final String str) {
                final Matcher m = Pattern.compile("[0-9]+").matcher(str);
                return m.find() ? Integer.parseInt(m.group()) : -1;
            }

            @Override
            protected void onPostExecute(AudioStream preferedAudioStream) {
                super.onPostExecute(preferedAudioStream);
                audioStream = preferedAudioStream;
                if(callback != null) {
                    callback.run();
                }
            }
        }.execute();
    }

    private int getPreferredAudioStreamId(final List<AudioStream> audioStreams) {
        String preferredFormatString = PreferenceManager.getDefaultSharedPreferences(activity)
                .getString(activity.getString(R.string.default_audio_format_key), "webm");

        int preferredFormat = MediaFormat.WEBMA.id;
        switch (preferredFormatString) {
            case "webm":
                preferredFormat = MediaFormat.WEBMA.id;
                break;
            case "m4a":
                preferredFormat = MediaFormat.M4A.id;
                break;
            default:
                break;
        }

        for (int i = 0; i < audioStreams.size(); i++) {
            if (audioStreams.get(i).format == preferredFormat) {
                Log.d(TAG, String.format("Preferred audio format found : %s with id : %d", preferredFormatString, preferredFormat));
                return i;
            }
        }

        //todo: make this a proper error
        Log.e(TAG, "FAILED to set audioStream value, use the default value 0 !");
        return 0;
    }

    public boolean hasLoadBitmap() {
        return hasLoadBitmap;
    }

    public boolean hasAudioStream() {
        return hasAudioStream;
    }

    public Intent retrieveIntent() {
        if (bitmap != null && audioStream != null) {
            ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail = bitmap;
            final String mime = MediaFormat.getMimeById(audioStream.format);
            final Uri uri = Uri.parse(audioStream.url);
            final Intent intent = new Intent(activity, BackgroundPlayer.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.putExtra(BackgroundPlayer.TITLE, info.title);
            intent.putExtra(BackgroundPlayer.WEB_URL, info.webpage_url);
            intent.putExtra(BackgroundPlayer.SERVICE_ID, info.service_id);
            intent.putExtra(PLAYLIST_ID, playListId);
            intent.putExtra(NewPipeSQLiteHelper.PLAYLIST_LINK_ENTRIES.POSITION, info.position);
            intent.putExtra(BackgroundPlayer.CHANNEL_NAME, info.uploader);
            return intent;
        } else {
            return null;
        }
    }

    public void process(final boolean forcePlay) {
        if (!BackgroundPlayer.isRunning || forcePlay) {
            if (bitmap != null && audioStream != null) {
                activity.startService(retrieveIntent());
            } else if (!hasLoadBitmap) {
                retrieveBitmap(new Runnable() {
                    @Override
                    public void run() {
                        process(forcePlay);
                    }
                });
            } else if (!hasAudioStream) {
                retrieveInfoFromService(new Runnable() {
                    @Override
                    public void run() {
                        process(forcePlay);
                    }
                });
            }
        }
    }
}
