package org.schabi.newpipe.extractor.services.niconico.extractors;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// See https://zenn.dev/negima1072/articles/howto-get-nicovideo-comments
public class NiconicoCommentsInfoItemExtractor implements CommentsInfoItemExtractor {
    public JsonObject json;
    public String url;

    NiconicoCommentsInfoItemExtractor(final JsonObject json, final String url) {
        this.json = json;
        this.url = url;
    }

    @Override
    public String getName() throws ParsingException {
        return null;
    }

    @Override
    public String getUrl() throws ParsingException {
        return url;
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return null;
    }

    @Override
    public int getLikeCount() throws ParsingException {
        return getScore();
    }

    @Override
    public String getTextualLikeCount() throws ParsingException {
        return String.valueOf(getLikeCount());
    }

    @Override
    public String getCommentTextContent() throws ParsingException {
        final Duration diff = getDuration();
        @SuppressWarnings("DefaultLocale") final String hms = String.format("%02d:%02d",
                diff.toMinutes(),
                diff.getSeconds() % 60);
        return hms + " " + getCommentTextRaw();
    }

    @Override
    public String getTextualUploadDate() throws ParsingException {
        return null;
        //new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(getUploadDate().offsetDateTime());
    }

    @Override
    public String getCommentId() throws ParsingException {
        return json.getString("no");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        return null;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        return null; // json.getString("user_id");
    }

    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return null;
    }

    @Override
    public boolean isHeartedByUploader() throws ParsingException {
        return false;
    }

    @Override
    public boolean isPinned() throws ParsingException {
        return false;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public Page getReplies() throws ParsingException {
        return null;
    }

    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        return new DateWrapper(
                OffsetDateTime.ofInstant(
                        Instant.ofEpochSecond(json.getInt("date")),
                        ZoneOffset.ofHours(0)
                )
        );
    }

    // Additional apis.
    private int getVpos() {
        return json.getInt("vpos");
    }

    @SuppressWarnings("checkstyle:LocalFinalVariableName")
    @Nonnull
    private Duration getDuration() {
        final long VPOS_MILLIS = 10;
        return Duration.ofMillis(getVpos() * VPOS_MILLIS);
    }

    private int getScore() {
        return json.getInt("score");
    }

    private String getCommentTextRaw() {
        return json.getString("content");
    }
}
