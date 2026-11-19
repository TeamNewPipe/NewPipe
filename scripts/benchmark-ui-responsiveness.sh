#!/usr/bin/env bash
set -euo pipefail

# Measure one installed version at a time, using the same device and app data.
# Usage: scripts/benchmark-ui-responsiveness.sh v1.40.0 /tmp/wize-perf
version="${1:?Provide a version label (e.g. v1.40.0)}"
output_root="${2:?Provide a results directory}"
package="org.wisso.newpipematerial"
activity="org.schabi.newpipe.MainActivity"
output="$output_root/$version"
mkdir -p "$output"
adb_cmd=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb_cmd+=(-s "$ANDROID_SERIAL")
fi
if ! "${adb_cmd[@]}" shell pm path "$package" | grep -q '^package:'; then
    echo "WizeStream is not installed on the selected device." >&2
    exit 1
fi

{
    date -u '+UTC %Y-%m-%dT%H:%M:%SZ'
    printf 'Version label: %s\n' "$version"
    printf 'Device: '
    "${adb_cmd[@]}" shell getprop ro.product.model
    printf 'Android: '
    "${adb_cmd[@]}" shell getprop ro.build.version.release
    printf 'SDK: '
    "${adb_cmd[@]}" shell getprop ro.build.version.sdk
    printf 'Build fingerprint: '
    "${adb_cmd[@]}" shell getprop ro.build.fingerprint
} > "$output/device.txt"

printf 'sample,kind,total_time_ms\n' > "$output/startup.csv"
for kind in cold warm; do
    for sample in $(seq 1 10); do
        if [[ "$kind" == cold ]]; then
            "${adb_cmd[@]}" shell am force-stop "$package" >/dev/null
            sleep 1
        else
            "${adb_cmd[@]}" shell input keyevent KEYCODE_HOME
            sleep 1
        fi
        result=$("${adb_cmd[@]}" shell am start -W -n "$package/$activity")
        printf '%s\n' "$result" > "$output/${kind}-${sample}.txt"
        duration=$(printf '%s\n' "$result" | sed -n 's/^TotalTime:[[:space:]]*\([0-9]*\).*$/\1/p' | tail -1)
        if [[ -z "$duration" ]]; then
            echo "Missing TotalTime for $kind sample $sample; inspect raw output." >&2
            duration="NA"
        fi
        printf '%s,%s,%s\n' "$sample" "$kind" "$duration" >> "$output/startup.csv"
    done
done

"${adb_cmd[@]}" shell dumpsys meminfo "$package" > "$output/meminfo-idle.txt"
"${adb_cmd[@]}" shell dumpsys gfxinfo "$package" reset > /dev/null
echo "On the device, scroll the same prepared subscription feed and switch the same Home tabs."
read -r -p "Press Enter after completing the navigation workload..." _
"${adb_cmd[@]}" shell dumpsys gfxinfo "$package" framestats > "$output/gfxinfo-navigation.txt"
"${adb_cmd[@]}" shell dumpsys meminfo "$package" > "$output/meminfo-navigation.txt"

echo "On the device, start the same downloaded/local video and use the same playback controls."
read -r -p "Press Enter once playback is running..." _
"${adb_cmd[@]}" shell dumpsys meminfo "$package" > "$output/meminfo-playback.txt"
echo "Results: $output"
