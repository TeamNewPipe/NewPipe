package org.schabi.newpipe.dearrow;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Optional DeArrow title/thumbnail metadata for YouTube videos. */
public final class DeArrowService {
    private static final String BRANDING_ENDPOINT = "https://sponsor.ajay.app/api/branding";
    private static final String THUMBNAIL_ENDPOINT =
            "https://dearrow-thumb.ajay.app/api/v1/getThumbnail";
    private static final int CACHE_SIZE = 500;
    private static final Pattern YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile(
            "(?:[?&]v=|youtu\\.be/|/(?:embed|shorts|live)/)([A-Za-z0-9_-]{11})");
    private static final Object CACHE_LOCK = new Object();
    private static final LruCache<String, Branding> CACHE = new LruCache<>(CACHE_SIZE);
    private static final Map<String, Single<Branding>> IN_FLIGHT = new HashMap<>();

    private DeArrowService() {
    }

    @NonNull
    public static Single<Branding> getBranding(@NonNull final Context context,
                                                @NonNull final StreamInfo info) {
        return getBranding(context, info.getServiceId(), info.getId(), info.getUrl());
    }

    public static boolean applyBranding(@NonNull final Context context,
                                        @NonNull final StreamInfo info,
                                        @NonNull final Branding branding) {
        if (!isEnabled(context)) {
            return false;
        }

        boolean changed = false;
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean(context.getString(R.string.dearrow_titles_key), true)
                && branding.title != null && !branding.title.equals(info.getName())) {
            info.setName(branding.title);
            changed = true;
        }
        if (preferences.getBoolean(context.getString(R.string.dearrow_thumbnails_key), true)
                && branding.thumbnailUrl != null) {
            final Image image = new Image(branding.thumbnailUrl, Image.HEIGHT_UNKNOWN,
                    Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN);
            info.setThumbnails(Collections.singletonList(image));
            changed = true;
        }
        return changed;
    }

    @NonNull
    private static Single<Branding> getBranding(@NonNull final Context context,
                                                 final int serviceId,
                                                 final String id,
                                                 final String url) {
        final String videoId = isYoutubeVideoId(id) ? id : youtubeVideoId(url);
        if (!isEnabled(context) || serviceId != ServiceList.YouTube.getServiceId()
                || !isYoutubeVideoId(videoId)) {
            return Single.just(Branding.EMPTY);
        }

        synchronized (CACHE_LOCK) {
            final Branding cached = CACHE.get(videoId);
            if (cached != null) {
                return Single.just(cached);
            }
            final Single<Branding> pending = IN_FLIGHT.get(videoId);
            if (pending != null) {
                return pending;
            }

            final Single<Branding> request = Single.fromCallable(() -> fetch(videoId))
                    .subscribeOn(Schedulers.io())
                    .onErrorReturnItem(Branding.EMPTY)
                    .doOnSuccess(branding -> {
                        synchronized (CACHE_LOCK) {
                            CACHE.put(videoId, branding);
                        }
                    })
                    .doFinally(() -> {
                        synchronized (CACHE_LOCK) {
                            IN_FLIGHT.remove(videoId);
                        }
                    })
                    .cache();
            IN_FLIGHT.put(videoId, request);
            return request;
        }
    }

    private static boolean isEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.dearrow_enable_key), false);
    }

    private static boolean isYoutubeVideoId(final String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }

    private static String youtubeVideoId(final String url) {
        if (url == null) {
            return null;
        }
        final Matcher matcher = YOUTUBE_VIDEO_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    @NonNull
    private static Branding fetch(@NonNull final String videoId) throws Exception {
        final String encodedId = URLEncoder.encode(videoId, StandardCharsets.UTF_8.name());
        final Response response = DownloaderImpl.getInstance().get(
                BRANDING_ENDPOINT + "?videoID=" + encodedId + "&fetchAll=true");
        if (response.responseCode() != 200) {
            return Branding.EMPTY;
        }
        return parseBranding(videoId, response.responseBody());
    }

    @NonNull
    static Branding parseBranding(@NonNull final String videoId, @NonNull final String body)
            throws Exception {
        final JsonObject root = JsonParser.object().from(body);
        String title = null;
        String thumbnailUrl = null;

        final JsonArray titles = root.getArray("titles");
        if (titles != null && !titles.isEmpty()) {
            final JsonObject candidate = titles.getObject(0);
            if (isAccepted(candidate) && !candidate.getBoolean("original", false)) {
                final String candidateTitle = candidate.getString("title");
                title = candidateTitle == null ? null : candidateTitle.trim();
                if (title != null && title.isEmpty()) {
                    title = null;
                }
            }
        }

        final JsonArray thumbnails = root.getArray("thumbnails");
        if (thumbnails != null && !thumbnails.isEmpty()) {
            final JsonObject candidate = thumbnails.getObject(0);
            if (isAccepted(candidate) && !candidate.getBoolean("original", false)
                    && candidate.containsKey("timestamp")) {
                final double timestamp = candidate.getDouble("timestamp");
                if (Double.isFinite(timestamp) && timestamp >= 0) {
                    thumbnailUrl = THUMBNAIL_ENDPOINT + "?videoID="
                            + URLEncoder.encode(videoId, StandardCharsets.UTF_8.name())
                            + "&time=" + timestamp;
                }
            }
        }
        return title == null && thumbnailUrl == null
                ? Branding.EMPTY : new Branding(title, thumbnailUrl);
    }

    private static boolean isAccepted(final JsonObject candidate) {
        return candidate != null
                && (candidate.getBoolean("locked", false) || candidate.getInt("votes", -1) >= 0);
    }

    public static final class Branding {
        public static final Branding EMPTY = new Branding(null, null);

        private final String title;
        private final String thumbnailUrl;

        Branding(final String title, final String thumbnailUrl) {
            this.title = title;
            this.thumbnailUrl = thumbnailUrl;
        }
    }
}
