#!/usr/bin/env bash
set -euo pipefail

url="${1:-https://www.youtube.com/watch?v=G-eNlqqkn1w}"
repetitions="${REPETITIONS:-5}"
detail_timeout_seconds="${DETAIL_TIMEOUT_SECONDS:-90}"
frame_timeout_seconds="${FRAME_TIMEOUT_SECONDS:-30}"
build_install="${BUILD_INSTALL:-true}"
youtube_client="${YOUTUBE_CLIENT:-mweb}"
benchmark_path="${BENCHMARK_PATH:-$youtube_client}"
cookie_file="${COOKIE_FILE-}"
device_cookie_file="${DEVICE_COOKIE_FILE:-/data/local/tmp/pipeplay-click-benchmark-token.txt}"
readonly adb_command="${ADB:-adb}"
output="${OUTPUT:-../log/youtube-click-to-first-frame-$(date +%Y%m%d-%H%M%S).log}"
jsonl="${JSONL_OUTPUT:-${output%.log}.jsonl}"

if [[ -z "${COOKIE_FILE+x}" && -f /tmp/token.txt ]]; then
  cookie_file=/tmp/token.txt
fi

mkdir -p "$(dirname "$output")" "$(dirname "$jsonl")"

case "${build_install,,}" in
  1|true|yes) build_install=true ;;
  0|false|no) build_install=false ;;
  *) echo "BUILD_INSTALL must be true or false" >&2; exit 2 ;;
esac

if [[ "$build_install" == true ]]; then
  ./gradlew assembleDebug assembleDebugAndroidTest
fi

abi="$($adb_command shell getprop ro.product.cpu.abi | tr -d '\r')"
app_apk="$(find app/build/outputs/apk/debug -name "*-${abi}-debug.apk" -print -quit)"
app_metadata="app/build/outputs/apk/debug/output-metadata.json"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test_metadata="app/build/outputs/apk/androidTest/debug/output-metadata.json"
app_id="$(sed -n 's/.*"applicationId": "\([^"]*\)".*/\1/p' "$app_metadata" | head -1)"
test_id="$(sed -n 's/.*"applicationId": "\([^"]*\)".*/\1/p' "$test_metadata" | head -1)"
if [[ -z "$app_apk" || -z "$app_id" || ! -f "$test_apk" || -z "$test_id" ]]; then
  echo "Could not locate the benchmark APKs or application IDs" >&2
  exit 2
fi
if [[ "$build_install" == true ]]; then
  $adb_command install -r "$app_apk" >/dev/null
  $adb_command install -r -t "$test_apk" >/dev/null
fi

setup_cookie_file=""
if [[ -n "$cookie_file" ]]; then
  if [[ ! -f "$cookie_file" ]]; then
    echo "Cookie/token file does not exist: $cookie_file" >&2
    exit 2
  fi
  $adb_command push "$cookie_file" "$device_cookie_file" >/dev/null
  setup_cookie_file="$device_cookie_file"
fi
setup_output="$($adb_command shell am instrument -w -r \
  -e class org.schabi.newpipe.player.YoutubeClickBenchmarkSetupTest \
  -e youtubeClient "$youtube_client" \
  -e cookieFile "$setup_cookie_file" \
  "$test_id/androidx.test.runner.AndroidJUnitRunner")"
$adb_command shell rm -f "$device_cookie_file" >/dev/null 2>&1 || true
if ! printf '%s\n' "$setup_output" | rg -q 'OK \(1 test\)'; then
  printf '%s\n' "$setup_output" >&2
  echo "Could not configure click benchmark inputs" >&2
  exit 1
fi
$adb_command shell am force-stop "$app_id"

dump_ui() {
  $adb_command exec-out uiautomator dump /dev/tty 2>/dev/null \
    | tr -d '\r\n'
}

detail_bounds() {
  sed -nE 's/.*resource-id="[^"]*:id\/detail_thumbnail_root_layout"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p'
}

show_info_bounds() {
  sed -nE 's/.*text="Show info"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p'
}

just_once_bounds() {
  sed -nE 's/.*resource-id="android:id\/button2"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p'
}

known_dialog_bounds() {
  local input
  local button_id
  input="$(cat)"
  if printf '%s' "$input" | rg -q 'text="Enable update checker"|text="Support the Project"'; then
    button_id="button2"
  elif printf '%s' "$input" | rg -q 'text="Announcement"|text="What.s New"'; then
    button_id="button1"
  else
    return
  fi
  printf '%s' "$input" \
    | sed -nE "s/.*resource-id=\"android:id\\/${button_id}\"[^>]*bounds=\"\\[([0-9]+),([0-9]+)\\]\\[([0-9]+),([0-9]+)\\]\".*/\\1 \\2 \\3 \\4/p"
}

tap_bounds() {
  local left top right bottom
  read -r left top right bottom <<< "$1"
  $adb_command shell input tap "$(((left + right) / 2))" "$(((top + bottom) / 2))"
}

$adb_command logcat -c
: > "$output"
: > "$jsonl"

for ((round=0; round<repetitions; round++)); do
  $adb_command shell am force-stop "$app_id"
  $adb_command shell am start -W -a android.intent.action.VIEW -d "$url" "$app_id" \
    | tee -a "$output"

  bounds=""
  for ((waited=0; waited<detail_timeout_seconds; waited++)); do
    ui="$(dump_ui || true)"
    dialog_confirm="$(printf '%s' "$ui" | known_dialog_bounds || true)"
    if [[ -n "$dialog_confirm" ]]; then
      tap_bounds "$dialog_confirm"
      sleep 0.25
      continue
    fi
    router_choice="$(printf '%s' "$ui" | show_info_bounds || true)"
    if [[ -n "$router_choice" ]]; then
      tap_bounds "$router_choice"
      sleep 0.25
      ui="$(dump_ui || true)"
      router_confirm="$(printf '%s' "$ui" | just_once_bounds || true)"
      [[ -n "$router_confirm" ]] && tap_bounds "$router_confirm"
      sleep 0.25
      continue
    fi
    bounds="$(printf '%s' "$ui" | detail_bounds || true)"
    [[ -n "$bounds" ]] && break
    sleep 1
  done
  if [[ -z "$bounds" ]]; then
    echo "Round $round: detail play target did not appear" | tee -a "$output" >&2
    $adb_command exec-out uiautomator dump /dev/tty 2>/dev/null >> "$output" || true
    exit 1
  fi

  sleep 0.5
  before_count="$($adb_command logcat -d -v raw -s PlaybackStartup:I '*:S' \
    | rg -c '"record":"click_to_first_frame"' || true)"
  before_click_count="$($adb_command logcat -d -v raw -s PlaybackStartup:I '*:S' \
    | rg -c '"record":"stage".*"stage":"detail_click"' || true)"
  click_registered=false
  for ((click_attempt=0; click_attempt<5; click_attempt++)); do
    tap_bounds "$bounds"
    for ((confirm_attempt=0; confirm_attempt<10; confirm_attempt++)); do
      after_click_count="$($adb_command logcat -d -v raw -s PlaybackStartup:I '*:S' \
        | rg -c '"record":"stage".*"stage":"detail_click"' || true)"
      if ((after_click_count > before_click_count)); then
        click_registered=true
        break 2
      fi
      sleep 0.1
    done
  done
  if [[ "$click_registered" != true ]]; then
    echo "Round $round: detail play click was not registered" | tee -a "$output" >&2
    exit 1
  fi

  summary=""
  for ((waited=0; waited<frame_timeout_seconds * 4; waited++)); do
    summaries="$($adb_command logcat -d -v raw -s PlaybackStartup:I '*:S' \
      | rg '"record":"click_to_first_frame"' || true)"
    after_count="$(printf '%s\n' "$summaries" | sed '/^$/d' | wc -l)"
    if ((after_count > before_count)); then
      summary="$(printf '%s\n' "$summaries" | tail -1 \
        | sed -E 's/^.*PIPEPLAY_PLAYBACK_STARTUP (\{.*\})$/\1/')"
      summary="$(printf '%s\n' "$summary" | jq -c \
        --arg path "$benchmark_path" --arg client "$youtube_client" \
        '. + {benchmarkPath: $path, youtubeClient: $client}')"
      break
    fi
    sleep 0.25
  done
  if [[ -z "$summary" ]]; then
    echo "Round $round: first frame timed out" | tee -a "$output" >&2
    $adb_command logcat -d -v threadtime -s PlaybackStartup:I SabrSessionHelper:I SabrLocalDomPoToken:I \
      >> "$output"
    exit 1
  fi
  printf '%s\n' "$summary" | tee -a "$output" "$jsonl"
done

$adb_command logcat -d -v threadtime \
  | rg 'PIPEPLAY_PLAYBACK_STARTUP|SabrSessionHelper|SabrLocalDomPoToken' \
  >> "$output" || true

echo "Click-to-first-frame results: $jsonl"
