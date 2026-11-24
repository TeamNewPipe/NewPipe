package org.schabi.newpipe.extractor.services.youtube.invidious;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.post.PostInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNull;

public class InvidiousExtractionTest {
    private final Downloader oldDownloader = NewPipe.getDownloader();
    private final Localization oldLocalization = NewPipe.getPreferredLocalization();
    private final ContentCountry oldCountry = NewPipe.getPreferredContentCountry();
    private final List<String> requests = new ArrayList<>();
    private String responseBody;
    private int status = 200;
    private java.util.Map<String, List<String>> responseHeaders = Collections.emptyMap();
    private java.util.function.Function<String, String> responseForUrl;
    private static final String VIDEO = "BaW_jenozKc";
    private static final String CHANNEL = "UCAAAAAAAAAAAAAAAAAAAAAA";
    private static final String ITEM = fixture("/invidious/video-item.json");

    private static String fixture(final String path) {
        try (var stream = java.util.Objects.requireNonNull(
                InvidiousExtractionTest.class.getResourceAsStream(path))) {
            return new String(stream.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (final java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Before
    public void setUp() {
        InvidiousBackend.configure(true, "https://example.org");
        NewPipe.init(new Downloader() {
            @Override
            public CancellableCall executeAsync(final Request request,
                    final AsyncCallback callback) {
                throw new UnsupportedOperationException();
            }
            @Override
            public Response execute(final Request request) {
                assertTrue("Unexpected direct request: " + request.url(), request.url()
                        .startsWith("https://example.org/api/v1/"));
                requests.add(request.url());
                final String body = responseForUrl == null ? responseBody : responseForUrl
                        .apply(request.url());
                return new Response(status, "fixture", responseHeaders, body,
                        body.getBytes(java.nio.charset.StandardCharsets.UTF_8), request.url());
            }
        });
    }

    @After
    public void tearDown() {
        InvidiousBackend.configure(false, "");
        NewPipe.init(oldDownloader, oldLocalization, oldCountry);
    }

    @Test
    public void loadsProxiedStreamMetadataAndMedia() throws Exception {
        responseBody = fixture("/invidious/video.json");
        final StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube,
                "https://www.youtube.com/watch?v=" + VIDEO);
        assertEquals("Video", info.getName());
        assertEquals(java.util.Arrays.asList("Science", "Space"), info.getTags());
        assertEquals(120, info.getDuration());
        assertEquals("https://example.org/videoplayback?itag=18", info.getVideoStreams().get(0)
                .getContent());
        assertEquals("1080p", info.getVideoOnlyStreams().get(0).getResolution());
        assertEquals(128, info.getAudioStreams().get(0).getAverageBitrate());
        assertEquals("https://example.org/api/v1/captions/" + VIDEO + "?label=English", info
                .getSubtitles().get(0).getContent());
        assertTrue(info.getThumbnails().get(0).getUrl().startsWith("https://example.org/vi/"));
        assertEquals("https://example.org/ggpht/avatar=s88", info.getUploaderAvatarUrl());
        assertEquals(1, info.getRelatedItems().size());
        assertEquals(Collections.singletonList("https://example.org/api/v1/videos/" + VIDEO
                + "?local=true"), requests);
    }

    @Test
    public void loadsLiveHlsOnlyThroughTheInstance() throws Exception {
        responseBody = ITEM.substring(0, ITEM.length() - 1)
                + ",\"liveNow\":true,\"hlsUrl\":\"https://example.org/api/manifest/hls/id/"
                        + VIDEO + "\"}";
        final StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube,
                "https://www.youtube.com/watch?v=" + VIDEO);
        assertEquals(org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM, info
                .getStreamType());
        assertEquals("https://example.org/api/manifest/hls/id/" + VIDEO + "?local=true", info
                .getHlsUrl());
        assertTrue(info.getVideoStreams().isEmpty());
        assertTrue(info.getAudioStreams().isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    public void loadsLiveDashOnlyThroughTheInstance() throws Exception {
        responseBody = ITEM.substring(0, ITEM.length() - 1)
                + ",\"liveNow\":true,\"dashUrl\":\"/api/manifest/dash/id/"
                + VIDEO + "?quality=hd\"}";
        final StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube,
                "https://www.youtube.com/watch?v=" + VIDEO);
        assertEquals(org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM,
                info.getStreamType());
        assertEquals("https://example.org/api/manifest/dash/id/" + VIDEO
                + "?quality=hd&local=true", info.getDashMpdUrl());
        assertTrue(info.getHlsUrl().isEmpty());
        assertTrue(info.getVideoStreams().isEmpty());
        assertTrue(info.getAudioStreams().isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    public void rejectsDirectDashManifests() throws Exception {
        responseBody = ITEM.substring(0, ITEM.length() - 1)
                + ",\"liveNow\":true,\"dashUrl\":\"https://manifest.googlevideo.com/live.mpd\"}";
        final var extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=" + VIDEO);
        extractor.fetchPage();
        assertThrows(ExtractionException.class, extractor::getDashMpdUrl);
        assertEquals(1, requests.size());
    }

    @Test
    public void resolvesChannelHandlesOnTheInstance() throws Exception {
        responseForUrl = url -> url.contains("resolveurl?")
                ? "{\"browseId\":\"" + CHANNEL + "\"}"
                : "{\"author\":\"Channel\",\"authorId\":\"" + CHANNEL + "\"}";
        final var channel = ServiceList.YouTube
                .getChannelExtractor("https://www.youtube.com/@Channel");
        channel.fetchPage();
        assertEquals("https://www.youtube.com/channel/" + CHANNEL, channel.getUrl());
        assertEquals(2, requests.size());
        assertTrue(requests.get(0)
                .contains("resolveurl?url=https%3A%2F%2Fwww.youtube.com%2F%40Channel"));
        assertEquals("https://example.org/api/v1/channels/" + CHANNEL, requests.get(1));
    }

    @Test
    public void mapsSearchFiltersToInvidiousParameters() throws Exception {
        responseBody = "[" + ITEM + "]";
        final var handler = new org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler(
                "https://www.youtube.com/results", "https://www.youtube.com/results", "video",
                Collections.singletonList(new FilterItem(-1, "videos")),
                java.util.Arrays.asList(new FilterItem(-1, "sort_view"),
                        new FilterItem(-1, "past_week"), new FilterItem(-1, "short_video"),
                        new FilterItem(-1, "Ccommons")));
        ServiceList.YouTube.getSearchExtractor(handler).fetchPage();
        final String request = requests.get(0);
        assertTrue(request.contains("type=video"));
        assertTrue(request.contains("sort=views"));
        assertTrue(request.contains("date=week"));
        assertTrue(request.contains("duration=short"));
        assertTrue(request.contains("features=creative_commons"));
    }

    @Test
    public void refusesDirectMediaAndBackendFailuresWithoutFallback() throws Exception {
        responseBody = ITEM.substring(0, ITEM.length() - 1)
                + ",\"formatStreams\":[{\"itag\":\"18\",\"type\":\"video/mp4\",\"url\":\"https:"
                        + "//rr1.googlevideo.com/videoplayback\"}]}";
        final var extractor = ServiceList.YouTube
                .getStreamExtractor("https://www.youtube.com/watch?v=" + VIDEO);
        extractor.fetchPage();
        assertThrows(ExtractionException.class, extractor::getVideoStreams);
        status = 503;
        assertThrows(ExtractionException.class, () -> new InvidiousApi()
                .object("https://example.org/api/v1/videos/" + VIDEO));
        assertEquals(2, requests.size());
        assertThrows(ExtractionException.class, () -> new InvidiousApi()
                .object("https://youtube.com/api/v1/videos/" + VIDEO));
        assertEquals(2, requests.size());
        status = 200;
        for (final String invalid : new String[]{"not JSON", "{\"error\":\"Unavailable\"}"}) {
            responseBody = invalid;
            assertThrows(ExtractionException.class,
                    () -> new InvidiousApi().object("https://example.org/api/v1/videos/" + VIDEO));
        }
        assertEquals(4, requests.size());
    }

    @Test
    public void searchesAndPaginatesOnTheCapturedInstance() throws Exception {
        responseBody = "[" + ITEM + "]";
        final var search = ServiceList.YouTube.getSearchExtractor("quoted & video");
        search.fetchPage();
        final var first = search.getInitialPage();
        assertEquals(1, first.getItems().size());
        assertTrue(requests.get(0).contains("q=quoted+%26+video"));
        assertTrue(first.getNextPage().getUrl().endsWith("page=2"));
        assertEquals(1, search.getPage(first.getNextPage()).getItems().size());
        InvidiousBackend.configure(true, "https://changed.example.org");
        assertThrows(ExtractionException.class, () -> search.getPage(first.getNextPage()));
        assertEquals(2, requests.size());
    }

    @Test
    public void preservesChannelIdentityAndUsesProxiedChannelTabsAndFeeds() throws Exception {
        responseBody = "{\"author\":\"Channel\",\"authorId\":\"" + CHANNEL
                + "\",\"subCount\":42,\"tabs\":[\"videos\",\"streams\"],\"authorThumbnails\":["
                        + "{\"url\":\"https://yt3.ggpht.com/avatar\"}]}";
        final var channel = ServiceList.YouTube
                .getChannelExtractor("https://www.youtube.com/channel/" + CHANNEL);
        channel.fetchPage();
        assertEquals("channel/" + CHANNEL, channel.getId());
        assertEquals(42, channel.getSubscriberCount());
        assertEquals(2, channel.getTabs().size());
        responseBody = "{\"videos\":[" + ITEM + "],\"continuation\":\"next & token\"}";
        final var tab = ServiceList.YouTube.getChannelTabExtractor(channel.getTabs().get(0));
        tab.fetchPage();
        final var first = tab.getInitialPage();
        assertEquals(1, first.getItems().size());
        assertTrue(first.getNextPage().getUrl().contains("continuation=next+%26+token"));
        assertEquals(1, tab.getPage(first.getNextPage()).getItems().size());
        assertNull(ServiceList.YouTube.getFeedExtractor(channel.getUrl()));
    }

    @Test
    public void loadsCommunityPostsWithAttachmentsAndProxiedImages() throws Exception {
        responseBody = "{\"authorId\":\"" + CHANNEL + "\",\"tabs\":[\"community\"]}";
        final var channel = ServiceList.YouTube
                .getChannelExtractor("https://www.youtube.com/channel/" + CHANNEL);
        channel.fetchPage();
        assertEquals(1, channel.getTabs().size());
        final var tab = ServiceList.YouTube.getChannelTabExtractor(channel.getTabs().get(0));
        assertEquals(ChannelTabs.POSTS, tab.getTab());
        responseBody = fixture("/invidious/community.json");
        tab.fetchPage();
        final var first = tab.getInitialPage();
        assertTrue(first.getErrors().toString(), first.getErrors().isEmpty());
        assertEquals(6, first.getItems().size());
        final var post = (PostInfoItem) first.getItems().get(0);
        assertEquals("Ugkx0", post.getPostId());
        assertEquals("https://www.youtube.com/post/Ugkx0", post.getUrl());
        assertEquals("Hello #community", post.getContent());
        assertEquals("Channel", post.getUploaderName());
        assertEquals("https://www.youtube.com/channel/" + CHANNEL, post.getUploaderUrl());
        assertEquals("https://example.org/ggpht/avatar", post.getUploaderAvatars().get(0).getUrl());
        assertEquals(12, post.getLikeCount());
        assertEquals(3, post.getCommentCount());
        assertEquals("2 days ago", post.getTextualUploadDate());
        assertEquals(1720000000L, post.getUploadDate().offsetDateTime().toEpochSecond());
        assertTrue(post.isEdited());
        assertEquals(2, post.getImages().size());
        assertEquals("https://example.org/ggpht/one", post.getImages().get(0).getUrl());
        assertEquals("https://example.org/ggpht/two", post.getImages().get(1).getUrl());
        final var video = ((PostInfoItem) first.getItems().get(1)).getAttachment();
        assertEquals(PostInfoItem.AttachmentType.VIDEO, video.getType());
        assertEquals("https://www.youtube.com/watch?v=" + VIDEO, video.getUrl());
        assertTrue(video.getThumbnails().get(0).getUrl().startsWith("https://example.org/"));
        final var playlist = ((PostInfoItem) first.getItems().get(2)).getAttachment();
        assertEquals(PostInfoItem.AttachmentType.PLAYLIST, playlist.getType());
        assertEquals("https://www.youtube.com/playlist?list=PLtest", playlist.getUrl());
        assertTrue(playlist.getThumbnails().get(0).getUrl().startsWith("https://example.org/"));
        final var poll = ((PostInfoItem) first.getItems().get(3)).getPoll();
        assertEquals("23", poll.getTotalVotes());
        assertEquals(2, poll.getChoices().size());
        assertEquals("First", poll.getChoices().get(0).getText());
        assertEquals("", poll.getChoices().get(0).getVotePercentage());
        assertEquals("https://example.org/ggpht/choice",
                poll.getChoices().get(0).getImages().get(0).getUrl());
        assertNull(((PostInfoItem) first.getItems().get(4)).getAttachment());
        final var singleImage = ((PostInfoItem) first.getItems().get(5)).getImages();
        assertEquals(1, singleImage.size());
        assertEquals("https://example.org/ggpht/single", singleImage.get(0).getUrl());
        assertEquals("https://example.org/api/v1/channels/" + CHANNEL + "/community",
                requests.get(1));
        responseBody = "{\"comments\":[]}";
        final var next = tab.getPage(first.getNextPage());
        assertTrue(next.getItems().isEmpty());
        assertNull(next.getNextPage());
        assertEquals("https://example.org/api/v1/channels/" + CHANNEL
                + "/community?continuation=next+%26+token", requests.get(2));
        InvidiousBackend.configure(true, "https://changed.example.org");
        assertThrows(ExtractionException.class, () -> tab.getPage(first.getNextPage()));
        assertEquals(3, requests.size());
    }

    @Test
    public void communityFailuresDoNotFallBackToYoutube() throws Exception {
        responseBody = "{\"error\":\"Community posts unavailable\"}";
        final var handler = ServiceList.YouTube.getChannelTabLHFactory().fromQuery(
                "channel/" + CHANNEL,
                Collections.singletonList(new FilterItem(-1, ChannelTabs.POSTS)), null);
        final var tab = ServiceList.YouTube.getChannelTabExtractor(handler);
        assertThrows(ExtractionException.class, tab::fetchPage);
        assertEquals(Collections.singletonList("https://example.org/api/v1/channels/"
                + CHANNEL + "/community"), requests);
    }

    @Test
    public void fullFeedCanLoadVideosShortsLiveStreamsAndVideoContinuations() throws Exception {
        responseForUrl = url -> {
            if (url.endsWith("/" + CHANNEL)) {
                return "{\"author\":\"Channel\",\"authorId\":\"" + CHANNEL
                        + "\",\"tabs\":[\"videos\",\"shorts\",\"streams\"]}";
            }
            if (url.contains("continuation=")) {
                return "{\"videos\":[" + ITEM.replace(VIDEO, "olderVideo1") + "]}";
            }
            if (url.endsWith("/videos")) {
                return "{\"videos\":[" + ITEM + "],\"continuation\":\"next\"}";
            }
            if (url.endsWith("/shorts")) {
                return "{\"videos\":[" + ITEM.replace(VIDEO, "shortVideo1") + "]}";
            }
            if (url.endsWith("/streams")) {
                return "{\"videos\":[" + ITEM.replace(VIDEO, "liveVideo01") + "]}";
            }
            throw new AssertionError("Unexpected feed request: " + url);
        };
        final String channelUrl = "https://www.youtube.com/channel/" + CHANNEL;
        assertNull(ServiceList.YouTube.getFeedExtractor(channelUrl));
        final var channel = ServiceList.YouTube.getChannelExtractor(channelUrl);
        channel.fetchPage();
        final List<String> videos = new ArrayList<>();
        for (final var handler : channel.getTabs()) {
            final var tab = ServiceList.YouTube.getChannelTabExtractor(handler);
            tab.fetchPage();
            final var first = tab.getInitialPage();
            first.getItems().forEach(item -> videos.add(item.getUrl()));
            if (first.hasNextPage()) {
                tab.getPage(first.getNextPage()).getItems()
                        .forEach(item -> videos.add(item.getUrl()));
            }
        }
        assertEquals(4, videos.size());
        assertTrue(videos.stream().anyMatch(url -> url.endsWith("olderVideo1")));
        assertTrue(videos.stream().anyMatch(url -> url.endsWith("shortVideo1")));
        assertTrue(videos.stream().anyMatch(url -> url.endsWith("liveVideo01")));
        InvidiousBackend.configure(false, "");
        assertTrue(ServiceList.YouTube.getFeedExtractor(channelUrl)
                instanceof org.schabi.newpipe.extractor.services.youtube.extractors
                        .YoutubeFeedExtractor);
    }

    @Test
    public void returnsPlaylistPagesCommentRepliesAndSuggestionsFromTheApi() throws Exception {
        responseBody = "{\"title\":\"Playlist\",\"videoCount\":2,\"videos\":[" + ITEM + "]}";
        final var playlist = ServiceList.YouTube
                .getPlaylistExtractor("https://www.youtube.com/playlist?list=PL1234567890123456");
        playlist.fetchPage();
        assertEquals("Playlist", playlist.getName());
        assertEquals(1, playlist.getPage(playlist.getInitialPage().getNextPage()).getItems()
                .size());
        responseBody = "{\"comments\":[{\"author\":\"Commenter\",\"commentId\":\"comment\",\"co"
                + "ntent\":\"Hello\","
                + "\"replies\":{\"replyCount\":1,\"continuation\":\"reply token\"}}],\"continua"
                        + "tion\":\"next\"}";
        final var comments = ServiceList.YouTube
                .getCommentsExtractor("https://www.youtube.com/watch?v=" + VIDEO);
        comments.setSortOrder(org.schabi.newpipe.extractor.comments.CommentSortOrder.NEWEST);
        comments.fetchPage();
        assertTrue(requests.get(requests.size() - 1).contains("sort_by=new"));
        final var comment = comments.getInitialPage().getItems().get(0);
        assertEquals("Hello", comment.getCommentTextContent());
        assertEquals(1, comment.getReplyCount());
        assertEquals(1, comments.getPage(comment.getReplies()).getItems().size());
        responseBody = "{\"suggestions\":[\"video one\",\"video two\"]}";
        assertEquals(2, ServiceList.YouTube.getSuggestionExtractor().suggestionList("video")
                .size());
    }

    @Test
    public void unsupportedTabsAndKiosksDoNotContactYoutube() throws Exception {
        final ListLinkHandler handler = ServiceList.YouTube.getChannelTabLHFactory()
                .fromQuery("channel/" + CHANNEL,
                Collections.singletonList(new FilterItem(-1, ChannelTabs.PODCASTS)), null);
        assertThrows(ExtractionException.class, () -> ServiceList.YouTube
                .getChannelTabExtractor(handler).fetchPage());
        assertThrows(ExtractionException.class, () -> ServiceList.YouTube.getKioskList()
                .getExtractorById("youtube_shorts", null).fetchPage());
        assertTrue(requests.isEmpty());
        InvidiousBackend.configure(false, "");
        assertFalse(ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v="
                + VIDEO)
                instanceof InvidiousStreamExtractor);
    }
    @Test
    public void reportsCompanionErrorsFromNonSuccessfulApiResponses() {
        status = 500;
        responseBody = "{\"error\":\"Error while communicating with Invidious companion:"
                + " Unexpected char '<' at line 1, column 1\"}";
        final ExtractionException error = assertThrows(ExtractionException.class, () ->
                ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v="
                        + VIDEO).fetchPage());
        assertTrue(error.getMessage().contains("HTTP 500"));
        assertTrue(error.getMessage().contains("Unexpected char '<'"));
        assertEquals(1, requests.size());
    }

    @Test
    public void deniedSuggestionsStayOptionalButSearchAndPlaybackErrorsRemainVisible()
            throws Exception {
        status = 403;
        responseBody = "<html>private firewall page</html>";
        assertTrue(ServiceList.YouTube.getSuggestionExtractor().suggestionList("games")
                .isEmpty());
        final ExtractionException playbackError = assertThrows(ExtractionException.class,
                () -> ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v="
                        + VIDEO).fetchPage());
        assertTrue(playbackError.getMessage().contains("denied API access"));
        assertTrue(playbackError.getMessage().contains("saved Invidious instance"));
        assertFalse(playbackError.getMessage().contains("private firewall"));
        final var search = ServiceList.YouTube.getSearchExtractor("games");
        assertThrows(ExtractionException.class, search::fetchPage);
        assertEquals(3, requests.size());
    }

    @Test
    public void malformedSuccessfulSuggestionsStillReportAnExtractionError() {
        responseBody = "not JSON";
        assertThrows(ExtractionException.class,
                () -> ServiceList.YouTube.getSuggestionExtractor().suggestionList("games"));
        assertEquals(1, requests.size());
    }

    @Test
    public void explainsUnavailableHtmlApiPagesWithoutIncludingTheirBody() {
        responseHeaders = java.util.Map.of("Content-Type", List.of("text/html; charset=UTF-8"));
        responseBody = "<html><h1>Site Unavailable</h1><p>private body</p></html>";
        final ExtractionException error = assertThrows(ExtractionException.class, () ->
                ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v="
                        + VIDEO).fetchPage());
        assertTrue(error.getMessage().contains("HTML page instead of API data"));
        assertFalse(error.getMessage().contains("private body"));
        assertEquals(1, requests.size());
    }

    @Test
    public void apiErrorDiagnosticsRedactUrlsAndBoundResponseDetails() {
        responseBody = "{\"error\":\"Cannot fetch https://example.org/stream?token=private "
                + "x".repeat(400) + "\"}";
        for (final int code : new int[]{401, 403, 429, 500, 503}) {
            status = code;
            final ExtractionException error = assertThrows(ExtractionException.class, () ->
                    ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v="
                            + VIDEO).fetchPage());
            assertFalse(error.getMessage().contains("private"));
            assertTrue(error.getMessage().contains("[URL]"));
            assertTrue(error.getMessage().length() < 300);
        }
    }

    @Test
    public void missingMediaResponseExplainsTheInstanceFailureWithoutFallback() {
        responseBody = ITEM;
        final ExtractionException error = assertThrows(ExtractionException.class, () ->
                StreamInfo.getInfo(ServiceList.YouTube,
                        "https://www.youtube.com/watch?v=" + VIDEO));
        assertTrue(error.getMessage().contains("without any media formats"));
        assertTrue(error.getMessage().contains("another saved Invidious instance"));
        assertEquals(1, requests.size());
    }

    @Test
    public void copiedStreamReportIncludesTheRejectedProxyUrlCauseWithoutItsUrl() {
        responseBody = ITEM.substring(0, ITEM.length() - 1)
                + ",\"formatStreams\":[{\"itag\":\"18\",\"type\":\"video/mp4\","
                + "\"url\":\"https://rr1.googlevideo.com/videoplayback?token=private\"}]}";
        final ExtractionException error = assertThrows(ExtractionException.class, () ->
                StreamInfo.getInfo(ServiceList.YouTube,
                        "https://www.youtube.com/watch?v=" + VIDEO));
        assertTrue(error instanceof StreamInfo.StreamExtractException);
        final var report = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(report));
        assertTrue(report.toString().contains("media host is not trusted"));
        assertFalse(report.toString().contains("token=private"));
        assertEquals(1, requests.size());
    }

    @Test
    public void successfulHttpApiErrorsAlsoRedactAndBoundDiagnosticDetails() {
        responseBody = "{\"error\":\"Cannot fetch HTTPS://example.org/stream?token=private "
                + "x".repeat(400) + "\"}";
        final ExtractionException error = assertThrows(ExtractionException.class, () ->
                StreamInfo.getInfo(ServiceList.YouTube,
                        "https://www.youtube.com/watch?v=" + VIDEO));
        assertTrue(error.getMessage().contains("Invidious: Cannot fetch [URL]"));
        assertFalse(error.getMessage().contains("private"));
        assertTrue(error.getMessage().length() < 300);
        assertEquals(1, requests.size());
    }

}
