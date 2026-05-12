# Repository Instructions (NewPipeExtended)

## Purpose
This repository contains an Android application based on NewPipe, built with Gradle Kotlin DSL.  
Primary goals are stability, maintainability, and privacy-respecting media features.

## Tech Stack
- Android app module: `:app`
- Languages: Kotlin + Java
- Build system: Gradle (`build.gradle.kts`)
- Minimum SDK: 23
- Media: ExoPlayer
- Persistence: Room
- Async/reactive: RxJava3 and Kotlin coroutine interop

## Project Structure
- `app/src/main/java/org/schabi/newpipe`: core app features (UI, player, settings, local data)
- `app/src/main/java/us/shandian/giga`: download engine and related services
- `app/src/main/res`: resources, themes, strings, layouts
- `app/src/test`: JVM unit tests
- `app/src/androidTest`: instrumentation tests
- `gradle/libs.versions.toml`: dependency/version catalog

## Recommended Workflow
1. Read the relevant package and existing patterns before editing.
2. Keep changes focused and avoid broad refactors unless explicitly requested.
3. Prefer small, reviewable commits grouped by behavior change.
4. Run formatting/linting/tests for touched areas before finalizing.

## Build and Test Commands
- Build debug APK: `./gradlew :app:assembleDebug`
- Run JVM tests: `./gradlew :app:testDebugUnitTest`
- Run instrumentation tests: `./gradlew :app:connectedDebugAndroidTest`
- Lint: `./gradlew :app:lintDebug`
- Style checks used by this repo include ktlint/checkstyle tasks wired in Gradle.

## Coding Guidance
- Follow existing style in the touched file (Kotlin or Java).
- Prefer clear, explicit names and small methods.
- Avoid introducing new frameworks unless required.
- Reuse existing utilities before adding new helpers.
- Keep Android lifecycle handling explicit and safe.

## Dependency Guidance
- Add/update library versions via `gradle/libs.versions.toml`.
- Avoid inline hardcoded dependency versions in module build files.
- For extractor-related changes, account for the external `NewPipeExtractor` dependency.

## PR / Change Expectations
- Summarize user-visible behavior changes.
- Note risk areas (playback, downloads, DB migrations, background work).
- Include test evidence (commands run + result).
- Flag any intentionally skipped tests with reason.

## Definition of Done (Default)
1. Code compiles for debug build.
2. Relevant tests pass for changed behavior.
3. No new lint/style violations in touched scope.
4. Documentation/comments updated when behavior changes are non-obvious.
