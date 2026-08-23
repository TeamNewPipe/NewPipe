package us.shandian.giga.get;

import android.content.Context;
import android.net.Uri;
import android.util.SparseArray;
import androidx.preference.PreferenceManager;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.SecondaryStreamHelper;
import org.schabi.newpipe.util.StreamItemAdapter;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;

import java.io.IOException;
// Keep for BiliBili video case if it writes to outputstream directly
import java.util.ArrayList;
import java.util.List;

import static org.schabi.newpipe.util.FilenameUtils.createFilename;

public class DirectDownloader {

    Context context;
    StreamInfo currentInfo;
    StreamItemAdapter.StreamSizeWrapper<AudioStream> wrappedAudioStreams = StreamItemAdapter.StreamSizeWrapper.empty();
    StreamItemAdapter.StreamSizeWrapper<VideoStream> wrappedVideoStreams = StreamItemAdapter.StreamSizeWrapper.empty();
    int selectedVideoIndex = 0;
    int selectedAudioIndex = 0;

    private StreamItemAdapter<AudioStream, Stream> audioStreamsAdapter;
    private StreamItemAdapter<VideoStream, AudioStream> videoStreamsAdapter;

    public enum DownloadType {
        AUDIO,
        VIDEO
    }

    private DownloadType type;

    public DirectDownloader(final Context context, final StreamInfo info, DownloadType type) {
        final ArrayList<VideoStream> streamsList = new ArrayList<>(ListHelper
                .getSortedStreamVideosList(context, info.getVideoStreams(),
                        info.getVideoOnlyStreams(), false, false));
        final List<VideoStream> filteredVideoStreams = ListHelper
                .filterVideoStreamsByPreferredLanguage(context, streamsList,
                        info.getAudioStreams());
        final int selectedStreamIndex = ListHelper.getDefaultResolutionIndex(
                context, filteredVideoStreams);
        HlsDownloadStreamHelper.addManifestFallbackIfNeeded(filteredVideoStreams, info);

        this.setVideoStreams(filteredVideoStreams);
        this.setSelectedVideoStream(selectedStreamIndex >= 0 ? selectedStreamIndex : 0);
        final List<AudioStream> downloadableAudio = ListHelper
                .filterDownloadableAudioStreams(info.getAudioStreams());
        HlsDownloadStreamHelper.addAudioFallbackIfNeeded(downloadableAudio, info);
        this.setAudioStreams(downloadableAudio);
        this.setInfo(info);
        this.type = type;
        this.context = context;
        init();
    }

    private void setInfo(final StreamInfo info) {
        this.currentInfo = info;
    }

    public void setAudioStreams(final List<AudioStream> audioStreams) {
        setAudioStreams(new StreamItemAdapter.StreamSizeWrapper<>(audioStreams, context));
    }

    public void setAudioStreams(final StreamItemAdapter.StreamSizeWrapper<AudioStream> was) {
        this.wrappedAudioStreams = was;
    }

    public void setVideoStreams(final List<VideoStream> videoStreams) {
        setVideoStreams(new StreamItemAdapter.StreamSizeWrapper<>(videoStreams, context));
    }

    public void setVideoStreams(final StreamItemAdapter.StreamSizeWrapper<VideoStream> wvs) {
        this.wrappedVideoStreams = wvs;
    }

    public void setSelectedVideoStream(final int svi) {
        this.selectedVideoIndex = svi;
    }

    public void setSelectedAudioStream(final int sai) {
        this.selectedAudioIndex = sai;
    }


    public void init(){
        final SparseArray<SecondaryStreamHelper<AudioStream>> secondaryStreams
                = new SparseArray<>(4);
        final List<VideoStream> videoStreams = wrappedVideoStreams.getStreamsList();

        for (int i = 0; i < videoStreams.size(); i++) {
            if (!videoStreams.get(i).isVideoOnly()) {
                continue;
            }
            final AudioStream audioStream = SecondaryStreamHelper
                    .getAudioStreamFor(context, SabrDownloadStreamHelper.audioStreamsForVideo(
                            wrappedAudioStreams.getStreamsList(), videoStreams.get(i)), videoStreams.get(i));

            if (audioStream != null && SabrDownloadStreamHelper
                    .isCompatibleSecondaryStream(videoStreams.get(i), audioStream)) {
                secondaryStreams
                        .append(i, new SecondaryStreamHelper<>(wrappedAudioStreams, audioStream));
            }
        }

        this.videoStreamsAdapter = new StreamItemAdapter<>(context, wrappedVideoStreams,
                secondaryStreams);
        this.audioStreamsAdapter = new StreamItemAdapter<>(context, wrappedAudioStreams);

        String filenameTmp = createFilename(context, currentInfo.getName()).concat(".");
        StoredDirectoryHelper mainStorage;
        MediaFormat format;
        String mimeTmp;
        String uri;
        switch (type) {
            case AUDIO:
                format = audioStreamsAdapter.getItem(selectedAudioIndex).getFormat();
                if (format == MediaFormat.WEBMA_OPUS) {
                    mimeTmp = "audio/ogg";
                    filenameTmp += "opus";
                } else {
                    mimeTmp = format.mimeType;
                    filenameTmp += format.suffix;
                }
                uri = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.download_path_audio_key), "");
                if (uri.isEmpty()) {
                    throw new RuntimeException("No download path selected");
                }
                try {
                    mainStorage = new StoredDirectoryHelper(context, Uri.parse(uri), DownloadManager.TAG_AUDIO);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            case VIDEO:
                format = videoStreamsAdapter.getItem(selectedVideoIndex).getFormat();
                mimeTmp = format.mimeType;
                filenameTmp += format.suffix;
                uri = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.download_path_video_key), "");
                if (uri.isEmpty()) {
                    throw new RuntimeException("No download path selected");
                }
                try {
                    mainStorage = new StoredDirectoryHelper(context, Uri.parse(uri), DownloadManager.TAG_VIDEO);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            default:
                throw new RuntimeException("Unknown download type"); // should never happen
        }
        Uri targetFile = mainStorage.findFile(filenameTmp);
        StoredFileHelper storage;
        String filename = filenameTmp;
        String mime = mimeTmp;
        if (targetFile != null) {
            return;
        }
        if (!mainStorage.mkdirs()) {
            // the directory does not exist and we can't create it
            throw new RuntimeException("Directory does not exist");
        }

        storage = mainStorage.createFile(filename, mime);
        if (storage == null || !storage.canWrite()) {
            throw new RuntimeException("Can't write to file");
        }

        if(currentInfo.getServiceId() == ServiceList.BiliBili.getServiceId() && type == DownloadType.VIDEO){
            mainStorage.createFile(filename.replace(".mp4", ".tmp.mp4"), "video/mp4");
            mainStorage.createFile(filename.replace(".mp4", ".tmp"), String.valueOf(MediaFormat.M4A));
        }

        startDownload(storage);
    }

    public void startDownload(StoredFileHelper storage) {
        final Stream selectedStream;
        Stream secondaryStream = null;
        final char kind;
        int threads = 4;
        final String[] urls;
        final MissionRecoveryInfo[] recoveryInfo;
        final String[] resourceDeliveryMethods;
        final String[] resourceManifestUrls;
        final boolean[] resourceIsUrls;
        String psName = null;
        String[] psArgs = null;
        long nearLength = 0;
        switch (type) {
            case AUDIO:
                kind = 'a';
                selectedStream = audioStreamsAdapter.getItem(selectedAudioIndex);

                if (currentInfo.getService() == ServiceList.NicoNico) {
                    psName = Postprocessing.NICONICO_MUXER;
                } else if (selectedStream.getFormat() == MediaFormat.M4A && currentInfo.getServiceId() != ServiceList.BiliBili.getServiceId()) {
                    psName = Postprocessing.ALGORITHM_M4A_NO_DASH;
                } else if (selectedStream.getFormat() == MediaFormat.WEBMA_OPUS) {
                    psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER;
                }
                break;
            case VIDEO:
                kind = 'v';
                selectedStream = videoStreamsAdapter.getItem(selectedVideoIndex);

                final SecondaryStreamHelper<AudioStream> secondary = videoStreamsAdapter
                        .getAllSecondary()
                        .get(wrappedVideoStreams.getStreamsList().indexOf(selectedStream));

                if (secondary != null) {
                    secondaryStream = secondary.getStream();

                    if(currentInfo.getServiceId() == ServiceList.BiliBili.getServiceId()) {
                        psName = Postprocessing.BILIBILI_MUXER;
                    } else if (currentInfo.getService() == ServiceList.NicoNico) {
                        psName = Postprocessing.NICONICO_MUXER;
                    } else {
                        if (selectedStream.getFormat() == MediaFormat.MPEG_4) {
                            psName = Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER;
                        } else {
                            psName = Postprocessing.ALGORITHM_WEBM_MUXER;
                        }
                    }

                    psArgs = null;
                    final long videoSize = wrappedVideoStreams
                            .getSizeInBytes((VideoStream) selectedStream);

                    // set nearLength, only, if both sizes are fetched or known. This probably
                    // does not work on slow networks but is later updated in the downloader
                    if (secondary.getSizeInBytes() > 0 && videoSize > 0) {
                        nearLength = secondary.getSizeInBytes() + videoSize;
                    }
                }
                break;
            default:
                throw new RuntimeException("Unknown download type"); // should never happen
        }
        if (secondaryStream == null) {
            urls = new String[]{
                    selectedStream.getContent()
            };
            recoveryInfo = new MissionRecoveryInfo[]{
                    new MissionRecoveryInfo(selectedStream)
            };
        } else {
            urls = new String[]{
                    selectedStream.getContent(),
                    secondaryStream.getContent()
            };
            recoveryInfo = new MissionRecoveryInfo[]{new MissionRecoveryInfo(selectedStream),
                    new MissionRecoveryInfo(secondaryStream)};
        }

        resourceDeliveryMethods = HlsDownloadStreamHelper
                .buildResourceDeliveryMethods(selectedStream, secondaryStream);
        resourceManifestUrls = HlsDownloadStreamHelper
                .buildResourceManifestUrls(selectedStream, secondaryStream);
        resourceIsUrls = HlsDownloadStreamHelper
                .buildResourceIsUrls(selectedStream, secondaryStream);
        if (HlsDownloadStreamHelper.containsHlsResource(resourceDeliveryMethods,
                resourceManifestUrls, urls)
                || SabrDownloadStreamHelper.containsSabrStream(selectedStream, secondaryStream)) {
            psName = null;
            psArgs = null;
        }

        DownloadManagerService.startMission(context, urls, storage, kind, threads,
                currentInfo.getUrl(), psName, psArgs, nearLength, recoveryInfo,
                resourceDeliveryMethods, resourceManifestUrls, resourceIsUrls);
    }
}
