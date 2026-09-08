/*
  ParallelChunkedDownloader

  - Tries an 8-chunk (configurable up to 16) parallel download using OkHttp.
  - Stores each chunk to a temporary "<filename>.partN" inside the mission metadata directory.
  - Resumes by reading existing .part file lengths and requesting only remaining bytes.
  - Merges the part files into the final file via mission.storage.getStream (SharpStream).
  - Reports aggregate progress through mission.notifyProgress(...) to reuse existing notification flow.
  - On permanent failure, cleans up parts and returns false so caller can fallback.
*/

package us.shandian.giga.get;

import okhttp3.Call;
import okhttp3.ConnectionPool;
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

    private ParallelChunkedDownloader() { /* utility */ }

    /**
     * Try to download the current resource of the mission using parallel ranged requests.
     * Blocks until finished or failed. If returns true, the mission is finished for the current resource.
     * If false is returned, caller should fallback to the existing single-threaded mechanism.
     */
    public static boolean tryChunkedDownload(DownloadMission mission) {
        // Basic preconditions (must be called from a background thread).
        if (mission == null || mission.urls == null || mission.urls.length == 0) return false;
        if (mission.metadata == null) return false; // we need a place to store part files
        if (!mission.running) return false;

        final String url = mission.urls[mission.current];

        // Create an OkHttpClient derived from the app client but with larger connection pool and dispatcher defaults.
        OkHttpClient baseClient = DownloaderImpl.getInstance().getClient();
        OkHttpClient client = baseClient.newBuilder()
                .connectionPool(new ConnectionPool(CONNECTION_POOL_SIZE, 5, TimeUnit.MINUTES))
                .build();

        long contentLength = -1;
        boolean supportRange = false;

        // 1) HEAD (preferred) to get Content-Length and Accept-Ranges
        Request headReq = new Request.Builder().url(url).head().build();
        try (Response r = client.newCall(headReq).execute()) {
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
        } catch (IOException ignored) {
            // We'll attempt a ranged GET test below if HEAD failed or didn't declare ranges.
        }

        // If we don't know length or HEAD didn't explicitly announce range support, try a small ranged GET
        if (!supportRange || contentLength <= 0) {
            Request r = new Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-1")
                    .get()
                    .build();
            try (Response resp = client.newCall(r).execute()) {
                if (resp != null) {
                    int code = resp.code();
                    if (code == 206) supportRange = true;
                    if (contentLength <= 0) {
                        String cl = resp.header("Content-Length");
                        if (cl != null) {
                            try { contentLength = Long.parseLong(cl); } catch (NumberFormatException ignored) {}
                        } else {
                            // Some servers report full length in Content-Range
                            String cr = resp.header("Content-Range"); // bytes 0-1/12345
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
                // If we can't reach server for this check, fallback to existing mechanism
                return false;
            }
        }

        // If server doesn't support range or we don't know the total length, fallback
        if (!supportRange || contentLength <= 0) return false;

        // Decide number of chunks
        int preferred = DEFAULT_CHUNKS;
        try {
            preferred = Math.max(1, Math.min(MAX_CHUNKS, mission.threadCount > 0 ? mission.threadCount : DEFAULT_CHUNKS));
        } catch (Exception ignored) {}
        int chunkCount = Math.min(MAX_CHUNKS, preferred);

        // Compute ranges
        long total = contentLength;
        long baseChunkSize = total / chunkCount;
        long remainder = total % chunkCount;

        // Prepare part files in same directory as mission.metadata so they survive restarts
        File tmpDir = mission.metadata.getParentFile();
        if (tmpDir == null) return false;

        List<Chunk> chunks = new ArrayList<>(chunkCount);
        long cursor = 0;
        for (int i = 0; i < chunkCount; i++) {
            long start = cursor;
            long size = baseChunkSize + (i < remainder ? 1 : 0);
            long end = start + size - 1;
            if (end < start) end = start; // guard
            File partFile = new File(tmpDir, mission.storage.getName() + ".part" + i);
            chunks.add(new Chunk(i, start, end, partFile));
            cursor += size;
        }

        // Determine already-downloaded progress from existing part file sizes
        final AtomicLong aggregateDownloaded = new AtomicLong(0L);
        for (Chunk c : chunks) {
            if (c.part.exists()) {
                long existing = c.part.length();
                long chunkLen = c.end - c.start + 1;
                if (existing >= chunkLen) {
                    c.next = c.end + 1; // already done
                    aggregateDownloaded.addAndGet(chunkLen);
                } else {
                    c.next = c.start + existing;
                    aggregateDownloaded.addAndGet(existing);
                }
            } else {
                c.next = c.start;
            }
        }

        // If already complete, merge and finish
        if (aggregateDownloaded.get() >= total) {
            try {
                mergePartsAndCleanup(mission, chunks, tmpDir);
                mission.notifyProgress((int)(total - mission.done)); // adjust mission.done if needed
                return true;
            } catch (IOException e) {
                // merging failed; cleanup and fallback
                deleteParts(chunks);
                return false;
            }
        }

        // Start concurrent downloads
        ExecutorService executor = Executors.newFixedThreadPool(chunkCount);
        List<Future<Boolean>> futures = new ArrayList<>(chunkCount);
        CountDownLatch latch = new CountDownLatch(chunkCount);
        final AtomicLong failureFlag = new AtomicLong(0);

        for (Chunk c : chunks) {
            Future<Boolean> f = executor.submit(() -> {
                try {
                    // If already finished, nothing to do
                    if (c.next > c.end) {
                        latch.countDown();
                        return true;
                    }

                    // Append to part file in case it exists
                    try (RandomAccessFile raf = new RandomAccessFile(c.part, "rw")) {
                        raf.seek(raf.length());
                        long pos = c.next;
                        while (pos <= c.end && mission.running && mission.errCode == DownloadMission.ERROR_NOTHING) {
                            String range = "bytes=" + pos + "-" + c.end;
                            Request req = new Request.Builder()
                                    .url(url)
                                    .header("Range", range)
                                    .get()
                                    .build();

                            Call call = client.newCall(req);
                            try (Response resp = call.execute()) {
                                if (resp == null || !resp.isSuccessful()) {
                                    throw new IOException("HTTP " + (resp == null ? "null" : resp.code()));
                                }
                                // Accept 206 or sometimes 200 (some servers ignore Range)
                                int code = resp.code();
                                if (code != 206 && code != 200) {
                                    throw new IOException("Unsupported response " + code);
                                }

                                ResponseBody body = resp.body();
                                if (body == null) throw new IOException("Empty body");

                                try (InputStream is = body.byteStream()) {
                                    byte[] buf = new byte[IO_BUFFER];
                                    int read;
                                    while ((read = is.read(buf)) != -1) {
                                        // compute how many bytes we should actually write (do not exceed chunk)
                                        int toWrite = read;
                                        long remaining = c.end - pos + 1;
                                        if (toWrite > remaining) toWrite = (int) remaining;
                                        raf.write(buf, 0, toWrite);
                                        pos += toWrite;
                                        mission.notifyProgress(toWrite);
                                        aggregateDownloaded.addAndGet(toWrite);
                                        if (toWrite < read) break; // extra bytes were sent
                                        if (pos > c.end) break;
                                        if (!mission.running) break;
                                    }
                                }
                            }
                        }
                    }

                    // completed or mission stopped
                    return (c.part.length() >= (c.end - c.start + 1));
                } catch (Exception e) {
                    failureFlag.set(1);
                    return false;
                } finally {
                    latch.countDown();
                }
            });

            futures.add(f);
        }

        // Wait for all to complete or for a failure
        try {
            // Wait while mission.running; but to keep responsiveness, wait with timeout
            boolean ok = latch.await(30, TimeUnit.MINUTES); // long cap
            // If any future failed, treat as permanent failure
            for (Future<Boolean> ff : futures) {
                try {
                    if (!ff.isDone() || !ff.get()) {
                        failureFlag.set(1);
                        break;
                    }
                } catch (Exception e) {
                    failureFlag.set(1);
                    break;
                }
            }
        } catch (InterruptedException e) {
            failureFlag.set(1);
        } finally {
            executor.shutdownNow();
        }

        if (failureFlag.get() != 0 || !mission.running || mission.errCode != DownloadMission.ERROR_NOTHING) {
            // abort: cleanup parts and return false so caller falls back
            deleteParts(chunks);
            return false;
        }

        // Merge parts into destination via SharpStream (atomic-ish)
        try {
            mergePartsAndCleanup(mission, chunks, tmpDir);
            // Ensure mission.notifyProgress was updated during downloads; final update to reach total
            long now = aggregateDownloaded.get();
            if (now < total) {
                mission.notifyProgress((int)(total - now));
            }
            return true;
        } catch (IOException e) {
            deleteParts(chunks);
            return false;
        }
    }

    private static void deleteParts(List<Chunk> chunks) {
        for (Chunk c : chunks) {
            try { c.part.delete(); } catch (Exception ignored) {}
        }
    }

    private static void mergePartsAndCleanup(DownloadMission mission, List<Chunk> chunks, File tmpDir) throws IOException {
        // Open mission final stream and write parts in order
        try (SharpStream out = mission.storage.getStream()) {
            // ensure output size is reserved (initializer usually set fs.setLength earlier)
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
