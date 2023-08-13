package us.shandian.giga.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.util.Util;

import org.schabi.newpipe.R;
import org.schabi.newpipe.streams.io.SharpInputStream;
import org.schabi.newpipe.streams.io.StoredFileHelper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.Random;

import okio.ByteString;
import us.shandian.giga.get.DownloadMission;

public class Utility {

    public enum FileType {
        VIDEO,
        MUSIC,
        SUBTITLE,
        UNKNOWN
    }

    public static String formatBytes(long bytes) {
        Locale locale = Locale.getDefault();
        if (bytes < 1024) {
            return String.format(locale, "%d B", bytes);
        } else if (bytes < 1024 * 1024) {
            return String.format(locale, "%.2f kB", bytes / 1024d);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(locale, "%.2f MB", bytes / 1024d / 1024d);
        } else {
            return String.format(locale, "%.2f GB", bytes / 1024d / 1024d / 1024d);
        }
    }

    public static String formatSpeed(double speed) {
        Locale locale = Locale.getDefault();
        if (speed < 1024) {
            return String.format(locale, "%.2f B/s", speed);
        } else if (speed < 1024 * 1024) {
            return String.format(locale, "%.2f kB/s", speed / 1024);
        } else if (speed < 1024 * 1024 * 1024) {
            return String.format(locale, "%.2f MB/s", speed / 1024 / 1024);
        } else {
            return String.format(locale, "%.2f GB/s", speed / 1024 / 1024 / 1024);
        }
    }

    public static void writeToFile(@NonNull File file, @NonNull Serializable serializable) {

        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            objectOutputStream.writeObject(serializable);
        } catch (Exception e) {
            //nothing to do
        }
        //nothing to do
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T readFromFile(File file) {
        T object;

        try (ObjectInputStream objectInputStream =
                     new ObjectInputStream(new FileInputStream(file))) {
            object = (T) objectInputStream.readObject();
        } catch (Exception e) {
            Log.e("Utility", "Failed to deserialize the object", e);
            object = null;
        }

        return object;
    }

    @Nullable
    public static String getFileExt(String url) {
        int index;
        if ((index = url.indexOf("?")) > -1) {
            url = url.substring(0, index);
        }

        index = url.lastIndexOf(".");
        if (index == -1) {
            return null;
        } else {
            String ext = url.substring(index);
            if ((index = ext.indexOf("%")) > -1) {
                ext = ext.substring(0, index);
            }
            if ((index = ext.indexOf("/")) > -1) {
                ext = ext.substring(0, index);
            }
            return ext.toLowerCase();
        }
    }

    public static FileType getFileType(char kind, String file) {
        switch (kind) {
            case 'v':
                return FileType.VIDEO;
            case 'a':
                return FileType.MUSIC;
            case 's':
                return FileType.SUBTITLE;
            //default '?':
        }

        if (file.endsWith(".srt") || file.endsWith(".vtt") || file.endsWith(".ssa")) {
            return FileType.SUBTITLE;
        } else if (file.endsWith(".mp3") || file.endsWith(".wav") || file.endsWith(".flac") || file.endsWith(".m4a") || file.endsWith(".opus")) {
            return FileType.MUSIC;
        } else if (file.endsWith(".mp4") || file.endsWith(".mpeg") || file.endsWith(".rm") || file.endsWith(".rmvb")
                || file.endsWith(".flv") || file.endsWith(".webp") || file.endsWith(".webm")) {
            return FileType.VIDEO;
        }

        return FileType.UNKNOWN;
    }

    @ColorInt
    public static int getBackgroundForFileType(Context ctx, FileType type) {
        int colorRes;
        switch (type) {
            case MUSIC:
                colorRes = R.color.audio_left_to_load_color;
                break;
            case VIDEO:
                colorRes = R.color.video_left_to_load_color;
                break;
            case SUBTITLE:
                colorRes = R.color.subtitle_left_to_load_color;
                break;
            default:
                colorRes = R.color.gray;
        }

        return ContextCompat.getColor(ctx, colorRes);
    }

    @ColorInt
    public static int getForegroundForFileType(Context ctx, FileType type) {
        int colorRes;
        switch (type) {
            case MUSIC:
                colorRes = R.color.audio_already_load_color;
                break;
            case VIDEO:
                colorRes = R.color.video_already_load_color;
                break;
            case SUBTITLE:
                colorRes = R.color.subtitle_already_load_color;
                break;
            default:
                colorRes = R.color.gray;
                break;
        }

        return ContextCompat.getColor(ctx, colorRes);
    }

    @DrawableRes
    public static int getIconForFileType(FileType type) {
        switch (type) {
            case MUSIC:
                return R.drawable.ic_headset;
            default:
            case VIDEO:
                return R.drawable.ic_movie;
            case SUBTITLE:
                return R.drawable.ic_subtitles;
        }
    }

    public static String checksum(final StoredFileHelper source, final int algorithmId)
            throws IOException {
        ByteString byteString;
        try (var inputStream = new SharpInputStream(source.getStream())) {
            byteString = ByteString.of(Util.toByteArray(inputStream));
        }
        if (algorithmId == R.id.md5) {
            byteString = byteString.md5();
        } else if (algorithmId == R.id.sha1) {
            byteString = byteString.sha1();
        }
        return byteString.hex();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static boolean mkdir(File p, boolean allDirs) {
        if (p.exists()) return true;

        if (allDirs)
            p.mkdirs();
        else
            p.mkdir();

        return p.exists();
    }

    public static long getContentLength(HttpURLConnection connection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return connection.getContentLengthLong();
        }

        try {
            return Long.parseLong(connection.getHeaderField("Content-Length"));
        } catch (Exception err) {
            // nothing to do
        }

        return -1;
    }

    /**
     * Get the content length of the entire file even if the HTTP response is partial
     * (response code 206).
     * @param connection http connection
     * @return content length
     */
    public static long getTotalContentLength(final HttpURLConnection connection) {
        try {
            if (connection.getResponseCode() == 206) {
                final String rangeStr = connection.getHeaderField("Content-Range");
                final String bytesStr = rangeStr.split("/", 2)[1];
                return Long.parseLong(bytesStr);
            } else {
                return getContentLength(connection);
            }
        } catch (Exception err) {
            // nothing to do
        }

        return -1;
    }

    private static String pad(int number) {
        return number < 10 ? ("0" + number) : String.valueOf(number);
    }

    public static String stringifySeconds(final long seconds) {
        final int h = (int) Math.floorDiv(seconds, 3600);
        final int m = (int) Math.floorDiv(seconds - (h * 3600L), 60);
        final int s = (int) (seconds - (h * 3600) - (m * 60));

        String str = "";

        if (h < 1 && m < 1) {
            str = "00:";
        } else {
            if (h > 0) str = pad(h) + ":";
            if (m > 0) str += pad(m) + ":";
        }

        return str + pad(s);
    }
}
