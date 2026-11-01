# AGENTS.md

This document provides essential instructions, architectural reference, build/test commands, and coding conventions for AI agents and developers working on the **Music Player** Android project.

---

## 1. Project Overview & Architecture

**Music Player** is a modern, lightweight Android music player designed specifically for organizing and playing local audio tracks (primarily MP3s) grouped by directory structure. It is built using modern Android architecture components.

### Module Structure & Dependency Rules
- The project currently uses a single-module architecture, containing only the `:app` module.
- **Dependency Rules**: If future feature modules are added (e.g., `:core:*`, `:feature:*`), feature modules **must not** depend on other feature modules.

### Architecture & Tech Stack
- **Architecture**: MVVM (Model-View-ViewModel) with Unidirectional Data Flow (UDF)
- **Language**: Kotlin 2.2.10
- **UI Framework**: Jetpack Compose with Material Design 3
- **State Management**: Kotlin Coroutines & `StateFlow`
- **Audio Engine**: Android `MediaPlayer` encapsulated in a singleton manager with a `ForegroundService` for background playback and lockscreen media notification controls.
- **Storage Access**: Dual-mode scanning using Android `MediaStore` (API 29+ scoped storage support) and Storage Access Framework (SAF) folder/file pickers.

### Directory Structure

```
MusicPlayer/
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Repositories & module inclusions
├── gradle/
│   ├── libs.versions.toml              # Centralized version catalog
│   └── gradle-daemon-jvm.properties    # Toolchain configuration (Java 21)
└── app/
    ├── build.gradle.kts                # App module configuration
    └── src/
        ├── androidTest/java/...        # Instrumented Android tests
        ├── test/java/...               # Host JVM unit tests
        └── main/
            ├── AndroidManifest.xml
            ├── java/app/kiddos/musicplayer/
            │   ├── model/              # Domain models and enums
            │   ├── data/               # Storage scanning & metadata extraction
            │   ├── playback/           # Low-level audio controller singleton
            │   ├── service/            # Background playback & notifications
            │   ├── ui/                 # Presentation layer (Compose)
            │   └── MainActivity.kt     # Single activity host
            └── res/                    # Resources (drawables, values, etc.)
```

---

## 2. Environment & Build Commands

- **JDK Version**: Java 11 compatibility is targeted (`sourceCompatibility = JavaVersion.VERSION_11`). A JDK 17 or JDK 21 is typically used to run the Gradle daemon (Project specifies Java 21 toolchain in `gradle/gradle-daemon-jvm.properties`).
- **Android SDK Setup**:
  - Compile SDK: 36 (minorApiLevel = 1)
  - Target SDK: 36
  - Min SDK: 28 (Android 9.0 Pie)

### Build Commands
Run all Gradle commands from the repository root using the Gradle wrapper (`./gradlew`):

- **Assemble Debug APK**: `./gradlew assembleDebug` (Outputs to `app/build/outputs/apk/debug/app-debug.apk`)
- **Compile App Module**: `./gradlew :app:assembleDebug`
- **Clean Project**: `./gradlew clean`
- **Assemble Release APK**: `./gradlew assembleRelease`

*(Note: Ensure `local.properties` specifies the path to your Android SDK and `JAVA_HOME` points to a compatible JDK).*

---

## 3. Testing & Verification Workflow

The project separates host-side JVM unit tests from on-device instrumented tests.

### 3.1 Host JVM Unit Tests (Agent-friendly)
Host JVM unit tests verify pure logic without needing a device. Agents can run these directly.
- **Location**: `app/src/test/java/app/kiddos/musicplayer/`
- **Framework**: JUnit 4 (`junit:junit:4.13.2`)
- **Note**: `testOptions.unitTests.isReturnDefaultValues = true` is enabled in `app/build.gradle.kts`.

**Commands**:
- **Run all local unit tests**: `./gradlew testDebugUnitTest`
- **Run a specific test class**: `./gradlew testDebugUnitTest --tests "app.kiddos.musicplayer.MusicPlayerModelTest"`

### 3.2 Static Analysis & Formatting Checks
- **Check Lint**: `./gradlew lintDebug` (Runs Android Lint analysis on the debug variant).
*(Note: There are no dedicated detekt, ktlint, or Spotless plugins currently configured, but Kotlin official code style is enforced in `gradle.properties`).*

### 3.3 Instrumented Android Tests
Instrumented tests require a running Android device or emulator.
- **Location**: `app/src/androidTest/java/app/kiddos/musicplayer/`
- **Framework**: AndroidX Test JUnit (`1.2.1`), Espresso Core (`3.6.1`), Compose UI Test JUnit 4 (`androidx.compose.ui:ui-test-junit4`).

**Commands**:
- **Run connected Android tests**: `./gradlew connectedDebugAndroidTest`

#### Android CLI Testing & UI Automation
The project environment is equipped with Google's Android CLI (`android`).
- **Environment Status**: `android info`, `android emulator list`, `adb devices -l`
- **Automated Permission Granting**:
  ```bash
  adb shell pm grant app.kiddos.musicplayer android.permission.READ_MEDIA_AUDIO
  adb shell pm grant app.kiddos.musicplayer android.permission.POST_NOTIFICATIONS
  ```

---

## 4. Kotlin & Android Coding Conventions

### UI Layer
- **Jetpack Compose**: Use Jetpack Compose for all new UI. Follow state hoisting principles.
- **Unidirectional Data Flow (UDF)**: Composables receive immutable state (`StateFlow` collected via `.collectAsState()` or `collectAsStateWithLifecycle()`) and emit events upwards (e.g., via lambda callbacks like `onClick`).
- **Edge-to-Edge**: `MainActivity` calls `enableEdgeToEdge()`. Handle padding properly using `Modifier.statusBarsPadding()`, `Modifier.navigationBarsPadding()`, or `Scaffold`'s `innerPadding`.
- **Performance**: Use `key` parameters when rendering lists in `LazyColumn`. Use `remember` and `rememberSaveable` to avoid unnecessary recalculations.

### Concurrency & State
- **Coroutines**: Use Kotlin Coroutines for asynchronous work. Background scanning and file I/O operations MUST execute on `Dispatchers.IO` (e.g., `withContext(Dispatchers.IO)`).
- **State Updates**: UI mutations and state updates are scoped to `viewModelScope` or `Dispatchers.Main`.
- **Cancelable Background Jobs**: Cancelable background jobs must use cooperative cancellation checks (`isActive`).

### Dependency Injection & Libraries
- **Libraries**: No Dagger, Hilt, or Koin is currently set up. Use manual dependency injection (e.g., singletons like `MusicPlayerManager` with double-checked locking) and ViewModel factories if needed.
- **Version Catalog**: All new dependencies MUST be added to `gradle/libs.versions.toml` first, and then referenced in `app/build.gradle.kts` using the `libs.*` notation. Avoid hardcoding dependency versions directly inside `app/build.gradle.kts`. Use the Compose BOM (`androidx-compose-bom`) for Compose artifacts.

### Project-Specific Audio & Lifecycle Rules
- **Root Storage ("No Folder")**: Standalone audio files residing at storage roots must be grouped under `MusicFolder.NO_FOLDER_NAME` (`"No Folder"`) and sorted to the top.
- **Wake Lock & Audio Attributes**: `MediaPlayer` should be configured with `PowerManager.PARTIAL_WAKE_LOCK` and `AudioAttributes.USAGE_MEDIA`.
- **Becoming Noisy**: Always register a `BroadcastReceiver` for `AudioManager.ACTION_AUDIO_BECOMING_NOISY` to pause playback when headphones are disconnected.
- **Service Lifecycle**: Run `MusicService` as a Foreground Service with `foregroundServiceType="mediaPlayback"`.
- **Permissions & SAF Fallback**: Target API 33+ requires `READ_MEDIA_AUDIO` and `POST_NOTIFICATIONS`. Always support manual directory picking (`OpenDocumentTree`) and file selection (`OpenMultipleDocuments`) via the Storage Access Framework.

---

## 5. Agent Guardrails (Do's and Don'ts)

- **NEVER** manually edit generated files (e.g., files in `build/`, Room schemas, Dagger/Hilt generated code if added in the future).
- **ALWAYS** run the relevant unit test (`./gradlew testDebugUnitTest`) and linter tasks (`./gradlew lintDebug`) before finalizing a task and submitting.
- **String Resources & Assets**: Add UI strings to `app/src/main/res/values/strings.xml` rather than hardcoding them in Compose files. Assets should be placed in `app/src/main/assets/`.
- **Database Migrations**: If Room is added, migrations must be properly tested and schemas should be updated via Room's code generation, never manually.
- **Testing Pure Logic**: Keep business logic isolated in pure functions or testable classes without direct Android framework dependencies.
