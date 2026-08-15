# 20 — Friction Unlock Challenges

> **Status:** 🔲 Not Started

Prerequisites: `19-strict-mode-controller.md`, `25-design-system.md`.

## Goal

Implement the high-friction unlock challenges required to deactivate strict mode or
bypass a block: long text match, PIN/master password, enforced cooldown delay, and
physical QR-code scan.

## Dependencies

- QR scanning uses **ML Kit barcode scanning** + CameraX. Add to the version catalog
  and `app/build.gradle.kts`:
  - `com.google.mlkit:barcode-scanning`
  - CameraX: `androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view`
    (catalog entries already exist, currently commented — enable them).
- Add `CAMERA` permission (runtime) to the manifest.

## Files to create / modify

- Create `ui/screens/challenge/TextMatchChallenge.kt`
- Create `ui/screens/challenge/PinChallenge.kt`
- Create `ui/screens/challenge/CooldownChallenge.kt`
- Create `ui/screens/challenge/QrChallenge.kt`
- Create `ui/screens/challenge/ChallengeHost.kt` — routes to the configured method
  and calls `StrictModeController.completeDeactivation()` on success.
- Settings/DataStore: `master_password_hash` (store a salted hash, never plaintext),
  `qr_expected_value`, `cooldown_minutes`, `text_challenge_length`.

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