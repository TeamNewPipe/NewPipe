# Invidious instance and media-server trust

## Choosing a public instance

In **Settings → Content**, select **Find a public Invidious instance** to fetch the
official Invidious directory. WizeStream includes only entries that currently advertise
HTTPS API access, are published and enabled in the directory monitor, are up with a
successful status, have at least 90% recorded uptime, and report a valid TLS certificate.

Discovery is explicit and one-shot. WizeStream does not contact the directory in the
background, automatically select an instance, enable Invidious, or trust companion media
servers. Review the selected origin before confirming it. Public instances are operated
by third parties and can see requests; availability and operator policies can change.
Self-hosted and manually verified instances can still be entered directly.

## Invidious Companion media servers

Some instances serve API requests on one host and redirect playback to an Invidious
Companion on another HTTPS host. WizeStream blocks those redirects by default. A
blocked redirect does not establish that the destination is safe or that it is a
Companion: verify it with the instance operator before granting access.

In **Settings → Content**, select the instance, then open **Trusted Invidious media
servers**. Enter up to 16 verified HTTPS root URLs, one per line, for example
`https://media.example.org`. Paths, credentials, queries, fragments, HTTP URLs and
YouTube/Google video hosts are rejected. Changes while Invidious is enabled restart
the app so active requests cannot retain the previous policy.

The list belongs only to the selected instance. Switching instances does not transfer
its permissions. Clear the list to restore instance-only media access. Saved instance
choices are not automatically trusted as media servers. A malformed restored list
permits no additional servers.

These servers can see your media requests and IP address. Direct YouTube connections
remain blocked, and WizeStream does not automatically trust an arbitrary redirect or
fall back to direct playback. Media, manifest, segment and download requests must stay
on the selected instance or one of its explicitly trusted servers. YouTube thumbnail URLs are routed through the selected instance. Images explicitly
served from a trusted media origin are also accepted; API selection stays on the
selected instance. WebP thumbnails use the instance’s JPEG proxy, and maximum-size
thumbnails use its available-resolution fallback.

Errors report only the blocked origin, without signed media paths or query parameters.
If the instance itself is unavailable, adding servers will not repair that outage.

Duplicate normalized origins count once toward the 16-server limit. The complete
input remains limited to 4,096 characters; oversized lists are rejected.
