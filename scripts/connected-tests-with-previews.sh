#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

collect_previews() {
    mkdir -p "$ROOT_DIR/app/build/reports/androidTests/connected"
    timeout 10s adb logcat -d -b main -b system -b crash \
        > "$ROOT_DIR/app/build/reports/androidTests/connected/device-logcat.txt" || true
    timeout 10s adb pull /sdcard/Download/wizestream-player-layout-previews \
        "${RUNNER_TEMP:-$ROOT_DIR/app/build/outputs}/player-layout-previews" || true
    kill "$LOGCAT_PID" 2>/dev/null || true
}

# Keep the test command's exit status while collecting previews on both success and failure.
trap collect_previews EXIT
# Stream progress before a disconnected emulator can make its saved logs unavailable.
adb logcat -v threadtime -s TestRunner AndroidRuntime HistoryLoadingBenchmark &
LOGCAT_PID=$!
# Leave time for logs and reports before the enclosing 30-minute CI job expires.
timeout --signal=TERM --kill-after=30s 20m "$ROOT_DIR/scripts/build.sh" connected "$@"
