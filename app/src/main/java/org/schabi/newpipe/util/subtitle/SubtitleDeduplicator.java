package org.schabi.newpipe.extractor.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

/**
 * SubtitleDeduplicator.java
 *
 * 1. This file is responsible for checking if the subtitles
 * contain any duplicate entries.
 *   a) If duplicates are found, it performs the following steps:
 *      downloads the subtitle (TTML format), deduplicates it,
 *      and stores it locally.
 *   b) If no duplicates are found, downloads and stores it.
 *
 * 2. Core Functions:
 * - checkAndDeduplicate(): Checks for duplicate subtitles
 *   and handles downloading, deduplication, and local storage.
 *
 */

public final class SubtitleDeduplicator {
    private static final String TAG = "SubtitleDeduplicator";
    public static final String LOCAL_SUBTITLE_URL_PREFIX = "file://";

    private static final float BACKOFF_FACTOR = 1.0f;

    private static String subCacheDir = "subtitle_cache";

    private static File cacheDir = null;

    private SubtitleDeduplicator() {
        // no instance
    }

    // cacheDir is /storage/emulated/0/Android/data/<package_name>/cache/{subCacheDir}
    public static void setCacheDirPath(final String path) {
        if (stringIsNullOrEmpty(path)) {
            return;
        }

        cacheDir = new File(path, subCacheDir);

        createDirIfNotExist(cacheDir);
    }

    // Returns either a remote subtitle URL or a local file URI (file://)
    // @param remoteSubtitleUrl: A valid YouTube subtitle URL, expected to
    //                           contain videoId and languageCode parameters.
    public static String checkAndDeduplicate(final String remoteSubtitleUrl,
                                             final MediaFormat format,
                                             final SubtitleOrigin currentSubtitleOrigin) {
        if (!isCacheDirAvailable()) {
            printCacheDirNotInitialized();
            return remoteSubtitleUrl;
        }
        // *** Step 1: Download remote subtitle content

        // - The remote subtitle is ALWAYS downloaded to ensure
        //   the newest version is used.
        // - Although cached subtitles are available, they may be
        //   outdated since the video creator or the YouTube
        //   platform can update them.

        // Current subtitle format is TTML
        final String downloadedContent = downloadRemoteSubtitleContent(
                                            remoteSubtitleUrl,
                                            currentSubtitleOrigin,
                                            3,
                                            1000);

        if (subtitleDownloadFails(downloadedContent)) {
            return fallbackToStoredOrRemote(remoteSubtitleUrl,
                                            format,
                                            currentSubtitleOrigin);
        }

        String finalContent = null;
        SubtitleState currentSubtitleState = SubtitleState.ORIGINAL;

        // *** Step 2: Detect and deduplicate if needed

        if (containsDuplicatedEntries(downloadedContent)) {
            finalContent = deduplicateContent(downloadedContent);
            currentSubtitleState = SubtitleState.DEDUPLICATED;
        } else {
            finalContent = downloadedContent;
            currentSubtitleState = SubtitleState.ORIGINAL;
        }

        // *** Step 3: Store subtitle to cache and return local URI if possible

        final File currentCacheFile = getCacheFile(remoteSubtitleUrl,
                                             format,
                                             currentSubtitleOrigin,
                                             currentSubtitleState);

        final String localSubtitleUri = storeItToCacheDir(finalContent,
                                             format,
                                             currentSubtitleOrigin,
                                             currentCacheFile);

        if (subtitleStorageFails(localSubtitleUri)) {
            return fallbackToStoredOrRemote(remoteSubtitleUrl,
                                            format,
                                            currentSubtitleOrigin);
        }

        return localSubtitleUri;
    }

    private static boolean isCacheDirAvailable() {
        if (null == cacheDir) {
            return false;
        }

        return createDirIfNotExist(cacheDir);
    }

    private static boolean createDirIfNotExist(final File directory) {
        if (!directory.exists()) {
            directory.mkdirs();
        }

        return ((directory.exists()) && (directory.isDirectory()));
    }

    private static void printCacheDirNotInitialized() {
        final String errorMessage =
                "SubtitleDeduplicator cache directory is not initialized. "
              + "Fallback to original subtitle without deduplication. "
              + "setCacheDirPath() should be called before using this class.";

        System.err.println(TAG + ": " + errorMessage);
    }

    private static String downloadRemoteSubtitleContent(final String urlStr,
                                                        final SubtitleOrigin currentOrigin,
                                                        final int maxRetries,
                                                        final int initialDelayMillis) {
        final Downloader downloader = NewPipe.getDownloader();
        if (downloader == null) {
            System.err.println(TAG + ": Downloader not initialized");
            return null;
        }
        // if auto-translate language subtitle, use the bigger data.
        int delay = resolveDelay(currentOrigin, initialDelayMillis);
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                final Map<String, List<String>> headers = new HashMap<>();
                headers.put("Accept", Collections.singletonList("text/*"));
                headers.put("Accept-Language", Collections.singletonList("en-US,en;q=0.9"));
                final Response response = downloader.get(urlStr, headers);
                if (response.responseCode() == 200) {
                    return response.responseBody();
                } else {
                    System.err.println(TAG + ": Attempt " + attempt
                                        + " failed with status: "
                                        + response.responseCode()
                                        + " URL: " + urlStr);
                    if (response.responseCode() != 503 && response.responseCode() != 429) {
                        return null;
                    }
                }
            } catch (IOException | ReCaptchaException e) {
                System.err.println(TAG + ": Attempt " + attempt
                                    + " failed: " + e.getMessage()
                                    + " URL: " + urlStr);
            }
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(delay);
                    delay = adjustDelayAfterRetry(delay);
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        System.err.println(TAG + ": Failed to download subtitle after "
                            + maxRetries + " URL: " + urlStr);
        return null;
    }

    private static boolean isAutoTranslateSubtitle(final SubtitleOrigin currentOrigin) {
        return (currentOrigin == SubtitleOrigin.AUTO_TRANSLATED);
    }

    private static int resolveDelay(final SubtitleOrigin currentOrigin,
                                    final int baseDelayMillis) {
        if (isAutoTranslateSubtitle(currentOrigin)) {
            // Auto-translated subtitles are observed to be less reliable.
            // A separate delay path is kept to allow future tuning without
            // affecting the common subtitle download flow.
            return (baseDelayMillis + 1);
        } else {
            return baseDelayMillis;
        }
    }

    private static int adjustDelayAfterRetry(final int currentDelayMillis) {
        return (int) (currentDelayMillis * BACKOFF_FACTOR);
    }

    public static boolean containsDuplicateTtmlEntries(final File subtitleFile) {
        if (subtitleFile == null || !subtitleFile.exists()) {
            return false;
        }

        try {
            final String content = readFileToString(subtitleFile);
            return containsDuplicatedEntries(content);
        } catch (final IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Detects whether the subtitle contains duplicated <p> entries
    // using the same normalized (whitespace-trimmed) comparison rules
    // as deduplicateContent().
    public static boolean containsDuplicatedEntries(final String subtitleContent) {
        if (stringIsNullOrEmpty(subtitleContent)) {
            return false;
        }

        final Matcher matcher = getTtmlMatcher(subtitleContent);

        final Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            final String key = getSubtitleKeyOfTtml(matcher);

            if (seen.contains(key)) {
                return true;
            }
            seen.add(key);
        }

        return false;
    }

    private static String readFileToString(final File file) throws IOException {
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public static String deduplicateTtmlFile(final File subtitleFile) {
        if (subtitleFile == null || !subtitleFile.exists()) {
            return "";
        }

        try {
            final String content = readFileToString(subtitleFile);
            return deduplicateContent(content);
        } catch (final IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String deduplicateContent(final String subtitleContent) {
        // Subtitle entries are considered duplicated only if:
        // 1) begin timestamp is exactly the same,
        // 2) end timestamp is exactly the same,
        // 3) subtitle text content is the same
        //    after normalized (trimming and whitespace normalization).
        //
        // This is a normalized comparison (trimmed and whitespace-normalized).
        // No semantic analysis or fuzzy matching is performed.

        if (stringIsNullOrEmpty(subtitleContent)) {
            return subtitleContent;
        }

        final Matcher matcher = getTtmlMatcher(subtitleContent);

        final Set<String> seen = new HashSet<>();
        final StringBuilder result = new StringBuilder();

        int lastIndex = 0;
        while (matcher.find()) {
            result.append(subtitleContent, lastIndex, matcher.start());

            final String key = getSubtitleKeyOfTtml(matcher);

            if (!seen.contains(key)) {
                result.append(matcher.group(0));
                seen.add(key);
            }

            lastIndex = matcher.end();
        }

        result.append(subtitleContent.substring(lastIndex));
        return result.toString();
    }

    private static boolean stringIsNullOrEmpty(final String inputString) {
        if (null == inputString) {
            return true;
        }

        if (inputString.isEmpty()) {
            return true;
        }

        return false;
    }

    private static Pattern defineTtmlSubtitlePattern() {
        return Pattern.compile(
            "<p[^>]*begin=\"([^\"]+)\"[^>]*end=\"([^\"]+)\"[^>]*>(.*?)</p>",
            Pattern.DOTALL
        );
    }

    private static Matcher getTtmlMatcher(final String subtitleContent) {
        final Pattern pattern = defineTtmlSubtitlePattern();
        return pattern.matcher(subtitleContent);
    }

    private static String getSubtitleKeyOfTtml(final Matcher matcher) {
        final String begin = matcher.group(1).trim();
        final String end = matcher.group(2).trim();

        // Normalize subtitle text before comparison:
        // - Leading and trailing whitespace is ignored
        // - Runs of whitespace are collapsed into a single space (' ')
        //
        // Note:
        // This operates on raw TTML text as received (before XML entity decoding).
        // XML-encoded whitespace (e.g. &#x9;) is not decoded at this stage.
        //
        // This is intentional: visually identical subtitles may differ only
        // in whitespace due to formatting or extraction differences, and
        // should be considered duplicates in such cases.
        final String content = matcher.group(3)
                                .trim()
                                .replaceAll("\\s+", " ");

        final String key = begin + "|" + end + "|" + content;
        return key;
    }

    private static String buildLocalFileUri(final File subtitleCacheFile) {
        final String path = LOCAL_SUBTITLE_URL_PREFIX + subtitleCacheFile.getAbsolutePath();

        return path;
    }

    private static String storeItToCacheDir(final String subtitleContent,
                                            final MediaFormat format,
                                            final SubtitleOrigin currentSubtitleOrigin,
                                            final File currentCacheFile) {
        final File cacheFile = currentCacheFile;

        final String cacheFilePathForExoplayer = buildLocalFileUri(cacheFile);

        if (!ensureItsParentDirExist(cacheFile)) {
            return null;
        }

        if (null == writeDeduplicatedContentToCachefile(subtitleContent, cacheFile)) {
            return cacheFilePathForExoplayer;
        } else {
            System.err.println(TAG + ": Failed to write cache file: "
                                + cacheFile.getAbsolutePath());
            return null;
        }
    }

    // filename without dir path
    private static String computeFilename(final String subtitleUrl,
                                          final MediaFormat format,
                                          final SubtitleOrigin currentSubtitleOrigin,
                                          final SubtitleState currentSubtitleState) {
        final String videoId = getVideoId(subtitleUrl);

        final String languageCode = resolveSubtitleLanguage(
                subtitleUrl,
                currentSubtitleOrigin
        );

        final String filename = buildSubtitleCacheFilename(videoId,
                                                     languageCode,
                                                     currentSubtitleOrigin,
                                                     currentSubtitleState,
                                                     format.getSuffix());

        return filename;
    }

    public static SubtitleOrigin getSubtitleOrigin(final boolean autoGenerated,
                                                   final boolean autoTranslate) {
        if (autoTranslate) {
            return SubtitleOrigin.AUTO_TRANSLATED;
        }
        if (autoGenerated) {
            return SubtitleOrigin.AUTO_GENERATED;
        }
        return SubtitleOrigin.HUMAN_PROVIDED;
    }

    @Nonnull
    private static String buildSubtitleCacheFilename(
            @Nonnull final String videoId,
            @Nonnull final String language,
            @Nonnull final SubtitleOrigin origin,
            @Nonnull final SubtitleState state,
            @Nonnull final String extension
    ) {
        final String filenamePartSeparator = "--";

        return videoId
                + filenamePartSeparator + language
                + filenamePartSeparator + origin.getId()
                + filenamePartSeparator + state.getId()
                + "." + extension;
    }

    private static String getLanguageCode(final String remoteSubtitleUrl) {
        String languageCode = null;
        languageCode = YoutubeParsingHelper.extractLanguageCode(remoteSubtitleUrl);
        return languageCode;
    }

    private static String getAutoTranslateLanguage(final String remoteSubtitleUrl) {
        // For auto-translate subtitles Url, there are two language code in it:
        // one is 'lang', now its meaning is source language;
        // the other is 'tlang', its meaning is target language.
        String targetAutoTranslate = null;
        targetAutoTranslate = YoutubeParsingHelper.extractTranslationCode(
                remoteSubtitleUrl
        );
        return targetAutoTranslate;
    }

    // For auto-translate subtitles, the cache filename language
    // represents the target language (tlang), not the source language.
    private static String resolveSubtitleLanguage(
            final String subtitleUrl,
            final SubtitleOrigin origin
    ) {
        if (origin == SubtitleOrigin.AUTO_TRANSLATED) {
            final String targetLang = getAutoTranslateLanguage(subtitleUrl);

            if (!stringIsNullOrEmpty(targetLang)) {
                return targetLang;
            } else {
                final String unknownLanguage = "unknownLanguage";
                return unknownLanguage;
            }
        }

        return getLanguageCode(subtitleUrl);
    }

    // Extract the videoId (e.g., "b7vmW_5HSpE") from a subtitle URL
    // (e.g., .../api/timedtext?v=b7vmW_5HSpE)
    // for use in generating unique filenames.
    private static String getVideoId(final String remoteSubtitleUrl) {
        return YoutubeParsingHelper.extractVideoId(remoteSubtitleUrl);
    }

    private static File getCacheFile(final String subtitleUrl,
                                    final MediaFormat format,
                                    final SubtitleOrigin currentSubtitleOrigin,
                                    final SubtitleState currentSubtitleState) {
        final String cachefilename = computeFilename(subtitleUrl,
                                                format,
                                                currentSubtitleOrigin,
                                                currentSubtitleState);

        final File cacheFile = new File(cacheDir, cachefilename);

        return cacheFile;
    }

    private static File findStoredCacheFile(
            final String remoteSubtitleUrl,
            final MediaFormat format,
            final SubtitleOrigin currentSubtitleOrigin
    ) {
        for (final SubtitleState state : SubtitleState.values()) {
            final File subtitleFile = getCacheFile(
                    remoteSubtitleUrl,
                    format,
                    currentSubtitleOrigin,
                    state
            );

            if (subtitleFile.exists() && subtitleFile.length() > 0) {
                return subtitleFile;
            }
        }

        return null;
    }

    @Nonnull
    private static String fallbackToStoredOrRemote(
            @Nonnull final String remoteSubtitleUrl,
            @Nonnull final MediaFormat format,
            @Nonnull final SubtitleOrigin origin
    ) {
        final File storedFile = findStoredCacheFile(
                remoteSubtitleUrl,
                format,
                origin
        );

        if (storedFile != null) {
            final String previousStoredUri = buildLocalFileUri(storedFile);
            return previousStoredUri;
        }

        return remoteSubtitleUrl;
    }

    private static boolean subtitleDownloadFails(final String contentDownloaded) {
        return (null == contentDownloaded);
    }

    private static boolean subtitleStorageFails(final String localUriAfterStores) {
        return (null == localUriAfterStores);
    }

    private static boolean ensureItsParentDirExist(final File tempCacheFile) {
        final File parentDir = tempCacheFile.getParentFile();

        if (parentDir.exists()) {
            return true;
        } else {
            final boolean result = parentDir.mkdirs();
            return result;
        }
    }

    private static String writeDeduplicatedContentToCachefile(
                                            final String subtitleContent,
                                            final File tempCacheFile) {
        return writeContentToFile(subtitleContent, tempCacheFile);
    }

    private static String writeContentToFile(final String content,
                                             final File tempFile) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
            writer.write(content);
            //ok
            return null;
        } catch (final IOException e) {
            final String errorMessage = e.getMessage();
            System.err.println(TAG + ": Failed to write cache file: " + errorMessage);
            return errorMessage;
        }
    }

}
