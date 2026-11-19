# Responsiveness regression comparison: v1.40.0 to current

This comparison responds to a user review reporting that WizeStream felt immediate on
v1.40.0 but slower in later versions. Source inspection found expensive recommendation
model updates on the main thread and synchronous For You/feed calculations. These are
plausible causes, **not established performance results**.

## Reproducible conditions

- Use the **same physical Android device**, OS build, screen refresh rate, thermal
  conditions, connection type, battery state and same local library/backup for each run.
- Install and test `v1.40.0`, `v1.43.0`, `v1.46.0` and a build containing the
  latest responsiveness PRs. Record the exact APK SHA-256 and installed version code.
- Keep profiles, subscriptions (including large groups), watch history, image cache and
  custom preferences equivalent across test runs. Do not clear app data between
  samples of one version. Use a separate test install to investigate fresh-install
  behavior. Preserve a backup before any downgrade: database schema may not be
  backward-compatible.
- Keep the same number of Home tabs and identical What's New/For You settings.
  Measure a second pass with optional local recommendations disabled to isolate
  recommendation overhead.
- Do not count emulator CI test durations as physical-device responsiveness.

## Data capture

After each version is installed on a test device:

```sh
bash scripts/benchmark-ui-responsiveness.sh v1.40.0 /tmp/wizestream-perf
# Repeat for v1.43.0, v1.46.0 and the exact tested current commit.
```

The script records ten force-stopped cold launches and ten warm launches using
`am start -W`; it also saves `dumpsys gfxinfo ... framestats` after the
**same manually performed navigation workload**, plus process memory snapshots
at idle, navigation and playback. Keep raw per-sample output rather than
reporting only an average. Label every run with its exact commit/version.

Also measure manually or by Perfetto tracing these repeatable interactions:

1. Home tab 1 to For You to What's New and back, ten cycles with the same tabs.
2. Scroll a 500/1,000-item subscription feed and change the sort and group.
3. Open the same cached channel and start the same video. Record tap-to-first-frame
   separately from network request and buffering time.
4. Seek, pause, resume and change playback speed while observing input-to-action latency.
5. Leave and reopen the app after playing for 15 minutes.

Use Android Studio's CPU profiler / Perfetto to attribute slow frames to
`RecommendationsFragment`, `LocalRecommendations`, feed filtering,
RecyclerView binding, file I/O, database queries or decoder work. A frame-time
trace is needed before concluding that the UI thread causes the reviewer's delay.

## Report template

| Build / commit | Cold median / max (ms) | Warm median / max (ms) | Navigation jank (%) | Idle PSS (MB) | Playback PSS (MB) |
| --- | --- | --- | --- | --- | --- |
| v1.40.0 | Not measured | Not measured | Not measured | Not measured | Not measured |
| v1.43.0 | Not measured | Not measured | Not measured | Not measured | Not measured |
| v1.46.0 | Not measured | Not measured | Not measured | Not measured | Not measured |
| Current tested build | Not measured | Not measured | Not measured | Not measured | Not measured |

Record input latency and first-frame latency separately in accompanying notes.
Compare **medians and slowest samples**, identify a repeatable regression,
and test the changed workload again after each optimization. Preserve the
features of v1.46.0 and the app's privacy guarantees.
