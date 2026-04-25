# AGENTS.md — Actifit Android

## Project Summary
Decentralized fitness tracker Android app. Tracks steps/workouts, posts activity to Hive/Blurt blockchains, rewards users with AFIT tokens.

## Build & Run
```
./gradlew assembleDebug        # build debug APK
./gradlew installDebug         # install on connected device
./gradlew lint                 # lint check
./gradlew test                 # unit tests (only ExampleUnitTest exists)
./gradlew connectedAndroidTest # instrumented tests
```
- **Gradle 8.13**, AGP **8.13.2**, **Java 1.8**
- compileSdk **36**, minSdk **28**, targetSdk **35**
- `org.gradle.configuration-cache=true` is enabled in `gradle.properties`

## Prerequisites (not in repo)
- `local.properties` must contain `gemini.api.key=<key>` — injected into `BuildConfig.GEMINI_API_KEY` for both debug and release
- `google-services.json` in `app/` — gitignored, required for Firebase build
- `keystore.properties` exists at root with release signing config (gitignored)
- Android SDK with target SDK 35/36 installed

## Architecture
- **Single module** (`:app`) plus `:com.exerpic.si.aar` AAR module
- All Java source in one flat package: `io.actifit.fitnesstracker.actifitfitnesstracker`
- **Entry point**: `LoginActivity` → `MainActivity` (dashboard/nav hub)
- **`BaseActivity`** — shared functionality inherited by most activities
- **`ActivityMonitorService`** — foreground health service (sensor monitoring, step tracking)
- **`HealthConnectManager`** — Android Health Connect API integration
- **`HiveRequests`** — Hive blockchain interactions (posting, voting, transfers)
- **`StepsDBHelper`** — SQLite persistence for step counts/history
- **`AiService`** — Gemini AI integration for fitness insights
- **`PostSteemitActivity`** — UI for publishing activity reports to blockchain
- **`WorkoutWizardActivity`** — multi-step workout logging wizard

## Key Conventions & Quirks
- **Hive RPC fallback**: `HiveRequests` retries with alternative nodes from `res/values/hive_queries.xml` (`hive_rpc_nodes` string-array) if primary fails
- **Network**: uses Volley, Retrofit+OkHttp, and raw HTTP. `usesCleartextTraffic=true` in manifest
- **Markdown rendering**: Markwon 4.6.2 for post content; `Utils.sanitizeContent` for HTML sanitization via jsoup
- **Image loading**: Glide 4.16.0; post image carousel in `PostAdapter` with PagerSnapHelper
- **Video upload**: TUS protocol (`tus-android-client`) for 3Speak video uploads via `VideoUploadFragment`
- **Translations**: DeepL API (`deepl-java`) via `LocaleManager`; supports ar, de, es, hi, it, ko, nl, pt, tr, uk, yo, zh
- **Root detection**: `rootbeer-lib` used for device integrity checks
- **Ads**: AdMob integrated (`play-services-ads`)
- **Firebase**: Crashlytics, Analytics, FCM (with direct boot support)
- **Charts**: MPAndroidChart v3.0.3 via JitPack
- **Exercise data**: JSON files in `app/src/main/assets/`

## Testing
- Only `ExampleUnitTest` exists — minimal test coverage
- No instrumented tests written
- When adding tests, use JUnit 4 (`junit:junit:4.12`), AndroidJUnitRunner, Espresso 3.5.1

## Build Output
- Release APK named `Actifit v0.13.4.2.apk` (versioned filename set in `app/build.gradle`)
- `minifyEnabled false` — no ProGuard/R8 shrinking currently
- `lint.checkReleaseBuilds false` — lint skipped on release builds

## VS Code Setup
- `.vscode/` directory contains `settings.json`, `tasks.json`, `launch.json`, `extensions.json`
- JDK: Android Studio bundled JDK at `C:\Program Files\Android\Android Studio\jbr` (Java 21)
- SDK: `C:\Users\mcfar\AppData\Local\Android\Sdk`
- `JAVA_HOME` must point to JDK — set in `.vscode/settings.json` for integrated terminal
- Gradle wrapper (`gradlew.bat`) was missing from repo; generated via `gradle wrapper`
- Recommended extensions: Java Pack, Gradle for Java, Android for VS Code
- Tasks: `Ctrl+Shift+P` → "Run Task" → select `assembleDebug`, `installDebug`, `build & install`, etc.

## Important Files
| Path | Purpose |
|------|---------|
| `app/build.gradle` | Module config, dependencies, BuildConfig fields |
| `build.gradle` | Root buildscript, AGP/Firebase/Crashlytics plugins |
| `settings.gradle` | Module includes, repo config (FAIL_ON_PROJECT_REPOS) |
| `app/src/main/AndroidManifest.xml` | Permissions, activities, services, providers |
| `res/values/hive_queries.xml` | Hive RPC node list |
| `app/src/main/assets/` | Exercise JSON data |
| `keystore.properties` | Release signing (gitignored) |
| `local.properties` | SDK path + `gemini.api.key` (gitignored) |
