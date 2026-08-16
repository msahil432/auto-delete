package com.msahil432.multitool.ui.screens.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.msahil432.multitool.util.PasswordSecurity
import com.msahil432.multitool.util.SecureScreen
import kotlinx.coroutines.delay

private const val MAX_ATTEMPTS = 5
private const val LOCKOUT_SECONDS = 30

/**
 * High-friction PIN / Master Password Challenge.
 * Compares against salted hash and enforces lockout on repeated failures.
 */
@Composable
fun PinChallenge(
    storedPasswordHash: String?,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    SecureScreen()
    var pinInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var remainingAttempts by remember { mutableIntStateOf(MAX_ATTEMPTS) }
    var lockoutRemainingSeconds by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Lockout countdown timer
    LaunchedEffect(lockoutRemainingSeconds) {
        if (lockoutRemainingSeconds > 0) {
            delay(1000L)
            lockoutRemainingSeconds -= 1
            if (lockoutRemainingSeconds == 0) {
                remainingAttempts = MAX_ATTEMPTS
                errorMessage = ""
                isError = false
            }
        }
    }

    val isLockedOut = lockoutRemainingSeconds > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ──
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
                    Icons.Default.Password,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Master PIN Challenge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel Challenge")
            }
        }

        Text(
            text = "Enter your master PIN or passphrase to deactivate strict mode. $remainingAttempts attempts remaining before temporary lockout.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── Lockout Banner ──
        AnimatedVisibility(visible = isLockedOut) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Column {
                        Text(
                            text = "Too many failed attempts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Please wait $lockoutRemainingSeconds seconds before trying again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // ── PIN Input ──
        OutlinedTextField(
            value = pinInput,
            onValueChange = {
                if (!isLockedOut) {
                    pinInput = it
                    isError = false
                    errorMessage = ""
                }
            },
            enabled = !isLockedOut,
            label = { Text("Master PIN / Password") },
            placeholder = { Text("Enter passcode") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            isError = isError,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Master password input field"
                }
        )

        // ── Error feedback ──
        AnimatedVisibility(visible = isError && !isLockedOut) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // ── Actions ──
        Button(
            onClick = {
                focusManager.clearFocus()
                if (storedPasswordHash.isNullOrBlank()) {
                    // No hash configured -> accept or reject
                    onSuccess()
                    return@Button
                }

                val verified = PasswordSecurity.verifyPassword(pinInput, storedPasswordHash)
                if (verified) {
                    isError = false
                    onSuccess()
                } else {
                    val nextAttempts = remainingAttempts - 1
                    if (nextAttempts <= 0) {
                        remainingAttempts = 0
                        lockoutRemainingSeconds = LOCKOUT_SECONDS
                        pinInput = ""
                    } else {
                        remainingAttempts = nextAttempts
                        isError = true
                        errorMessage = "Incorrect passcode. $remainingAttempts attempts left."
                    }
                }
            },
            enabled = !isLockedOut && pinInput.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(if (isLockedOut) "Locked (${lockoutRemainingSeconds}s)" else "Verify & Unlock", style = MaterialTheme.typography.titleMedium)
        }

        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Cancel", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
