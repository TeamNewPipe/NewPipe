#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

MODE="${1:-debug}"
shift || true

case "$MODE" in
    debug)
        exec ./gradlew clean assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint "$@"
        ;;
    release)
        exec "$ROOT_DIR/scripts/reproducible-build.sh" "$@"
        ;;
    nightly)
        exec ./gradlew \
            --init-script "$ROOT_DIR/scripts/nightly.init.gradle" \
            clean assembleNightly \
            --stacktrace \
            -DskipFormatKtlint \
            "$@"
        ;;
    checkstyle)
        exec ./gradlew runCheckstyle --stacktrace -DskipFormatKtlint "$@"
        ;;
    connected)
        exec ./gradlew connectedCheck --stacktrace -DskipFormatKtlint "$@"
        ;;
    *)
        echo "Usage: $0 {debug|release|nightly|checkstyle|connected} [extra Gradle arguments]" >&2
        exit 2
        ;;
esac
