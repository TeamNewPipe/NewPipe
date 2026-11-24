package org.schabi.newpipe.extractor.bulletComments;

import java.time.Duration;

import org.schabi.newpipe.extractor.InfoItem;

import javax.annotation.Nonnull;

public class BulletCommentsInfoItem extends InfoItem implements Comparable<BulletCommentsInfoItem> {
    // Original Android descriptor uses j$.time.Duration after core-library desugaring.
    private static final long serialVersionUID = 7010924713763063802L;

    @Override
    public int compareTo(@Nonnull BulletCommentsInfoItem bulletCommentsInfoItem) {
        return this.duration.compareTo(bulletCommentsInfoItem.duration);
    }

    public enum Position {
        REGULAR,
        BOTTOM,
        TOP,
        SUPERCHAT
    }

    private String commentText;
    private int argbColor;
    private Position position;
    private double relativeFontSize;
    /* It really should be named as timePosition or some other thing*/
    private Duration duration;
    private int lastingTime;
    private long pinnedTimeMillis;
    private boolean isLive;
    private String authorName;
    private String authorAvatarUrl;
    private String amountText;

    public BulletCommentsInfoItem(final int serviceId, final String url, final String name) {
        super(InfoType.COMMENT, serviceId, url, name);
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(final String commentText) {
        this.commentText = commentText;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(final String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorAvatarUrl() {
        return authorAvatarUrl;
    }

    public void setAuthorAvatarUrl(final String authorAvatarUrl) {
        this.authorAvatarUrl = authorAvatarUrl;
    }

    public String getAmountText() {
        return amountText;
    }

    public void setAmountText(final String amountText) {
        this.amountText = amountText;
    }

    public int getArgbColor() {
        return argbColor;
    }

    public void setArgbColor(final int argbColor) {
        this.argbColor = argbColor;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(final Position position) {
        this.position = position;
    }

    public double getRelativeFontSize() {
        return relativeFontSize;
    }

    public void setRelativeFontSize(final double relativeFontSize) {
        this.relativeFontSize = relativeFontSize;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(final Duration duration) {
        this.duration = duration;
    }

    public int getLastingTime() {
        return -1;
    }

    public void setLastingTime(int lastingTime) {
        this.lastingTime = lastingTime;
    }

    public long getPinnedTimeMillis() {
        return pinnedTimeMillis;
    }

    public void setPinnedTimeMillis(final long pinnedTimeMillis) {
        this.pinnedTimeMillis = pinnedTimeMillis;
    }

    public boolean isLive() {
        return isLive;
    }

    public void setLive(boolean live) {
        isLive = live;
    }
}
