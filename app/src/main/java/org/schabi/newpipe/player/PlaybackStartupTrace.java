package org.schabi.newpipe.player;

import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Measures the production detail-click to rendered-first-frame path. */
public final class PlaybackStartupTrace {
    public static final String EXTRA_TRACE_ID =
            "org.schabi.newpipe.player.extra.STARTUP_TRACE_ID";
    private static final String TAG = "PlaybackStartup";
    private static final String RECORD = "PIPEPLAY_PLAYBACK_STARTUP";
    private static final int MAX_TRACES = 32;
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final Map<Long, Trace> TRACES = new ConcurrentHashMap<>();
    private static final Map<String, Long> ACTIVE_URLS = new ConcurrentHashMap<>();
    private static final Map<String, Long> ACTIVE_VIDEO_IDS = new ConcurrentHashMap<>();

    private PlaybackStartupTrace() {
    }

    public static long begin(@NonNull final String videoId, @NonNull final String url) {
        final long id = NEXT_ID.incrementAndGet();
        final Trace trace = new Trace(id, videoId, url, SystemClock.elapsedRealtimeNanos());
        TRACES.put(id, trace);
        ACTIVE_URLS.put(url, id);
        ACTIVE_VIDEO_IDS.put(videoId, id);
        trim();
        mark(id, "detail_click");
        return id;
    }

    public static void attach(@NonNull final Intent intent, final long id) {
        if (id > 0) {
            intent.putExtra(EXTRA_TRACE_ID, id);
            mark(id, "intent_created");
        }
    }

    public static long fromIntent(@NonNull final Intent intent) {
        return intent.getLongExtra(EXTRA_TRACE_ID, 0);
    }

    public static void markForUrl(@NonNull final String url, @NonNull final String stage) {
        final Long id = ACTIVE_URLS.get(url);
        if (id != null) {
            mark(id, stage);
        }
    }

    public static void markForVideoId(@NonNull final String videoId,
                                      @NonNull final String stage) {
        final Long id = ACTIVE_VIDEO_IDS.get(videoId);
        if (id != null) {
            mark(id, stage);
        }
    }

    public static void mark(final long id, @NonNull final String stage) {
        final Trace trace = TRACES.get(id);
        if (trace == null) {
            return;
        }
        final Stage recorded = trace.mark(stage, SystemClock.elapsedRealtimeNanos());
        if (recorded != null) {
            Log.i(TAG, RECORD + " " + trace.stageJson(stage, recorded));
        }
    }

    public static void finish(final long id) {
        mark(id, "first_frame");
        final Trace trace = TRACES.get(id);
        if (trace != null && trace.finish()) {
            ACTIVE_URLS.remove(trace.url, id);
            ACTIVE_VIDEO_IDS.remove(trace.videoId, id);
            Log.i(TAG, RECORD + " " + trace.summaryJson());
        }
    }

    @Nullable
    public static Snapshot snapshot(final long id) {
        final Trace trace = TRACES.get(id);
        return trace == null ? null : trace.snapshot();
    }

    private static void trim() {
        if (TRACES.size() <= MAX_TRACES) {
            return;
        }
        long oldestId = Long.MAX_VALUE;
        for (final Long id : TRACES.keySet()) {
            oldestId = Math.min(oldestId, id);
        }
        final Trace removed = TRACES.remove(oldestId);
        if (removed != null) {
            ACTIVE_URLS.remove(removed.url, oldestId);
            ACTIVE_VIDEO_IDS.remove(removed.videoId, oldestId);
        }
    }

    public static final class Snapshot {
        public final long id;
        @NonNull public final String videoId;
        @NonNull public final String url;
        @NonNull public final Map<String, Long> elapsedMs;
        public final boolean finished;

        Snapshot(final long id, @NonNull final String videoId, @NonNull final String url,
                 @NonNull final Map<String, Long> elapsedMs, final boolean finished) {
            this.id = id;
            this.videoId = videoId;
            this.url = url;
            this.elapsedMs = elapsedMs;
            this.finished = finished;
        }

        @NonNull
        public JSONObject toJson() throws JSONException {
            final JSONObject stages = new JSONObject();
            for (final Map.Entry<String, Long> entry : elapsedMs.entrySet()) {
                stages.put(entry.getKey(), entry.getValue());
            }
            return new JSONObject().put("record", "click_to_first_frame")
                    .put("traceId", id).put("videoId", videoId).put("url", url)
                    .put("finished", finished).put("stagesMs", stages);
        }
    }

    private static final class Trace {
        private final long id;
        @NonNull private final String videoId;
        @NonNull private final String url;
        private final long startedNs;
        private final LinkedHashMap<String, Stage> stages = new LinkedHashMap<>();
        private boolean finished;

        Trace(final long id, @NonNull final String videoId, @NonNull final String url,
              final long startedNs) {
            this.id = id;
            this.videoId = videoId;
            this.url = url;
            this.startedNs = startedNs;
        }

        @Nullable
        synchronized Stage mark(@NonNull final String stage, final long nowNs) {
            if (stages.containsKey(stage)) {
                return null;
            }
            final long previousNs = stages.isEmpty()
                    ? startedNs : stages.values().stream().reduce((a, b) -> b).get().atNs;
            final Stage value = new Stage(toMs(nowNs - startedNs), toMs(nowNs - previousNs), nowNs);
            stages.put(stage, value);
            return value;
        }

        synchronized String stageJson(@NonNull final String stage, @NonNull final Stage value) {
            return "{\"record\":\"stage\",\"traceId\":" + id
                    + ",\"videoId\":\"" + json(videoId) + "\",\"stage\":\""
                    + json(stage) + "\",\"elapsedMs\":" + value.elapsedMs
                    + ",\"deltaMs\":" + value.deltaMs + "}";
        }

        synchronized String summaryJson() {
            try {
                return snapshot().toJson().toString();
            } catch (final JSONException impossible) {
                return "{\"record\":\"click_to_first_frame\",\"traceId\":" + id + "}";
            }
        }

        synchronized Snapshot snapshot() {
            final LinkedHashMap<String, Long> values = new LinkedHashMap<>();
            for (final Map.Entry<String, Stage> entry : stages.entrySet()) {
                values.put(entry.getKey(), entry.getValue().elapsedMs);
            }
            return new Snapshot(id, videoId, url, values, finished);
        }

        synchronized boolean finish() {
            if (finished) {
                return false;
            }
            finished = true;
            return true;
        }
    }

    private static final class Stage {
        private final long elapsedMs;
        private final long deltaMs;
        private final long atNs;

        Stage(final long elapsedMs, final long deltaMs, final long atNs) {
            this.elapsedMs = elapsedMs;
            this.deltaMs = deltaMs;
            this.atNs = atNs;
        }
    }

    private static long toMs(final long nanos) {
        return nanos / 1_000_000L;
    }

    private static String json(@NonNull final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
