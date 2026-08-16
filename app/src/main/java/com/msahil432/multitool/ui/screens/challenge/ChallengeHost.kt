package com.msahil432.multitool.ui.screens.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.msahil432.multitool.blocking.StrictModeController
import com.msahil432.multitool.data.SettingsRepository
import com.msahil432.multitool.data.UnlockMethod

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (method) {
            UnlockMethod.TEXT -> {
                TextMatchChallenge(
                    targetLength = textLength,
                    onSuccess = handleSuccess,
                    onCancel = handleCancel
                )
            }
            UnlockMethod.PIN -> {
                PinChallenge(
                    storedPasswordHash = masterPasswordHash,
                    onSuccess = handleSuccess,
                    onCancel = handleCancel
                )
            }
            UnlockMethod.COOLDOWN -> {
                CooldownChallenge(
                    pendingDeactivationAt = strictState.pendingDeactivationAt,
                    onSuccess = handleSuccess,
                    onCancel = handleCancel
                )
            }
            UnlockMethod.QR -> {
                QrChallenge(
                    expectedQrValue = qrExpectedValue,
                    onSuccess = handleSuccess,
                    onCancel = handleCancel
                )
            }
        }
    }
}
