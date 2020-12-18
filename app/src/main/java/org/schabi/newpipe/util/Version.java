package org.schabi.newpipe.util;

import android.util.Log;

public final class Version implements Comparable<Version> {
    private static final String TAG = Version.class.getSimpleName();
    private final int major;
    private final int minor;
    private final int build;
    private final int rev;

    public Version(final int major, final int minor, final int build, final int rev) {
        this.major = major;
        this.minor = minor;
        this.build = build;
        this.rev = rev;
    }

    public static Version fromString(final String str) {
        // examples of valid version strings:
        // - 0.1
        // - v0.1.0.4
        // - 0.20.6
        // - v0.20.6_r2
        try {
            // example: v0.20.6_r2 -> v0.20.6.r2 -> 0.20.6.2
            final String[] split = str
                    .replaceAll("_", ".")
                    .replaceAll("[^0-9.]", "")
                    .split("[^\\d]");

            final int major = Integer.parseInt(split[0]);
            final int minor = split.length > 1
                    ? Integer.parseInt(split[1])
                    : 0;
            final int build = split.length > 2
                    ? Integer.parseInt(split[2])
                    : 0;
            final int rev = split.length > 3
                    ? Integer.parseInt(split[3])
                    : 0;

            return new Version(major, minor, build, rev);
        } catch (final Exception e) {
            Log.e(TAG, "Could not successfully parse version string.", e);
            return new Version(0, 0, 0, 0);
        }
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getBuild() {
        return build;
    }

    public int getRev() {
        return rev;
    }

    @Override
    public int compareTo(final Version that) {
        if (this.getMajor() != that.getMajor()) {
            return this.getMajor() < that.getMajor() ? -1 : 1;
        } else if (this.getMinor() != that.getMinor()) {
            return this.getMinor() < that.getMinor() ? -1 : 1;
        } else if (this.getBuild() != that.getBuild()) {
            return this.getBuild() < that.getBuild() ? -1 : 1;
        } else if (this.getRev() != that.getRev()) {
            return this.getRev() < that.getRev() ? -1 : 1;
        }
        return 0;
    }
}
