WizeStream’s privacy approach is based on local library storage, optional features and deliberate data sharing. It does not require a WizeStream account or include advertising, analytics or tracking SDKs. That does not make ordinary internet requests anonymous.

## Local data and Incognito

Subscriptions, playlists, supported profile data, history and settings are held locally or in a destination you choose for export. Incognito pauses supported activity recording, including relevant playback, search and learning records.

Incognito is not a VPN. Your media service, proxy, receiver or network provider can still receive ordinary connection information while you use it. Clearing app storage can remove app-private data; a separate export is needed for recovery.

## Optional network features

| Feature | What leaves the device when used |
| --- | --- |
| Normal streaming | Requests to the chosen service and its media hosts, with normal connection information. |
| Optional Vimeo API features | Search keywords or public video/showcase identifiers, plus your configured public-only API token sent to Vimeo's API over HTTPS. |
| Optional service plugins | Repository and package requests to the configured HTTPS publishers; service requests made by the installed plugin. |
| Update checks | Requests to the WizeStream GitHub Releases API when checks are enabled or requested. |
| SponsorBlock playback | Supported public video lookups to the selected community service. |
| SponsorBlock contributions | The public submission/vote and a private persistent contribution ID, after explicit opt-in. |
| DeArrow | Public video identifiers for accepted replacement titles/thumbnail frames when enabled. |
| Live Radio / Live TV | Directory requests and requests to the chosen external stream source. |
| Casting / home audio | Selected media information sent to your chosen receiver or configured server. |

An optional community provider has its own policies. Disabling a feature is different from erasing records already submitted to an external service.

## On-device comment translation

On Android 12 and newer, translation can use Android’s on-device translation framework when the required language pair is already present and ready. WizeStream does not download a translation model or provide a cloud fallback for this feature.

Language detection uses Android text-classification services, which can be supplied by the OS or device manufacturer in a local process. A missing Translate action can reflect language detection, matching languages or an unavailable ready language pair rather than a missing Google login.

## Optional Vimeo API access

Global Vimeo video search and public comments/replies can use a token you supply under **Settings > Content > Vimeo**. Vimeo receives the token to identify your API app and apply its request limits. WizeStream verifies that it grants only public access, sends it only in an HTTPS authorization header to `api.vimeo.com`, and rejects redirects for these authenticated requests.

The token is encrypted with Android Keystore and stored only on that device, outside preferences, backups and Device Sync. Removing it disables the optional API features; ordinary public video, showcase and event playback continues without it. This integration does not sign in to a Vimeo account or request private account data.

## Optional service plugins

Service plugins are disabled until you enable them in **Settings → Plugins**. Installed packages execute in the app process with its permissions. Signature verification and a pinned publisher key protect package integrity and update identity; they do not isolate untrusted code from the app.

Plugin packages, repository addresses and publisher pins stay on the device. Supported plugin preferences and optional login cookies are separated by profile, and switching profiles restarts the process when plugins are loaded. Backups and Device Sync do not transfer plugin packages or configuration; a restored enable switch does not install code. See [service plugins](services-discovery.html#add-a-service-plugin).

## Proxy connections

Open **Settings → Proxy** to configure HTTP or SOCKS5 with optional authentication. Supported new remote browsing, image, playback, download, update and community-service connections use the selected proxy configuration.

Localhost, private addresses and supported local hostnames bypass it so Device Sync and casting can remain reachable. HTTPS content stays encrypted in transit, but a proxy can still see destination hosts. Credentials are protected with Android Keystore and excluded from normal backups.

A configured proxy failure does not silently fall back to a direct remote connection. Plain HTTP proxy transport can expose HTTP Basic credentials to the network between the device and proxy; choose a proxy and network you trust.

## Invidious and media-host trust

Invidious mode depends on the selected instance and the media infrastructure it uses. Saved instances and explicit per-instance trust for Companion hosts are separate from trusting every redirected URL.

**Settings → Content → Trusted Invidious media servers** accepts verified HTTPS root origins, up to sixteen per selected instance. Do not enter broad arbitrary hosts or unknown redirect destinations just to suppress an error.

Direct YouTube requests remain blocked in Invidious mode until an explicit direct-playback choice is made after a failure. Switching to direct playback changes who receives the request.

## Sync and backups

Device Sync uses encrypted peer-to-peer connections after one-time pairing. The reviewed release supports QR scanning or copying and entering the same code manually. Search history is excluded unless enabled on both devices. Actual downloaded media files are not transferred.

Portable backups can contain personal library information even when the app uses no cloud account. Share those files intentionally and review them before sending them to anyone. Proxy credentials, Vimeo API tokens and private SponsorBlock contribution identity are not normal backup/sync content.

## Error reports are deliberate

WizeStream does not upload crash reports automatically. Review and edit the report before copying it, sending email or opening a GitHub issue. Reports can include public or private requested URLs, service/language details, app and Android versions, exceptions and your text.

Remove passwords, cookies, private stream links and other sensitive information from public reports. For the broader explanation and the website’s separate privacy behavior, see the [privacy overview](../privacy.html).
