package us.shandian.giga.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import org.schabi.newpipe.R;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utility {

    public enum FileType {
        VIDEO,
        MUSIC,
        UNKNOWN
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return String.format("%d B", bytes);
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f kB", (float) bytes / 1024);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", (float) bytes / 1024 / 1024);
        } else {
            return String.format("%.2f GB", (float) bytes / 1024 / 1024 / 1024);
        }
    }

    public static String formatSpeed(float speed) {
        if (speed < 1024) {
            return String.format("%.2f B/s", speed);
        } else if (speed < 1024 * 1024) {
            return String.format("%.2f kB/s", speed / 1024);
        } else if (speed < 1024 * 1024 * 1024) {
            return String.format("%.2f MB/s", speed / 1024 / 1024);
        } else {
            return String.format("%.2f GB/s", speed / 1024 / 1024 / 1024);
        }
    }

    public static void writeToFile(@NonNull String fileName, @NonNull Serializable serializable) {
        ObjectOutputStream objectOutputStream = null;

        try {
            objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)));
            objectOutputStream.writeObject(serializable);
        } catch (Exception e) {
            //nothing to do
        } finally {
            if(objectOutputStream != null) {
                try {
                    objectOutputStream.close();
                } catch (Exception e) {
                    //nothing to do
                }
            }
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T readFromFile(String file) {
        T object = null;
        ObjectInputStream objectInputStream = null;

        try {
            objectInputStream = new ObjectInputStream(new FileInputStream(file));
            object = (T) objectInputStream.readObject();
        } catch (Exception e) {
            //nothing to do
        }

        if(objectInputStream != null){
            try {
                objectInputStream .close();
            } catch (Exception e) {
                //nothing to do
            }
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

    public static FileType getFileType(String file) {
        if (file.endsWith(".mp3") || file.endsWith(".wav") || file.endsWith(".flac") || file.endsWith(".m4a")) {
            return FileType.MUSIC;
        } else if (file.endsWith(".mp4") || file.endsWith(".mpeg") || file.endsWith(".rm") || file.endsWith(".rmvb")
                || file.endsWith(".flv") || file.endsWith(".webp") || file.endsWith(".webm")) {
            return FileType.VIDEO;
        } else {
            return FileType.UNKNOWN;
        }
    }

    @ColorRes
    public static int getBackgroundForFileType(FileType type) {
        switch (type) {
            case MUSIC:
                return R.color.audio_left_to_load_color;
            case VIDEO:
                return R.color.video_left_to_load_color;
            default:
                return R.color.gray;
        }
    }

    @ColorRes
    public static int getForegroundForFileType(FileType type) {
        switch (type) {
            case MUSIC:
                return R.color.audio_already_load_color;
            case VIDEO:
                return R.color.video_already_load_color;
            default:
                return R.color.gray;
        }
    }

    @DrawableRes
    public static int getIconForFileType(FileType type) {
        switch (type) {
            case MUSIC:
                return R.drawable.music;
            case VIDEO:
                return R.drawable.video;
            default:
                return R.drawable.video;
        }
    }

    public static void copyToClipboard(Context context, String str) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("text", str));
        Toast.makeText(context, R.string.msg_copied, Toast.LENGTH_SHORT).show();
    }

    public static String checksum(String path, String algorithm) {
        MessageDigest md = null;

        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        FileInputStream i = null;

        try {
            i = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        byte[] buf = new byte[1024];
        int len = 0;

        try {
            while ((len = i.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
        } catch (IOException ignored) {

        }

        byte[] digest = md.digest();

        // HEX
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }

        return sb.toString();

    }
}
