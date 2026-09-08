/*
  ParallelChunkedDownloader

  - Starts an asynchronous chunked download manager using OkHttp.
  - Stores each chunk to a temporary "<filename>.partN" inside the mission metadata directory.
  - Resumes by reading existing .part file lengths and requesting only remaining bytes.
  - Merges the part files into the final file via mission.storage.getStream (SharpStream).
  - Reports aggregate progress through mission.notifyProgress(...) to reuse existing notification flow.
  - On permanent failure, cleans up parts and returns to fallback by launching existing single-threaded downloader.
  - Respects mission.running (pause) by cancelling in-flight requests and allowing resume.
*/

package us.shandian.giga.get;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

public final class ParallelChunkedDownloader {

    private static final int DEFAULT_CHUNKS = 8;
    private static final int MAX_CHUNKS = 16;
    private static final int IO_BUFFER = 64 * 1024; // 64KB
    private static final int CONNECTION_POOL_SIZE = 32;
    private static final int MAX_RETRIES = 3;

    private ParallelChunkedDownloader() { /* utility */ }

    /**
     * Start an asynchronous chunked download manager for the mission's current resource.
     * Returns true if the manager was started; false if chunked download is not applicable
     * (e.g. no range support) and the caller should continue with the existing fallback logic.
     *
     * The manager sets mission.threads to a single Thread representing itself so that
     * mission.notifyFinished() semantics remain compatible.
     */
    public static boolean startChunkedDownload(final DownloadMission mission) {
        if (mission == null || mission.urls == null || mission.urls.length == 0) return false;
        if (mission.metadata == null) return false; // need a place to store part files
        if (!mission.running) return false;

        final String url = mission.urls[mission.current];

        // Create OkHttp client based on app client but with a larger connection pool and custom dispatcher
        OkHttpClient baseClient = DownloaderImpl.getInstance().getClient();
        Dispatcher dispatcher = new Dispatcher();
        // maxRequests will be adjusted later to chunkCount + 2; set a safe default now
        dispatcher.setMaxRequests(64);
        dispatcher.setMaxRequestsPerHost(64);

        OkHttpClient clientTemplate = baseClient.newBuilder()
                .connectionPool(new ConnectionPool(CONNECTION_POOL_SIZE, 5, TimeUnit.MINUTES))
                .dispatcher(dispatcher)
                .build();

        // We'll perform a quick synchronous check to see if range is supported and get content-length.
        long contentLength = -1;
        boolean supportRange = false;

        try {
            Request headReq = new Request.Builder().url(url).head().build();
            try (Response r = clientTemplate.newCall(headReq).execute()) {
                if (r != null) {
                    String cl = r.header("Content-Length");
                    if (cl != null) {
                        try { contentLength = Long.parseLong(cl); } catch (NumberFormatException ignored) {}
                    }
                    String ar = r.header("Accept-Ranges");
                    if (ar != null && ar.toLowerCase().contains("bytes")) {
                        supportRange = true;
                    }
                }
            }
        } catch (IOException ignored) {
            // fall through to ranged GET test
        }

        if (!supportRange || contentLength <= 0) {
            Request r = new Request.Builder().url(url).header("Range", "bytes=0-1").get().build();
            try (Response resp = clientTemplate.newCall(r).execute()) {
                if (resp != null) {
                    int code = resp.code();
                    if (code == 206) supportRange = true;
                    if (contentLength <= 0) {
                        String cl = resp.header("Content-Length");
                        if (cl != null) {
                            try { contentLength = Long.parseLong(cl); } catch (NumberFormatException ignored) {}
                        } else {
                            String cr = resp.header("Content-Range");
                            if (cr != null) {
                                int slash = cr.indexOf('/');
                                if (slash > 0 && slash + 1 < cr.length()) {
                                    try { contentLength = Long.parseLong(cr.substring(slash + 1)); } catch (NumberFormatException ignored) {}
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                return false; // network problem; don't start chunked manager
            }
        }

        if (!supportRange || contentLength <= 0) return false;

        // Determine chunk count
        int preferred = DEFAULT_CHUNKS;
        try {
            preferred = Math.max(1, Math.min(MAX_CHUNKS, mission.threadCount > 0 ? mission.threadCount : DEFAULT_CHUNKS));
        } catch (Exception ignored) {}
        final int chunkCount = Math.min(MAX_CHUNKS, preferred);

        // Adjust dispatcher limits to match concurrency needs
        dispatcher.setMaxRequests(chunkCount + 2);
        dispatcher.setMaxRequestsPerHost(chunkCount + 2);

        final OkHttpClient client = clientTemplate.newBuilder().dispatcher(dispatcher).build();

        // Compute ranges
        final long total = contentLength;
        final long baseChunkSize = total / chunkCount;
        final long remainder = total % chunkCount;

        File tmpDir = mission.metadata.getParentFile();
        if (tmpDir == null) return false;

        final List<Chunk> chunks = new ArrayList<>(chunkCount);
        long cursor = 0;
        for (int i = 0; i < chunkCount; i++) {
            long start = cursor;
            long size = baseChunkSize + (i < remainder ? 1 : 0);
            long end = start + size - 1;
            if (end < start) end = start;
            File partFile = new File(tmpDir, mission.storage.getName() + ".part" + i);
            chunks.add(new Chunk(i, start, end, partFile));
            cursor += size;
        }

        final AtomicLong aggregateDownloaded = new AtomicLong(0L);
        for (Chunk c : chunks) {
            if (c.part.exists()) {
                long existing = c.part.length();
                long chunkLen = c.end - c.start + 1;
                if (existing >= chunkLen) {
                    c.next = c.end + 1;
                    aggregateDownloaded.addAndGet(chunkLen);
                } else {
                    c.next = c.start + existing;
                    aggregateDownloaded.addAndGet(existing);
                }
            } else {
                c.next = c.start;
            }
        }

        // If already complete, merge in background and return true
        Thread manager = new Thread(() -> {
            boolean success = false;
            try {
                if (aggregateDownloaded.get() >= total) {
                    mergePartsAndCleanup(mission, chunks, tmpDir);
                    long now = aggregateDownloaded.get();
                    if (now < total) mission.notifyProgress((int)(total - now));
                    success = true;
                } else {
                    // Start executor and download chunks concurrently
                    ExecutorService executor = Executors.newFixedThreadPool(chunkCount);
                    final AtomicLong failureFlag = new AtomicLong(0);
                    final List<Future<Boolean>> futures = new ArrayList<>(chunkCount);

                    for (Chunk c : chunks) {
                        Future<Boolean> f = executor.submit(() -> {
                            // skip finished
                            if (c.next > c.end) return true;

                            int attempts = 0;
                            while (c.next <= c.end && mission.running && mission.errCode == DownloadMission.ERROR_NOTHING) {
                                String range = "bytes=" + c.next + "-" + c.end;
                                Request req = new Request.Builder().url(url).header("Range", range).get().build();

                                try (Response resp = client.newCall(req).execute()) {
                                    if (resp == null || !resp.isSuccessful()) {
                                        throw new IOException("HTTP " + (resp == null ? "null" : resp.code()));
                                    }
                                    int code = resp.code();
                                    if (code != 206 && code != 200) {
                                        throw new IOException("Unsupported response " + code);
                                    }

                                    ResponseBody body = resp.body();
                                    if (body == null) throw new IOException("Empty body");

                                    try (InputStream is = body.byteStream(); RandomAccessFile raf = new RandomAccessFile(c.part, "rw")) {
                                        raf.seek(raf.length());
                                        long pos = c.next;
                                        byte[] buf = new byte[IO_BUFFER];
                                        int read;
                                        while ((read = is.read(buf)) != -1 && mission.running && mission.errCode == DownloadMission.ERROR_NOTHING) {
                                            int toWrite = read;
                                            long remaining = c.end - pos + 1;
                                            if (toWrite > remaining) toWrite = (int) remaining;
                                            raf.write(buf, 0, toWrite);
                                            pos += toWrite;
                                            mission.notifyProgress(toWrite);
                                            aggregateDownloaded.addAndGet(toWrite);
                                            if (toWrite < read) break;
                                            if (pos > c.end) break;
                                        }
                                        c.next = pos;
                                    }

                                    // successful fetch for this iteration, reset attempts
                                    attempts = 0;

                                    // if completed, break
                                    if (c.next > c.end) return true;

                                } catch (IOException e) {
                                    attempts++;
                                    if (attempts >= MAX_RETRIES) {
                                        failureFlag.set(1);
                                        return false;
                                    }
                                    // transient failure: small backoff and retry
                                    try { Thread.sleep(1000L * attempts); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
                                    continue;
                                }
                            }

                            // if mission was paused, we didn't fail permanently: just return false to indicate not done
                            if (!mission.running) return false;

                            return c.part.length() >= (c.end - c.start + 1);
                        });

                        futures.add(f);
                    }

                    // Wait for completion while respecting mission pause
                    boolean aborted = false;
                    try {
                        for (Future<Boolean> ff : futures) {
                            try {
                                // poll each future with small timeout to remain responsive to pause
                                while (true) {
                                    if (!mission.running) {
                                        aborted = true;
                                        break;
                                    }
                                    try {
                                        Boolean res = ff.get(1, TimeUnit.SECONDS);
                                        if (res == null || !res) {
                                            failureFlag.set(1);
                                        }
                                        break;
                                    } catch (TimeoutException te) {
                                        // loop and check mission.running again
                                        continue;
                                    }
                                }
                                if (aborted) break;
                            } catch (ExecutionException ee) {
                                failureFlag.set(1);
                            }
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        failureFlag.set(1);
                    } finally {
                        executor.shutdownNow();
                    }

                    if (!mission.running) {
                        // paused: leave part files for resume and do not cleanup
                        return;
                    }

                    if (failureFlag.get() != 0) {
                        // permanent failure: cleanup parts & meta and kick off fallback single-threaded download
                        deleteParts(chunks);
                        deleteMetaFile(tmpDir, mission.storage.getName());

                        // Start fallback single-threaded download in a new thread to mimic original behavior
                        Thread fallback = new Thread(() -> {
                            Thread.currentThread().setName("[DLFallback] " + mission.storage.getName());
                            try {
                                new DownloadRunnableFallback(mission).run();
                            } catch (Throwable t) {
                                // let mission.notifyError handle
                                mission.notifyError(t instanceof Exception ? (Exception) t : new Exception(t));
                            }
                        });
                        mission.threads = new Thread[]{fallback};
                        fallback.start();
                        return;
                    }

                    // Success: merge and cleanup
                    mergePartsAndCleanup(mission, chunks, tmpDir);
                    deleteMetaFile(tmpDir, mission.storage.getName());
                    long now = aggregateDownloaded.get();
                    if (now < total) mission.notifyProgress((int)(total - now));
                    success = true;
                }
            } catch (Exception ex) {
                // On unexpected exceptions, cleanup and fallback
                deleteParts(chunks);
                deleteMetaFile(tmpDir, mission.storage.getName());
                try {
                    Thread fallback = new Thread(() -> new DownloadRunnableFallback(mission).run());
                    mission.threads = new Thread[]{fallback};
                    fallback.start();
                } catch (Exception ignored) {}
            } finally {
                if (success) {
                    // notifyFinished to advance the mission for the current resource
                    mission.notifyFinished();
                }
            }
        }, "[ParallelChunkedDownloader] " + mission.storage.getName());

        // set the mission thread to manager so pause/interrupt logic works (pauseThreads will interrupt it)
        mission.threads = new Thread[]{manager};
        manager.start();
        return true;
    }

    private static void deleteParts(List<Chunk> chunks) {
        for (Chunk c : chunks) {
            try { c.part.delete(); } catch (Exception ignored) {}
        }
    }

    private static void deleteMetaFile(File tmpDir, String baseName) {
        try {
            File meta = new File(tmpDir, baseName + ".chunks.meta");
            if (meta.exists()) meta.delete();
        } catch (Exception ignored) {}
    }

    private static void mergePartsAndCleanup(DownloadMission mission, List<Chunk> chunks, File tmpDir) throws IOException {
        try (SharpStream out = mission.storage.getStream()) {
            out.seek(mission.offsets[mission.current]);
            byte[] buf = new byte[IO_BUFFER];
            for (Chunk c : chunks) {
                try (FileInputStream fis = new FileInputStream(c.part)) {
                    int r;
                    while ((r = fis.read(buf)) != -1) {
                        out.write(buf, 0, r);
                    }
                }
            }
            out.flush();
        }

        // delete parts
        deleteParts(chunks);
    }

    private static class Chunk {
        final int idx;
        final long start;
        final long end;
        final File part;
        volatile long next;

        Chunk(int idx, long start, long end, File part) {
            this.idx = idx;
            this.start = start;
            this.end = end;
            this.part = part;
            this.next = start;
        }
    }
}
