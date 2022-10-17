package org.schabi.newpipe.util;

import android.content.Context;

import org.schabi.newpipe.R;

public enum SponsorBlockCategory {
    SPONSOR("sponsor"),
    INTRO("intro"),
    OUTRO("outro"),
    INTERACTION("interaction"),
    HIGHLIGHT("poi_highlight"),
    SELF_PROMO("selfpromo"),
    NON_MUSIC("music_offtopic"),
    PREVIEW("preview"),
    FILLER("filler"),
    PENDING("pending");

    private final String apiName;

    SponsorBlockCategory(final String apiName) {
        this.apiName = apiName;
    }

    public static SponsorBlockCategory fromApiName(final String apiName) {
        switch (apiName) {
            case "sponsor":
                return SponsorBlockCategory.SPONSOR;
            case "intro":
                return SponsorBlockCategory.INTRO;
            case "outro":
                return SponsorBlockCategory.OUTRO;
            case "interaction":
                return SponsorBlockCategory.INTERACTION;
            case "poi_highlight":
                return SponsorBlockCategory.HIGHLIGHT;
            case "selfpromo":
                return SponsorBlockCategory.SELF_PROMO;
            case "music_offtopic":
                return SponsorBlockCategory.NON_MUSIC;
            case "preview":
                return SponsorBlockCategory.PREVIEW;
            case "filler":
                return SponsorBlockCategory.FILLER;
            default:
                throw new IllegalArgumentException("Invalid API name");
        }
    }

    public String getFriendlyName(final Context context) {
        switch (this) {
            case SPONSOR:
                return context.getString(
                        R.string.sponsor_block_category_sponsor);
            case INTRO:
                return context.getString(
                        R.string.sponsor_block_category_intro);
            case OUTRO:
                return context.getString(
                        R.string.sponsor_block_category_outro);
            case INTERACTION:
                return context.getString(
                        R.string.sponsor_block_category_interaction);
            case HIGHLIGHT:
                return context.getString(
                        R.string.sponsor_block_category_highlight);
            case SELF_PROMO:
                return context.getString(
                        R.string.sponsor_block_category_self_promo);
            case NON_MUSIC:
                return context.getString(
                        R.string.sponsor_block_category_non_music);
            case PREVIEW:
                return context.getString(
                        R.string.sponsor_block_category_preview);
            case FILLER:
                return context.getString(
                        R.string.sponsor_block_category_filler);
            case PENDING:
                return context.getString(
                        R.string.sponsor_block_category_pending);
        }
        return null;
    }

    public String getApiName() {
        return apiName;
    }
}
