# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Summary

Actifit is a decentralized fitness tracker Android app. It tracks steps and workouts, posts activity reports to the Hive/Blurt blockchains, and rewards users with AFIT tokens.

## Build Commands

```bash
./gradlew assembleDebug         # build debug APK
./gradlew installDebug          # install on connected device
./gradlew build                 # full build
./gradlew lint                  # lint checks
./gradlew test                  # unit tests
./gradlew connectedAndroidTest  # instrumented tests on device
```

- **Gradle 8.13**, AGP **8.13.2**, Java 1.8 source/target compatibility
- compileSdk **36**, minSdk **28**, targetSdk **35**
- Configuration caching is enabled (`org.gradle.configuration-cache=true`)
- On Windows use `gradlew.bat` instead of `./gradlew`

## Prerequisites (gitignored, must be provided)

- `local.properties` — must include `gemini.api.key=<key>` (injected into `BuildConfig.GEMINI_API_KEY`)
- `app/google-services.json` — Firebase configuration
- `keystore.properties` — release signing config at project root

## Architecture

**Single module** (`:app`) plus a local AAR module (`:com.exerpic.si.aar`). All ~90 Java classes live in one flat package: `io.actifit.fitnesstracker.actifitfitnesstracker`.

**Entry point**: `LoginActivity` → `MainActivity` (central dashboard and nav hub)

**Core classes:**

| Class | Role |
|---|---|
| `BaseActivity` | Shared functionality inherited by most activities |
| `MainActivity` | Dashboard, activity stats, rewards, navigation |
| `ActivityMonitorService` | Foreground service: sensor monitoring and step tracking |
| `HealthConnectManager` | Android Health Connect API integration |
| `HiveRequests` | Hive/Blurt blockchain RPC (posting, voting, transfers) |
| `StepsDBHelper` | SQLite persistence for steps and activity history |
| `AiService` | Gemini AI integration for fitness insights |
| `PostSteemitActivity` | UI for composing and publishing blockchain activity reports |
| `WorkoutWizardActivity` | Multi-step workout logging wizard |
| `ApiManager` | Centralized REST API communication |

**Data persistence**: SQLite (`StepsDBHelper`) + SharedPreferences for settings + Gson for JSON serialization.

## Key Conventions & Quirks

**Hive RPC fallback**: `HiveRequests` retries failed calls against alternative nodes defined in `res/values/hive_queries.xml` (`hive_rpc_nodes` string-array). The working node is cached to minimize latency. Primary is `hiveapi.actifit.io`; fallbacks include `api.hive.blog`, `anyx.io`, `api.openhive.network`, and others.

**Networking**: Three networking layers coexist — Volley for simple calls, Retrofit+OkHttp for structured REST, and raw HTTP for some blockchain interactions. `usesCleartextTraffic=true` is set in the manifest.

**Markdown & HTML**: Markwon 4.6.2 renders post content. `Utils.sanitizeContent` cleans HTML via jsoup before display.

**Image loading**: Glide 4.16.0. Post images use a `PostImageCarouselAdapter` with `PagerSnapHelper` for swipeable carousels.

**Video upload**: TUS resumable protocol via `tus-android-client` in `VideoUploadFragment` for 3Speak video uploads.

**Translations**: DeepL API (`deepl-java`) managed by `LocaleManager`. Supports 12 languages: ar, de, es, hi, it, ko, nl, pt, tr, uk, yo, zh.

**Security checks**: Root detection via `rootbeer-lib`, emulator detection, app signature validation, and SIM card verification run at startup.

**Charts**: MPAndroidChart v3.0.3 (via JitPack) managed by `ChartManager`. Charts toggle between hourly and daily bar views.

## Important Files

| Path | Purpose |
|---|---|
| `app/build.gradle` | Module config, all dependencies, BuildConfig fields |
| `build.gradle` | Root buildscript: AGP, Firebase, Crashlytics plugins |
| `settings.gradle` | Module includes, repo config (`FAIL_ON_PROJECT_REPOS`) |
| `app/src/main/AndroidManifest.xml` | Permissions, activities, services, providers |
| `res/values/hive_queries.xml` | Hive RPC node list with fallback ordering |
| `app/src/main/assets/exercises.json` | Exercise database loaded at runtime |
| `local.properties` | SDK path + `gemini.api.key` (gitignored) |
| `keystore.properties` | Release signing credentials (gitignored) |

## VS Code / IDE Setup

- JDK: Android Studio bundled JDK at `C:\Program Files\Android\Android Studio\jbr` (Java 21)
- Android SDK: `C:\Users\mcfar\AppData\Local\Android\Sdk`
- `JAVA_HOME` must point to the JDK — configured in `.vscode/settings.json`
- Gradle tasks are exposed as VS Code tasks (`Ctrl+Shift+P` → "Run Task")

## Testing

Test coverage is minimal — only a placeholder `ExampleUnitTest` exists. When adding tests, use JUnit 4 (`junit:junit:4.12`) for unit tests and Espresso 3.5.1 for instrumented tests.

## Build Notes

- Release APK filename: `Actifit v0.13.4.2.apk` (version-stamped, configured in `app/build.gradle`)
- `minifyEnabled false` — ProGuard/R8 shrinking is disabled
- `lint.checkReleaseBuilds false` — lint is skipped on release builds
- Multidex is enabled (`multiDexEnabled true`)
