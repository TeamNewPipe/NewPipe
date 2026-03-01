# Nostr Sync

## What This PR Adds

This PR adds a new **Nostr Sync** feature area and end-to-end private sync for:

1. Watch history (including per-video resume position)
2. Subscriptions (including subscription group membership)

The sync transport is Nostr app data events:

1. **Kind**: `30078`
2. **Storage convention**: `NIP-78`
3. **Payload encryption**: `NIP-44`
4. **External signer integration**: `NIP-55` (Amber-compatible)

## User-Facing Changes

### Navigation and screen

1. Added a new **Nostr Sync** section in the left panel.
2. Nostr Sync screen contains:
   - `Sync watch history` toggle
   - `Sync subscriptions` toggle
   - `Sign In` (when no identity)
   - `Show Identity` + `Clear Identity` (when identity exists)
   - Relay checklist with:
     - add relay
     - remove relay
     - reset relays to defaults

### Identity flows

When identity is not set:

1. `Sign In` opens dialog.
2. If NIP-55 signer app exists:
   - Prompt to use signer app.
3. If signer app is missing:
   - Prompt to install Amber.
4. `Advanced` section supports:
   - Generate local keypair
   - Scan `nsec` from QR
   - Paste `nsec` in input field and import via dialog `Done`

When identity is set:

1. `Show Identity` dialog shows:
   - `npub` + copy + QR
   - `nsec` (if local) masked by default + eye toggle + copy + QR
   - profile image if available
2. For signer-managed identities (no local `nsec`):
   - `nsec` label remains visible
   - explanatory message shown under label
3. `Clear Identity` shows destructive confirmation.

### Pull-to-sync

1. Added pull-to-refresh-triggered sync in:
   - History view
   - Subscriptions view
2. Refresh indicator remains visible while sync is running and stops when it completes/fails.

## Relay Configuration

Configured relay list:

- `wss://relay.primal.net`
- `wss://relay.damus.io`
- `wss://relay.snort.social`
- `wss://nostr.oxtr.dev`
- `wss://nos.lol`
- `wss://nostr.bitcoiner.social`
- `wss://nostr.semisol.dev`
- `wss://shu01.shugur.net`
- `wss://shu02.shugur.net`
- `wss://shu03.shugur.net`
- `wss://shu04.shugur.net`
- `wss://shu05.shugur.net`

Default enabled relays for fresh config:

1. `wss://relay.primal.net`
2. `wss://relay.damus.io`
3. `wss://relay.snort.social`
4. `wss://nostr.oxtr.dev`
5. `wss://nos.lol`
6. `wss://nostr.bitcoiner.social`
7. `wss://nostr.semisol.dev`

All `shu*.shugur.net` relays are unchecked by default.

Relay list is user-configurable in-app:

1. Add custom relay URL
2. Remove any relay entry
3. Reset list and checked state back to defaults

## Sync Protocol and Event Shape

### Event kind and filtering

1. Sync reads/writes Nostr events with kind `30078`.
2. Relay query filter includes:
   - `kinds: [30078]`
   - author pubkey

### Payload envelope

Published event content wraps encrypted payload in JSON:

- `data.enc = "nip44"`
- `data.ciphertext = <nip44 ciphertext>`

The encrypted plaintext payload includes:

1. `v` (payload version)
2. `category` (`watch_history` or `subscriptions`)
3. `updated_at`
4. `data` object (actual merged records)

Event tags include:

1. `d` tag (device/category-scoped)
2. `p` tag (self pubkey)
3. `client` tag (`newpipe-sync`)

### Sign/encrypt modes

Two modes are supported:

1. **Local key mode** (`nsec` present):
   - NIP-44 encrypt/decrypt locally
   - event signing locally
2. **Signer-only mode** (no local `nsec`, NIP-55 identity):
   - NIP-44 encrypt/decrypt via signer app
   - event signing via signer app

## Merge Strategy (CRDT-style)

This implementation uses deterministic per-record merge rules (CRDT-style behavior), so data from multiple devices converges.

### Watch history merge

Keyed by `serviceId + url`.

For each record:

1. `repeatCount = max(local, remote)`
2. Newer `accessTs` wins for time-sensitive fields (title/type/duration/uploader/thumbnail)
3. Missing fields are filled from the other side when possible
4. `progressMillis` (resume position) follows newer `accessTs`; if winner has no value,
   keep the available non-negative value from the other side; on equal `accessTs`, max progress wins

DB apply rule:

1. Upsert stream row
2. Compare with latest history entry
3. Store merged `accessDate = max(local, remote)` and merged `repeatCount = max(local, remote)`
4. Upsert `stream_state.progress_time` when merged record includes `progressMillis`

### Subscription merge

Keyed by `serviceId + url`.

For each record:

1. Text fields kept if non-empty (prefer existing non-empty, fill blanks)
2. `subscriberCount` uses max positive value when available
3. Group memberships merged as union by group name
4. Group icon conflict rule prefers non-default icon over default `ALL`

DB apply rule:

1. Insert missing subscriptions
2. Update existing only if merged values changed
3. Reconcile feed groups by name (create/update)
4. Rewrite membership for each group from merged membership set

## Robustness and UX Fixes Included

1. Crash fixes around Nostr Sync screen initialization.
2. Icon updates (`sync`, QR icon variant).
3. Portrait-locked QR scanning capture activity.
4. NIP-55 handling fixes for signer result parsing and package resolution.
5. Improved identity dialog layout/visibility behavior across screen sizes.

## Known Scope Boundaries

1. Watch later playlist sync is intentionally not implemented in the active flow.
2. Public playlist publication is not implemented in this PR.

## Testing Notes

Manual flows covered during development:

1. Local `nsec` identity generation/import/clear/show
2. Signer-managed identity via NIP-55
3. History sync across devices with same identity and relays
4. Resume-position sync (`stream_state.progress_time`) across devices
5. Subscription sync including grouped subscriptions
6. Pull-to-sync behavior in History and Subscriptions
7. Relay enable/disable behavior

## Files of Interest

Core:

1. `app/src/main/java/org/schabi/newpipe/local/nostr/NostrSyncManager.java`
2. `app/src/main/java/org/schabi/newpipe/local/nostr/NostrSyncFragment.java`
3. `app/src/main/java/org/schabi/newpipe/local/nostr/Nip55SignerClient.java`
4. `app/src/main/java/org/schabi/newpipe/local/nostr/NostrKeyUtils.java`

DAO:

1. `app/src/main/java/org/schabi/newpipe/database/history/dao/StreamHistoryDAO.kt`
2. `app/src/main/java/org/schabi/newpipe/database/stream/dao/StreamStateDAO.kt`
3. `app/src/main/java/org/schabi/newpipe/database/subscription/SubscriptionDAO.kt`
4. `app/src/main/java/org/schabi/newpipe/database/feed/dao/FeedGroupDAO.kt`

UI:

1. `app/src/main/res/layout/fragment_nostr_sync.xml`
2. `app/src/main/res/layout/dialog_nostr_sign_in.xml`
3. `app/src/main/res/layout/dialog_nostr_identity.xml`
4. `app/src/main/res/layout/fragment_history_playlist.xml`
5. `app/src/main/res/layout/fragment_subscription.xml`
