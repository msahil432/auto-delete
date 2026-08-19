package com.msahil432.multitool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.msahil432.multitool.blocking.StrictModeController
import com.msahil432.multitool.data.DeactivationFlow
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.StrictModeState
import com.msahil432.multitool.data.UnlockMethod
import com.msahil432.multitool.data.UnlockParams
import com.msahil432.multitool.dataStore
import com.msahil432.multitool.ui.components.ConfirmDialog
import com.msahil432.multitool.ui.screens.challenge.ChallengeHost
import com.msahil432.multitool.ui.theme.MultiToolTheme
import com.msahil432.multitool.util.PasswordSecurity
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictModeScreen(
    onBack: () -> Unit,
    settingsRepository: SettingsRepository? = null,
    innerPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember(settingsRepository, context) {
        settingsRepository ?: SettingsRepository(context.dataStore)
    }

    val strictState by StrictModeController.state.collectAsState()
    val tamperAlarmEnabled by repo.tamperAlarmEnabled.collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()

    var selectedMethod by remember { mutableStateOf(UnlockMethod.TEXT) }
    var selectedDurationHours by remember { mutableIntStateOf(0) } // 0 = indefinite
    var customTextLength by remember { mutableIntStateOf(100) }
    var pinValue by remember { mutableStateOf("") }
    var cooldownMinutes by remember { mutableIntStateOf(15) }
    var qrSecretValue by remember {
        mutableStateOf("STRICT-UNLOCK-" + generateRandomSecret(8))
    }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var activeChallengeMethod by remember { mutableStateOf<UnlockMethod?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Strict Mode", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val bottomNavPadding = maxOf(padding.calculateBottomPadding(), innerPadding.calculateBottomPadding())
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomNavPadding + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (strictState.isActive) {
                // ── Active Strict Mode Card ──
                ActiveStrictModeContent(
                    state = strictState,
                    tamperAlarmEnabled = tamperAlarmEnabled,
                    onDeactivateClick = {
                        val flow = StrictModeController.requestDeactivation(cooldownMinutes)
                        when (flow) {
                            is DeactivationFlow.TimeExpired -> {
                                Toast.makeText(context, "Session expired. Strict mode deactivated.", Toast.LENGTH_LONG).show()
                            }
                            is DeactivationFlow.ChallengeRequired -> {
                                activeChallengeMethod = flow.method
                            }
                            is DeactivationFlow.NotActive -> {
                                Toast.makeText(context, "Strict mode is not active.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            } else {
                // ── Inactive / Setup View ──
                SetupStrictModeContent(
                    selectedMethod = selectedMethod,
                    onMethodSelected = { selectedMethod = it },
                    selectedDurationHours = selectedDurationHours,
                    onDurationSelected = { selectedDurationHours = it },
                    customTextLength = customTextLength,
                    onTextLengthChanged = { customTextLength = it },
                    pinValue = pinValue,
                    onPinChanged = { pinValue = it },
                    cooldownMinutes = cooldownMinutes,
                    onCooldownChanged = { cooldownMinutes = it },
                    qrSecretValue = qrSecretValue,
                    onQrSecretChanged = { qrSecretValue = it },
                    tamperAlarmEnabled = tamperAlarmEnabled,
                    onTamperAlarmChanged = { enabled ->
                        coroutineScope.launch { repo.setTamperAlarmEnabled(enabled) }
                    },
                    onActivateClick = { showConfirmDialog = true }
                )
            }
        }
    }

    // ── Confirmation Dialog before Activation ──
    if (showConfirmDialog) {
        ConfirmDialog(
            title = "Activate Strict Mode?",
            text = "Once activated, you cannot delete, weaken, or disable any focus rules until the session ends or you pass the ${selectedMethod.displayName()} challenge.",
            confirmLabel = "Activate Now",
            onConfirm = {
                showConfirmDialog = false
                val now = System.currentTimeMillis()
                val endAt = if (selectedDurationHours > 0) {
                    now + (selectedDurationHours * 3600_000L)
                } else {
                    0L
                }
                val passwordHash = if (selectedMethod == UnlockMethod.PIN && pinValue.isNotBlank()) {
                    PasswordSecurity.hashPassword(pinValue)
                } else {
                    null
                }
                val params = UnlockParams(
                    textLength = customTextLength,
                    masterPasswordHash = passwordHash,
                    cooldownMinutes = cooldownMinutes,
                    qrExpectedValue = if (selectedMethod == UnlockMethod.QR) qrSecretValue else null
                )
                StrictModeController.activate(
                    method = selectedMethod,
                    endAt = endAt,
                    params = params
                )
                Toast.makeText(context, "Strict Mode is now active.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    // ── Active Challenge Full-Screen Modal ──
    if (activeChallengeMethod != null) {
        Dialog(
            onDismissRequest = {
                activeChallengeMethod = null
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                ChallengeHost(
                    settingsRepository = repo,
                    targetMethod = activeChallengeMethod,
                    onSuccess = {
                        activeChallengeMethod = null
                        Toast.makeText(context, "Strict Mode deactivated successfully.", Toast.LENGTH_SHORT).show()
                    },
                    onCancel = {
                        activeChallengeMethod = null
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveStrictModeContent(
    state: StrictModeState,
    tamperAlarmEnabled: Boolean,
    onDeactivateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM dd, hh:mm a") }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Active Strict Mode",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column {
                    Text(
                        text = "Strict Mode is Active",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Rules cannot be weakened or deleted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Details rows
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow(
                    label = "Session Started",
                    value = if (state.startedAt > 0) {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(state.startedAt), ZoneId.systemDefault()).format(formatter)
                    } else {
                        "Active"
                    }
                )

                DetailRow(
                    label = "Scheduled End",
                    value = if (state.endAt > 0) {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(state.endAt), ZoneId.systemDefault()).format(formatter)
                    } else {
                        "Until manually unlocked"
                    }
                )

                DetailRow(
                    label = "Unlock Challenge",
                    value = state.unlockMethod.displayName()
                )

                DetailRow(
                    label = "Tamper Siren Alarm",
                    value = if (tamperAlarmEnabled) "Armed (Siren enabled)" else "Disabled"
                )

                if (state.pendingDeactivationAt > 0) {
                    val remainingMinutes = ((state.pendingDeactivationAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
                    DetailRow(
                        label = "Cooldown Pending",
                        value = if (remainingMinutes > 0) "$remainingMinutes minutes left" else "Ready to deactivate"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDeactivateClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deactivate Strict Mode", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SetupStrictModeContent(
    selectedMethod: UnlockMethod,
    onMethodSelected: (UnlockMethod) -> Unit,
    selectedDurationHours: Int,
    onDurationSelected: (Int) -> Unit,
    customTextLength: Int,
    onTextLengthChanged: (Int) -> Unit,
    pinValue: String,
    onPinChanged: (String) -> Unit,
    cooldownMinutes: Int,
    onCooldownChanged: (Int) -> Unit,
    qrSecretValue: String,
    onQrSecretChanged: (String) -> Unit,
    tamperAlarmEnabled: Boolean,
    onTamperAlarmChanged: (Boolean) -> Unit,
    onActivateClick: () -> Unit
) {
    val context = LocalContext.current

    // ── 1. Explanation Banner ──
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Asymmetric Lock-In Guarantee",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "While strict mode is active, focus rules can be made stricter at any time, but never weakened, reduced, or deleted. Device Admin protection will prevent premature uninstalling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // ── 2. Duration / End Time ──
    Text(
        text = "Session Duration",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )

    val durationOptions = listOf(
        0 to "Indefinite",
        1 to "1 Hour",
        4 to "4 Hours",
        8 to "8 Hours",
        24 to "24 Hours"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        durationOptions.forEach { (hours, label) ->
            FilterChip(
                selected = selectedDurationHours == hours,
                onClick = { onDurationSelected(hours) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }

    // ── 3. Unlock Method Selector ──
    Text(
        text = "Unlock Challenge Method",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UnlockMethod.entries.forEach { method ->
            val isSelected = method == selectedMethod
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMethodSelected(method) },
                shape = RoundedCornerShape(14.dp),
                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onMethodSelected(method) },
                        modifier = Modifier.semantics {
                            contentDescription = "Select ${method.displayName()}"
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = method.displayName(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = method.description(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // ── 4. Method-specific Customization ──
    when (selectedMethod) {
        UnlockMethod.TEXT -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Challenge Text Length: $customTextLength characters",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 200, 500).forEach { len ->
                        FilterChip(
                            selected = customTextLength == len,
                            onClick = { onTextLengthChanged(len) },
                            label = { Text("$len chars") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }
        UnlockMethod.PIN -> {
            OutlinedTextField(
                value = pinValue,
                onValueChange = onPinChanged,
                label = { Text("Set Master PIN / Passcode") },
                placeholder = { Text("e.g., 1234") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }
        UnlockMethod.COOLDOWN -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cooldown Delay: $cooldownMinutes minutes",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { mins ->
                        FilterChip(
                            selected = cooldownMinutes == mins,
                            onClick = { onCooldownChanged(mins) },
                            label = { Text("${mins}m") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }
        UnlockMethod.QR -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "A physical QR code scan will be required to unlock. Print or encode this secret code into a QR code and place it in another room:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = qrSecretValue,
                    onValueChange = onQrSecretChanged,
                    label = { Text("Expected QR Key / Secret") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    trailingIcon = {
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("QR Secret", qrSecretValue))
                            Toast.makeText(context, "QR secret copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy QR Secret")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }

    // ── 5. Tamper Alarm Option ──
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tamper Alarm Siren",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sound a loud siren if system settings (App Info, Accessibility, Device Admin) are opened during strict mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = tamperAlarmEnabled,
                onCheckedChange = onTamperAlarmChanged,
                modifier = Modifier.semantics {
                    contentDescription = "Toggle Tamper Alarm Siren"
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // ── 6. Activate Button ──
    Button(
        onClick = onActivateClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.Lock, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Activate Strict Mode", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun generateRandomSecret(length: Int): String {
    val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    val rnd = SecureRandom()
    return (1..length).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
}

@Preview(showBackground = true, name = "Strict Mode Inactive")
@Composable
private fun StrictModeScreenInactivePreview() {
    MultiToolTheme {
        SetupStrictModeContent(
            selectedMethod = UnlockMethod.TEXT,
            onMethodSelected = {},
            selectedDurationHours = 4,
            onDurationSelected = {},
            customTextLength = 100,
            onTextLengthChanged = {},
            pinValue = "",
            onPinChanged = {},
            cooldownMinutes = 15,
            onCooldownChanged = {},
            qrSecretValue = "STRICT-UNLOCK-DEMO",
            onQrSecretChanged = {},
            tamperAlarmEnabled = false,
            onTamperAlarmChanged = {},
            onActivateClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Strict Mode Active")
@Composable
private fun StrictModeScreenActivePreview() {
    MultiToolTheme {
        ActiveStrictModeContent(
            state = StrictModeState(
                isActive = true,
                startedAt = System.currentTimeMillis() - 3600_000L,
                endAt = System.currentTimeMillis() + 7200_000L,
                unlockMethod = UnlockMethod.COOLDOWN,
                pendingDeactivationAt = 0L
            ),
            tamperAlarmEnabled = true,
            onDeactivateClick = {}
        )
    }
}
