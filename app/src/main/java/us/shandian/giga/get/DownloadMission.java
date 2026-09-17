@@
         if (blocks.length < 1) {
             threads = new Thread[]{runAsync(1, new DownloadRunnableFallback(this))};
         } else {
             int remainingBlocks = 0;
             for (int block : blocks) if (block >= 0) remainingBlocks++;
 
             if (remainingBlocks < 1) {
                 notifyFinished();
                 return;
             }
-
-            threads = new Thread[Math.min(threadCount, remainingBlocks)];
-
-            for (int i = 0; i < threads.length; i++) {
-                threads[i] = runAsync(i + 1, new DownloadRunnable(this, i));
-            }
+
+            // Try the new parallel chunked downloader (per-chunk .part files + OkHttp).
+            // If it returns true, the current resource was downloaded and merged already.
+            boolean chunkedOk = ParallelChunkedDownloader.tryChunkedDownload(this);
+            if (chunkedOk) {
+                notifyFinished();
+                return;
+            }
+
+            // Fallback: original threaded block downloader
+            threads = new Thread[Math.min(threadCount, remainingBlocks)];
+
+            for (int i = 0; i < threads.length; i++) {
+                threads[i] = runAsync(i + 1, new DownloadRunnable(this, i));
+            }
         }
@@
