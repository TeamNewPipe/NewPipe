#!/usr/bin/env bash

dir="fastlane"
size="500"
name_filter="${1:-}"

find_cmd=(find "$dir" -type f -size +"${size}c")
if [[ -n $name_filter ]]; then
  find_cmd+=(-name "$name_filter")
fi
find_cmd+=(-print0)

# find: -type f (files), -size +Nc (N bytes, + means strictly greater)
# print0/xargs -0 to safely handle filenames with spaces/newlines

  # macOS stat: "%z %N" => size and filename
"${find_cmd[@]}"| xargs -0 stat -f "%z %N"


