#!/usr/bin/env bash
set -euo pipefail

# Capture repeated Home-tab memory and frame-time snapshots on the same physical device.
# Usage: bash scripts/benchmark-home-tab-memory.sh v1.48.0 /tmp/wize-memory 12
version="${1:?Provide the installed build version or commit label}"
output_root="${2:?Provide an evidence output directory}"
rounds="${3:-12}"
package="org.wisso.newpipematerial"

if [[ ! "$rounds" =~ ^[1-9][0-9]*$ ]] || ((rounds > 40)); then
    echo "Rounds must be between 1 and 40." >&2
    exit 1
fi

adb_cmd=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb_cmd+=(-s "$ANDROID_SERIAL")
fi

if ! "${adb_cmd[@]}" shell pm path "$package" | grep -q '^package:'; then
    echo "WizeStream is not installed on the selected device." >&2
    exit 1
fi

output="$output_root/$version"
mkdir -p "$output"
{
    date -u '+UTC %Y-%m-%dT%H:%M:%SZ'
    printf 'Version label: %s\n' "$version"
    printf 'Device: '
    "${adb_cmd[@]}" shell getprop ro.product.model
    printf 'Android API: '
    "${adb_cmd[@]}" shell getprop ro.build.version.sdk
    printf 'Build fingerprint: '
    "${adb_cmd[@]}" shell getprop ro.build.fingerprint
} > "$output/device.txt"

printf 'round,pid,total_pss_kib\n' > "$output/memory.csv"
echo "Open WizeStream, select the Home tab and let the content settle."
echo "For each round, switch Home → For You → What's New → Bookmarks → Home."
echo "Use the same sequence and wait duration on every build."

for ((round = 0; round <= rounds; round++)); do
    if ((round == 0)); then
        read -r -p "Press Enter to record the initial, settled memory snapshot... " _
    else
        read -r -p "Perform round $round/$rounds and press Enter to capture... " _
    fi

    pid=$("${adb_cmd[@]}" shell pidof "$package" 2>/dev/null | tr -d '\r' | awk '{ print $1 }' || true)
    printf -v prefix 'round-%02d' "$round"
    "${adb_cmd[@]}" shell dumpsys meminfo "$package" > "$output/$prefix-meminfo.txt"
    "${adb_cmd[@]}" shell dumpsys gfxinfo "$package" framestats > "$output/$prefix-gfxinfo.txt"
    "${adb_cmd[@]}" shell dumpsys gfxinfo "$package" reset >/dev/null

    pss=$(sed -n -E 's/^[[:space:]]*TOTAL PSS:[[:space:]]*([0-9]+).*$/\1/p' \
        "$output/$prefix-meminfo.txt" | head -1)
    printf '%s,%s,%s\n' "$round" "${pid:-not-running}" "${pss:-NA}" >> "$output/memory.csv"

    if [[ -z "$pid" ]]; then
        echo "The app process is not running at round $round; preserve this evidence." >&2
    fi
done

echo "Evidence captured in $output"
echo "Compare PSS growth, process restarts and frame stats across identical workloads."
echo "A rising PSS alone does not prove a leak; inspect retained Java objects with a profiler."
