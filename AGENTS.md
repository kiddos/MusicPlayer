# AGENTS.md

This document provides essential instructions, architectural reference, build/test commands, and coding conventions for AI agents and developers working on the **Music Player** Android project.

---

## 1. Project Overview & Architecture

**Music Player** is a modern, lightweight Android music player designed specifically for organizing and playing local audio tracks (primarily MP3s) grouped by directory structure. It is built using modern Android architecture components:
- **Language**: Kotlin (2.2.x)
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel) with Unidirectional Data Flow (UDF)
- **State Management**: Kotlin Coroutines & `StateFlow`
- **Audio Engine**: Android `MediaPlayer` encapsulated in a singleton manager with a `ForegroundService` for background playback and lockscreen media notification controls
- **Storage Access**: Dual-mode scanning using Android `MediaStore` (API 29+ scoped storage support) and Storage Access Framework (SAF) folder/file pickers

### Architecture Layers

```
app.kiddos.musicplayer
├── model/                 # Domain models and enums (AudioFile, MusicFolder, RepeatMode)
├── data/                  # Storage scanning & metadata extraction (MusicScanner)
├── playback/              # Low-level audio controller singleton (MusicPlayerManager)
├── service/               # Background playback & notifications (MusicService)
├── ui/                    # Presentation layer
│   ├── components/        # Reusable UI widgets (MiniPlayer, FullPlayerSheet)
│   ├── screens/           # Main screens (FolderListScreen, SongListScreen)
│   ├── theme/             # Material 3 styling (Color, Type, Theme)
│   ├── MusicApp.kt        # Root composable with permission handling & scaffold
│   └── MusicViewModel.kt  # ViewModel coordinating playback, scanning, and UI state
└── MainActivity.kt        # Single activity host (edge-to-edge enabled)
```

---

## 2. Build System

The project uses **Gradle** with **Kotlin DSL (`.kts`)** and the **Gradle Version Catalog** (`gradle/libs.versions.toml`).

### Key Specifications
- **Gradle Version**: 9.3.1 (configured in `gradle/wrapper/gradle-wrapper.properties`)
- **Android Gradle Plugin (AGP)**: 9.1.1
- **Kotlin Version**: 2.2.10 (with `org.jetbrains.kotlin.plugin.compose`)
- **JDK / Toolchain**: Java 21 recommended (configured in `gradle/gradle-daemon-jvm.properties`), compatibility set to Java 11
- **Compile SDK**: 36 (minorApiLevel = 1)
- **Target SDK**: 36
- **Min SDK**: 28 (Android 9.0 Pie)
- **Namespace / Application ID**: `app.kiddos.musicplayer`

### Common Build Commands

Run all Gradle commands from the repository root using the Gradle wrapper (`./gradlew`):

| Task | Command | Description |
|---|---|---|
| **Assemble Debug APK** | `./gradlew assembleDebug` | Compiles and outputs `app/build/outputs/apk/debug/app-debug.apk` |
| **Assemble Release APK**| `./gradlew assembleRelease` | Compiles unsigned release APK |
| **Build Project** | `./gradlew build` | Assembles and runs all check tasks |
| **Clean Build** | `./gradlew clean` | Deletes build outputs across all modules |
| **Check Dependencies** | `./gradlew dependencies app:dependencies` | Inspects the dependency resolution graph |
| **Check Lint** | `./gradlew lintDebug` | Runs Android Lint analysis |

*Note: Ensure `local.properties` specifies the path to your Android SDK (`sdk.dir=/path/to/Android/Sdk`) and `JAVA_HOME` points to a compatible JDK (Java 17 or 21).*

---

## 3. Test Commands & Frameworks

The project separates host-side JVM unit tests from on-device instrumented tests.

### 3.1 Host JVM Unit Tests
- **Location**: `app/src/test/java/app/kiddos/musicplayer/`
- **Framework**: JUnit 4 (`junit:junit:4.13.2`)
- **Key Test Classes**:
  - `MusicPlayerModelTest.kt`: Tests domain logic, `RepeatMode` cycling, `AudioFile` formatting, `MusicScanner.resolveFolderInfo`, and folder grouping / root storage ("No Folder") handling.
  - `ExampleUnitTest.kt`: Standard baseline unit test.

#### Run Unit Tests:
```bash
# Run all host unit tests
./gradlew testDebugUnitTest

# Run all unit tests for all variants
./gradlew test

# Run a specific test class
./gradlew testDebugUnitTest --tests "app.kiddos.musicplayer.MusicPlayerModelTest"

# Run a specific test method
./gradlew testDebugUnitTest --tests "app.kiddos.musicplayer.MusicPlayerModelTest.repeatMode_cyclesCorrectly"
```

#### Test Reports:
HTML test reports are generated at:
`app/build/reports/tests/testDebugUnitTest/index.html`

### 3.2 Instrumented Android Tests
- **Location**: `app/src/androidTest/java/app/kiddos/musicplayer/`
- **Runner**: `androidx.test.runner.AndroidJUnitRunner`
- **Framework**: AndroidX Test JUnit (`1.2.1`), Espresso Core (`3.6.1`), Compose UI Test JUnit 4 (`composeBom`)

#### Run Connected Tests:
```bash
# Run instrumented tests on an attached device or emulator
./gradlew connectedDebugAndroidTest
```

---

## 4. Directory Structure

```
MusicPlayer/
├── .gitignore                          # Root Git ignore rules
├── build.gradle.kts                    # Root build configuration (plugins declaration)
├── settings.gradle.kts                 # Repositories, toolchain convention & module inclusions
├── gradle.properties                   # JVM memory args and Kotlin official code style flag
├── gradlew / gradlew.bat               # Gradle wrapper executable scripts
├── local.properties                    # Local SDK configuration (git-ignored)
├── gradle/
│   ├── gradle-daemon-jvm.properties    # Toolchain configuration (Java 21)
│   ├── libs.versions.toml              # Centralized version catalog for dependencies & plugins
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── app/
    ├── .gitignore
    ├── build.gradle.kts                # App module configuration, SDK targets, dependencies
    ├── proguard-rules.pro              # ProGuard / R8 code shrinking rules
    └── src/
        ├── androidTest/java/app/kiddos/musicplayer/
        │   └── ExampleInstrumentedTest.kt
        ├── test/java/app/kiddos/musicplayer/
        │   ├── ExampleUnitTest.kt
        │   └── MusicPlayerModelTest.kt
        └── main/
            ├── AndroidManifest.xml      # Permissions, Activity, Foreground Service
            ├── java/app/kiddos/musicplayer/
            │   ├── MainActivity.kt      # Single activity entry point
            │   ├── model/
            │   │   ├── AudioFile.kt     # Track metadata data class & formatting
            │   │   └── MusicFolder.kt   # Folder data class & RepeatMode enum
            │   ├── data/
            │   │   └── MusicScanner.kt  # MediaStore & SAF audio file scanner
            │   ├── playback/
            │   │   └── MusicPlayerManager.kt # Singleton audio controller
            │   ├── service/
            │   │   └── MusicService.kt  # Media foreground service & notification
            │   └── ui/
            │       ├── MusicApp.kt      # Root scaffold & permission flows
            │       ├── MusicViewModel.kt # Presentation ViewModel
            │       ├── components/
            │       │   ├── MiniPlayer.kt       # Persistent bottom mini player
            │       │   └── FullPlayerSheet.kt  # Expanding modal player sheet
            │       ├── screens/
            │       │   ├── FolderListScreen.kt # Directory browser
            │       │   └── SongListScreen.kt   # Track browser for folder
            │       └── theme/
            │           ├── Color.kt
            │           ├── Theme.kt
            │           └── Type.kt
            └── res/
                ├── drawable/            # Vector graphics & drawables
                ├── mipmap-*/            # App launcher icons
                ├── values/              # strings.xml, colors.xml, themes.xml
                └── xml/                 # backup_rules.xml, data_extraction_rules.xml
```

---

## 5. Coding Conventions & Best Practices

When modifying or extending this codebase, adhere to the following principles:

### 5.1 Kotlin Style & Formatting
- **Code Style**: Adhere to `kotlin.code.style=official` (configured in `gradle.properties`).
- **Naming Conventions**:
  - `PascalCase` for Composable functions (`MiniPlayer`, `FolderCard`), classes, interfaces, and singletons.
  - `camelCase` for functions, properties, and parameters.
  - `UPPER_SNAKE_CASE` for constants (`CHANNEL_ID`, `NOTIFICATION_ID`, `NO_FOLDER_NAME`).
  - Private backing fields prefix with underscore: `_folders` (MutableStateFlow) and `folders` (StateFlow).

### 5.2 Dependency Management
- **Centralized Dependencies**: All libraries and plugins MUST be registered in `gradle/libs.versions.toml`. Avoid hardcoding dependency versions directly inside `app/build.gradle.kts`.
- Prefer using the Compose BOM (`androidx-compose-bom`) for Compose-related artifacts.

### 5.3 Jetpack Compose & UI Guidelines
- **Unidirectional Data Flow**:
  - Composables receive immutable state (`StateFlow` collected via `.collectAsState()`) and emit lambda events (`onClick`, `onPlayPause`).
  - Avoid passing `ViewModel` instances deep down the Composable hierarchy; pass values and callback lambdas instead.
- **Edge-to-Edge & System Bars**:
  - `MainActivity` calls `enableEdgeToEdge()`.
  - Always handle padding properly using `Modifier.statusBarsPadding()`, `Modifier.navigationBarsPadding()`, or `Scaffold`'s `innerPadding`.
- **Performance**:
  - Use `key` parameters when rendering lists in `LazyColumn` (`items(folders, key = { it.path })`).
  - Use `remember` and `rememberSaveable` to avoid unnecessary recalculations during recomposition.

### 5.4 State & Concurrency
- **Coroutines**:
  - Background scanning and file I/O operations MUST execute on `Dispatchers.IO` (e.g., `withContext(Dispatchers.IO)` in `MusicScanner`).
  - UI mutations and state updates are scoped to `viewModelScope` or `Dispatchers.Main`.
  - Cancelable background jobs (such as progress tickers) must use cooperative cancellation checks (`isActive`).
- **Singletons**:
  - `MusicPlayerManager` uses thread-safe double-checked locking with `@Volatile private var instance`.

### 5.5 Android Permissions & Storage Compatibility
- **Permissions**:
  - Target API 33+ (Tiramisu) requires `READ_MEDIA_AUDIO` and `POST_NOTIFICATIONS`.
  - Legacy Android versions (<= API 32) fallback to `READ_EXTERNAL_STORAGE`.
- **SAF Fallback**:
  - Always support manual directory picking (`OpenDocumentTree`) and file selection (`OpenMultipleDocuments`) via the Storage Access Framework for directories not automatically indexed by `MediaStore`.
- **Root Storage ("No Folder")**:
  - Standalone audio files residing at storage roots (e.g. `/sdcard/song.mp3` or `/storage/emulated/0/song.mp3`) must be grouped under `MusicFolder.NO_FOLDER_NAME` (`"No Folder"`) and sorted to the top of the folder list.

### 5.6 Audio & Lifecycle Management
- **Wake Lock & Audio Attributes**:
  - `MediaPlayer` should be configured with `PowerManager.PARTIAL_WAKE_LOCK` and `AudioAttributes.USAGE_MEDIA`.
- **Becoming Noisy**:
  - Always register a `BroadcastReceiver` for `AudioManager.ACTION_AUDIO_BECOMING_NOISY` in `MusicService` to automatically pause playback when headphones are disconnected.
- **Service Lifecycle**:
  - Run `MusicService` as a Foreground Service with `foregroundServiceType="mediaPlayback"` whenever audio is actively playing.

---

## 6. Testing Conventions

- **Unit Testing Pure Logic**: Keep business logic and data manipulation (such as duration formatting, folder grouping, path resolution) isolated in pure functions or testable classes without direct Android framework dependencies.
- **Android Mocking**: `testOptions.unitTests.isReturnDefaultValues = true` is enabled in `app/build.gradle.kts`. For components requiring Android context (like `ContentResolver`), isolate the scanning algorithms to accept standard data structures or test them with instrumented tests.
- When creating new domain models or utility methods, accompany them with corresponding unit tests in `app/src/test/java/app/kiddos/musicplayer/`.
