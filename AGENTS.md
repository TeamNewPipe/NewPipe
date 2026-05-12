# AGENTS.md - NewPipeExtended

## Agent Objective
Act as a pragmatic coding agent for this repository: implement requested changes safely, keep scope tight, and verify outcomes with concrete evidence.

## Repository Context
- Project type: Android application (single module `:app`)
- Build system: Gradle Kotlin DSL
- Languages: Kotlin and Java
- Key domains:
  - `org.schabi.newpipe`: app/UI/player/local data/settings
  - `us.shandian.giga`: download subsystem

## Operating Rules
1. Do not assume this repo is Angular/.NET/microservice unless explicitly directed.
2. Keep edits minimal and task-focused; avoid unrelated cleanup.
3. Preserve existing architecture and conventions in touched packages.
4. Avoid destructive git operations unless explicitly requested.
5. Never revert user changes outside the requested scope.

## Planning and Execution
1. Locate relevant files first (`rg`, package scan, build files).
2. Confirm the smallest viable change.
3. Implement code updates with readable, maintainable logic.
4. Run targeted verification for the changed behavior.
5. Report exactly what changed and what was verified.

## Validation Checklist
When feasible, run the smallest relevant set:
- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- Feature-focused test task(s) if known
- Additional lint/check tasks when touched code warrants it

If commands are not run, state that explicitly and why.

## Code Quality Expectations
- Follow existing Kotlin/Java patterns in the file being edited.
- Keep nullability and lifecycle safety explicit.
- Avoid broad API surface changes unless needed.
- Add comments only where intent is not obvious.
- Maintain backward compatibility for user-facing behavior unless asked to change it.

## Dependency and Build Constraints
- Use `gradle/libs.versions.toml` for dependency/version changes.
- Respect existing Gradle task wiring (ktlint/checkstyle/dependency order checks).
- For extractor-dependent work, consider compatibility with `NewPipeExtractor`.

## Response Format for Completed Work
Include:
1. Change summary
2. Files touched
3. Verification commands and outcomes
4. Any risks, assumptions, or follow-ups

Use concise, factual language.
