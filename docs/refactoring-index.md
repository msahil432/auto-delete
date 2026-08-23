# Refactoring Plans — Summary Index

These plans document where the codebase diverges from the guidelines in `AGENTS.md`.
Each plan is self-contained and can be assigned to a light agent independently.

## Plans

| # | Plan | Guideline | Severity | Scope | Status |
|---|---|---|---|---|---|
| 01 | [Sentry Exceptions](refactor-01-sentry-exceptions.md) | §2 Error Handling | **High** | ~35 catch blocks silently swallowing + 0 breadcrumbs | **Done ✓** |
| 02 | [KDoc Comments](refactor-02-kdoc-comments.md) | §6 Documentation | **Medium** | ~50 files missing class/function KDoc | **Done ✓** |
| 03 | [Compose Previews](refactor-03-compose-previews.md) | §3 UI/Dark Mode | **Medium** | 13 screens missing previews, 2 missing dark variant | Pending |
| 04 | [Accessibility Content Descriptions](refactor-04-accessibility-content-descriptions.md) | §9 Accessibility | **High** | 85+ `contentDescription = null` | Pending |
| 05 | [Test Coverage](refactor-05-test-coverage.md) | §7 Testing | **Medium** | notification/ fully untested, 6 data layer repos partially | Pending |

## What's already compliant ✓

- **Architecture** (§1): Correct layer separation, manual DI, DataStore pattern
- **Error Handling & Sentry** (§2): No silent catches, `Sentry.captureException()` on critical failures, `Sentry.addBreadcrumb()` at key milestones
- **Material 3 theming** (§3): Dynamic color enabled, `MaterialTheme.colorScheme.*` used throughout — no hardcoded colors in composables (only in `Color.kt` / `Theme.kt` fallbacks which is correct)
- **Coroutine patterns** (§4): `SupervisorJob`, `viewModelScope`, `SharingStarted.WhileSubscribed(5000)` used correctly
- **Data layer conventions** (§5): Room naming, DataStore keys in companion, Moshi codegen
- **Services** (§8): `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+, receiver unregistration in `onDestroy()`
- **Security** (§10): `PasswordSecurity` with PBKDF2, secrets via env vars
- **Build/CI** (§11): Version catalog, R8 keep rules, lint fatals enabled

## Suggested Execution Order

1. **01-sentry** — Highest impact, smallest diff. Makes all future debugging easier.
2. **04-accessibility** — Legal/compliance risk. Mechanical changes.
3. **02-kdoc** — Purely additive, no risk. Improves all future agent interactions.
4. **03-previews** — Some require content extraction refactors. Medium effort.
5. **05-tests** — Largest effort. Work through P0 first.
