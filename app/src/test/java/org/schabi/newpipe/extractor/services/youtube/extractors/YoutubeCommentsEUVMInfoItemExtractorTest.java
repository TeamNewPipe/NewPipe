package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class YoutubeCommentsEUVMInfoItemExtractorTest {
    @Test
    void extractsAvatarThumbnailUrlFromCurrentAuthorPayload() throws Exception {
        final YoutubeCommentsEUVMInfoItemExtractor extractor = createExtractor("""
                {
                  "author": {
                    "avatarThumbnailUrl": "https://yt3.ggpht.com/comment-avatar"
                  }
                }
                """);

        assertEquals("https://yt3.ggpht.com/comment-avatar",
                extractor.getUploaderAvatarUrl());
    }

    @Test
    void collectorPreservesCurrentAvatarThumbnailUrl() throws Exception {
        final YoutubeCommentsEUVMInfoItemExtractor extractor = createExtractor("""
                {
                  "author": {
                    "avatarThumbnailUrl": "https://yt3.ggpht.com/comment-avatar"
                  }
                }
                """);
        final CommentsInfoItem item = new CommentsInfoItemsCollector(0).extract(extractor);

        assertEquals("https://yt3.ggpht.com/comment-avatar", item.getUploaderAvatarUrl());
    }

    @Test
    void retainsCompatibilityWithLegacyRootAvatarPayload() throws Exception {
        final YoutubeCommentsEUVMInfoItemExtractor extractor = createExtractor("""
                {
                  "avatar": {
                    "image": {
                      "sources": [{
                        "url": "https://yt3.ggpht.com/legacy-comment-avatar",
                        "width": 48,
                        "height": 48
                      }]
                    }
                  }
                }
                """);

        assertEquals("https://yt3.ggpht.com/legacy-comment-avatar",
                extractor.getUploaderAvatarUrl());
    }

    @Test
    void retainsCompatibilityWithNestedAuthorAvatarPayload() throws Exception {
        final YoutubeCommentsEUVMInfoItemExtractor extractor = createExtractor("""
                {
                  "author": {
                    "avatar": {
                      "image": {
                        "sources": [{
                          "url": "https://yt3.ggpht.com/nested-comment-avatar",
                          "width": 48,
                          "height": 48
                        }]
                      }
                    }
                  }
                }
                """);

        assertEquals("https://yt3.ggpht.com/nested-comment-avatar",
                extractor.getUploaderAvatarUrl());
    }

    @Test
    void extractsImageAttachmentFromCommentSurfaceWithoutDuplicatingSizes() throws Exception {
        final JsonObject surface = JsonParser.object().from("""
                {
                  "imageAttachmentViewModel": {
                    "commentImageAttachmentViewModel": {
                      "image": {
                        "sources": [
                          {"url": "https://i.ytimg.com/a/small", "width": 160, "height": 90},
                          {"url": "https://i.ytimg.com/a/large", "width": 1280, "height": 720}
                        ]
                      }
                    }
                  }
                }
                """);
        final YoutubeCommentsEUVMInfoItemExtractor extractor =
                createExtractorWithSurface(surface);
        final CommentsInfoItem item = new CommentsInfoItemsCollector(0).extract(extractor);

        assertEquals(1, item.getPictures().size());
        assertEquals("https://i.ytimg.com/a/large",
                item.getPictures().iterator().next().getUrl());
    }

    @Test
    void commentsWithoutAttachmentsStayValid() throws Exception {
        final YoutubeCommentsEUVMInfoItemExtractor extractor =
                createExtractorWithSurface(new JsonObject());
        assertEquals(0, extractor.getPictures().size());
    }

    private static YoutubeCommentsEUVMInfoItemExtractor createExtractorWithSurface(
            final JsonObject surface) throws Exception {
        return new YoutubeCommentsEUVMInfoItemExtractor(
                new JsonObject(),
                null,
                new JsonObject(),
                new JsonObject(),
                surface,
                "https://www.youtube.com/watch?v=abcdefghijk",
                mock(TimeAgoParser.class));
    }

    private static YoutubeCommentsEUVMInfoItemExtractor createExtractor(
            final String commentEntityPayload) throws Exception {
        final JsonObject payload = JsonParser.object().from(commentEntityPayload);
        return new YoutubeCommentsEUVMInfoItemExtractor(
                new JsonObject(),
                null,
                payload,
                new JsonObject(),
                "https://www.youtube.com/watch?v=abcdefghijk",
                mock(TimeAgoParser.class));
    }
}
