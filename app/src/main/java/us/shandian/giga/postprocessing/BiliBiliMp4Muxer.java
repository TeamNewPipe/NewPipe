package us.shandian.giga.postprocessing;

import android.content.Context;
import android.net.Uri;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import org.schabi.newpipe.streams.io.SharpStream;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import us.shandian.giga.io.CircularFileWriter;

import java.io.IOException;

public class BiliBiliMp4Muxer extends Postprocessing{


    public BiliBiliMp4Muxer() {
        super(true, true, BILIBILI_MUXER);
    }

    @Override
    int process(String source, Context context, SharpStream out, SharpStream... sources) throws IOException {
        return OK_RESULT;
    }
    public int mux(StoredFileHelper storage, Context context, SharpStream out, SharpStream... sources) throws IOException {
        byte[] buffer = new byte[8 * 1024];
        int read;
        String source = storage.source;
        // write audio to bytes
        SharpStream audioOut =  new StoredFileHelper(context, Uri.parse(storage.sourceTree), Uri.parse(source.replace(".mp4", ".tmp")), "audio").getStream();
        while ((read = sources[1].read(buffer)) > 0) {
            audioOut.write(buffer, 0, read);
        }

        buffer = new byte[8 * 1024];
        while ((read = sources[0].read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        ((CircularFileWriter)out).finalizeFile();
        String video = FFmpegKitConfig.getSafParameter(context, Uri.parse(source), "rw");
        String audio = FFmpegKitConfig.getSafParameterForRead(context, Uri.parse(source.replace(".mp4", ".tmp")));
        String temp = FFmpegKitConfig.getSafParameter(context, Uri.parse(source.replace(".mp4", ".tmp.mp4")), "rw");
        FFmpegKit.execute(String.format("-i %s -i %s -strict -2 -c copy -y %s", video, audio, temp));
        temp = FFmpegKitConfig.getSafParameter(context, Uri.parse(source.replace(".mp4", ".tmp.mp4")), "rw");
        video  = FFmpegKitConfig.getSafParameter(context, Uri.parse(source), "w");
        FFmpegKit.execute(String.format("-i %s -strict -2 -c copy -y %s", temp, video));

        return OK_RESULT;
    }

//    public void mux() {
//
//        String temp = FFmpegKitConfig.getSafParameter(context, Uri.parse(fileName.replace(".mp4", ".tmp.mp4")), "rw");
//        String video  = FFmpegKitConfig.getSafParameter(context, Uri.parse(fileName), "rw");
//        String audio = FFmpegKitConfig.getSafParameterForRead(context, Uri.parse(fileName.replace(".mp4", ".tmp")));
//        FFmpegKit.execute(String.format("-i %s -i %s -strict -2 -c copy -y %s", video, audio, temp));
//        temp = FFmpegKitConfig.getSafParameter(context, Uri.parse(fileName.replace(".mp4", ".tmp.mp4")), "rw");
//        video  = FFmpegKitConfig.getSafParameter(context, Uri.parse(fileName), "w");
//        FFmpegKit.execute(String.format("-i %s -strict -2 -c copy -y %s", temp, video));
//    }
}
