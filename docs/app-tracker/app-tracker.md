# Product & Technical Specification: Digital Wellbeing & Focus Enforcement Engine
  
**Document Type:** Technical System Specification & Product Requirements Document (PRD)  
**Architecture:** Client-Side Daemon & Local Policy Enforcement  

---

## 1. Executive Summary

This specification outlines the functional, technical, data tracking, and boundary requirements for an Android digital wellbeing, self-control, and app enforcement platform. The architecture combines passive system telemetry with active overlay interception, UI hierarchy parsing, and anti-bypass mechanisms to eliminate digital distractions and enforce behavioral friction.


+-----------------------------------------------------------------------------------+
|                                 APPLICATION CORE                                  |
+------------------------------------+----------------------------------------------+
|        INTERCEPTION ENGINE         |              TRACKING ENGINE                 |
|  - System Alert Window Overlays    |  - Foreground App Duration (UsageStats)      |
|  - Accessibility Node Parsing      |  - App Launch Counts & Screen Unlocks        |
|  - Package Intent Interception     |  - Activity Timelines & Geofence Transitions |
+------------------------------------+----------------------------------------------+
|                              STRICT MODE CONTROLLER                               |
|  - Anti-Uninstall (Device Admin)   - Friction Challenges (Typing, QR, Cooldown)   |
|  - Rule Tightening Lock-in         - Audible Siren Tamper Trigger                 |
+-----------------------------------------------------------------------------------+


---

## 2. Core Functional Modules

### 2.1 Application Blocking Engine
* **Target Scopes:** Single package identification, custom multi-app groupings, or pre-categorized clusters (e.g., Social, Gaming, Video Streaming).
* **Enforcement Triggers:**
  * **Time Schedules:** Recurring weekly calendar matrices (e.g., Mon–Fri, 08:00–17:00).
  * **Daily Usage Quotas:** Cumulative foreground usage limits per 24-hour cycle (e.g., 30 minutes total across selected targets).
  * **Launch Frequency Limits:** Hourly or daily launch caps (e.g., max 5 opens per day).
  * **Continuous Session Duration:** Micro-session limits (e.g., 10 minutes maximum per launch followed by a mandatory 15-minute lockout).
  * **Goal-Based Unlocking:** Reciprocal dependencies requiring $N$ minutes of designated productive app usage before unlocking target recreational apps.

### 2.2 Short-Form Video (In-App Sub-Feature) Blocker
* **Granular Target Filtering:** Isolates and restricts short-form video UI feeds (such as YouTube Shorts and Instagram/Facebook Reels) while preserving access to main search and standard long-form feeds.
* **UI Node Interception:** Detects layout resource identifiers and UI accessibility node signatures unique to short-form player layouts, terminating or navigating away from the sub-activity upon launch.

### 2.3 Geofenced & Contextual Activation
* **Spatial Triggers:** Automated profile activation based on entry or exit from defined circular geofences (latitude, longitude, radius).
* **State Machine:**
  * `ENTER_REGION`: Trigger focus profile (e.g., restrict distraction apps when arriving at the workplace).
  * `EXIT_REGION`: Restore default baseline profile or trigger alternate location profiles.

### 2.4 Strict Mode & Anti-Tamper Enforcement
Strict Mode mitigates impulsive override and habit relapse through high-friction unlock mechanisms:
* **Asymmetric Rule Modification:** Active focus rules can be made stricter (adding blocked apps, extending lock duration) but cannot be reduced, edited, or deleted during an active session.
* **Unlock Challenges:**
  * **High-Volume Text Match:** Requires typing a complex, randomized alphanumeric string (100–1,000+ characters) with zero error tolerance.
  * **PIN / Master Password:** Requires an administrative passcode (configured by an accountability partner).
  * **Enforced Cooldown Delay:** Imposes a mandatory waiting period (e.g., 10–60 minutes) between requesting deactivation and actual deactivation.
  * **Physical QR Code Scanning:** Requires optical scanning of a printed QR code positioned in a designated remote physical location.
  * **Tamper Alarm:** Triggers an audible siren if protected settings or permission management screens are accessed during an active block.

### 2.5 Interruption Management (Notification Blocker)
* **Push Notification Interception:** Silences and dismisses real-time push banners from restricted apps during active focus schedules.
* **Notification Vault:** Buffers held notifications locally and delivers a consolidated digest once the restriction schedule lapses.

---

## 3. Data Tracking & Telemetry Specifications

| Tracked Metric | Metric Granularity | Primary Android API |
| :--- | :--- | :--- |
| **Foreground Screen Time** | Per-package active runtime in milliseconds | `UsageStatsManager` / `UsageEvents` |
| **App Launch Invocations** | Exact count of app launches per day/hour | `UsageEvents.Event.ACTIVITY_RESUMED` |
| **Device Unlock Frequency** | Screen unlock timestamps and cumulative counts | `ACTION_USER_PRESENT` / `ACTION_SCREEN_ON` |
| **Activity Timeline** | Chronological audit trail of app switches and durations | Event timestamp logging (`Room` / `SQLite`) |
| **Active Browser URLs** | Domain names and full URLs entered into address bars | `AccessibilityNodeInfo` hierarchy parsing |
| **Search Engine Queries** | Raw text entered into browser search bars | `AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED` |
| **Geofence State** | Entry/exit timestamps and coordinates | `FusedLocationProviderClient` |
| **Block Interceptions** | Timestamps and target package of blocked launch attempts | Overlay dispatch engine |

---

## 4. Technical Boundaries & Non-Capabilities

| Domain | Boundary / Limitation | Root Technical Cause |
| :--- | :--- | :--- |
| **Network Payloads** | Cannot inspect encrypted HTTPS network packets | Android TLS/SSL termination sandboxing prevents Man-in-the-Middle packet inspection without custom root certificates. |
| **Private Chat Contents** | Cannot log end-to-end encrypted messaging logs | Sandboxed app storage; reading conversation trees inside third-party apps is restricted and violates OS privacy rules. |
| **Passwords & Credentials** | Cannot read passcodes or sensitive financial forms | `AccessibilityService` nodes flagged with `TYPE_TEXT_VARIATION_PASSWORD` are automatically masked at the OS layer. |
| **Emergency Services** | Cannot block emergency calling or recovery alerts | System safety overrides prevent interception of emergency numbers (911, 112, 999) or telecom call screens. |
| **Safe Mode & ADB Removal** | Cannot prevent uninstall via Android Safe Mode or USB Debugging | System-level recovery modes and `adb uninstall` bypass standard client-side `DeviceAdmin` protections. |
| **Hardware Powered Off** | Cannot track telemetry while device is powered down | Background processes terminate entirely upon shutdown; tracking resumes only after `BOOT_COMPLETED`. |
| **Remote Cloud Surveillance** | Does not operate a remote live monitoring feed | Data capture runs client-side without streaming live device screens or usage logs to remote servers. |

---

## 5. Android API & Permissions Matrix

| Android Permission / API | Level / Group | Technical Purpose |
| :--- | :--- | :--- |
| `PACKAGE_USAGE_STATS` | Special App Access | Accesses foreground runtimes, launch events, and daily screen time statistics. |
| `BIND_ACCESSIBILITY_SERVICE` | Accessibility Framework | Parses UI node hierarchies to detect browser URLs, block keywords, and filter sub-app views (Shorts/Reels). |
| `SYSTEM_ALERT_WINDOW` | Overlay (`Draw over other apps`) | Displays immediate blocking overlay screens over restricted apps. |
| `BIND_DEVICE_ADMIN` | Device Administration | Enables anti-uninstall protection by preventing standard package removal during active focus. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Notification Access | Intercepts, silences, and batches notifications originating from restricted applications. |
| `ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` | Location Services | Powers geofenced location-based blocking profiles (work/study coordinates). |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Power Management | Prevents Android Doze Mode and OEM task killers from terminating background services. |
| `RECEIVE_BOOT_COMPLETED` | Broadcast Receiver | Automatically starts background monitoring services when the smartphone reboots. |
| `FOREGROUND_SERVICE` | Service Architecture | Maintains a persistent foreground service with a persistent notification to prevent process death. |

---

## 6. Implementation Challenges & OS Mitigations

### 6.1 OEM Background Task Termination
* **Issue:** Aggressive vendor battery managers (e.g., Xiaomi MIUI/HyperOS, Samsung One UI, Huawei EMUI) terminate background services after screen-off events.
* **Mitigation Strategy:**
  * Bind a persistent `ForegroundService` with an ongoing system notification.
  * Direct users to explicitly whitelist the app via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
  * Provide visual onboarding flows guiding users through OEM-specific "Auto-start" and "App Lock" system screens.

### 6.2 Google Play Store Policy Compliance
* **Accessibility Service Declaration:** Must implement an in-app prominent disclosure dialog *before* redirecting the user to system Accessibility Settings, explicitly stating that accessibility is used strictly for digital wellbeing/deterministic UI-blocking automation.
* **Device Administration Disclosure:** Must explicitly declare that `DeviceAdmin` permission is used solely to prevent premature uninstallation during active strict mode sessions.

---

## 7. Data Storage & Privacy Architecture

* **Local Storage Baseline:** All usage logs, app launch events, timeline history, and URL records reside locally in a structured database (`Room` / `SQLite`) inside the application’s protected private directory (`/data/data/<package_name>/`).
* **Zero Telemetry Exfiltration:** Application telemetry and browsing metrics are not transmitted off-device to external analytics or marketing platforms.
* **Purge on Uninstall:** All historical tracking data and profile rules are permanently destroyed upon application uninstallation.

---

## 8. Capability Comparison Matrix

+-------------------------------------------------------------+--------------+
| Capability / Feature                                        | Supported?   |
+-------------------------------------------------------------+--------------+
| Native Android App Blocking                                 | YES (Full)   |
| YouTube Shorts & Instagram Reels Specific Blocking          | YES (Full)   |
| Keyword Search Filtering                                    | YES (Full)   |
| Daily & Hourly Screen Time Quotas                           | YES (Full)   |
| App Launch Count Limiters                                   | YES (Full)   |
| Geofenced Location-Based Profiles                           | YES (Full)   |
| Anti-Uninstall via Device Administrator                     | YES (Full)   |
| Strict Mode Multi-Friction Unlock Challenges                | YES (Full)   |
| Chronological Activity Timeline Logging                     | YES (Full)   |
| Screen Unlock Pattern & Frequency Tracking                  | YES (Full)   |
| Notification Interception & Silencing                       | YES (Full)   |
| HTTPS Packet Payload / SSL Decryption                       | NO (Blocked) |
| Reading End-to-End Encrypted Message Contents (WhatsApp/etc)| NO (Blocked) |
| Password & Keystroke Logging                                | NO (Blocked) |
| Remote Surveillance Dashboard / Cloud Spyware               | NO (Blocked) |
| Blocking Emergency Calls (911 / 112)                        | NO (Blocked) |
| Circumvention Prevention in Android Safe Mode / ADB         | NO (Blocked) |
+-------------------------------------------------------------+--------------+
