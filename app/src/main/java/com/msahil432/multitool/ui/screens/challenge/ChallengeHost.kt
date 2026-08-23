package com.msahil432.multitool.ui.screens.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.msahil432.multitool.blocking.StrictModeController
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.UnlockMethod
import com.msahil432.multitool.ui.theme.MultiToolTheme

/**
 * Host container routing to the active unlock challenge.
 * On challenge success, automatically invokes [StrictModeController.completeDeactivation].
 */
@Composable
fun ChallengeHost(
    settingsRepository: SettingsRepository,
    targetMethod: UnlockMethod? = null,
    onSuccess: () -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strictState by StrictModeController.state.collectAsState()
    val method = targetMethod ?: strictState.unlockMethod

    val masterPasswordHash by settingsRepository.masterPasswordHash.collectAsState(initial = null)
    val qrExpectedValue by settingsRepository.qrExpectedValue.collectAsState(initial = null)
    val textLength by settingsRepository.textChallengeLength.collectAsState(initial = 100)

    val handleSuccess = {
        StrictModeController.completeDeactivation()
        onSuccess()
    }

    val handleCancel = {
        StrictModeController.cancelPendingDeactivation()
        onCancel()
    }

    ChallengeHostContent(
        method = method,
        textLength = textLength,
        masterPasswordHash = masterPasswordHash,
        qrExpectedValue = qrExpectedValue,
        pendingDeactivationAt = strictState.pendingDeactivationAt,
        onSuccess = handleSuccess,
        onCancel = handleCancel,
        modifier = modifier
    )
}

/**
 * Stateless content composable for [ChallengeHost], rendering the appropriate challenge based on [method].
 */
@Composable
fun ChallengeHostContent(
    method: UnlockMethod,
    textLength: Int = 100,
    masterPasswordHash: String? = null,
    qrExpectedValue: String? = null,
    pendingDeactivationAt: Long = 0L,
    onSuccess: () -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (method) {
            UnlockMethod.TEXT -> {
                TextMatchChallenge(
                    targetLength = textLength,
                    onSuccess = onSuccess,
                    onCancel = onCancel
                )
            }
            UnlockMethod.PIN -> {
                PinChallenge(
                    storedPasswordHash = masterPasswordHash,
                    onSuccess = onSuccess,
                    onCancel = onCancel
                )
            }
            UnlockMethod.COOLDOWN -> {
                CooldownChallenge(
                    pendingDeactivationAt = pendingDeactivationAt,
                    onSuccess = onSuccess,
                    onCancel = onCancel
                )
            }
            UnlockMethod.QR -> {
                QrChallenge(
                    expectedQrValue = qrExpectedValue,
                    onSuccess = onSuccess,
                    onCancel = onCancel
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "ChallengeHost Text Light")
@Composable
private fun ChallengeHostPreviewLight() {
    MultiToolTheme {
        ChallengeHostContent(
            method = UnlockMethod.TEXT,
            textLength = 50,
            onSuccess = {},
            onCancel = {}
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    name = "ChallengeHost Text Dark"
)
@Composable
private fun ChallengeHostPreviewDark() {
    MultiToolTheme {
        ChallengeHostContent(
            method = UnlockMethod.TEXT,
            textLength = 50,
            onSuccess = {},
            onCancel = {}
        )
    }
}

