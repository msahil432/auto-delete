# Privacy Policy & Data Safety — Multi Tool

**Effective Date:** August 16, 2026  
**Last Updated:** August 16, 2026  

Multi Tool ("the App") is an offline-first digital wellbeing and automated file management utility developed to help users organize their storage, reduce digital distractions, and understand their screen time habits.

Your privacy is paramount. This document outlines the data practices, storage mechanisms, and permission usages of the App in accordance with Google Play Developer Policies.

---

## 1. Core Architecture: 100% On-Device & Offline

* **Local Storage Baseline:** All usage logs, app launch statistics, activity timeline events, file monitoring configurations, and browsing domain records reside strictly and exclusively on your device in the App's private SQLite/Room database (`/data/data/com.msahil432.multitool/`).
* **Zero Telemetry / Zero Exfiltration:** The App does **not** transmit any telemetry, analytics, personal identifiers, usage patterns, or monitoring data off your device.
* **No Third-Party Trackers:** The App contains no advertising SDKs, no cloud tracking SDKs, no analytics services (e.g. Firebase Analytics, Google Analytics), and no remote crash reporting services that collect user information.
* **No Cloud Accounts Required:** The App requires no user registration, email, phone number, login, or cloud authentication.
* **Purge on Uninstall:** When you uninstall the App, all stored data, custom profiles, usage history, and configuration files are permanently and automatically deleted by Android.

---

## 2. Sensitive Permissions & Purpose Disclosures

The App utilizes specific sensitive Android permissions solely for executing core user-facing functionality. Each permission is accompanied by an in-app prominent disclosure before requesting system authorization.

### 2.1 Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`)
* **Purpose:** Enables real-time detection of the foreground application to enforce user-configured focus blocking rules, session limits, and short-form video filters (such as YouTube Shorts and Instagram Reels).
* **Data Handling:** The Accessibility Service inspects only the foreground package name and minimal UI identifiers necessary to identify distraction features. It **never** logs keystrokes, reads private chat message contents, captures passwords, or captures screen recordings. All evaluations occur in memory on the device and are never sent off the device.

### 2.2 Usage Stats Access (`PACKAGE_USAGE_STATS`)
* **Purpose:** Queries standard Android system usage statistics via `UsageStatsManager` to aggregate daily screen time, app launch counts, and display the chronological activity timeline.
* **Data Handling:** Usage data is read from the local operating system and stored locally in the App's database. No usage analytics are uploaded to any server.

### 2.3 Device Administrator (`BIND_DEVICE_ADMIN`)
* **Purpose:** Provides anti-uninstall protection during an active strict-mode focus session. This prevents impulsive uninstallation of the App to bypass active focus locks.
* **Data Handling:** The App declares empty policy requirements (`<uses-policies />`) and does not use Device Administrator privileges for remote wiping, password enforcement, device encryption, or any other device management features.

### 2.4 Notification Listener Service (`BIND_NOTIFICATION_LISTENER_SERVICE`)
* **Purpose:** Silences notifications originating from restricted apps during active focus schedules and stores held notifications in a local "Notification Vault" to be delivered as a single digest once the focus session ends.
* **Data Handling:** Intercepted notification metadata (package name, post timestamp, encrypted/held summary) is stored in the local SQLite database. Notifications are never shared, uploaded, or exposed to third parties.

### 2.5 Location Services (`ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`)
* **Purpose:** Powers geofenced focus profiles (e.g., automatically activating a "Work" or "Library" focus group upon arrival at a designated geographic radius).
* **Data Handling:** Geofences are evaluated locally using Android Geofencing APIs. Geographic coordinates (latitude/longitude) configured by the user are stored only on the device and are never transmitted to any remote location server or tracking service.

### 2.6 All Files Access (`MANAGE_EXTERNAL_STORAGE`)
* **Purpose:** Core file management functionality — allows the App to monitor user-designated folders (such as Screenshots and Downloads) and execute scheduled automatic trash/delete actions to keep device storage clean.
* **Data Handling:** File operations are performed locally using standard file system APIs. File contents are never indexed, read, uploaded, or transmitted off-device.

### 2.7 Camera (`CAMERA`)
* **Purpose:** Used optionally for Strict Mode friction challenges (e.g., photo-based unlock verification tasks).
* **Data Handling:** Camera frames are used temporarily for verification tasks in real time and are not stored permanently or shared.

---

## 3. Google Play Data Safety Summary

| Data Type | Collected? | Shared? | Stored Locally? | Ephemeral / User-Controlled Deletion |
| :--- | :--- | :--- | :--- | :--- |
| **Personal Info (Name, Email, Phone, ID)** | No | No | No | N/A |
| **Location (Precise / Approximate)** | No | No | Yes (User geofences only) | Deleted on app uninstall |
| **App Activity & Usage Data** | No | No | Yes (On-device database) | Deleted on app uninstall or manual reset |
| **Web Browsing & Search Queries** | No | No | Yes (On-device browser log if enabled) | User can clear log at any time |
| **Files & Documents** | No | No | Yes (Local folders monitored) | User-managed |
| **Photos & Videos** | No | No | No | N/A |
| **Messages & Communications** | No | No | No | N/A |
| **Financial / Payment Info** | No | No | No | N/A |
| **Device or Other IDs** | No | No | No | N/A |

---

## 4. User Rights & Data Management

* **Clear Data / Reset:** Users can clear all recorded browsing activity, notification vault items, or reset configurations at any time from within the App's settings or Android System App Settings.
* **Revoke Permissions:** Any permission granted to Multi Tool can be revoked at any time in Android Settings (`Settings > Apps > Multi Tool > Permissions / Special App Access`).
* **Complete Deletion:** Uninstalling Multi Tool removes all application databases, preferences, and cached assets completely.

---

## 5. Changes to This Privacy Policy

We may update this privacy policy periodically to reflect updates in app features or Android platform requirements. The latest version will always be published in the application documentation and repository.

## 6. Contact

For questions or feedback regarding this Privacy Policy, please open an issue in the official project repository.
