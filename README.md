# Align — Habit, Deep Focus & Salat Tracker

**Align** is a mindful daily companion designed to help users build meaningful daily routines, maintain focus, and track spiritual consistency with zero friction. It is completely FREE!!! You don't have to pay a dime. In case you want to donate you can do it through BKASH.

[![Latest Release](https://img.shields.io/github/v/release/ahnaftaieb/Align-app?style=for-the-badge&logo=github&color=0969da)](https://github.com/ahnaftaieb/Align-app/releases/latest)
[![Download APK](https://img.shields.io/badge/Download_APK-v1.0.1-brightgreen.svg?style=for-the-badge&logo=android)](https://github.com/ahnaftaieb/Align-app/releases/latest)
[![Total Downloads](https://img.shields.io/github/downloads/ahnaftaieb/Align-app/total?style=for-the-badge&logo=googleplay&label=Downloads&color=34a853)](https://github.com/ahnaftaieb/Align-app/releases)
[![bKash Support](https://img.shields.io/badge/bKash-01716259459-e2136e?style=for-the-badge&logo=cashapp&logoColor=white)](#support-the-project)

---

## Key Features

- **Daily Habit System**:
  - Anchor habits, one-off daily missions, and milestone tracking.
  - Energy rating on completion (⚡ Energizing, ⚖️ Neutral, 🔻 Draining) to reflect on routines.
  - Streak shields to protect momentum when life gets busy.
  - Interactive GitHub-style yearly & monthly contribution heatmap.

- **Salat (Prayer) Tracker**:
  - Interactive 5×5 weekly and monthly consistency matrix (Fajr, Dhuhr, Asr, Maghrib, Isha).
  - 1-tap cycling between Jama'at (congregation), On Time, and Qada.
  - Built-in post-prayer Dhikr companion (Subhanallah 33, Alhamdulillah 33, Allahu Akbar 34) with haptic feedback.
  - **Women's Prayer Exemption (Excused Days)**:
    - Dedicated setting in the Manage tab.
    - Continuous auto-protection: marking a period as excused automatically protects all following days until you click "Resume Prayers".
    - Frictionless tap-to-resume: simply tapping any prayer tile on the resumption day automatically restores active logging.
    - Streaks bridge across excused days without resetting or falsely penalizing completion rates.

- **Deep Focus Telemetry**:
  - Focus sprint timer with interval options (25m, 45m, 90m, or custom).
  - Real-time weekly progress bar toward custom hourly goals.

- **Curriculum & Academy Modules**:
  - Structured modules with lesson completion tracking.

- **Offline-First & Cloud Sync**:
  - 100% private and works completely offline with local storage.
  - Optional Google Drive backup and local JSON export/import.

- **Firebase Cloud Messaging & Push Notifications**:
  - Heads-up system notifications for daily habit reminders and prayer alerts.
  - Distinct notification channels (`align_prayer_channel` and `align_updates_channel`) for granular user volume and vibration control.
  - Respects religious preferences: Islamic and Salat push messages (`category: "salat"`) are automatically suppressed if the user turns off the Salat Tracker Module.
  - Native runtime notification permission handling (`POST_NOTIFICATIONS` for Android 13+).
  - Background FCM token registration and test alert triggering directly from the Settings screen.

---

## Project Structure

```
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       ├── assets/              # App frontend web assets (HTML/CSS/JS)
│   │       │   ├── index.html
│   │       │   └── broadcast.json
│   │       ├── java/com/example/    # Android Kotlin activity & native bridges
│   │       └── res/                 # App launcher icons, layouts, and styles
│   └── build.gradle.kts             # App-level Android Gradle dependencies
├── broadcast.json                   # Remote announcement & daily reflection feed
├── build.gradle.kts                 # Root Gradle build configuration
├── settings.gradle.kts              # Project name & module registry
├── index.html                       # Core frontend web app interface
└── README.md
```

---

## Updating Broadcasts on GitHub

To push a new reflection or announcement to all users:
1. Edit `broadcast.json` directly on GitHub (in your `main` branch).
2. Change the `id` (e.g. `align-broadcast-002`) and update the `badge`, `title`, and `message`.
3. Set `enabled: true`.
4. Commit the change. The app will automatically fetch and display the updated reflection.

---

## Getting Started & Building the APK

### Prerequisites
- [Android Studio](https://developer.android.com/studio) (Hedgehog or newer recommended)
- JDK 17 or higher
- Android SDK (API 34 / Android 14)

### Opening the Project in Android Studio
1. Clone or download this repository.
2. Open **Android Studio** and select **Open** (choose the project root directory).
3. Allow Gradle to sync dependencies automatically.
4. Connect an Android device or launch an Android emulator.
5. Click **Run** (`Shift + F10`) or select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

---

## Support the Project

If **Align** has helped you build better routines, maintain deep focus, or stay consistent with daily habits and prayers, you can support future updates and development:

### 🇧🇩 Support via bKash

| Method | Number | Type | Reference |
| :--- | :--- | :--- | :--- |
| **bKash** | `01716259459` | Personal (Send Money) | `Align` |

> *Thank you for your support! Every contribution helps keep Align independent, ad-free, and actively developed.*

---

## License
Open source and available under the [MIT License](LICENSE).
