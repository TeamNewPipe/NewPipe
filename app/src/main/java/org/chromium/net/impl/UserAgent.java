// Based on Cronet embedded's UserAgent class

// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net.impl;

import android.content.Context;

import org.schabi.newpipe.DownloaderImpl;

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

    private UserAgent() {}

    /**
     * This method used to return a user agent leaking user information, and so has been replaced.
     * @param ignoredContext not used but kept for compatibility
     * @return User-Agent string.
     */
    public static String from(Context ignoredContext) {
        return DownloaderImpl.USER_AGENT;
    }

    /**
     * This method used to return a user agent leaking user information, and so has been replaced.
     * @param ignoredContext not used but kept for compatibility
     * @return User-Agent string.
     */
    @SuppressWarnings("unused")
    static String getQuicUserAgentIdFrom(Context ignoredContext) {
        return "Cronet/" + ImplVersion.getCronetVersion();
    }
}
