# Download counters and badges

These PHP endpoints provide aggregate WizeStream download totals for the README and website:

- `izzy-downloads.php`: calendar-year IzzyOnDroid totals
- `github-downloads.php`: stable and archived nightly GitHub APK asset totals
- `website-downloads.php`: direct website ARM and x86_64 requests
- `total-downloads.php`: the sum of those three independent sources
- `summary.php`: same-origin JSON used by the themed website statistics cards

## Private state

The endpoints default to this state file, outside the public document root:

```text
/home/i4f32jjkbvgk/wizestream-private/downloads-state.json
```

The PHP account must be able to create and update that file. To use another location, configure the
`WIZESTREAM_DOWNLOADS_STATE` environment variable. The directory must already exist and must stay
outside every public document root. The file contains only aggregate ARM/x86_64 counters and cached
provider totals; it contains no IP addresses, user agents, referrers, cookies or device identifiers.

The first successful request creates the file automatically. An optional initial file is:

```json
{
  "schemaVersion": 1,
  "website": {"arm": 0, "x86_64": 0},
  "cache": {}
}
```

## Deployment

Upload `badges/`, `download.php` and `downloads/` with hidden `.htaccess` files. Upload signed APKs
as described in `downloads/README.md`. If the host applies a static `connect-src 'none'` Content
Security Policy, allow `connect-src 'self'` on `downloads.html` so its native statistics cards can
read `badges/summary.php`. The README badges do not require browser JavaScript.

Remote provider totals are cached for six hours. When a provider is temporarily unavailable, a
stale cached value is used. The nightly workflow archives each APK asset count before retention
deletes an old prerelease, preserving future all-time nightly totals.
