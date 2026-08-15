# 22 — Boot Persistence & Background Survival

> **Status:** ✅ Complete

Prerequisites: `01-rename-package.md`.

## Goal

Ensure monitoring (file monitor, unlock tracker, accessibility-dependent features,
geofences, blocking) resumes after reboot and survives OEM battery killers.

## Decisions

1. **OEM Auto-Start Queryability**: Standard Android APIs do not expose whether auto-start or background exemption has been granted in proprietary OEM vendor settings (Xiaomi MIUI, Samsung Smart Manager, Huawei Optimizer, ColorOS, vivo). Therefore `isGranted` returns `false` (prompting optional guidance), and `OemAutostart` provides manufacturer-tailored instruction text based on `Build.MANUFACTURER`.
2. **OEM Intent Candidates & Fallback**: `OemAutostart.open(context)` inspects intent resolution for candidate vendor components across Xiaomi, Samsung, Huawei, Oppo/Realme/OnePlus, and Vivo, launching the first resolvable target within a try/catch, and gracefully falling back to standard `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
3. **Boot Sequence**: `BootReceiver` triggers foreground service startup, enqueues `UsageCollectorWorker.schedule(context)` (with `ExistingPeriodicWorkPolicy.KEEP`), and re-registers all enabled geofences via `GeofenceManager.reRegisterAll()` within a coroutine using `goAsync()`.

## Files to modify / create

- Modify `service/BootReceiver.kt`: on `BOOT_COMPLETED`, (re)start the foreground
  service(s), re-schedule the usage collector worker, and re-register geofences
  (spec 17). It already exists for the files feature — extend it, don't replace.
- Ensure the manifest keeps `RECEIVE_BOOT_COMPLETED` and the receiver's
  `BOOT_COMPLETED` intent-filter (already present).
- Create `util/BatteryOptimization.kt`:
  ```kotlin
  object BatteryOptimization {
    fun isIgnoring(ctx: Context): Boolean =
      (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(ctx.packageName)
    fun requestIgnore(ctx: Context) {
      ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }
  ```
  (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is already in the manifest.)
- Create `util/OemAutostart.kt` — best-effort intents to open OEM auto-start / app-
  lock screens (Xiaomi/MIUI, Samsung, Huawei, Oppo/Realme, Vivo). Wrap each in
  try/catch; if the intent fails, fall back to app-details settings.

## Boot receiver logic

```kotlin
override fun onReceive(context: Context, intent: Intent) {
  if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
    // start foreground monitor service(s)
    // WorkManager: enqueue unique periodic UsageCollectorWorker (KEEP)
    // GeofenceManager.reRegisterAll(context)   // from spec 17
  }
}
```

## UX / Onboarding guidance

- Onboarding/permissions steps (reuse `PermissionTile`) for:
  - "Ignore battery optimization" → `BatteryOptimization.requestIgnore`.
  - "Enable auto-start" (OEM) → `OemAutostart.open(context)` with a short visual
    explanation, since these screens vary by manufacturer.
- Show current status where detectable (battery optimization is queryable; auto-start
  usually is not — just provide the shortcut + instructions).
- Accessibility: buttons labeled; instructions readable. Dark mode: theme-driven.

## Acceptance criteria

- After reboot, the foreground service restarts and the usage worker is scheduled.
- Geofences are re-registered after reboot (with spec 17).
- Battery-optimization exemption can be requested and its status reflected.
- OEM auto-start shortcut opens (or gracefully falls back) on major OEMs.

## Out of scope / boundaries

- Cannot track anything while the device is fully powered off (documented boundary).
- Cannot guarantee survival against every OEM killer; provide best-effort guidance.