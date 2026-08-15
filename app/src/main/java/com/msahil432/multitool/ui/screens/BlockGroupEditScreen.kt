package com.msahil432.multitool.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.data.BlockGroup
import com.msahil432.multitool.data.BlockRule
import com.msahil432.multitool.data.BlockRuleType
import com.msahil432.multitool.data.BlockingRepository
import com.msahil432.multitool.ui.components.AppPicker
import com.msahil432.multitool.ui.components.ConfirmDialog
import com.msahil432.multitool.ui.components.LoadingState
import com.msahil432.multitool.ui.components.SectionHeader
import com.msahil432.multitool.ui.theme.MultiToolTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockGroupEditScreen(
  groupId: Long,
  blockingRepository: BlockingRepository,
  onBack: () -> Unit
) {
  val coroutineScope = rememberCoroutineScope()
  var isLoaded by remember { mutableStateOf(groupId == 0L) }
  var name by remember { mutableStateOf("") }
  var enabled by remember { mutableStateOf(true) }
  var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
  var rules by remember { mutableStateOf<List<BlockRule>>(emptyList()) }
  var nextTempRuleId by remember { mutableLongStateOf(-1L) }

  var showDeleteGroupDialog by remember { mutableStateOf(false) }
  var ruleToDelete by remember { mutableStateOf<BlockRule?>(null) }
  var showAppPicker by remember { mutableStateOf(false) }
  var pickingGoalRuleId by remember { mutableStateOf<Long?>(null) }

  // Load existing group if editing
  LaunchedEffect(groupId) {
    if (groupId != 0L) {
      val existingGroup = blockingRepository.getGroupById(groupId)
      if (existingGroup != null) {
        name = existingGroup.name
        enabled = existingGroup.enabled
        selectedPackages = existingGroup.packageNames
          .split(';')
          .filter { it.isNotBlank() }
          .toSet()
        val existingRules = blockingRepository.getRulesForGroupSync(groupId)
        rules = existingRules
      }
      isLoaded = true
    }
  }

  if (!isLoaded) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Edit Block Group") },
          navigationIcon = {
            IconButton(onClick = onBack) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
        )
      }
    ) { padding ->
      LoadingState(modifier = Modifier.padding(padding))
    }
    return
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            if (groupId == 0L) "New Block Group" else "Edit Block Group",
            style = MaterialTheme.typography.titleLarge
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (groupId != 0L) {
            IconButton(onClick = { showDeleteGroupDialog = true }) {
              Icon(
                Icons.Default.Delete,
                contentDescription = "Delete group",
                tint = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // ── 1. Group Name & Enabled Toggle ──
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Group Name") },
        placeholder = { Text("e.g., Social Media, Gaming") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(
            text = "Group Enabled",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
          )
          Text(
            text = "Enforce rules in this group",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(
          checked = enabled,
          onCheckedChange = { enabled = it }
        )
      }

      // ── 2. Target Apps Section ──
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
              )
              Text(
                text = "Target Apps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
              )
            }

            Button(
              onClick = { showAppPicker = true },
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Select (${selectedPackages.size})")
            }
          }

          if (selectedPackages.isEmpty()) {
            Text(
              text = "No apps selected. Tap 'Select' to add apps to this block group.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            val context = LocalContext.current
            val appLabels = remember(selectedPackages) {
              val pm = context.packageManager
              selectedPackages.map { pkg ->
                try {
                  val info = pm.getApplicationInfo(pkg, 0)
                  pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                  pkg
                }
              }.sorted()
            }

            Text(
              text = "${selectedPackages.size} apps: ${appLabels.joinToString(", ")}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // ── 3. Rules Section ──
      RulesHeaderWithAddMenu(
        onAddRule = { ruleType ->
          val newRule = when (ruleType) {
            BlockRuleType.SCHEDULE -> BlockRule(
              id = nextTempRuleId--,
              groupId = groupId,
              type = BlockRuleType.SCHEDULE,
              daysOfWeekMask = 0x1F, // Mon-Fri
              startMinuteOfDay = 540, // 09:00
              endMinuteOfDay = 1020 // 17:00
            )
            BlockRuleType.DAILY_QUOTA -> BlockRule(
              id = nextTempRuleId--,
              groupId = groupId,
              type = BlockRuleType.DAILY_QUOTA,
              dailyQuotaMinutes = 30
            )
            BlockRuleType.LAUNCH_LIMIT -> BlockRule(
              id = nextTempRuleId--,
              groupId = groupId,
              type = BlockRuleType.LAUNCH_LIMIT,
              maxLaunchesPerDay = 10
            )
            BlockRuleType.SESSION_LIMIT -> BlockRule(
              id = nextTempRuleId--,
              groupId = groupId,
              type = BlockRuleType.SESSION_LIMIT,
              maxSessionMinutes = 15,
              cooldownMinutes = 15
            )
            BlockRuleType.GOAL_UNLOCK -> BlockRule(
              id = nextTempRuleId--,
              groupId = groupId,
              type = BlockRuleType.GOAL_UNLOCK,
              goalPackageNames = null,
              goalRequiredMinutes = 20
            )
          }
          rules = rules + newRule
        }
      )

      if (rules.isEmpty()) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
          )
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "No rules added yet. Tap '+ Add Rule' above to configure blocking conditions.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        rules.forEach { rule ->
          RuleEditorCard(
            rule = rule,
            onRuleUpdated = { updated ->
              rules = rules.map { if (it.id == rule.id) updated else it }
            },
            onDeleteRule = {
              ruleToDelete = rule
            },
            onPickGoalApps = {
              pickingGoalRuleId = rule.id
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // ── 4. Save Button ──
      val canSave = name.isNotBlank() && selectedPackages.isNotEmpty()
      Button(
        onClick = {
          coroutineScope.launch {
            val groupToSave = BlockGroup(
              id = if (groupId == 0L) 0 else groupId,
              name = name.trim(),
              packageNames = selectedPackages.joinToString(";"),
              enabled = enabled,
              createdAt = System.currentTimeMillis()
            )
            val savedGroupId = blockingRepository.upsertGroup(groupToSave)
            val effectiveGroupId = if (groupId == 0L) savedGroupId else groupId

            // Reconcile rules: delete old, insert new
            blockingRepository.deleteRulesForGroup(effectiveGroupId)
            for (r in rules) {
              blockingRepository.upsertRule(
                r.copy(id = 0, groupId = effectiveGroupId)
              )
            }
            onBack()
          }
        },
        enabled = canSave,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp),
        shape = RoundedCornerShape(16.dp)
      ) {
        Icon(Icons.Default.Save, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Save Block Group", style = MaterialTheme.typography.titleMedium)
      }

      if (!canSave) {
        val hint = when {
          name.isBlank() -> "Enter a group name to save"
          selectedPackages.isEmpty() -> "Select at least 1 target app to save"
          else -> ""
        }
        Text(
          text = hint,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(horizontal = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }

  // ── Target App Picker Dialog ──
  if (showAppPicker) {
    AppPicker(
      initialSelectedPackages = selectedPackages,
      onDismiss = { showAppPicker = false },
      onConfirm = { updated ->
        selectedPackages = updated
        showAppPicker = false
      }
    )
  }

  // ── Goal-based Unlock App Picker Dialog ──
  pickingGoalRuleId?.let { targetRuleId ->
    val targetRule = rules.find { it.id == targetRuleId }
    val initialGoalApps = targetRule?.goalPackageNames?.split(';')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    AppPicker(
      title = "Select Productive Apps",
      initialSelectedPackages = initialGoalApps,
      onDismiss = { pickingGoalRuleId = null },
      onConfirm = { updated ->
        val updatedStr = if (updated.isEmpty()) null else updated.joinToString(";")
        rules = rules.map {
          if (it.id == targetRuleId) it.copy(goalPackageNames = updatedStr) else it
        }
        pickingGoalRuleId = null
      }
    )
  }

  // ── Delete Group Confirm Dialog ──
  if (showDeleteGroupDialog) {
    ConfirmDialog(
      title = "Delete Block Group?",
      text = "All associated rules and history will be permanently deleted.",
      confirmLabel = "Delete",
      onConfirm = {
        showDeleteGroupDialog = false
        coroutineScope.launch {
          val group = blockingRepository.getGroupById(groupId)
          if (group != null) {
            blockingRepository.deleteGroup(group)
          }
          onBack()
        }
      },
      onDismiss = { showDeleteGroupDialog = false }
    )
  }

  // ── Delete Rule Confirm Dialog ──
  ruleToDelete?.let { rule ->
    ConfirmDialog(
      title = "Delete Rule?",
      text = "Are you sure you want to remove this ${rule.type.displayName()} rule?",
      confirmLabel = "Delete",
      onConfirm = {
        rules = rules.filter { it.id != rule.id }
        ruleToDelete = null
      },
      onDismiss = { ruleToDelete = null }
    )
  }
}

@Composable
private fun RulesHeaderWithAddMenu(
  onAddRule: (BlockRuleType) -> Unit
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        Icons.Default.Security,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp)
      )
      Text(
        text = "Enforcement Rules",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
    }

    Box {
      FilledTonalButton(
        onClick = { menuExpanded = true },
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add Rule")
      }

      DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false }
      ) {
        BlockRuleType.entries.forEach { type ->
          DropdownMenuItem(
            text = { Text(type.displayName()) },
            leadingIcon = {
              Icon(type.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
            },
            onClick = {
              menuExpanded = false
              onAddRule(type)
            }
          )
        }
      }
    }
  }
}

@Composable
fun RuleEditorCard(
  rule: BlockRule,
  onRuleUpdated: (BlockRule) -> Unit,
  onDeleteRule: () -> Unit,
  onPickGoalApps: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // ── Rule Header ──
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            rule.type.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Text(
            text = rule.type.displayName(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Switch(
            checked = rule.enabled,
            onCheckedChange = { onRuleUpdated(rule.copy(enabled = it)) },
            modifier = Modifier.semantics {
              contentDescription = "Toggle ${rule.type.displayName()}"
            }
          )
          IconButton(
            onClick = onDeleteRule,
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              Icons.Default.DeleteOutline,
              contentDescription = "Delete rule",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 0.5.dp
      )

      // ── Specific Editor based on type ──
      when (rule.type) {
        BlockRuleType.SCHEDULE -> ScheduleRuleEditor(rule, onRuleUpdated)
        BlockRuleType.DAILY_QUOTA -> DailyQuotaRuleEditor(rule, onRuleUpdated)
        BlockRuleType.LAUNCH_LIMIT -> LaunchLimitRuleEditor(rule, onRuleUpdated)
        BlockRuleType.SESSION_LIMIT -> SessionLimitRuleEditor(rule, onRuleUpdated)
        BlockRuleType.GOAL_UNLOCK -> GoalUnlockRuleEditor(rule, onRuleUpdated, onPickGoalApps)
      }
    }
  }
}

// ── 1. Schedule Rule Editor ──
@Composable
private fun ScheduleRuleEditor(
  rule: BlockRule,
  onRuleUpdated: (BlockRule) -> Unit
) {
  val context = LocalContext.current
  val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = "Active Days",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      days.forEachIndexed { index, day ->
        val isSelected = (rule.daysOfWeekMask and (1 shl index)) != 0
        FilterChip(
          selected = isSelected,
          onClick = {
            val newMask = rule.daysOfWeekMask xor (1 shl index)
            onRuleUpdated(rule.copy(daysOfWeekMask = newMask))
          },
          label = { Text(day, style = MaterialTheme.typography.labelSmall) },
          shape = RoundedCornerShape(8.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Start Time
      OutlinedCard(
        modifier = Modifier
          .weight(1f)
          .clickable {
            showTimePicker(context, rule.startMinuteOfDay) { selectedMinute ->
              onRuleUpdated(rule.copy(startMinuteOfDay = selectedMinute))
            }
          },
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text("Start Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            formatMinuteOfDay(rule.startMinuteOfDay),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
        }
      }

      // End Time
      OutlinedCard(
        modifier = Modifier
          .weight(1f)
          .clickable {
            showTimePicker(context, rule.endMinuteOfDay) { selectedMinute ->
              onRuleUpdated(rule.copy(endMinuteOfDay = selectedMinute))
            }
          },
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text("End Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            formatMinuteOfDay(rule.endMinuteOfDay),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }
  }
}

// ── 2. Daily Quota Rule Editor ──
@Composable
private fun DailyQuotaRuleEditor(
  rule: BlockRule,
  onRuleUpdated: (BlockRule) -> Unit
) {
  var minutesText by remember(rule.dailyQuotaMinutes) { mutableStateOf(rule.dailyQuotaMinutes.toString()) }
  val presets = listOf(15, 30, 45, 60, 120)

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Daily Screen Time Limit across group",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      presets.forEach { preset ->
        val selected = rule.dailyQuotaMinutes == preset
        FilterChip(
          selected = selected,
          onClick = {
            onRuleUpdated(rule.copy(dailyQuotaMinutes = preset))
          },
          label = { Text("${preset}m") },
          shape = RoundedCornerShape(8.dp)
        )
      }
    }

    OutlinedTextField(
      value = minutesText,
      onValueChange = { input ->
        val digits = input.filter { it.isDigit() }
        minutesText = digits
        val value = digits.toIntOrNull() ?: 0
        onRuleUpdated(rule.copy(dailyQuotaMinutes = value))
      },
      label = { Text("Custom Minutes / Day") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      shape = RoundedCornerShape(12.dp)
    )
  }
}

// ── 3. Launch Limit Rule Editor ──
@Composable
private fun LaunchLimitRuleEditor(
  rule: BlockRule,
  onRuleUpdated: (BlockRule) -> Unit
) {
  var limitText by remember(rule.maxLaunchesPerDay) { mutableStateOf(rule.maxLaunchesPerDay.toString()) }
  val presets = listOf(5, 10, 15, 20, 30)

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Max launches allowed per day",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      presets.forEach { preset ->
        val selected = rule.maxLaunchesPerDay == preset
        FilterChip(
          selected = selected,
          onClick = {
            onRuleUpdated(rule.copy(maxLaunchesPerDay = preset))
          },
          label = { Text("$preset") },
          shape = RoundedCornerShape(8.dp)
        )
      }
    }

    OutlinedTextField(
      value = limitText,
      onValueChange = { input ->
        val digits = input.filter { it.isDigit() }
        limitText = digits
        val value = digits.toIntOrNull() ?: 0
        onRuleUpdated(rule.copy(maxLaunchesPerDay = value))
      },
      label = { Text("Max Launches per day") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      shape = RoundedCornerShape(12.dp)
    )
  }
}

// ── 4. Session Limit Rule Editor ──
@Composable
private fun SessionLimitRuleEditor(
  rule: BlockRule,
  onRuleUpdated: (BlockRule) -> Unit
) {
  var sessionText by remember(rule.maxSessionMinutes) { mutableStateOf(rule.maxSessionMinutes.toString()) }
  var cooldownText by remember(rule.cooldownMinutes) { mutableStateOf(rule.cooldownMinutes.toString()) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = "Continuous usage cap & required break cooldown",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      OutlinedTextField(
        value = sessionText,
        onValueChange = { input ->
          val digits = input.filter { it.isDigit() }
          sessionText = digits
          val value = digits.toIntOrNull() ?: 0
          onRuleUpdated(rule.copy(maxSessionMinutes = value))
        },
        label = { Text("Max Session (min)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
      )

      OutlinedTextField(
        value = cooldownText,
        onValueChange = { input ->
          val digits = input.filter { it.isDigit() }
          cooldownText = digits
          val value = digits.toIntOrNull() ?: 0
          onRuleUpdated(rule.copy(cooldownMinutes = value))
        },
        label = { Text("Cooldown (min)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
      )
    }
  }
}

// ── 5. Goal Unlock Rule Editor ──
@Composable
private fun GoalUnlockRuleEditor(
  rule: BlockRule,
  onRuleUpdated: (BlockRule) -> Unit,
  onPickGoalApps: () -> Unit
) {
  val context = LocalContext.current
  var minutesText by remember(rule.goalRequiredMinutes) { mutableStateOf(rule.goalRequiredMinutes.toString()) }

  val goalPkgs = remember(rule.goalPackageNames) {
    rule.goalPackageNames?.split(';')?.filter { it.isNotBlank() } ?: emptyList()
  }

  val goalAppLabels = remember(goalPkgs) {
    val pm = context.packageManager
    goalPkgs.map { pkg ->
      try {
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
      } catch (_: Exception) {
        pkg
      }
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = "Unlock this group by spending time in productive apps",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = if (goalPkgs.isEmpty()) "No productive apps selected" else "${goalPkgs.size} apps: ${goalAppLabels.joinToString(", ")}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f)
      )
      Spacer(modifier = Modifier.width(8.dp))
      OutlinedButton(
        onClick = onPickGoalApps,
        shape = RoundedCornerShape(12.dp)
      ) {
        Text("Select Apps")
      }
    }

    OutlinedTextField(
      value = minutesText,
      onValueChange = { input ->
        val digits = input.filter { it.isDigit() }
        minutesText = digits
        val value = digits.toIntOrNull() ?: 0
        onRuleUpdated(rule.copy(goalRequiredMinutes = value))
      },
      label = { Text("Productive Minutes Required") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      shape = RoundedCornerShape(12.dp)
    )
  }
}

private fun showTimePicker(
  context: Context,
  currentMinuteOfDay: Int,
  onTimeSelected: (Int) -> Unit
) {
  val hour = (currentMinuteOfDay / 60).coerceIn(0, 23)
  val minute = (currentMinuteOfDay % 60).coerceIn(0, 59)

  TimePickerDialog(
    context,
    { _, selectedHour, selectedMinute ->
      onTimeSelected(selectedHour * 60 + selectedMinute)
    },
    hour,
    minute,
    true
  ).show()
}

private fun BlockRuleType.displayName(): String {
  return when (this) {
    BlockRuleType.SCHEDULE -> "Schedule"
    BlockRuleType.DAILY_QUOTA -> "Daily Quota"
    BlockRuleType.LAUNCH_LIMIT -> "Launch Cap"
    BlockRuleType.SESSION_LIMIT -> "Session Limit"
    BlockRuleType.GOAL_UNLOCK -> "Goal-Based Unlock"
  }
}

private fun BlockRuleType.icon(): ImageVector {
  return when (this) {
    BlockRuleType.SCHEDULE -> Icons.Default.Schedule
    BlockRuleType.DAILY_QUOTA -> Icons.Default.Timer
    BlockRuleType.LAUNCH_LIMIT -> Icons.Default.RocketLaunch
    BlockRuleType.SESSION_LIMIT -> Icons.Default.HourglassBottom
    BlockRuleType.GOAL_UNLOCK -> Icons.Default.Flag
  }
}

@Preview(showBackground = true, name = "BlockGroupEditScreen Light")
@Composable
private fun BlockGroupEditScreenPreviewLight() {
  MultiToolTheme {
    Surface {
      RuleEditorCard(
        rule = BlockRule(
          id = 1,
          groupId = 1,
          type = BlockRuleType.DAILY_QUOTA,
          dailyQuotaMinutes = 30
        ),
        onRuleUpdated = {},
        onDeleteRule = {},
        onPickGoalApps = {}
      )
    }
  }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "BlockGroupEditScreen Dark")
@Composable
private fun BlockGroupEditScreenPreviewDark() {
  MultiToolTheme {
    Surface {
      RuleEditorCard(
        rule = BlockRule(
          id = 1,
          groupId = 1,
          type = BlockRuleType.SCHEDULE,
          daysOfWeekMask = 0x1F,
          startMinuteOfDay = 540,
          endMinuteOfDay = 1020
        ),
        onRuleUpdated = {},
        onDeleteRule = {},
        onPickGoalApps = {}
      )
    }
  }
}
