package com.msahil432.multitool.ui.screens.challenge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.SecureRandom

/**
 * High-friction Text Match Challenge.
 * User must manually type an exact random alphanumeric string character-by-character.
 * Clipboard pasting is prohibited and no live progress is shown.
 */
@Composable
fun TextMatchChallenge(
    targetLength: Int = 100,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val length = targetLength.coerceIn(50, 1000)
    val targetText = remember(length) { generateChallengeText(length) }
    var userInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

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
                    Icons.Default.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Text Match Challenge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel Challenge")
            }
        }

        Text(
            text = "Retype the text below exactly as shown ($length characters). Clipboard pasting is disabled. Any discrepancy on submission will be rejected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── Monospace Target Box ──
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Target text to type: $targetText"
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                text = targetText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp)
            )
        }

        // ── Input Field (paste disabled by rejecting multiple chars in one update) ──
        OutlinedTextField(
            value = userInput,
            onValueChange = { newValue ->
                isError = false
                errorMessage = ""
                // Disallow bulk paste: allow only 1-char additions or deletions
                if (newValue.length <= userInput.length + 1) {
                    userInput = newValue
                }
            },
            label = { Text("Retype text here") },
            placeholder = { Text("Start typing...") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            isError = isError,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Challenge text input"
                },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            trailingIcon = {
                if (userInput.isNotEmpty()) {
                    IconButton(onClick = { userInput = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear input")
                    }
                }
            }
        )

        // ── Error Alert ──
        AnimatedVisibility(visible = isError) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // ── Actions ──
        Button(
            onClick = {
                focusManager.clearFocus()
                if (userInput == targetText) {
                    isError = false
                    onSuccess()
                } else {
                    isError = true
                    errorMessage = "Text mismatch. Zero tolerance policy: please ensure every character matches exactly."
                }
            },
            enabled = userInput.isNotEmpty(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Verify & Unlock", style = MaterialTheme.typography.titleMedium)
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

/**
 * Generates an alphanumeric challenge string with readable characters.
 */
private fun generateChallengeText(length: Int): String {
    val charPool = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    val random = SecureRandom()
    val bytes = CharArray(length)
    for (i in 0 until length) {
        bytes[i] = charPool[random.nextInt(charPool.length)]
    }
    return String(bytes)
}
