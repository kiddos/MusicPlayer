# Privacy Policy

**Last Updated:** October 21, 2026

**Music Player** (`app.kiddos.musicplayer`) is an offline Android application designed for organizing and playing local audio files on your device. We respect your privacy and are committed to being transparent about how the application operates.

---

## 1. Summary

- **No Personal Data Collection:** Music Player does **not** collect, store, transmit, or share any personal information or usage data.
- **100% Offline Operation:** The application does **not** request internet permission (`android.permission.INTERNET`) and cannot connect to external servers.
- **No Ads or Trackers:** There are no third-party advertising SDKs, analytics trackers, or crash-reporting services integrated into the app.

---

## 2. Information Collection and Use

### 2.1 Local Audio Files and Metadata
To function as a music player, the application scans and reads audio files stored locally on your device using Android's `MediaStore` API or the Storage Access Framework (SAF) when you manually select folders or files.
- Metadata such as song titles, artist names, album names, durations, and directory paths are read and processed **strictly in memory on your device** to display your music library and play audio.
- None of your audio files, folder structures, or metadata ever leave your device.

### 2.2 Local App Preferences
The application stores minimal configuration settings locally on your device using Android `SharedPreferences` (`music_player_prefs`), specifically:
- **Language Preference (`selected_language`):** Remembers your chosen display language (System Default, English, or Traditional Chinese).

If Android Auto Backup is enabled on your device (`android:allowBackup="true"`), your device operating system may back up these basic app preferences according to your device's system backup settings.

---

## 3. Device Permissions

Music Player only requests the minimum permissions necessary to play local audio and provide media controls:

| Permission | Purpose |
| :--- | :--- |
| `android.permission.READ_MEDIA_AUDIO` *(Android 13+)* | Allows the app to discover and play audio files stored on your device. |
| `android.permission.READ_EXTERNAL_STORAGE` *(Android 12 and below, max SDK 32)* | Allows the app to read audio files on older versions of Android. |
| `android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Allows audio playback to continue in the background while you use other apps or lock your screen. |
| `android.permission.POST_NOTIFICATIONS` | Displays playback controls (Play, Pause, Next, Previous) in the system notification shade and lock screen. |
| `android.permission.WAKE_LOCK` | Prevents the device processor from sleeping while a track is actively playing so playback is not interrupted. |

You can revoke storage or notification permissions at any time through your device's system settings. Even without broad storage permissions, you can still play specific folders or audio files using Android's system file picker (Storage Access Framework).

---

## 4. Data Sharing and Third-Party Services

Because Music Player does not collect or transmit any data:
- We do **not** sell, trade, or transfer any user data to third parties.
- We do **not** use any third-party analytics, cloud services, or advertising networks.

---

## 5. Children's Privacy

Music Player does not knowingly or unknowingly collect personal information from anyone, including children under the age of 13. The app is completely offline and safe for users of all ages.

---

## 6. Changes to This Privacy Policy

We may update our Privacy Policy from time to time if new features are introduced. Any changes will be reflected by updating the "Last Updated" date at the top of this document. You are advised to review this page periodically for any changes.

---

## 7. Contact Us

If you have any questions or concerns about this Privacy Policy or the application's privacy practices, please open an issue in the project repository or contact the developer directly.
