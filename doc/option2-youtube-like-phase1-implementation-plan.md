# Option 2 Phase 1 Implementation Plan

## Goal
Implement an accountless "YouTube-like" experience using local data only:
- Watch history + continue watching
- Subscriptions feed + notifications
- Suggested videos on Home

This plan is mapped to existing NewPipeExtended packages to reduce risk and rework.

## Constraints
- No Google/YouTube account login.
- No cloud profile sync.
- Preserve existing behavior for users who do not enable new personalization settings.

## Existing Areas to Reuse
- Database/history/subscriptions/feed:
  - `app/src/main/java/org/schabi/newpipe/database/history`
  - `app/src/main/java/org/schabi/newpipe/database/subscription`
  - `app/src/main/java/org/schabi/newpipe/database/feed`
- Local feature surfaces:
  - `app/src/main/java/org/schabi/newpipe/local/history`
  - `app/src/main/java/org/schabi/newpipe/local/subscription`
  - `app/src/main/java/org/schabi/newpipe/local/feed`
- Player lifecycle and playback:
  - `app/src/main/java/org/schabi/newpipe/player`
- Home/fragment integration:
  - `app/src/main/java/org/schabi/newpipe/fragments`

## Milestone A - Foundation (Schema + Core APIs)

### A1. Audit and finalize schema strategy
- Review existing entities/DAOs for:
  - history timestamps and watch progress fields
  - subscription uniqueness and notify toggles
  - feed freshness markers
- Decision:
  - extend existing entities where possible
  - add new table only if existing schema cannot support signal scoring cleanly

### A2. Migration
- Update `AppDatabase` version and migrations:
  - `app/src/main/java/org/schabi/newpipe/database/AppDatabase*`
  - `app/src/main/java/org/schabi/newpipe/database/Migrations*`
- Add migration tests in:
  - `app/src/androidTest/java/.../database/DatabaseMigrationTest*`

### A3. Repository/API layer
- Introduce or extend repositories in local packages:
  - `local/history/*`
  - `local/subscription/*`
  - `local/feed/*`
- Add stable read APIs:
  - get continue-watching rows
  - get personalized candidates
  - get unseen subscription items

Acceptance:
- DB migration tests pass.
- Repository APIs return deterministic outputs for fixed fixtures.

## Milestone B - Watch History + Continue Watching

### B1. Player progress capture
- Hook progress snapshots into player lifecycle events:
  - likely touch points in `player/Player*`, `player/Audio*`, or playback helpers
- Persist at low frequency (e.g., interval + on pause/stop) to avoid write churn.

### B2. Completion and noise filtering
- Add thresholds:
  - ignore accidental plays under N seconds
  - mark completed when progress ratio exceeds X%
- Ensure rewatches update recency and optionally play count.

### B3. Continue Watching rail
- Add Home rail/card section showing resumable items.
- Exclude completed items by default.
- Tapping item resumes from stored position.

### B4. History UX controls
- Verify/update clear item / clear all / remove from history flows in local history UI.

Acceptance:
- Resume works across app restarts.
- Completion and partial progress states are consistent.

## Milestone C - Subscriptions Feed + Notifications

### C1. Subscription flow hardening
- Ensure channel subscribe/unsubscribe actions consistently update local DB.
- Validate dedupe key: `(serviceId, channelUrl/channelId)`.

### C2. Feed refresh pipeline
- Reuse and extend current feed service/worker classes in:
  - `local/feed/service/*`
  - `local/subscription/worker/*`
- Merge newest items from subscriptions with dedupe + stable sort.

### C3. Notification behavior
- Extend existing feed notification path in:
  - `local/feed/notification/*`
- Notify only unseen items.
- Add guardrails: network constraints, backoff, notification channel settings.

Acceptance:
- Subscribed-channel new upload appears in feed and optionally notifies.
- No duplicate notifications for same item.

## Milestone D - Suggested Videos (Personalized Home)

### D1. Candidate generation
- Build candidates from:
  - recently watched items/channels
  - subscribed channels latest uploads
  - service trending fallback
- Implement in a recommendation service under `local/feed` or a new `local/recommendation` package.

### D2. Ranking model v1 (rule-based)
- Score using:
  - recency
  - completion ratio
  - channel affinity
  - skip penalties
- Add diversity cap to avoid one-channel domination.

### D3. Home integration
- Add "Recommended for You" section in home fragment composition.
- Cold-start behavior:
  - no history/subscriptions => fallback popular/trending rails.

Acceptance:
- Home recommendations change with user watch behavior.
- Cold-start never shows empty section.

## Milestone E - Settings, Privacy, and Controls
- Add or extend settings XML/screens:
  - personalization toggle
  - history tracking toggle
  - recommendation toggle
  - subscription notification toggle/frequency
  - clear personalization data action
- Paths likely in:
  - `app/src/main/res/xml/*_settings.xml`
  - `app/src/main/java/org/schabi/newpipe/settings/*`

Acceptance:
- Toggling settings takes effect without app restart where feasible.
- Clearing data removes history-driven recommendations immediately.

## Milestone F - Testing and Verification

### Unit tests
- Scoring/threshold logic
- Deduplication and sorting rules
- unseen-notification filtering

### Integration tests
- DAO queries with realistic fixtures
- migration tests for new schema version

### Instrumentation tests
- subscribe -> refresh -> feed update
- playback -> progress saved -> resume from continue-watching

### Manual QA matrix
- Offline/network loss
- Mixed services behavior
- App upgrade/migration path
- Large history/subscription datasets

## Execution Order (Recommended)
1. Milestone A
2. Milestone B
3. Milestone C
4. Milestone D
5. Milestone E
6. Milestone F hardening pass

## Definition of Done for Phase 1
1. Continue Watching works reliably.
2. Subscription feed updates and notifications are correct/deduplicated.
3. Home shows meaningful personalized recommendations with cold-start fallback.
4. Migration and core feature tests pass.
5. User controls exist to disable/clear personalization data.

## Out of Scope (Phase 1)
- Real Google account sync
- Cross-device sync
- ML model training pipeline
- Full "YouTube parity" UX cloning
