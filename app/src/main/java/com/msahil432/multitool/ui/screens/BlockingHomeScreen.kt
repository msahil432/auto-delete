package com.msahil432.multitool.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.data.BlockGroup
import com.msahil432.multitool.data.BlockRule
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.ui.components.EmptyState
import com.msahil432.multitool.ui.components.LoadingState
import com.msahil432.multitool.ui.theme.MultiToolTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockingHomeScreen(
  blockingRepository: BlockingRepository,
  innerPadding: PaddingValues,
  onNavigateToGroup: (Long) -> Unit
) {
  val coroutineScope = rememberCoroutineScope()
  val groups by blockingRepository.groups().collectAsState(initial = null)
  val allRules by blockingRepository.allRules().collectAsState(initial = emptyList())

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Blocking", style = MaterialTheme.typography.headlineSmall) }
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { onNavigateToGroup(0L) },
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("New group") }
      )
    },
    contentWindowInsets = WindowInsets(0)
  ) { scaffoldPadding ->
    val combinedPadding = PaddingValues(
      top = scaffoldPadding.calculateTopPadding(),
      bottom = innerPadding.calculateBottomPadding()
    )

    BlockingHomeScreenContent(
      groups = groups,
      allRules = allRules,
      paddingValues = combinedPadding,
      onNavigateToGroup = onNavigateToGroup,
      onToggleGroupEnabled = { group, enabled ->
        coroutineScope.launch {
          blockingRepository.upsertGroup(group.copy(enabled = enabled))
        }
      }
    )
  }
}

@Composable
fun BlockingHomeScreenContent(
  groups: List<BlockGroup>?,
  allRules: List<BlockRule>,
  paddingValues: PaddingValues,
  onNavigateToGroup: (Long) -> Unit,
  onToggleGroupEnabled: (BlockGroup, Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  when {
    groups == null -> {
      LoadingState(modifier = modifier.padding(paddingValues))
    }
    groups.isEmpty() -> {
      EmptyState(
        icon = Icons.Default.Block,
        title = "No blocks yet",
        message = "Create a group to start focusing.",
        actionLabel = "New group",
        onAction = { onNavigateToGroup(0L) },
        modifier = modifier.padding(paddingValues)
      )
    }
    else -> {
      val rulesByGroup = remember(allRules) {
        allRules.groupBy { it.groupId }
      }

      LazyColumn(
        modifier = modifier
          .fillMaxSize()
          .padding(paddingValues),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(groups, key = { it.id }) { group ->
          val groupRules = rulesByGroup[group.id] ?: emptyList()
          BlockGroupCard(
            group = group,
            rules = groupRules,
            onClick = { onNavigateToGroup(group.id) },
            onToggleEnabled = { isChecked -> onToggleGroupEnabled(group, isChecked) }
          )
        }
      }
    }
  }
}

@Composable
fun BlockGroupCard(
  group: BlockGroup,
  rules: List<BlockRule>,
  onClick: () -> Unit,
  onToggleEnabled: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  val appCount = remember(group.packageNames) {
    group.packageNames.split(';').filter { it.isNotBlank() }.size
  }

  val summaryLine = remember(rules) {
    formatGroupRulesSummary(rules)
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "$appCount ${if (appCount == 1) "app" else "apps"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Switch(
          checked = group.enabled,
          onCheckedChange = onToggleEnabled,
          modifier = Modifier.semantics {
            contentDescription = "Toggle ${group.name}"
          }
        )
      }

      HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp
      )

      Text(
        text = summaryLine,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

private fun formatGroupRulesSummary(rules: List<BlockRule>): String {
  if (rules.isEmpty()) return "No rules configured"
  val activeRules = rules.filter { it.enabled }
  val countText = "${rules.size} ${if (rules.size == 1) "rule" else "rules"}" +
    if (activeRules.size < rules.size) " (${activeRules.size} active)" else ""

  val summaries = rules.map { rule ->
    when (rule.type) {
      BlockRuleType.SCHEDULE -> {
        val days = formatDaysOfWeekMask(rule.daysOfWeekMask)
        val start = formatMinuteOfDay(rule.startMinuteOfDay)
        val end = formatMinuteOfDay(rule.endMinuteOfDay)
        "Schedule ($days $start-$end)"
      }
      BlockRuleType.DAILY_QUOTA -> "Quota (${rule.dailyQuotaMinutes}m/day)"
      BlockRuleType.LAUNCH_LIMIT -> "Launch cap (${rule.maxLaunchesPerDay}/day)"
      BlockRuleType.SESSION_LIMIT -> "Session (${rule.maxSessionMinutes}m + ${rule.cooldownMinutes}m cooldown)"
      BlockRuleType.GOAL_UNLOCK -> "Goal unlock (${rule.goalRequiredMinutes}m)"
    }
  }

  return "$countText · ${summaries.joinToString(", ")}"
}

fun formatMinuteOfDay(minuteOfDay: Int): String {
  val clamped = minuteOfDay.coerceIn(0, 1439)
  val hours = clamped / 60
  val minutes = clamped % 60
  return "%02d:%02d".format(hours, minutes)
}

fun formatDaysOfWeekMask(mask: Int): String {
  if (mask == 0) return "Never"
  if (mask == 0x7F) return "Daily"
  if (mask == 0x1F) return "Weekdays"
  if (mask == 0x60) return "Weekends"

  val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
  val selected = dayLabels.filterIndexed { index, _ -> (mask and (1 shl index)) != 0 }
  return selected.joinToString(",")
}

@Preview(showBackground = true, name = "BlockingHomeScreen Content Light")
@Composable
private fun BlockingHomeScreenPreviewLight() {
  MultiToolTheme {
    BlockingHomeScreenContent(
      groups = listOf(
        BlockGroup(
          id = 1,
          name = "Social Media",
          packageNames = "com.instagram.android;com.twitter.android",
          enabled = true,
          createdAt = System.currentTimeMillis()
        ),
        BlockGroup(
          id = 2,
          name = "Gaming",
          packageNames = "com.supercell.clashroyale",
          enabled = false,
          createdAt = System.currentTimeMillis()
        )
      ),
      allRules = listOf(
        BlockRule(
          id = 1,
          groupId = 1,
          type = BlockRuleType.DAILY_QUOTA,
          dailyQuotaMinutes = 30
        ),
        BlockRule(
          id = 2,
          groupId = 1,
          type = BlockRuleType.SCHEDULE,
          daysOfWeekMask = 0x1F,
          startMinuteOfDay = 540,
          endMinuteOfDay = 1020
        )
      ),
      paddingValues = PaddingValues(),
      onNavigateToGroup = {},
      onToggleGroupEnabled = { _, _ -> }
    )
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "BlockingHomeScreen Content Dark")
@Composable
private fun BlockingHomeScreenPreviewDark() {
  MultiToolTheme {
    BlockingHomeScreenContent(
      groups = listOf(
        BlockGroup(
          id = 1,
          name = "Social Media",
          packageNames = "com.instagram.android;com.twitter.android",
          enabled = true,
          createdAt = System.currentTimeMillis()
        )
      ),
      allRules = listOf(
        BlockRule(
          id = 1,
          groupId = 1,
          type = BlockRuleType.DAILY_QUOTA,
          dailyQuotaMinutes = 45
        )
      ),
      paddingValues = PaddingValues(),
      onNavigateToGroup = {},
      onToggleGroupEnabled = { _, _ -> }
    )
  }
}

@Preview(showBackground = true, name = "BlockingHomeScreen Empty State")
@Composable
private fun BlockingHomeScreenEmptyPreview() {
  MultiToolTheme {
    BlockingHomeScreenContent(
      groups = emptyList(),
      allRules = emptyList(),
      paddingValues = PaddingValues(),
      onNavigateToGroup = {},
      onToggleGroupEnabled = { _, _ -> }
    )
  }
}
