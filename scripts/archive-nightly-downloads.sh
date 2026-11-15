#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

releases_file="${1:?Pass the JSON file produced by gh api --paginate}"
nightly_repository="${NIGHTLY_REPOSITORY:?NIGHTLY_REPOSITORY is required}"
archive_path="download-stats.json"
workspace="$(mktemp -d)"
trap 'rm -rf "$workspace"' EXIT

archive_response="$workspace/archive-response.json"
archive_file="$workspace/archive.json"
if gh api "repos/${nightly_repository}/contents/${archive_path}" > "$archive_response" 2>/dev/null; then
  archive_sha="$(jq -r '.sha' "$archive_response")"
  jq -r '.content' "$archive_response" | tr -d '\n' | base64 --decode > "$archive_file"
else
  archive_sha=""
  printf '%s\n' '{"schemaVersion":1,"assets":{}}' > "$archive_file"
fi

jq -s \
  --slurpfile archive "$archive_file" \
  '($archive[0].assets // {}) as $saved
  | (add
     | [.[].assets[]?
        | select(.name | ascii_downcase | endswith(".apk"))
        | {key: (.id | tostring), value: {name: .name, downloads: .download_count}}]
     | from_entries) as $current
  | {
      schemaVersion: 1,
      updatedAt: (now | todateiso8601),
      assets: ($saved * $current)
    }' \
  "$releases_file" > "$workspace/merged.json"

encoded="$(base64 -w 0 "$workspace/merged.json")"
if [[ -n "$archive_sha" ]]; then
  jq -n \
    --arg message "Update archived nightly download counts" \
    --arg content "$encoded" \
    --arg sha "$archive_sha" \
    '{message: $message, content: $content, sha: $sha}' > "$workspace/request.json"
else
  jq -n \
    --arg message "Start archived nightly download counts" \
    --arg content "$encoded" \
    '{message: $message, content: $content}' > "$workspace/request.json"
fi

gh api \
  --method PUT \
  "repos/${nightly_repository}/contents/${archive_path}" \
  --input "$workspace/request.json" > /dev/null
