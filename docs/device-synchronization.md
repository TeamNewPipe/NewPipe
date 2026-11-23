# Device synchronization

WizeStream can synchronize supported app data directly between trusted WizeStream
devices. It does not require a platform login or a WizeStream cloud account.

## Build availability

The latest release checked for this guide is **WizeStream 1.50.0 (22 November 2026)**. It supports
QR pairing and Device Sync. The current `pipe` source also includes changes merged after that
release: manual code entry, pairing/setup log events, the performance-diagnostics switch in
release-build sync settings, ongoing non-Default profile history fixes, and local-only profile
removal. Those additions require a build containing the corresponding changes; merging them does
not put them in the 1.50.0 APK. See the [changelog](changelogs.md).

## Pair two devices

Both devices must have a WizeStream version that supports device
synchronization and must be able to reach each other on the same Wi-Fi,
Ethernet, or local hotspot network. Synchronization does not use the public
internet.

1. Open **Settings > Device synchronization** on both devices.
2. On the first device, select **Show pairing code** and keep the code visible.
3. On the second device, select **Scan pairing code** and scan the first
   device's QR code.
4. Wait for both devices to confirm that pairing succeeded.

The pairing code expires after five minutes and can be used only once. Pairing establishes trust
between the two device identities; later synchronization does not require a new code.

### Pair without a camera

**Merged after 1.50.0:** use the same one-time code through the manual controls when a camera is
unavailable or scanning is impractical.

1. On the first device, open **Show pairing code**, choose **Copy pairing code**, and keep that
   pairing screen open.
2. Transfer the complete code privately to the other device. Share it only with the device you
   intend to trust; do not paste it in a public issue or log.
3. On the other device, select **Enter pairing code**, paste the complete code and choose **Pair**
   within five minutes.
4. Wait for pairing confirmation, then choose **Sync now**.

For a phone and Waydroid, show and copy the code on the phone, enter it in Waydroid, and start
**Sync now** in Waydroid. Manual entry uses the same signed invitation, expiry and single-use
checks as QR scanning. It solves the camera requirement, not network isolation: the Waydroid
environment must still be able to reach the phone over the local network.

Use **Clear trusted devices** to remove every pairing. Each device must be paired again before
it can synchronize.

### If pairing or synchronization fails

Keep the pairing screen open until setup finishes. If the code is expired, already used or
incomplete, display and copy a new code. Check the date and time on both devices if the setup
message asks you to do so.

For connection failures, check guest-network isolation, local firewalls, Waydroid networking,
VPN routing and Android background restrictions. A device-wide VPN may need its local-network
access option enabled; Android's **Block connections without VPN** can still block local peers.
Do not expose the synchronization listener to the public internet as a workaround.

Open **Settings > Device synchronization > Synchronization log** on both devices after a failed
attempt. In builds containing the post-1.50.0 pairing change, logs can distinguish a failed setup,
an unreachable device and a data-exchange failure. Setup records use safe failure categories and
omit invitation codes, tokens and network addresses.

**Device Sync performance diagnostics** is off by default. Its switch is directly below
**Synchronization log** in current source; this release-build settings placement was merged after
1.50.0. When enabled, it adds elapsed stage timings and total app-process CPU time to local sync
logs, without recording media or history contents. These measurements help investigate slow or
failed attempts; they are not a repair action.

For a report, record each app version, Android version, device or Waydroid environment, which
device initiated the attempt, and the corresponding logs from both sides. Review copied logs
before sharing them. Pairing workarounds and recent fixes do not confirm that all transport or
history problems are resolved.

## Synchronize now or in the background

Select **Sync now** to exchange data immediately with reachable trusted
devices. The result lists the data categories exchanged with each device and
any category or device that needs attention.

**Automatic background synchronization** is enabled after pairing unless it
is turned off. While it is enabled and at least one trusted device is paired,
WizeStream keeps a small foreground listener active so another trusted device
can reach it even when the app UI is closed. Android shows a low-priority
**Device synchronization** notification while this listener is active.

WizeStream attempts a background sync approximately once per hour when:

- The device has sufficient battery.
- The device is connected through Wi-Fi or Ethernet.
- A trusted device is reachable on the same Wi-Fi, Ethernet, or hotspot network.

A trusted device being temporarily unavailable does not remove the pairing.
Use **Sync now** when both devices are available if an immediate update is
needed.

## Investigate a slow or interrupted synchronization

Enable **Device Sync performance diagnostics** under **Device synchronization**, below
**Synchronization log**, on both devices before comparing attempts in each direction.
The option is off by default and records only local aggregate timing, alongside existing
category counts and error diagnostics. It does not record synchronized media or history
contents and does not upload telemetry.

The initiating device records elapsed time and app-process CPU time for each stage.
The receiving device records the summed processing time and app-process CPU time for
measured incoming watch-history, search-history, and learning-note batches, with the
number of measured batches. Receiver processing covers handling the request and queuing
its response; it excludes time waiting in the request queue and network transit.
App-process CPU time can include concurrent app work, so it is not a per-thread CPU
percentage or a direct measure of network speed. Existing log entries remain available
when diagnostics is turned off.

Compare logs with matching attempt IDs and keep the same app version, network, and
workload on both devices. A timing improvement alone does not establish that an
intermittent connection failure has been fixed.

## Synchronized data

WizeStream synchronizes:

- Local profile identities and profile metadata
- Subscriptions within each profile
- Feed groups within each profile
- Local and remote playlists within each profile
- Watch history and playback progress within each profile
- Home-tab configuration
- Content-filter selections
- Blocked videos, blocked channels, blocked keywords, blocking targets, and AiSList behavior
- Per-channel playback profiles
- Other explicitly allowlisted settings
- Completed-download metadata

Search history is private by default. It is synchronized only when
**Synchronize search history** is enabled on both devices.

Profiles use stable local UUIDs during device synchronization, so Personal, Work, Study,
and other containers remain distinct even when they contain the same channel, playlist, or video.
The **Default** profile keeps its existing synchronization identity for compatibility.

Watch and search history operations retain the profile that started them, including when the
active profile changes before a pending write finishes. Later views, progress changes and explicit
history deletions are synchronized after the initial exchange as well.

Builds before the post-1.50.0 profile-history fix could miss non-Default history changes after the
first exchange. A build containing the fix journals future writes but cannot establish the order
of those missed changes against deletions or progress recorded on another device. Synchronization
therefore preserves existing journal ordering rather than replaying ambiguous old rows as new
actions. Missing local rows alone do not prove a deletion.

Learning Notes, home tabs, content filters, blocked-content rules, channel playback
profiles, allowlisted settings, and completed-download metadata remain app-wide rather than
profile-scoped.

AiSList synchronization exchanges only the user's enable/behavior choices. Each device downloads
and caches the upstream AiSList data independently.

In current source, deleting a non-Default profile is a local action. This behavior was merged after
1.50.0. WizeStream tombstones that profile ID on the device so a paired peer cannot silently
recreate it there. Profile-owned data already synchronized to another device is not remotely
deleted merely because the profile was removed locally.

Synchronization merges supported records from both devices. It is not a
one-way replacement or a full-device backup. Keep using
**Settings > Backup and restore** when you need a portable backup.

## Downloads and media files

Only completed-download metadata is synchronized. WizeStream does not
transfer downloaded video or audio files between devices.

When synchronized metadata describes a download whose file is missing on the
current device, the Downloads screen shows the item as **Not local**. Select
it and choose **Download on this device** to open the original source in
WizeStream and use the normal download dialog. This resolves currently
available streams and lets you choose the format and quality for this device.

If the same source and media type is already downloaded or pending locally,
WizeStream hides the metadata-only duplicate.

The original source may no longer be available or may have changed since the
other device completed its download. In that case, WizeStream cannot recreate
the missing local media.

## Privacy and security

- Synchronization uses an encrypted peer-to-peer connection between paired
  device identities.
- Only trusted devices can participate after one-time pairing succeeds, whether the code is
  scanned or entered using the manual controls in newer builds.
- Profile-owned synchronized records remain separated by stable profile IDs.
- A locally deleted profile ID is blocked from being re-materialized by a paired peer.
- Search history remains excluded unless it is explicitly enabled on both devices.
- Media-file contents are never transferred by device synchronization.
- Clearing trusted devices requires every device to pair again.

Review the project's [privacy policy](../PRIVACY.md) for the broader app
privacy model.
