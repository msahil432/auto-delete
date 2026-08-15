# 10 — Blocking Rules UI

> **Status:** ✅ Done

Prerequisites: `09-blocking-entities.md`, `25-design-system.md`.


## Goal

Build the **Blocking** tab: list block groups, create/edit a group and its rules
(schedule, daily quota, launch cap, session limit, goal-based unlock), and pick
target apps.

## Files to create / modify

- Create `data/BlockingDao.kt` + `data/BlockingRepository.kt` (CRUD for groups,
  rules, counters; Flows for lists).
- Add `abstract fun blockingDao(): BlockingDao` to `AppDatabase`.
- Replace placeholder `ui/screens/BlockingHomeScreen.kt`.
- Create `ui/screens/BlockGroupEditScreen.kt` (pushed sub-route
  `block_group/{id}`; `id = 0` means new).
- Create `ui/components/AppPicker.kt` — multi-select list of installed apps.

## BlockingRepository (API sketch)

```kotlin
class BlockingRepository(private val dao: BlockingDao) {
  fun groups(): Flow<List<BlockGroup>>
  fun rulesFor(groupId: Long): Flow<List<BlockRule>>
  suspend fun upsertGroup(g: BlockGroup): Long
  suspend fun deleteGroup(g: BlockGroup)
  suspend fun upsertRule(r: BlockRule)
  suspend fun deleteRule(r: BlockRule)
}
```

## App picker

- Query launchable apps via `PackageManager.queryIntentActivities` (MAIN/LAUNCHER).
- Show icon + label + package; searchable; multi-select with checkboxes.
- Return selected package names as the delimited `packageNames` string.

## UX / Screen Design

- **Blocking tab:** list of `BlockGroup` cards (name, app-count, enabled toggle, a
  summary line like "2 rules · quota 30m/day"). FAB "New group".
  - States: Loading / Empty (`EmptyState("No blocks yet", "Create a group to start
    focusing.", actionLabel="New group")`) / Content.
- **Group edit screen:**
  - Group name field.
  - "Apps" section → opens `AppPicker`; shows chips/count of selected apps.
  - "Rules" section: add-rule menu offering the 5 `BlockRuleType`s. Each rule renders
    an inline editor:
    - SCHEDULE: day-of-week toggles + start/end time pickers.
    - DAILY_QUOTA: minutes stepper/field.
    - LAUNCH_LIMIT: max launches field.
    - SESSION_LIMIT: session minutes + cooldown minutes fields.
    - GOAL_UNLOCK: productive app picker + required minutes.
  - Enabled toggle per rule; delete rule (with `ConfirmDialog`).
  - Save / discard; deleting the group uses `ConfirmDialog`.
- **Strict-mode note:** if strict mode is active (spec 19), edits that *weaken* a
  rule must be blocked/hidden. Read a `StrictModeController.isActive` flag; when
  active, disable delete/reduce actions and show a lock hint. (Full logic in 19.)
- **Accessibility:** all toggles/fields labeled; time pickers reachable via
  TalkBack; 48dp touch targets.
- **Dark mode:** theme-driven; verify previews.

## Acceptance criteria

- Can create a group, pick apps, add each of the 5 rule types, save, and see it in
  the list.
- Edits persist and reflect immediately (Flow-backed).
- Delete requires confirmation.
- Empty/loading states render; light + dark previews render.

## Out of scope

- Enforcing the rules at runtime (specs 11-13). Strict-mode enforcement (19).