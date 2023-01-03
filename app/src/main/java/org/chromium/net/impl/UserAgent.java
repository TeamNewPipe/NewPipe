// Based on Cronet embedded's UserAgent class

// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net.impl;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;

import java.util.Locale;

/**
 * Constructs a User-Agent string.
 *
 * <p>
 * This class is a modified version of the original one, which changes the default QUIC User Agent
 * ID to {@code Cronet/CRONET_VERSION}, where {@code CRONET_VERSION} is the version of the Cronet
 * embedded version used.
 * </p>
 */
public final class UserAgent {
    private static final Object sLock = new Object();

    private static final int VERSION_CODE_UNINITIALIZED = 0;
    private static int sVersionCode = VERSION_CODE_UNINITIALIZED;

    private UserAgent() {}

    /**
     * Constructs a User-Agent string including application name and version,
     * system build version, model and Id, and Cronet version.
     * @param context the context to fetch the application name and version
     *         from.
     * @return User-Agent string.
     */
    public static String from(Context context) {
        StringBuilder builder = new StringBuilder();

        // Our package name and version.
        builder.append(context.getPackageName());
        builder.append('/');
        builder.append(versionFromContext(context));

        // The platform version.
        builder.append(" (Linux; U; Android ");
        builder.append(Build.VERSION.RELEASE);
        builder.append("; ");
        builder.append(Locale.getDefault().toString());

        String model = Build.MODEL;
        if (model.length() > 0) {
            builder.append("; ");
            builder.append(model);
        }

        String id = Build.ID;
        if (id.length() > 0) {
            builder.append("; Build/");
            builder.append(id);
        }

        builder.append(";");
        appendCronetVersion(builder);

        builder.append(')');

        return builder.toString();
    }

    /**
     * Constructs default QUIC User Agent Id string including Cronet version.
     * @param context the context to fetch the application name from, unused with the changes of
     *                the method but kept for signature matches purpose with the method callers
     *                from other Cronet modules
     * @return User-Agent string.
     */
    @SuppressWarnings("unused")
    static String getQuicUserAgentIdFrom(Context context) {
        return "Cronet/" + ImplVersion.getCronetVersion();
    }

    private static int versionFromContext(Context context) {
        synchronized (sLock) {
            if (sVersionCode == VERSION_CODE_UNINITIALIZED) {
                PackageManager packageManager = context.getPackageManager();
                String packageName = context.getPackageName();
                try {
                    PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
                    sVersionCode = packageInfo.versionCode;
                } catch (NameNotFoundException e) {
                    throw new IllegalStateException("Cannot determine package version");
                }
            }
            return sVersionCode;
        }
    }

    private static void appendCronetVersion(StringBuilder builder) {
        builder.append(" Cronet/");
        builder.append(ImplVersion.getCronetVersion());
    }
}
