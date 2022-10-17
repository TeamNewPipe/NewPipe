package org.schabi.newpipe.util;

public enum SponsorBlockAction {
    SKIP("skip"),
    POI("poi");

    private final String apiName;

    SponsorBlockAction(final String apiName) {
        this.apiName = apiName;
    }

    public static SponsorBlockAction fromApiName(final String apiName) {
        switch (apiName) {
            case "skip":
                return SponsorBlockAction.SKIP;
            case "poi":
                return SponsorBlockAction.POI;
            default:
                throw new IllegalArgumentException("Invalid API name");
        }
    }

    public String getApiName() {
        return apiName;
    }
}
