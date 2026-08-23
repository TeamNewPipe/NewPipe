#!/usr/bin/env bash
set -euo pipefail

url="${1:-https://www.youtube.com/watch?v=G-eNlqqkn1w}"
repetitions="${REPETITIONS:-5}"
warmups="${WARMUPS:-1}"
play_seconds="${PLAY_SECONDS:-60}"
start_position_ms="${START_POSITION_MS:-2995000}"
seek_target_ms="${SEEK_TARGET_MS:--1}"
paths="${PATHS:-}"
cookie_file="${COOKIE_FILE-}"
control_client="${CONTROL_CLIENT:-auto}"
device_cookie_file="${DEVICE_COOKIE_FILE:-/data/local/tmp/pipeplay-benchmark-token.txt}"
warm_webview_runtime="${WARM_WEBVIEW_RUNTIME:-false}"
diagnostic_details="${DIAGNOSTIC_DETAILS:-false}"
max_height="${MAX_VIDEO_HEIGHT:-1080}"
target_codec="${TARGET_CODEC:-avc}"
replace_player_cache="${REPLACE_PLAYER_CACHE:-false}"
output="${OUTPUT:-../log/youtube-playback-benchmark-$(date +%Y%m%d-%H%M%S).log}"
jsonl="${JSONL_OUTPUT:-${output%.log}.jsonl}"
adb="${ADB:-adb}"
cookie_pushed=false

if [[ -z "${COOKIE_FILE+x}" && -f /tmp/token.txt ]]; then
  cookie_file=/tmp/token.txt
fi

case "${control_client,,}" in
  auto)
    if [[ -n "$cookie_file" && -s "$cookie_file" ]]; then
      control_client=tv_downgraded
    else
      control_client=visionos
    fi
    ;;
  tv_downgraded|visionos|android_vr) control_client="${control_client,,}" ;;
  *) echo "CONTROL_CLIENT must be auto, tv_downgraded, visionos, or android_vr" >&2; exit 2 ;;
esac
if [[ -z "$paths" ]]; then
  paths="sabr,${control_client}_generated_dash"
fi

cleanup() {
  if [[ "$cookie_pushed" == true ]]; then
    $adb shell rm -f "$device_cookie_file" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

mkdir -p "$(dirname "$output")" "$(dirname "$jsonl")"

case "${replace_player_cache,,}" in
  1|true|yes) replace_player_cache=true ;;
  0|false|no) replace_player_cache=false ;;
  *) echo "REPLACE_PLAYER_CACHE must be true or false" >&2; exit 2 ;;
esac
case "${warm_webview_runtime,,}" in
  1|true|yes) warm_webview_runtime=true ;;
  0|false|no) warm_webview_runtime=false ;;
  *) echo "WARM_WEBVIEW_RUNTIME must be true or false" >&2; exit 2 ;;
esac
case "${diagnostic_details,,}" in
  1|true|yes) diagnostic_details=true ;;
  0|false|no) diagnostic_details=false ;;
  *) echo "DIAGNOSTIC_DETAILS must be true or false" >&2; exit 2 ;;
esac

./gradlew assembleDebug assembleDebugAndroidTest

abi="$($adb shell getprop ro.product.cpu.abi | tr -d '\r')"
app_apk="$(find app/build/outputs/apk/debug -name "*-${abi}-debug.apk" -print -quit)"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
app_metadata="app/build/outputs/apk/debug/output-metadata.json"
test_metadata="app/build/outputs/apk/androidTest/debug/output-metadata.json"
app_id="$(sed -n 's/.*"applicationId": "\([^"]*\)".*/\1/p' "$app_metadata" | head -1)"
test_id="$(sed -n 's/.*"applicationId": "\([^"]*\)".*/\1/p' "$test_metadata" | head -1)"

if [[ -z "$app_apk" || ! -f "$test_apk" || -z "$app_id" || -z "$test_id" ]]; then
  echo "Could not locate benchmark APKs or application IDs" >&2
  exit 2
fi

# install -r intentionally retains the target app's private player-response cache. The Gradle
# connectedAndroidTest task uninstalls the app and would silently turn every invocation into a miss.
$adb install -r -t "$app_apk"
$adb install -r -t "$test_apk"
$adb logcat -c
device_cookie_arg=()
if [[ -n "$cookie_file" ]]; then
  if [[ ! -f "$cookie_file" ]]; then
    echo "Cookie/token file does not exist: $cookie_file" >&2
    exit 2
  fi
  $adb push "$cookie_file" "$device_cookie_file" >/dev/null
  cookie_pushed=true
  device_cookie_arg=(-e cookieFile "$device_cookie_file")
fi
instrument_args=(
  -e class org.schabi.newpipe.player.YoutubePlaybackBenchmarkTest
  -e url "$url"
  -e repetitions "$repetitions"
  -e warmups "$warmups"
  -e playSeconds "$play_seconds"
  -e startPositionMs "$start_position_ms"
  -e seekTargetMs "$seek_target_ms"
  -e paths "$paths"
  -e warmWebViewRuntime "$warm_webview_runtime"
  -e diagnosticDetails "$diagnostic_details"
  -e maxVideoHeight "$max_height"
  -e replacePlayerCache "$replace_player_cache"
)
if [[ -n "$target_codec" ]]; then
  instrument_args+=(-e targetCodec "$target_codec")
fi
$adb shell am instrument -w -r "${instrument_args[@]}" "${device_cookie_arg[@]}" \
  "$test_id/androidx.test.runner.AndroidJUnitRunner" | tee "$output"
instrumentation_succeeded=false
if rg -q 'OK \(1 test\)' "$output" && ! rg -q 'FAILURES!!!' "$output"; then
  instrumentation_succeeded=true
fi

$adb logcat -d -v brief \
  | rg 'YoutubePlayerCache|PIPEPLAY_BENCHMARK_' \
  | tee -a "$output"

benchmark_pid="$(rg 'PIPEPLAY_BENCHMARK_CONFIG' "$output" | tail -1 \
  | sed -nE 's/^.*System\.out\( *([0-9]+)\).*$/\1/p')"
if [[ -n "$benchmark_pid" ]] && \
    rg -q "System\\.out\\( *${benchmark_pid}\\).*PIPEPLAY_BENCHMARK_" "$output"; then
  rg --no-filename "System\\.out\\( *${benchmark_pid}\\).*PIPEPLAY_BENCHMARK_" "$output" \
    | sed -E 's/^.*PIPEPLAY_BENCHMARK_[A-Z_]+ (\{.*\})$/\1/' | tee "$jsonl"
elif rg -q 'PIPEPLAY_BENCHMARK_' "$output"; then
  rg --no-filename 'PIPEPLAY_BENCHMARK_' "$output" \
    | sed -E 's/^.*PIPEPLAY_BENCHMARK_[A-Z_]+ (\{.*\})$/\1/' | tee "$jsonl"
else
  : > "$jsonl"
fi

expected_summaries="$(tr ',' '\n' <<< "$paths" | sed '/^[[:space:]]*$/d' | wc -l)"
actual_summaries="$(jq -r 'select(.record == "summary") | .path' "$jsonl" 2>/dev/null \
  | wc -l)"
if [[ "$instrumentation_succeeded" != true ]]; then
  echo "Playback benchmark instrumentation failed" >&2
  exit 1
fi
if [[ "$actual_summaries" -ne "$expected_summaries" ]]; then
  echo "Expected $expected_summaries benchmark summaries, got $actual_summaries" >&2
  exit 1
fi
