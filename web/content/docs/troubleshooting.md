Start with the installed version, selected service and exact action that failed. Many problems come from a changed source stream, a local filter, a missing permission or a background scheduling restriction. A useful report explains the observed behavior without assuming its internal cause.

## Installation or update fails

Check the APK architecture, Android version and installation source. The normal APK is for ARM64/ARMv7; the separate x86_64 file is for compatible Android environments on Intel/AMD hardware.

A signature mismatch can mean the installed build and incoming APK were signed by different keys. Keep a backup before considering removal of an existing installation. Stable, debug and nightly package identities can coexist, but they are not interchangeable update targets.

## A video will not play

1. Check whether the public item is still available from the source service.
2. Note the selected service or Invidious instance and whether a proxy is configured.
3. Try another available quality or audio track when those choices exist.
4. Record the app version and exact error before restarting or clearing data.

Do not assume a black screen proves a decoder failure, or that every error is caused by connectivity. A fresh debug log can help distinguish HTTP, extraction, codec and rendering behavior. Remove private URLs and credentials before sharing it.

## Live playback stops or rewind changes

The broadcaster can change manifests, revoke URLs, reduce DVR availability or finish the stream. Full live rewind depends on the history actually exposed by that broadcast.

When reporting, say whether you were at the live edge or rewound, whether audio continued, and whether the broadcast had ended. Use a public stream URL and a fresh log from the failing build. The reviewed 1.50.0 release includes several live and playback recovery improvements, but that does not guarantee every source or device failure is resolved. Check the release notes for your actual build.

## A subscription upload is missing

Check group, service, content-type, watch-state, date-cutoff and content-blocking filters. Refresh the relevant feed, and confirm that the channel is still in the active profile.

Include the specific public video and channel URL in a report. Refreshing is not a guarantee that every historical upload will appear; distinguish a recent missing upload from an older item outside the collected feed window.

## Downloads pause or do not start

Check free space, destination access, default folders and whether the source exposes a supported format. For bulk downloads, configure the default audio/video folders and disable Ask where to download.

Inspect a scheduled download window, charging requirement or unmetered-network condition before treating a paused queue as broken. Android may delay background work, and manually paused jobs remain paused.

## Pairing, casting or Auto is unavailable

For Device Sync and casting, check that both devices can reach each other on the same local network. Guest-network isolation, VPN routing and firewalls can interfere. Use a fresh pairing code and inspect battery restrictions for background sync.

**Merged after 1.50.0:** manual **Copy pairing code** and **Enter pairing code** controls avoid the camera requirement. For Waydroid, copy the phone's code into Waydroid and start **Sync now** there. Routing and firewall requirements still apply. The [profiles and synchronization guide](profiles-sync.html) explains the steps, release-build diagnostic setting and logs to collect from both devices.

## Home closes or reports out-of-memory

Record the failing build, Android/device details, active Home tabs, the last action and any available crash log. Recent changes reduce retained Home references and some loading work, but an out-of-memory report can have more than one cause. A merged mitigation or a successful run on another device does not establish that the reported failure is resolved. Include a reproducible sequence without posting your private database.

Android Auto has its own app-discovery rules. Debug/nightly or other sideloaded builds may need its developer-only Unknown sources setting, distinct from APK installation permission. Auto is audio-oriented, not a car-screen video surface.

## Appearance, captions or translation differs

Reopen an existing player after applying a theme. Glass can fall back to an opaque surface on unsupported layouts or runtime conditions. Review pack format and asset limits when import fails.

Subtitle timing is session-only. On-device translation needs Android 12+ and an already-ready language pair; WizeStream does not fall back to a cloud translator when those requirements are missing.

## Write a useful report

Include your WizeStream version, installation channel, Android/device model, selected service, a public example URL, expected result and reproduction steps. Explain what you already tried and attach a relevant, reviewed log if available.

Use the [support page](../support.html) for the project’s issue and discussion links. Do not post private backups, authentication cookies, passwords or a private review-management link in public.
