# Refactoring Plan: Test Coverage Gaps — **Done ✓**

**Guideline violated:** §7 Testing — "Every new feature or bugfix must include tests."
**Status:** Done ✓

## Problem

Several source packages have no corresponding tests, and some existing files with complex logic are untested.

## Source → Test Coverage Map

| Source Package | Source Files | Test Files | Gap |
|---|---|---|---|
| `data/` | 22 files | 2 tests (`UsageRepositoryTest`, `GeofenceRepositoryTest`) | **BlockingRepository, BrowsingRepository, NotificationRepository, SettingsRepository, FilterRule, Converters** untested |
| `service/` | 8 files | 2 tests (`BootReceiverTest`, `TamperAlarmTest`) | **FileMonitorService, FileActionWorker, MoveHelper, PromptHelper, ActionReceiver, MultiToolNotificationListener** untested |
| `blocking/` | 5 files | 3 tests (`BlockEngineTest`, `BlockOverlayTest`, `StrictModeControllerTest`) | **BlockActivity, BlockEnforcementController** untested |
| `accessibility/` | 10 files | 3 tests | **MultiToolAccessibilityService, AccessibilityUtil, Dispatcher** untested |
| `tracking/` | 3 files | 2 tests | **UsageStatsReader** untested |
| `location/` | 2 files | 1 test (`GeofenceBroadcastReceiverTest`) | **GeofenceManager** untested |
| `notification/` | 2 files | 0 tests | **NotificationDigestWorker, NotificationDigestScheduler** fully untested |
| `admin/` | 2 files | 1 test | **MultiToolDeviceAdminReceiver** untested |
| `util/` | 7 files | 4 tests | **NotificationAccess, UsageAccess, SecureScreen** untested |
| `ui/screens/` | 17 files + challenge/ | 1 test (`UsageViewModelTest`) | All screen composables untested (Roborazzi screenshot tests recommended) |

## Priority Order

### P0 — Core data layer (most impactful, easiest to test)
1. `data/BlockingRepository.kt` — Complex CRUD + interceptor logging
2. `data/SettingsRepository.kt` — DataStore getter/setter pairs (follow `UsageRepositoryTest` pattern)
3. `data/BrowsingRepository.kt` — Same pattern as UsageRepository
4. `data/NotificationRepository.kt` — CRUD operations
5. `data/FilterRule.kt` — Matching logic (pure functions, very testable)
6. `data/Converters.kt` — Type converters (pure functions)

### P1 — Service layer
7. `service/FileActionWorker.kt` — File operations with error handling
8. `service/MoveHelper.kt` — SAF move/copy logic
9. `notification/NotificationDigestWorker.kt` — Worker logic
10. `notification/NotificationDigestScheduler.kt` — Scheduling logic

### P2 — Blocking logic
11. `blocking/BlockEnforcementController.kt` — Enforcement loop

### P3 — Screenshot tests for composables
12. Add Roborazzi screenshot tests for key screens

## Test Template (follow existing patterns)

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlockingRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repo: BlockingRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BlockingRepository(db.blockingDao())
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun `insert and retrieve group`() = runTest { ... }
}
```

## Verification

`./gradlew testDebugUnitTest` — all new and existing tests must pass.
