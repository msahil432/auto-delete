# 18 — Device Admin (Anti-Uninstall)

Prerequisites: `01-rename-package.md`.

## Goal

Register a Device Administrator so the app cannot be casually uninstalled while a
strict-mode focus session is active.

## Files to create / modify

- Create `admin/MultiToolDeviceAdminReceiver.kt` extending `DeviceAdminReceiver`.
- Create `res/xml/device_admin.xml` — admin policy (minimal: no extra policies
  needed just to block uninstall; enabling admin is what blocks uninstall).
  ```xml
  <device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies/>
  </device-admin>
  ```
- `AndroidManifest.xml`: declare the receiver with
  `BIND_DEVICE_ADMIN` permission + meta-data pointing to `device_admin.xml` +
  intent-filter `android.app.action.DEVICE_ADMIN_ENABLED`.
- Create `admin/DeviceAdminHelper.kt`:
  ```kotlin
  object DeviceAdminHelper {
    fun component(ctx: Context) = ComponentName(ctx, MultiToolDeviceAdminReceiver::class.java)
    fun isActive(ctx: Context) =
      (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
        .isAdminActive(component(ctx))
    fun requestActivation(ctx: Context) {
      val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(ctx))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
          "Enables Multi Tool to prevent uninstalling during an active focus session.")
      ctx.startActivity(i)
    }
    fun deactivate(ctx: Context) =
      (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
        .removeActiveAdmin(component(ctx))
  }
  ```

## Uninstall protection

- While strict mode is active (spec 19), keep device admin active. Android prevents
  uninstalling an app that is an active device admin (user must disable admin first,
  which strict mode gates).
- `onDisableRequested` in the receiver: return a warning string discouraging
  disable; if strict mode is active you cannot fully prevent it, but the friction +
  tamper alarm (spec 21) act as deterrents.

## UX

- Settings entry "Anti-uninstall protection" (`SettingRow` + status). Prominent
  disclosure BEFORE requesting activation, stating device admin is used solely to
  prevent premature uninstall during strict mode (Play policy).
- Show current status (active/inactive) and a button to enable.

## Acceptance criteria

- User can activate device admin via the system dialog.
- `DeviceAdminHelper.isActive` reflects state.
- With admin active, the app cannot be uninstalled without first disabling admin.
- Disclosure shown before activation.

## Out of scope / boundaries

- Cannot prevent removal via Safe Mode or `adb uninstall` (documented boundary).
- Actual strict-mode gating of deactivation is in spec 19.
