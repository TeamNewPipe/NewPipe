/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

const val ANDROID_COMPILE_SDK_MAJOR = 37
const val ANDROID_COMPILE_SDK_MINOR = 0
const val ANDROID_MIN_SDK = 23
const val ANDROID_TARGET_SDK = 35

const val WIZESTREAM_VERSION_MAJOR = 1
const val WIZESTREAM_VERSION_MINOR = 51
const val WIZESTREAM_VERSION_PATCH = 0

const val WIZESTREAM_VERSION_NAME =
    "$WIZESTREAM_VERSION_MAJOR.$WIZESTREAM_VERSION_MINOR.$WIZESTREAM_VERSION_PATCH"

// Keep Android's monotonically increasing versionCode independent from upstream.
// Minor and patch components must remain in the 0..999 range.
const val WIZESTREAM_VERSION_CODE =
    WIZESTREAM_VERSION_MAJOR * 1_000_000 +
        WIZESTREAM_VERSION_MINOR * 1_000 +
        WIZESTREAM_VERSION_PATCH

// The source namespace and installed application ID are intentionally stable for compatibility.
const val UPSTREAM_NEWPIPE_NAMESPACE = "org.schabi.newpipe"
const val NEWPIPE_APPLICATION_ID_NEW = "net.newpipe.app"
const val WIZESTREAM_APPLICATION_ID = "org.wisso.newpipematerial"
