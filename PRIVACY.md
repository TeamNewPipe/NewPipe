# WizeStream Privacy Policy

Last updated: September 21, 2026

WizeStream is an independently maintained, open-source Android streaming application. It does not require a WizeStream account and does not include advertising, analytics, or tracking SDKs.

## Network requests

WizeStream connects directly to the media services you choose to use. Those services and your network provider can receive ordinary connection information such as your IP address, request details, and cookies required for the selected service. Their own privacy policies apply to that activity.

When update checks are enabled or manually requested, WizeStream contacts the GitHub Releases API for `wizdom13/WizeStream`. The app does not add a WizeStream account identifier or advertising identifier to those requests.

AiSList content filtering is optional and disabled by default. When enabled or manually refreshed,
WizeStream downloads the public high-confidence blocklist and medium-confidence warnlist directly
from the `Override92/AiSList` project on GitHub and caches them in app-private storage. The update
request does not include the video or channel being viewed, a WizeStream account identifier, or an
advertising identifier. Matching against the downloaded channel handles and IDs happens entirely
on-device.

DeArrow is optional and disabled by default. When enabled for YouTube, WizeStream sends the video's
public YouTube identifier to the community-operated DeArrow endpoints at `sponsor.ajay.app` and
`dearrow-thumb.ajay.app` to request accepted replacement titles and thumbnail frames. DeArrow
requests do not include a WizeStream account identifier or advertising identifier. The service and
your network provider receive ordinary connection information such as your IP address.

## On-device comment translation

On Android 12 and newer, WizeStream can translate a comment when you explicitly select
**Translate**. WizeStream uses Android's on-device translation framework only when the requested
language pair is already present and ready on the device. WizeStream does not request a translation
model download and does not provide a cloud translation fallback.

Language detection is handled by Android's text-classification service. Depending on the device,
that service can be provided by Android or the device manufacturer and can run in a separate local
process. WizeStream does not send comment text to its own server or to a remote translation API,
and translated comment text is not persisted by WizeStream.

## Device synchronization

Device synchronization is optional and does not require a WizeStream cloud account. After you pair
two devices with a one-time QR code, WizeStream can exchange supported records directly through an
encrypted peer-to-peer connection between those trusted device identities.

Synchronized records can include subscriptions, feed groups, playlists, watch history, playback
progress, home tabs, content-filter selections, channel playback profiles, allowlisted settings, and
completed-download metadata. Search history is excluded by default and is exchanged only when the
option is enabled on both devices.

Downloaded video and audio files are not transferred. Completed-download metadata can include the
original content URL, media type, filename, MIME type, file size, and completion time so another
device can identify the item and offer to download it again from the original media service.

Trusted-device identities and synchronization state are stored locally. Automatic synchronization
periodically attempts to reach paired devices when the device has sufficient battery and is
connected through Wi-Fi or Ethernet. Clearing trusted devices removes their authorization and
requires them to pair again.

## TV casting

TV casting is initiated by the user. On Android 8 and newer, WizeStream can discover FCast and
Chromecast-compatible receivers on the local network. After you choose a receiver, WizeStream sends
that receiver the selected media URL and content type, together with the WizeStream version and the
Android device manufacturer and model used to identify the sender. The receiver then requests the
media directly from its original service, so that service and the receiver's network provider can
receive the receiver's ordinary connection information. WizeStream does not use Google Play
Services for discovery or casting.

## Crash and error reports

WizeStream does not upload crash reports automatically. The error screen lets you copy a report or explicitly choose to send it through an email application or GitHub. Before sending, you can review and edit your comment. A report can include the requested URL, service, app language, content country and language, package and app version, Android version, timestamp, exception details, and your comment.

Email providers and GitHub process information according to their own privacy policies. GitHub issue reports can be public, so do not include passwords, authentication cookies, private links, or other sensitive information.

## Data stored on your device

Subscriptions, playlists, history, settings, cookies, content-blocking rules, downloads, and backups
are stored locally on your device or in a location you select. WizeStream only exports data when you
choose an export action. Supported downloaded media can contain the public title, uploader, genre,
upload date, and original source URL as embedded file metadata. Removing the app or clearing its
storage removes app-private data, subject to Android and your backup provider's behavior.

## Permissions

WizeStream requests Android permissions only for features you use, such as notifications,
background playback, overlay playback, network and local-receiver discovery, camera access for
scanning a device-pairing QR code, and legacy file access on supported older Android versions.
Android settings can be used to revoke optional permissions.

## Website download counters

The WizeStream website offers optional direct ARM and x86_64 APK downloads. An initial, non-range
GET request increments only the corresponding aggregate counter before the browser is redirected
to the locally hosted APK. HEAD requests, prefetch requests and resumed range requests are not
counted. The counter does not store IP addresses, user agents, referrers, cookies or device
identifiers.

Public badges combine this aggregate website count with public GitHub release-asset counts and
public IzzyOnDroid aggregate statistics. Provider and combined totals are cached in a private file
outside the public document root. Ordinary hosting access logs remain governed by the hosting
configuration and are not disabled or erased by the counter.

## Optional community reviews

The optional community review page is a separate PHP/MySQL website hosted with the project site,
intended for https://wizestream.org/reviews/. The About action remains hidden until its live HTTPS
URL has been configured and verified. Tapping it opens your external browser without appending
app, account or device data; the app makes no background review requests.

The form asks for a 1–5-star rating, written feedback and explicit publication consent, plus an
optional nickname and manually entered app version. No account or email is required. All reviews
wait for moderation, and only published reviews count toward public ratings and badges. These are
community reviews, not verified installations or a guarantee of one review per person.

The service stores review fields and a hash of a private management/deletion key. The raw key is
created in the browser and appears in the private link fragment; it is sent only in a same-origin
request header, not as a query parameter. Anyone with the key can access or delete that review.

The PHP service uses no analytics, tracking cookies, third-party scripts or application request logs.
It does not store raw IPs, user agents, email addresses or device identifiers. For abuse prevention,
PHP workers share temporary HMAC-derived address/kind buckets in MySQL. Their identifiers change
each UTC hour and are not attached to reviews. They expire at the next UTC hour and are removed
on the next API request or hourly scheduled cleanup. These short-lived records are pseudonymous
abuse data, not anonymous identities.

Pending reviews expire after 30 days and are removed on the next API request or scheduled cleanup.
Published reviews remain until author/moderator removal. Deletion removes the live row and live
rating; GoDaddy/hosting logs, database logs, replicas, snapshots and operator-managed backups may
retain earlier data according to their actual configuration and retention. The PHP service does
not disable or erase provider logs or backups. Deployment instructions require the operator to
review and disclose those hosting practices before public launch.

## Changes and contact

Policy changes are published in this repository. For privacy questions, use the WizeStream repository's issue form without including sensitive personal information.

WizeStream is based on NewPipe but is not affiliated with, sponsored by, or endorsed by the official NewPipe project, TeamNewPipe, or NewPipe e.V. This policy applies to WizeStream; upstream projects maintain their own policies.
