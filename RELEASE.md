# Release Notes

## Version 1.0.0

Initial release of **Music Player**, a clean, lightweight, directory-based audio player for local music files on Android.

### Key Features
- **Folder-Based Organization**: Easily browse and play local audio organized by device directories and root storage.
- **Modern Jetpack Compose UI**: Fast and intuitive Material Design 3 interface with dynamic theming.
- **Dual Playback Interfaces**:
  - Persistent **Mini Player** for quick access across screens.
  - Expanding **Full Player Sheet** with playback controls, progress slider, and repeat modes (Off, All, One).
- **Background Playback**: Seamless audio playback using an Android Foreground Service with lock screen and notification media controls.
- **Audio Becoming Noisy Handling**: Automatically pauses playback when headphones or external audio devices are disconnected.
- **Flexible Storage Access**: Scans via Android `MediaStore` with Storage Access Framework (SAF) folder picker support.
