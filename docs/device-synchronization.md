# Device synchronization

WizeStream can synchronize supported app data directly between trusted WizeStream
devices. It does not require a platform login or a WizeStream cloud account.

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

The QR code expires after five minutes and can be used only once. Pairing
establishes trust between the two device identities; later synchronization
does not require rescanning the code.

Use **Clear trusted devices** to remove every pairing. Each device must be
paired again before it can synchronize.

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

Older builds could miss non-Default history changes after the first exchange. Updating fixes
future writes but cannot establish the order of those missed changes against deletions or
progress recorded on another device. Synchronization therefore preserves existing journal
ordering rather than replaying ambiguous old rows as new actions. Missing local rows alone
do not prove a deletion.

Learning Notes, home tabs, content filters, blocked-content rules, channel playback
profiles, allowlisted settings, and completed-download metadata remain app-wide rather than
profile-scoped.

AiSList synchronization exchanges only the user's enable/behavior choices. Each device downloads
and caches the upstream AiSList data independently.

Deleting a non-Default profile is a local action. WizeStream tombstones that profile ID on the
device so a paired peer cannot silently recreate it there. Profile-owned data already synchronized
to another device is not remotely deleted merely because the profile was removed locally.

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
- Only trusted devices can participate after the one-time QR pairing succeeds.
- Profile-owned synchronized records remain separated by stable profile IDs.
- A locally deleted profile ID is blocked from being re-materialized by a paired peer.
- Search history remains excluded unless it is explicitly enabled on both devices.
- Media-file contents are never transferred by device synchronization.
- Clearing trusted devices requires every device to pair again.

Review the project's [privacy policy](../PRIVACY.md) for the broader app
privacy model.
