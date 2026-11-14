/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

const val NEWPIPE_VERSION_SDK_COMPILE_MAJOR = 37
const val NEWPIPE_VERSION_SDK_COMPILE_MINOR = 0
const val NEWPIPE_VERSION_SDK_MIN = 23
const val NEWPIPE_VERSION_SDK_TARGET = 35

const val NEWPIPE_VERSION_CODE = 1015
const val NEWPIPE_VERSION_NAME = "0.29.1"

// Merak: an unofficial app based on NewPipe, with its own name and app ID as NewPipe's
// trademark policy requires (https://newpipe-ev.de/policy/trademark/).
// Android app only: the desktop installer needs a plain MAJOR.MINOR.PATCH version name.
const val FORK_VERSION_SUFFIX = "-merak.2.0.0"
const val FORK_APP_NAME = "Merak"
const val FORK_APPLICATION_ID = "io.github.ninjaeu.merak"

const val NEWPIPE_APPLICATION_ID_OLD = "org.schabi.newpipe"
const val NEWPIPE_APPLICATION_ID_NEW = "net.newpipe.app"
