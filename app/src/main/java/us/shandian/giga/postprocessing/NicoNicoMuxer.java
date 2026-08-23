package us.shandian.giga.postprocessing;

import android.content.Context;
import android.net.Uri;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.Statistics;
import com.arthenica.ffmpegkit.StatisticsCallback;
import org.schabi.newpipe.streams.io.SharpStream;
import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.io.CircularFileWriter;

import java.io.IOException;
import java.net.URLDecoder;

public class NicoNicoMuxer extends Postprocessing{
    NicoNicoMuxer() {
        super(true, true, NICONICO_MUXER);
    }

    @Override
    int process(String source, Context context, SharpStream out, SharpStream... sources) throws IOException {
        ((CircularFileWriter)out).finalizeFile();
        return OK_RESULT;
    }
    int download(String source, Context context, String[] urls, DownloadMission mission) {
        String stream = FFmpegKitConfig.getSafParameter(context, Uri.parse(source), "w");
        String url = urls[0].split("#cookie=")[0];
        String additionalParam = URLDecoder.decode(urls[0].split("cookie=")[1]);
        String cookie = additionalParam.split("&length=")[0];
        String length = additionalParam.split("&length=")[1];
        String audioUrl = urls.length > 1 ? urls[1] : "";
        String filter = "";
        if (url.contains("audio")) {
            filter = "-bsf:a aac_adtstoasc";
        }
        FFmpegKitConfig.enableStatisticsCallback(new StatisticsCallback() {
            @Override
            public void apply(Statistics statistics) {
                mission.done += 1;
            }
        });
        String audioString = audioUrl.isEmpty() ? "" : String.format(" -headers \"Cookie: %s\" -i \"%s\"", cookie, audioUrl);
        FFmpegKit.execute(String.format("-headers \"Cookie: %s\" -protocol_whitelist \"file,http,https,tcp,tls,httpproxy,crypto\"  -i \"%s\" %s -c copy %s %s", cookie, url, audioString, filter, stream));
        return OK_RESULT;
    }
}
