# 20 — Friction Unlock Challenges

> **Status:** ✅ Complete

Prerequisites: `19-strict-mode-controller.md`, `25-design-system.md`.

## Goal

Implement the high-friction unlock challenges required to deactivate strict mode or
bypass a block: long text match, PIN/master password, enforced cooldown delay, and
physical QR-code scan.

## Implementation Decisions

1. **Salted Password Hashing (`PasswordSecurity`)**:
   - Implemented PBKDF2 with SHA-256 (10,000 iterations) using 16-byte secure random salts.
   - Stored in DataStore as `<saltHex>:<hashHex>`. Plaintext passcodes are never persisted or logged.
   - Verification uses `MessageDigest.isEqual` for constant-time comparison against timing attacks.

2. **Paste Prevention in Text Match (`TextMatchChallenge`)**:
   - Rejects bulk text additions where `newValue.length > oldValue.length + 1` in `onValueChange`, enforcing character-by-character typing.
   - Hides live progress / tolerance indicators per zero-tolerance specification; checks match on submit.

3. **PIN Lockout Rate-Limiting (`PinChallenge`)**:
   - Limits failed attempts to 5 before triggering a 30-second lockout timer.

4. **Persistent Cooldown Delay (`CooldownChallenge`)**:
   - Stores `strict_pending_deactivation_at` in DataStore via `StrictModeController.requestDeactivation(cooldownMinutes)`.
   - "Cancel" invokes `StrictModeController.cancelPendingDeactivation()` to safely reset the request while keeping strict mode active.

5. **CameraX & ML Kit Integration (`QrChallenge`)**:
   - Enabled CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-core`) and `com.google.mlkit:barcode-scanning`.
   - Added runtime `CAMERA` permission check, viewfinder reticle, and torch/flashlight toggle.

6. **Host Routing (`ChallengeHost`)**:
   - Container composable routing to the active challenge method and executing `StrictModeController.completeDeactivation()` upon verified success.

## Dependencies

- QR scanning uses **ML Kit barcode scanning** + CameraX. Add to the version catalog
  and `app/build.gradle.kts`:
  - `com.google.mlkit:barcode-scanning`
  - CameraX: `androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view`
    (catalog entries enabled).
- Added `CAMERA` permission (runtime) to the manifest.

## Files created / modified

- Created `util/PasswordSecurity.kt`
- Created `ui/screens/challenge/TextMatchChallenge.kt`
- Created `ui/screens/challenge/PinChallenge.kt`
- Created `ui/screens/challenge/CooldownChallenge.kt`
- Created `ui/screens/challenge/QrChallenge.kt`
- Created `ui/screens/challenge/ChallengeHost.kt`
- Modified `blocking/StrictModeController.kt`
- Modified `ui/screens/StrictModeScreen.kt`
- Modified `ui/navigation/BottomNavScaffold.kt`
- Modified `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`
- Created `util/PasswordSecurityTest.kt`, updated `StrictModeControllerTest.kt`

## Challenge specs

- **Text match:** generate a random alphanumeric string of configured length
  (100–1000+). Show it; user must retype exactly (zero tolerance). Compare on submit;
  on mismatch, regenerate. No copy/paste (disable clipboard on the field).
- **PIN / master password:** user enters the passcode; compare against a **salted
  hash** (e.g., PBKDF2/`MessageDigest` with random salt stored alongside). Never
  store or log plaintext. Rate-limit attempts.
- **Cooldown:** on request, set `strict_pending_deactivation_at = now + cooldown`.
  Show a live countdown; deactivation only completes after the timer elapses AND the
  user confirms. Closing the app doesn't reset the timer (persisted in DataStore).
- **QR scan:** open CameraX preview + ML Kit `BarcodeScanning`; compare the decoded
  value to `qr_expected_value`. Success only on exact match. This enables the
  "printed code in another room" use case.

## UX / Screen Design

- Full-screen challenge with clear title, instructions, and progress/feedback.
- **Text:** monospace prompt block; input field; live "x/N correct" is NOT shown
  (zero tolerance) — just success/fail on submit; "Regenerate" not offered (defeats
  friction).
- **PIN:** secure text field; attempt counter; lockout after N failures.
- **Cooldown:** big countdown timer; "Cancel" returns to active strict mode.
- **QR:** camera preview with a framing reticle; permission-needed state if camera
  denied; torch toggle.
- States for all: idle / in-progress / success / failure. Use design-system
  components; error uses error color.
- Accessibility: inputs labeled; countdown announced periodically; camera screen has
  a text alternative explaining what to do.
- Dark mode: theme-driven.

## Acceptance criteria

- Each configured method gates deactivation; only success calls
  `completeDeactivation()`.
- Master password is stored hashed+salted; plaintext never persisted or logged.
- Cooldown timer persists across app restarts and cannot be skipped.
- QR requires the exact expected value; wrong codes are rejected.
- Text challenge requires an exact full-length match.

## Out of scope / boundaries

- Never bypass emergency dialer. Do not use these to lock the user out of system
  recovery.