# Multi Tool — Implementation Specs

This folder contains small, self-contained implementation spec files. Each one is
designed to be handed to a single (possibly small) LLM agent to implement in one
sitting without needing the whole codebase in context.

## How to use these specs

1. Read **`00-conventions.md`** first. It defines the package layout, coding
   standards, data-access patterns, and rules that ALL other specs assume.
2. If the spec builds UI, also read **`25-design-system.md`**.
3. Implement specs in dependency order (see the phase list below). Each spec lists
   its own **Prerequisites**.
4. When a spec is done, verify against its **Acceptance criteria** section before
   moving on.

## Product source of truth

The full product requirements live in [`../../app-tracker.md`](../../app-tracker.md).
These specs decompose that document into buildable units. Where the two disagree,
`app-tracker.md` wins for *what*, these specs win for *how*.

## Global rules (summary — full detail in 00-conventions.md)

- **New package:** `com.msahil432.multitool` (app renamed from "Auto Delete" to "Multi Tool").
- **minSdk stays 36.** Do not lower it.
- **All tracking data is local only** — Room / DataStore. No network, no cloud, no analytics.
- **Firebase AI is removed** (see `24-remove-firebase-ai.md`).
- Kotlin + Jetpack Compose (Material 3) + Room + DataStore + WorkManager.

## Phase / dependency order

**Status legend:** 🔲 Not Started · 🔶 In Progress · ✅ Done

> When a spec is completed, update its status both here **and** in the
> `> **Status:**` line at the top of that spec file.

| # | Spec | Depends on | Status |
|---|------|-----------|--------|
| Foundation | `00-conventions.md` | — | 🔲 Not Started |
| Foundation | `25-design-system.md` | 00 | 🔲 Not Started |
| Foundation | `01-rename-package.md` | 00 | ✅ Done |
| Foundation | `24-remove-firebase-ai.md` | 00 | ✅ Done |
| Foundation | `02-navigation-hub.md` | 01, 25 | 🔲 Not Started |
| Usage data | `03-usage-data-entities.md` | 01 | 🔲 Not Started |
| Usage data | `04-usage-repository.md` | 03 | 🔲 Not Started |
| Usage track | `05-usage-permission.md` | 02, 25 | 🔲 Not Started |
| Usage track | `06-usage-stats-collector.md` | 04, 05 | 🔲 Not Started |
| Usage track | `07-unlock-tracker.md` | 04 | 🔲 Not Started |
| Usage track | `08-usage-ui.md` | 04, 06, 07, 25 | 🔲 Not Started |
| Blocking | `09-blocking-entities.md` | 01 | 🔲 Not Started |
| Blocking | `10-blocking-rules-ui.md` | 09, 25 | 🔲 Not Started |
| Blocking | `11-accessibility-core.md` | 09, 25 | 🔲 Not Started |
| Blocking | `12-block-overlay.md` | 11, 25 | 🔲 Not Started |
| Blocking | `13-block-enforcement.md` | 04, 11, 12 | 🔲 Not Started |
| Shortform | `14-shortform-blocker.md` | 11 | 🔲 Not Started |
| Browser | `15-browser-url-tracker.md` | 04, 11 | 🔲 Not Started |
| Notifications | `16-notification-listener.md` | 01, 25 | 🔲 Not Started |
| Geofence | `17-geofence-profiles.md` | 09, 25 | 🔲 Not Started |
| Strict mode | `18-device-admin.md` | 01 | 🔲 Not Started |
| Strict mode | `19-strict-mode-controller.md` | 09, 18 | 🔲 Not Started |
| Strict mode | `20-friction-challenges.md` | 19, 25 | 🔲 Not Started |
| Strict mode | `21-tamper-alarm.md` | 11, 18 | 🔲 Not Started |
| Resilience | `22-boot-persistence.md` | 01 | 🔲 Not Started |
| Compliance | `23-play-compliance.md` | 11, 18 | 🔲 Not Started |

## Capability coverage (app-tracker.md §8)

Every "YES (Full)" capability maps to at least one spec. The "NO (Blocked)"
capabilities are intentionally **out of scope** and must not be implemented; they
are documented as boundaries in the relevant specs.
