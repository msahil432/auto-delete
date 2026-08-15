# 16 — Notification Interception & Vault

Prerequisites: `01-rename-package.md`, `25-design-system.md`.

## Goal

Silence/dismiss notifications from restricted apps during active focus schedules,
buffer them locally in a "vault", and deliver a consolidated digest when the
restriction lapses.

## Files to create / modify

- Create `service/MultiToolNotificationListener.kt` extending
  `NotificationListenerService`.
- `AndroidManifest.xml`: declare with `BIND_NOTIFICATION_LISTENER_SERVICE` +
  intent-filter `android.service.notification.NotificationListenerService`.
- Create `data/NotificationEntities.kt`:
  ```kotlin
  @Entity(tableName = "vaulted_notifications", indices = [Index("postedAt")])
  data class VaultedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val delivered: Boolean = false
  )
  ```
  Add to `AppDatabase` (bump version + migration, sequential).
- Create `data/NotificationDao.kt` + repository methods.
- DataStore/settings: which packages are "restricted for notifications" (reuse block
  groups OR a dedicated list `notification_blocked_packages`).
- Helper `NotificationAccess.isGranted(context)` reading
  `Settings.Secure.ENABLED_NOTIFICATION_LISTENERS`; helper to open
  `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.

## Listener logic

```kotlin
class MultiToolNotificationListener : NotificationListenerService() {
  override fun onNotificationPosted(sbn: StatusBarNotification) {
    val pkg = sbn.packageName
    if (!isRestrictedNow(pkg)) return          // check active schedule/group
    // buffer then remove:
    scope.launch { repo.vault(VaultedNotification(pkg=pkg,
       title=extractTitle(sbn), text=extractText(sbn), postedAt=sbn.postTime)) }
    cancelNotification(sbn.key)                 // silences/dismisses it
  }
}
```

- `isRestrictedNow`: true if pkg is in a restricted set AND a schedule is currently
  active (reuse `BlockEngine.nowWithinSchedule` or a simple schedule check).
- Extract title/text from `sbn.notification.extras`
  (`EXTRA_TITLE`, `EXTRA_TEXT`).
- Never store contents of E2E-encrypted message bodies beyond what the notification
  itself exposes (that's just the public notification text — acceptable).

## Digest delivery

- When a restriction window ends (schedule end reached), post a single summary
  notification: "N notifications while you were focused" and mark rows `delivered`.
- Trigger digest via a WorkManager job scheduled for the schedule end time, or a
  periodic check.

## UX / Screen Design

- **Vault screen** (sub-route from Settings or Blocking): list of vaulted
  notifications grouped by app, newest first; each row = app icon, title, text
  preview, time. States: Loading / Empty ("No held notifications") / Content.
- **Settings:** "Notification blocking" section — enable + pick restricted apps +
  grant listener access (`PermissionTile`). Prominent disclosure before opening the
  listener settings.
- Accessibility: rows expose app+title+time; buttons labeled.
- Dark mode: theme-driven.

## Acceptance criteria

- During an active restriction, notifications from restricted apps are removed from
  the shade and stored in the vault.
- Outside restrictions, notifications pass through untouched.
- A digest is delivered after the window ends and rows are marked delivered.
- Vault screen shows held items with all states.

## Out of scope

- Reading message contents inside apps (boundary). Only public notification
  title/text is used.
