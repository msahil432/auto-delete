package com.msahil432.multitool.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.msahil432.multitool.data.UsageDailyStat
import com.msahil432.multitool.data.UsageRepository
import com.msahil432.multitool.ui.components.AppListItem
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.components.ErrorState
import com.msahil432.multitool.ui.components.LoadingState
import com.msahil432.multitool.ui.components.SectionHeader
import com.msahil432.multitool.ui.components.StatCard
import com.msahil432.multitool.ui.theme.MultiToolTheme
import com.msahil432.multitool.util.UsageAccess
import com.msahil432.multitool.util.toHms
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageHomeScreen(
  usageRepository: UsageRepository,
  innerPadding: PaddingValues,
  onNavigateToTimeline: () -> Unit,
  viewModel: UsageViewModel = viewModel(
    factory = UsageViewModel.Factory(usageRepository, LocalContext.current.applicationContext)
  )
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermission()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val isUsageAccessGranted by viewModel.isUsageAccessGranted.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val totalScreenTimeToday by viewModel.totalScreenTimeToday.collectAsState()
  val perApp by viewModel.perApp.collectAsState()
  val unlocksToday by viewModel.unlocksToday.collectAsState()
  val appMetaCache by viewModel.appMetaCache.collectAsState()
  var showUsageDisclosure by remember { mutableStateOf(false) }

  if (showUsageDisclosure) {
    com.msahil432.multitool.ui.components.ConfirmDialog(
      title = "Usage Access Disclosure",
      text = "Multi Tool uses Usage Access to measure your screen time, count app launches, and build your activity timeline. All usage statistics are stored and processed entirely on your device and are never sent off the device.",
      confirmLabel = "Continue to Settings",
      onConfirm = {
        showUsageDisclosure = false
        UsageAccess.openSettings(context)
      },
      onDismiss = { showUsageDisclosure = false }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Usage", style = MaterialTheme.typography.headlineSmall) }
      )
    },
    contentWindowInsets = WindowInsets(0)
  ) { scaffoldPadding ->
    val combinedPadding = PaddingValues(
      top = scaffoldPadding.calculateTopPadding(),
      bottom = innerPadding.calculateBottomPadding()
    )

    UsageHomeScreenContent(
      isUsageAccessGranted = isUsageAccessGranted,
      isLoading = isLoading,
      totalScreenTimeToday = totalScreenTimeToday,
      unlocksToday = unlocksToday,
      perApp = perApp,
      appMetaCache = appMetaCache,
      paddingValues = combinedPadding,
      onGrantPermission = { showUsageDisclosure = true },
      onNavigateToTimeline = onNavigateToTimeline
    )
  }
}

@Composable
fun UsageHomeScreenContent(
  isUsageAccessGranted: Boolean,
  isLoading: Boolean,
  totalScreenTimeToday: Long,
  unlocksToday: Int,
  perApp: List<UsageDailyStat>,
  appMetaCache: Map<String, AppMeta>,
  paddingValues: PaddingValues,
  onGrantPermission: () -> Unit,
  onNavigateToTimeline: () -> Unit,
  modifier: Modifier = Modifier
) {
  val numberFormat = remember { NumberFormat.getInstance() }

  when {
    !isUsageAccessGranted -> {
      ErrorState(
        message = "Usage access needed to show your stats",
        actionLabel = "Grant Permission",
        onRetry = onGrantPermission,
        modifier = modifier.padding(paddingValues)
      )
    }
    isLoading -> {
      LoadingState(modifier = modifier.padding(paddingValues))
    }
    perApp.isEmpty() && totalScreenTimeToday == 0L && unlocksToday == 0 -> {
      EmptyState(
        icon = Icons.Default.BarChart,
        title = "No usage yet",
        message = "Come back after using your phone for a bit.",
        modifier = modifier.padding(paddingValues)
      )
    }
    else -> {
      val totalLaunches = remember(perApp) { perApp.sumOf { it.launchCount } }

      LazyColumn(
        modifier = modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(bottom = 16.dp)
      ) {
        // ── 1. Header row of StatCards ───────────────────────────────────────
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            StatCard(
              label = "Screen Time",
              value = totalScreenTimeToday.toHms(),
              icon = Icons.Default.Timer,
              modifier = Modifier
                .weight(1f)
                .semantics {
                  contentDescription = "Screen Time, ${totalScreenTimeToday.toHms()}"
                }
            )
            StatCard(
              label = "Unlocks",
              value = numberFormat.format(unlocksToday),
              icon = Icons.Default.LockOpen,
              modifier = Modifier
                .weight(1f)
                .semantics {
                  contentDescription = "Unlocks, ${numberFormat.format(unlocksToday)}"
                }
            )
            StatCard(
              label = "Launches",
              value = numberFormat.format(totalLaunches),
              icon = Icons.Default.RocketLaunch,
              modifier = Modifier
                .weight(1f)
                .semantics {
                  contentDescription = "Launches, ${numberFormat.format(totalLaunches)}"
                }
            )
          }
        }

        // ── 2. Section Header ────────────────────────────────────────────────
        item {
          SectionHeader(title = "App usage today")
        }

        // ── 3. List of AppListItems ──────────────────────────────────────────
        items(perApp, key = { it.packageName }) { stat ->
          val meta = appMetaCache[stat.packageName]
          val appLabel = meta?.label ?: stat.packageName
          val painter = meta?.icon?.let { remember(it) { BitmapPainter(it.asImageBitmap()) } }
          val formattedDuration = stat.foregroundMillis.toHms()
          val formattedLaunches = "${numberFormat.format(stat.launchCount)} launches"

          AppListItem(
            appLabel = appLabel,
            packageName = stat.packageName,
            icon = painter,
            trailing = {
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = formattedDuration,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = formattedLaunches,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            },
            modifier = Modifier.semantics(mergeDescendants = true) {
              contentDescription = "$appLabel, $formattedDuration, $formattedLaunches"
            }
          )
          HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp))
        }

        // ── 4. View timeline button ──────────────────────────────────────────
        item {
          Spacer(modifier = Modifier.height(16.dp))
          OutlinedButton(
            onClick = onNavigateToTimeline,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
          ) {
            Icon(
              imageVector = Icons.Default.History,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("View Timeline")
          }
        }
      }
    }
  }
}

@Preview(showBackground = true, name = "UsageHomeScreen Content Light")
@Composable
private fun UsageHomeScreenPreviewLight() {
  MultiToolTheme {
    UsageHomeScreenContent(
      isUsageAccessGranted = true,
      isLoading = false,
      totalScreenTimeToday = 8100000L, // 2h 15m
      unlocksToday = 42,
      perApp = listOf(
        UsageDailyStat(
          id = 1,
          dateEpochDay = 20000,
          packageName = "com.google.android.youtube",
          foregroundMillis = 4500000L,
          launchCount = 12,
          lastUpdated = 0L
        ),
        UsageDailyStat(
          id = 2,
          dateEpochDay = 20000,
          packageName = "com.instagram.android",
          foregroundMillis = 2400000L,
          launchCount = 28,
          lastUpdated = 0L
        ),
        UsageDailyStat(
          id = 3,
          dateEpochDay = 20000,
          packageName = "com.whatsapp",
          foregroundMillis = 1200000L,
          launchCount = 35,
          lastUpdated = 0L
        )
      ),
      appMetaCache = mapOf(
        "com.google.android.youtube" to AppMeta(label = "YouTube"),
        "com.instagram.android" to AppMeta(label = "Instagram"),
        "com.whatsapp" to AppMeta(label = "WhatsApp")
      ),
      paddingValues = PaddingValues(),
      onGrantPermission = {},
      onNavigateToTimeline = {}
    )
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "UsageHomeScreen Content Dark")
@Composable
private fun UsageHomeScreenPreviewDark() {
  MultiToolTheme {
    UsageHomeScreenContent(
      isUsageAccessGranted = true,
      isLoading = false,
      totalScreenTimeToday = 8100000L,
      unlocksToday = 42,
      perApp = listOf(
        UsageDailyStat(
          id = 1,
          dateEpochDay = 20000,
          packageName = "com.google.android.youtube",
          foregroundMillis = 4500000L,
          launchCount = 12,
          lastUpdated = 0L
        )
      ),
      appMetaCache = mapOf(
        "com.google.android.youtube" to AppMeta(label = "YouTube")
      ),
      paddingValues = PaddingValues(),
      onGrantPermission = {},
      onNavigateToTimeline = {}
    )
  }
}

@Preview(showBackground = true, name = "UsageHomeScreen Permission Missing")
@Composable
private fun UsageHomeScreenPermissionMissingPreview() {
  MultiToolTheme {
    UsageHomeScreenContent(
      isUsageAccessGranted = false,
      isLoading = false,
      totalScreenTimeToday = 0L,
      unlocksToday = 0,
      perApp = emptyList(),
      appMetaCache = emptyMap(),
      paddingValues = PaddingValues(),
      onGrantPermission = {},
      onNavigateToTimeline = {}
    )
  }
}

@Preview(showBackground = true, name = "UsageHomeScreen Empty State")
@Composable
private fun UsageHomeScreenEmptyPreview() {
  MultiToolTheme {
    UsageHomeScreenContent(
      isUsageAccessGranted = true,
      isLoading = false,
      totalScreenTimeToday = 0L,
      unlocksToday = 0,
      perApp = emptyList(),
      appMetaCache = emptyMap(),
      paddingValues = PaddingValues(),
      onGrantPermission = {},
      onNavigateToTimeline = {}
    )
  }
}
