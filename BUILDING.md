# Building WizeStream

WizeStream includes the complete extractor and timeago-parser sources directly in the `app`
module. Java sources live under `app/src/main/java`, protocol definitions under
`app/src/main/proto`, and their unit tests under `app/src/test/java`. Every WizeStream commit and
release tag therefore records and builds the application and service extraction logic together.

## Requirements

- Git
- JDK 21
- Android SDK platform 37.0 and Build Tools 36.1.0
- Android NDK 28.2.13676358
- Accepted Android SDK licenses

The Android platform, Build Tools, NDK, AGP, and Java toolchain versions are declared in the
Gradle build. With accepted Android SDK licenses, Gradle installs missing Android components.
They can also be installed in advance with:

```bash
sdkmanager "platforms;android-37.0" "build-tools;36.1.0" "ndk;28.2.13676358"
```

## Clone the exact source

```bash
git clone https://github.com/wizdom13/WizeStream.git
cd WizeStream
```

No submodule initialization, separate extractor checkout, or separate extractor build is required.

## Shared build entry points

The same committed scripts are used locally and in GitHub Actions.

Build the debug APK and run JVM checks:

```bash
scripts/build.sh debug
```

Build the default ARM64/ARMv7 release APK:

```bash
scripts/build.sh release
```

Build it twice from clean state with different JVM identity-hash strategies and require
identical SHA-256 hashes:

```bash
scripts/reproducible-build.sh --verify
```

Build the x86_64 release APK for Waydroid and Android-x86:

```bash
scripts/build.sh release -PreleaseAbi=x86_64
```

The `releaseAbi` property accepts `arm` (the default) or `x86_64`. Published releases build
both targets from the same tagged source and sign them with the same release key.

The reproducible entry point runs the complete release pipeline with one visible processor and one
Gradle worker. `JAVA_TOOL_OPTIONS` propagates the processor limit to Gradle, D8/L8, R8, and child
JVMs.
It also applies `gradle/reproducible-r8.init.gradle.kts`, which fixes the unstable retarget-interface
ordering in the R8 9.2.14 compiler embedded in AGP 9.2.1. The patch replaces the retargeter's
identity-keyed map with a descriptor-ordered map. It changes one compiler class in Gradle's
transformed artifact cache; downloaded dependencies remain untouched. No patched compiler binary
is stored in this repository. A checksum guard rejects unreviewed compiler changes when AGP is
upgraded. The equivalent source change and removal instructions are in
[the compiler patch notes](gradle/r8-interface-order.md).

With `--verify`, the first build uses HotSpot's default identity-hash strategy (5), and the second
uses sequential identity hashes (3). This exposes the compiler ordering defect that two builds
under the same JVM configuration can miss. Both builds must produce byte-identical APKs.
Normal debug and test builds keep their parallelism. Build and configuration caches are disabled,
the locale and timezone are fixed, and `SOURCE_DATE_EPOCH` comes from the checked-out commit. A
verified APK and its `SHA256SUMS` file are written to `dist/reproducible/`.

Independent rebuilders should use `scripts/reproducible-build.sh` rather than invoking
`./gradlew assembleRelease` directly. The script applies the compiler correction and JVM settings
used for published releases. It requires a HotSpot-based JDK 21, such as Temurin or Ubuntu OpenJDK,
without assuming a particular SDK installation directory.

Run Android instrumented tests:

```bash
scripts/build.sh connected
```

Run style checks:

```bash
scripts/build.sh checkstyle
```

## CI source and schema checks

The shared build entry points disable automatic Kotlin formatting, including for instrumented
tests. Style checks still run, so CI tests the checked-out Kotlin source and reports formatting
errors without rewriting it.

After the debug build, CI checks every generated JSON file under `app/schemas` against the exact
checked-out commit:

```bash
python3 scripts/check-room-schemas.py
```

The check fails if the current database version has no schema, a committed schema is missing or
changed, or a generated schema has not been committed. It includes untracked and ignored JSON
files. Generate schemas with `scripts/build.sh debug`, review them, and commit them alongside the
database changes. A schema-version change must also increase the app version, as checked by
`scripts/check-database-version-bump.py`. CI validates schemas without changing branches or
creating commits.

## Versioning and release tags

Stable WizeStream releases use semantic versions in `MAJOR.MINOR.PATCH` form. Update the three
`WIZESTREAM_VERSION_*` components in `buildSrc/src/main/kotlin/ProjectConfig.kt`; do not derive
them from a NewPipe version.

The Android version code is encoded as:

```text
MAJOR × 1,000,000 + MINOR × 1,000 + PATCH
```

Keep `MINOR` and `PATCH` between 0 and 999. Add the release notes at
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, then tag the exact release commit
as `vMAJOR.MINOR.PATCH`. The release workflow rejects a tag that does not match Gradle's
`versionName`.

## Release signing

Provide all four WizeStream signing variables before running `scripts/build.sh release`:

```text
WIZESTREAM_RELEASE_STORE_FILE
WIZESTREAM_RELEASE_STORE_PASSWORD
WIZESTREAM_RELEASE_KEY_ALIAS
WIZESTREAM_RELEASE_KEY_PASSWORD
```

The legacy `NEWPIPE_MATERIAL_RELEASE_*` names remain accepted as fallbacks so existing CI secrets and local build environments continue to work during migration.

The resulting APK is written under `app/build/outputs/apk/release/`. The release workflow
publishes the existing `wizestream_vX.Y.Z.apk` ARM asset and a separate
`wizestream_vX.Y.Z_x86_64.apk` asset so ARM downloads do not increase in size.

## Reproducible release rule

Every published WizeStream APK must be built from the exact commit referenced by its release tag.
That commit includes the integrated extractor source under `app/src/main/java`. An APK must not be
replaced with one built from a newer untagged commit; publish a new version and tag instead.

Pull-request CI builds the release APK twice and fails when the SHA-256 hashes differ. Build-tool
changes also run this check in stacked pull requests targeting `feature-*` or `fix-*` branches. The release
workflow applies the same check to both the signed ARM and x86_64 APKs before either asset can be
published.
