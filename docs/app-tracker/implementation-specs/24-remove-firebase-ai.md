# 24 — Remove Firebase AI (Gemini)

> **Status:** 🔲 Not Started

Prerequisites: `00-conventions.md`. Can run in parallel with `01-rename-package.md`,
but if both touch `app/build.gradle.kts`, apply them sequentially.

## Goal

Completely remove Firebase AI / Gemini and its supporting Firebase/Google-Services
wiring from the project. The app is fully local (see privacy rule in 00).

## Investigate first

Search the codebase for usages before deleting deps so nothing breaks:
- `grep` for: `firebase`, `Firebase`, `GenerativeModel`, `generativeModel`, `gemini`,
  `FirebaseAI`, `appcheck`, `AppCheck`.
- Note every file that references them; those call sites must be removed or replaced
  with local logic. If a feature depended on Gemini, replace with a no-op or a
  simple local heuristic and leave a one-line comment.

## Files to modify

### `app/build.gradle.kts`
- Remove these lines:
  - `alias(libs.plugins.google.services)` (plugins block)
  - `implementation(platform(libs.firebase.bom))`
  - `implementation(libs.firebase.ai)`
  - `implementation(libs.firebase.appcheck.recaptcha)`
  - the `googleServices { ... }` configuration block
  - the `import com.google.gms.googleservices.GoogleServicesPlugin...` at the top
- Evaluate `alias(libs.plugins.secrets)` + the `secrets { ... }` block: keep only if
  `.env` secrets are used for something non-Firebase. If they exist solely for the
  Gemini/Maps key, remove them too. Document the decision.

### `gradle/libs.versions.toml`
- Remove now-unused entries (versions + libraries + plugins) that nothing else
  references: `firebase-bom`, `firebase-ai`, `firebase-appcheck-recaptcha`,
  `firebase-firestore`, `firebase-auth`, `google-services` plugin, and
  `secretsGradlePlugin`/`secrets` **only if** the secrets plugin is being removed.
  Leave anything still referenced elsewhere.

### `metadata.json`
- Remove `"MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"` from `majorCapabilities`
  (leave the array empty `[]` if it becomes empty).

### Google services / config files
- Delete `app/google-services.json` if present.
- Remove any `.env` / `.env.example` entries that only held Firebase/Gemini keys
  (only if the secrets plugin is removed).

### Manifest
- Remove any Firebase-related `<meta-data>`, providers, or app-check entries in
  `app/src/main/AndroidManifest.xml` if present (search for `firebase`).

### Code
- Delete or rewrite the call sites found in the investigation step.

## Step-by-step

1. Search & list all Firebase/Gemini references.
2. Remove/replace call sites in `.kt` files.
3. Strip deps/plugins from `build.gradle.kts` and `libs.versions.toml`.
4. Update `metadata.json`; delete `google-services.json`.
5. Clean manifest of Firebase entries.
6. Gradle sync + build.

## Acceptance criteria

- Repo-wide search for `firebase`, `Firebase`, `gemini`, `GenerativeModel`,
  `google-services` returns no matches in source/build config (ignoring `build/`).
- Project builds and runs without the Google Services plugin.
- `metadata.json` no longer lists the Gemini capability.
- No feature crashes due to a removed Gemini call (replaced with local logic/no-op).

## Out of scope

- Adding any replacement cloud/AI service. The app stays fully local.