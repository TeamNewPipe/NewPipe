package org.schabi.newpipe;

import android.util.AtomicFile;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/** Persistent, explicit cache for benchmark YouTube player responses. */
final class YoutubePlayerResponseCache {
    private static final String TAG = "YoutubePlayerCache";
    private static final String PLAYER_PATH = "/youtubei/v1/player";

    private final File directory;
    private final boolean replace;

    YoutubePlayerResponseCache(@NonNull final File directory, final boolean replace) {
        this.directory = directory;
        this.replace = replace;
    }

    boolean handles(@NonNull final String url) {
        return url.contains(PLAYER_PATH);
    }

    @Nullable
    synchronized byte[] read(@NonNull final String url,
                             @NonNull final Map<String, List<String>> headers,
                             @Nullable final byte[] requestBody) {
        if (replace) {
            return null;
        }
        final File file = cacheFile(url, headers, requestBody);
        if (!file.isFile()) {
            Log.i(TAG, "miss file=" + file.getName());
            return null;
        }
        try {
            final byte[] body = readFully(new AtomicFile(file));
            Log.i(TAG, "hit file=" + file.getName() + " bytes=" + body.length);
            return body;
        } catch (final IOException e) {
            throw new IllegalStateException("Could not read cached YouTube player response "
                    + file, e);
        }
    }

    synchronized void write(@NonNull final String url,
                            @NonNull final Map<String, List<String>> headers,
                            @Nullable final byte[] requestBody,
                            @NonNull final byte[] responseBody) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create YouTube player cache directory " + directory);
        }
        final File file = cacheFile(url, headers, requestBody);
        if (file.isFile() && !replace) {
            return;
        }
        final AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomicFile.startWrite();
            output.write(responseBody);
            atomicFile.finishWrite(output);
            Log.i(TAG, (replace ? "replaced" : "stored") + " file=" + file.getName()
                    + " bytes=" + responseBody.length);
        } catch (final IOException e) {
            if (output != null) {
                atomicFile.failWrite(output);
            }
            throw e;
        }
    }

    @NonNull
    private File cacheFile(@NonNull final String url,
                           @NonNull final Map<String, List<String>> headers,
                           @Nullable final byte[] requestBody) {
        final String videoId = getVideoId(requestBody);
        final String clientId = firstHeader(headers, "X-YouTube-Client-Name");
        final String clientVersion = firstHeader(headers, "X-Youtube-Client-Version");
        final String key = url.substring(0, url.indexOf(PLAYER_PATH) + PLAYER_PATH.length())
                + '\n' + videoId + '\n' + clientId + '\n' + clientVersion;
        return new File(directory, sha256(key) + ".json");
    }

    @NonNull
    private static String getVideoId(@Nullable final byte[] requestBody) {
        if (requestBody == null) {
            throw new IllegalArgumentException("YouTube player request has no body");
        }
        try {
            final JsonObject body = JsonParser.object()
                    .from(new String(requestBody, StandardCharsets.UTF_8));
            final String videoId = body.getString("videoId");
            if (videoId == null || videoId.isEmpty()) {
                throw new IllegalArgumentException("YouTube player request has no videoId");
            }
            return videoId;
        } catch (final Exception e) {
            throw new IllegalArgumentException("Could not parse YouTube player request", e);
        }
    }

    @NonNull
    private static String firstHeader(@NonNull final Map<String, List<String>> headers,
                                      @NonNull final String wantedName) {
        for (final Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (wantedName.equalsIgnoreCase(entry.getKey()) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return "unknown";
    }

    @NonNull
    private static byte[] readFully(@NonNull final AtomicFile file) throws IOException {
        try (FileInputStream input = file.openRead();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    @NonNull
    private static String sha256(@NonNull final String value) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder output = new StringBuilder(digest.length * 2);
            for (final byte item : digest) {
                output.append(String.format("%02x", item & 0xff));
            }
            return output.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
