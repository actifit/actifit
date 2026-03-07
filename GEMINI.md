# Actifit Android Project Overview

Actifit is a decentralized fitness tracking application that rewards users with AFIT tokens (on the Hive/Blurt blockchains) based on their physical activity.

## Project Purpose
The app tracks user activity (steps, workouts) and allows users to post their daily progress to the Hive blockchain to earn cryptocurrency rewards. It integrates with various health platforms like Google Fit and Health Connect.

## Core Technology Stack
- **Language**: Java 1.8 (Android)
- **Build System**: Gradle
- **Architecture**: Service-based background monitoring with SQLite persistence.
- **Blockchain**: Hive/Blurt integration via `bitcoinj-core` and custom Hive signing logic.
- **AI Integration**: Custom `AiService` utilizing the Gemini API for fitness-related insights.
- **Data Persistence**: `StepsDBHelper` (SQLite) for storing activity history and settings.
- **Networking**: Volley, Retrofit, and OkHttp for API communication.
- **UI/UX**: 
    - Material Design components.
    - `MPAndroidChart` for activity visualization.
    - `Markwon` for Markdown content rendering.
    - `Glide` for image loading and caching.

## Key Components & Files
- `MainActivity.java`: The central dashboard and navigation hub.
- `ActivityMonitorService.java`: A foreground service that monitors sensors and tracks activity in the background.
- `StepsDBHelper.java`: Manages the local SQLite database for step counts and history.
- `HiveRequests.java`: Core logic for interacting with the Hive blockchain (posting, voting, transfers).
- `HealthConnectManager.java`: Handles integration with the Android Health Connect API.
- `WorkoutWizardActivity.java`: A multi-step wizard for logging and managing specific workouts.
- `AiService.java`: Handles interactions with the Gemini AI for generating responses or analyzing data.
- `PostSteemitActivity.java`: The interface for creating and publishing activity reports to the blockchain.

## Development Conventions
- **Naming**: Follows standard Android/Java camelCase conventions.
- **Architecture**: Heavily relies on `BaseActivity.java` for shared functionality across screens.
- **Networking**: API keys (like Gemini) are managed via `local.properties` and injected into `BuildConfig`.
- **Localization**: Managed via `LocaleManager.java` and supports multiple languages (ar, de, es, hi, it, ko, nl, pt, tr, uk, yo, zh).

## Building and Running
### Prerequisites
- Android Studio / SDK (target SDK 35/36).
- `local.properties` file with necessary API keys (e.g., `gemini.api.key`).
- `keystore.properties` for release signing.

### Key Commands
- **Build Debug APK**: `./gradlew assembleDebug`
- **Install on Device**: `./gradlew installDebug`
- **Run Unit Tests**: `./gradlew test`
- **Run Instrumented Tests**: `./gradlew connectedAndroidTest`
- **Lint Check**: `./gradlew lint`

## Asset Management
- UI layouts are located in `app/src/main/res/layout/`.
- Drawables and icons are in `app/src/main/res/drawable/`.
- Raw assets (like JSON exercise data) are in `app/src/main/assets/`.

## Hive RPC Fallback Mechanism
The application implements a recursive fallback mechanism for Hive blockchain requests. If the primary node fails, it automatically retries with alternative nodes managed in `app/src/main/res/values/hive_queries.xml` via the `hive_rpc_nodes` string-array.

Currently configured nodes include:
- `https://hiveapi.actifit.io` (Actifit Default)
- `https://api.deathwing.me`
- `https://api.hive.blog`
- `https://anyx.io`
- `https://api.openhive.network`
- `https://rpc.syncad.com`
- `https://rpc.ausbit.dev`

The system tracks the currently working node to minimize latency for subsequent requests.

## Content Sanitization & Image Extraction
- **Image Extraction**: The app extracts preview images from the post body if missing from `json_metadata`.
- **Sanitization**: `Utils.sanitizeContent` returns sanitized HTML to preserve structural integrity for `Markwon`.
- **Preview Optimization**: `SingleHivePostModel` now strips Markdown and HTML images *before* trimming text for the short preview, eliminating "image" text artifacts and broken Markdown tags.
- **Placeholder UX**: `PostAdapter` dynamically manages `ScaleType`—using `FIT_CENTER` for the Actifit logo placeholder to avoid distortion, and switching to `CENTER_CROP` once the full post image loads.
- **Image Carousel**: Every post now features a horizontal image carousel (`RecyclerView`) that loads all images found in the post's metadata and body.
    - **Visual Indicators**: Includes a "1 / X" counter, a dots indicator, and left/right navigation arrows for easy browsing.
    - **Paging UX**: Uses `PagerSnapHelper` for smooth, centered paging behavior.
    - **Loading States**: Each image displays a progress spinner while loading and falls back to the Actifit logo if it fails.
    - **Visibility Management**: Automatically hides when the post is expanded or if no images are present.
