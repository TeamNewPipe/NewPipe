Local profiles and Device Sync solve different problems. A profile separates parts of a library on one device; synchronization exchanges supported records between trusted WizeStream devices. Neither requires a WizeStream cloud account.

## Create a useful profile structure

Create profiles for the contexts you actually need, such as Personal, Work or Study, and switch from the navigation drawer. Profile-owned subscriptions, playlists, feed groups, history and playback positions remain distinct.

Do not assume every setting is profile-specific. Some controls, learning notes and selected synchronization categories are app-wide. Review the individual setting and the sync category list before relying on separation for a particular record type.

## Pair two devices

1. Put both devices on the same reachable Wi-Fi, Ethernet or local hotspot network.
2. Open **Settings → Device synchronization** on both devices.
3. Choose **Show pairing code** on one and keep that screen visible.
4. Choose **Scan pairing code** on the other and scan the QR code.
5. Wait for confirmation, then select **Sync now** for an immediate exchange.

The pairing code expires after five minutes and is single-use. Successful pairing establishes trust between device identities, so ordinary later synchronization does not require a new code.

## Pair without a camera, including Waydroid

**Merged after 1.50.0:** current source adds **Copy pairing code** and **Enter pairing code**. These controls are not in the reviewed 1.50.0 release.

1. On the first device, choose **Show pairing code**, then **Copy pairing code**. Keep its pairing screen open.
2. Transfer the complete code privately to the second device. Share it only with the device you intend to trust.
3. On the second device, choose **Enter pairing code**, paste the complete code and choose **Pair** within five minutes.
4. Wait for confirmation and choose **Sync now**.

For a phone and Waydroid, copy the phone's code into Waydroid and start **Sync now** there. The manual path uses the same signed invitation and single-use checks as QR scanning. It removes the camera requirement; Waydroid must still be able to reach the phone on the local network. Guest isolation, VPN routing, host firewalls or container networking can prevent that connection.

## Select what to synchronize

| Category | Behavior |
| --- | --- |
| Profile-owned libraries | Subscriptions, groups, playlists, history and progress preserve supported profile identities. |
| Search history | Excluded unless enabled on both devices. |
| Selected settings | Only explicitly supported or allowlisted settings are exchanged. |
| Content filters | Supported blocking and filtering choices can be exchanged. |
| Learning records | Notes and supported optional difficulty ratings have their own controls. |
| Completed downloads | Metadata is synchronized; media-file contents are not. |

AiSList synchronization transfers your selected behavior, not a shared copy of its upstream data. Each device downloads its own cache when that feature is enabled.

**Merged after 1.50.0:** profile-history changes keep subsequent watch/progress changes and explicit deletions attached to the profile that initiated them. They do not reconstruct the ordering of missed events from older builds. Updating both devices helps avoid differing behavior, but this change does not establish that every synchronization failure is resolved.

## Background behavior

After pairing, automatic synchronization can attempt an exchange roughly hourly over Wi-Fi or Ethernet when battery and reachability conditions permit. An optional charging condition can further constrain scheduled work.

While automatic synchronization is enabled and a trusted device remains paired, a foreground listener can show a low-priority Device synchronization notification. A temporarily offline peer remains paired; use Sync now when both devices are available.

## Merging and deleting profiles

Synchronization merges supported records. It is not a one-way replacement or a full backup. Stable profile identifiers help prevent similarly named profiles from silently merging into each other.

**Merged after 1.50.0:** deleting a non-default profile is a local action. It does not remotely delete every record already synchronized to another device. A local tombstone prevents that same profile identity from silently reappearing on the deleting device.

## Diagnose an unsuccessful attempt

Open **Settings → Device synchronization → Synchronization log** on both devices after reproducing the problem. Note both app versions, Android/device details, which device initiated the attempt, and the results on each side. Successful pairing alone does not prove that every data category exchanged successfully.

Current source adds pairing and listener-start events with safe failure categories. These setup records omit invitation codes, tokens and network addresses. If the code expired or was incomplete, copy a new one while keeping the first device's pairing screen open; check both devices' clocks if prompted.

**Merged after 1.50.0:** **Device Sync performance diagnostics** is available directly below **Synchronization log** in the release-build settings screen. It is off by default. When enabled, it adds local stage timings and total app-process CPU time without recording media or history contents. This supplies evidence about slow or failed attempts; it does not change network routing or repair missing data.

## Kids Mode and Incognito

Kids Mode uses a local parent PIN to protect settings and profile changes and restrict selected YouTube actions. It does not inspect every video or provide an automatic guarantee that content is suitable for children.

Incognito pauses supported activity recording. It does not make network requests anonymous or hide your IP address from media services, proxies or network providers. See [privacy and networking](privacy-network.html).

## Disconnect and recover safely

Use **Clear trusted devices** to remove pairings. Devices must pair again before exchanging data. Maintain an independent portable backup through **Settings → Backup and restore**; a synchronized deletion or unwanted merge can propagate to peers, so sync is not your recovery history.

If pairing fails, check local-network reachability, guest-network isolation, camera permission when scanning, expired codes and Android battery restrictions. A device-wide VPN may need local-network access enabled; Android's **Block connections without VPN** can still prevent local connections. Do not expose the synchronization listener to the public internet as a workaround. Review copied logs and remove private details before sharing them.
