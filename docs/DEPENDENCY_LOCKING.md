# Gradle Dependency Locking Runbook

## Overview

Multi Tool uses **Gradle Dependency Locking** in `STRICT` mode to guarantee reproducible builds across local developer workstations and CI/CD environments (GitHub Actions, Google Play Store releases).

- **Lock File**: [`app/gradle.lockfile`](file:///d:/personal/auto%20delete/app/gradle.lockfile)
- **Configuration**: [`app/build.gradle.kts`](file:///d:/personal/auto%20delete/app/build.gradle.kts)
- **Lock Mode**: `LockMode.STRICT` — any missing lock state or version mismatch will fail the build immediately.

---

## Why Dependency Locking?

1. **Deterministic Builds**: Prevents unexpected transitive dependency upgrades from breaking the build or introducing bugs.
2. **Supply Chain Security**: Ensures identical binary dependencies are resolved on developers' machines and CI release runners.
3. **Early Drift Detection**: Build and release pipelines fail fast if a dependency is modified without updating the lockfile.

---

## Developer Workflow

### 1. Adding, Updating, or Removing a Dependency

When modifying dependencies in [`gradle/libs.versions.toml`](file:///d:/personal/auto%20delete/gradle/libs.versions.toml) or [`app/build.gradle.kts`](file:///d:/personal/auto%20delete/app/build.gradle.kts):

1. **Edit the Version Catalog or Build Script**:
   Update the desired version in `gradle/libs.versions.toml`.

2. **Regenerate Lockfile**:
   Run the build tasks with `--write-locks` to update all configuration lock states:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug --write-locks
   ```
   *(Or on Windows PowerShell: `.\gradlew testDebugUnitTest assembleDebug --write-locks`)*

3. **Verify in Strict Mode**:
   Run the unit tests normally without `--write-locks` to confirm strict mode passes:
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. **Review and Commit**:
   Inspect the diff in `git status`:
   - `gradle/libs.versions.toml`
   - `app/gradle.lockfile`
   
   Commit both files together in the same PR.

---

## Dependabot Integration

Dependabot natively supports Gradle dependency lockfiles.

- **Configuration**: [`.github/dependabot.yml`](file:///d:/personal/auto%20delete/.github/dependabot.yml)
- **Workflow**:
  - Dependabot opens automated PRs with bumped versions in `gradle/libs.versions.toml`.
  - It automatically recalculates and updates `app/gradle.lockfile`.
  - The CI workflow (`.github/workflows/ci.yml`) runs an early verification step to validate the lockfile before tests.

---

## CI / CD Verification

Both [`.github/workflows/ci.yml`](file:///d:/personal/auto%20delete/.github/workflows/ci.yml) and [`.github/workflows/publish-to-play.yml`](file:///d:/personal/auto%20delete/.github/workflows/publish-to-play.yml) include an early verification step:

```yaml
- name: Verify dependency lock files
  run: ./gradlew :app:dependencies --no-build-cache
```

If a PR contains a version bump in `libs.versions.toml` but forgets to include the updated `app/gradle.lockfile`, the workflow will fail immediately with:
```
> Locking strict mode: Configuration ':app:...' is locked but does not have lock state.
```

---

## Troubleshooting

### Error: `Configuration '...' is locked but does not have lock state`
**Cause**: A dependency version was changed or a new configuration was introduced without generating lock state.
**Fix**: Run:
```bash
./gradlew testDebugUnitTest assembleDebug --write-locks
```
Then commit `app/gradle.lockfile`.

### Error: `Did not resolve to locked version ...`
**Cause**: The version resolved by Gradle differs from the pinned version in `app/gradle.lockfile`.
**Fix**: Regenerate the lockfile with `--write-locks` and review the diff to ensure the bump was intentional.
