package org.schabi.newpipe.extractor.services.bitchute.extractor;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.services.bitchute.BitchuteConstants;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BitchuteCommentsInfoItemExtractor implements CommentsInfoItemExtractor {
    private final JsonObject json;
    private final String url;

    public BitchuteCommentsInfoItemExtractor(final JsonObject json, final String url) {
        this.json = json;
        this.url = url;
    }

    @Override
    public String getCommentId() {
        return json.getString("id");
    }

    @Override
    public String getCommentTextContent() {
        return json.getString("content");
    }

    @Override
    public String getUploaderName() {
        return json.getString("fullname");
    }

    @Nonnull
    @Override
    public List<Image> getUploaderAvatars() throws ParsingException {
        final String avatarUrl =
                BitchuteConstants.BASE_URL + json.getString("profile_picture_url");
        return List.of(new Image(avatarUrl,
                Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN));
    }

    @Override
    public String getUploaderUrl() {
        // return BitchuteConstants.BASE_URL + "/profile/" + json.getString("creator");
        // the */profile/* link is not linking to a channel but to a user that could have
        // a channel. --> therefore disabled for now
        return "";
    }

    @Override
    public String getTextualUploadDate() {
        return json.getString("created");
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSXXX");
        final var datetime = ZonedDateTime.parse(getTextualUploadDate(), formatter);
        return new DateWrapper(datetime.toOffsetDateTime(), false);
    }

    @Override
    public String getName() throws ParsingException {
        return getUploaderName();
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Nonnull
    @Override
    public List<Image> getThumbnails() throws ParsingException {
        return getUploaderAvatars();
    }

    @Override
    public String getTextualLikeCount() throws ParsingException {
        return json.getString("upvote_count");
    }

    @Override
    public int getLikeCount() throws ParsingException {
        return json.getInt("upvote_count");
    }
}
