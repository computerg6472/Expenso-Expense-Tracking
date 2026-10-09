package com.spendwise.app.ui.screen.startup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendwise.app.data.backup.RestoreStep
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Restore-in-progress screen (step-by-step)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoadingFinancesScreen(
    restoreStep: RestoreStep = RestoreStep.SignedIn,
    canSkip: Boolean = false,
    onSkip: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Auto-advance safety: if somehow stuck >15s, force skip
    LaunchedEffect(Unit) {
        delay(15_000L)
        onSkip?.invoke()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cloud icon with pulse
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(scale)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Restoring your Expenso",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Google Drive → Your Phone (Encrypted)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            // Step list
            RestoreStepList(currentStep = restoreStep)

            Spacer(Modifier.height(28.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .width(200.dp)
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Skip / Continue button
            AnimatedVisibility(
                visible = canSkip && onSkip != null,
                enter = fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(28.dp))
                    FilledTonalButton(
                        onClick = { onSkip?.invoke() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Continue to Expenso")
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreStepList(currentStep: RestoreStep) {
    val steps = listOf(
        StepInfo(
            label = "Signed in with Google",
            active = true,
            done = when (currentStep) {
                is RestoreStep.SignedIn -> false
                else -> true
            },
            icon = Icons.Default.AccountCircle
        ),
        StepInfo(
            label = "Reaching Google Drive",
            active = currentStep !is RestoreStep.SignedIn,
            done = when (currentStep) {
                is RestoreStep.SignedIn,
                is RestoreStep.ReachingCloud -> false
                else -> true
            },
            icon = Icons.Default.Cloud
        ),
        StepInfo(
            label = "Checking encrypted backup",
            active = currentStep !is RestoreStep.SignedIn && currentStep !is RestoreStep.ReachingCloud,
            done = when (currentStep) {
                is RestoreStep.SignedIn,
                is RestoreStep.ReachingCloud,
                is RestoreStep.CheckingBackup -> false
                else -> true
            },
            icon = Icons.Default.ManageSearch
        ),
        StepInfo(
            label = when (currentStep) {
                is RestoreStep.LoadingCurrentMonth -> "Loading this month (${currentStep.monthKey})"
                else -> "Loading this month"
            },
            active = currentStep is RestoreStep.LoadingCurrentMonth ||
                     currentStep is RestoreStep.LoadingHistory ||
                     currentStep is RestoreStep.VerifyingChecksums ||
                     currentStep is RestoreStep.Complete,
            done = currentStep is RestoreStep.LoadingHistory ||
                   currentStep is RestoreStep.VerifyingChecksums ||
                   currentStep is RestoreStep.Complete,
            icon = Icons.Default.CalendarMonth
        ),
        StepInfo(
            label = when (currentStep) {
                is RestoreStep.LoadingHistory ->
                    "Loading history (${currentStep.done}/${currentStep.total})"
                else -> "Loading history"
            },
            active = currentStep is RestoreStep.LoadingHistory ||
                     currentStep is RestoreStep.VerifyingChecksums ||
                     currentStep is RestoreStep.Complete,
            done = currentStep is RestoreStep.VerifyingChecksums || currentStep is RestoreStep.Complete,
            icon = Icons.Default.History
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEach { step ->
            StepRow(step)
        }
    }
}

private data class StepInfo(
    val label: String,
    val active: Boolean,
    val done: Boolean,
    val icon: ImageVector
)

@Composable
private fun StepRow(step: StepInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val iconColor = when {
            step.done -> MaterialTheme.colorScheme.primary
            step.active -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
        AnimatedContent(
            targetState = step.done,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "step_icon"
        ) { done ->
            Icon(
                imageVector = if (done) Icons.Default.CheckCircle else step.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = step.label,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                step.done -> MaterialTheme.colorScheme.onSurface
                step.active -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.outlineVariant
            },
            fontWeight = if (step.active && !step.done) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error screen (shown when restore fails after all retries)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RestoreErrorScreen(
    state: StartupState.RestoreError,
    onRetry: () -> Unit,
    onUseCachedData: () -> Unit,
    onSignOut: () -> Unit,
    onSkipToHome: () -> Unit = {}
) {
    val isPermissionDenied = state.errorTag == "PERMISSION_DENIED"
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isPermissionDenied) Icons.Default.CloudSync else Icons.Default.CloudOff,
                contentDescription = null,
                tint = if (isPermissionDenied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(72.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (isPermissionDenied) "Google Drive Authorization Required" else state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isPermissionDenied)
                    "Expenso needs access to your Google Drive AppData folder to restore your encrypted cloud backup. Tap 'Authorize Google Drive' to grant permission."
                else
                    state.detail,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.errorTag.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Error code: ${state.errorTag}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            Spacer(Modifier.height(36.dp))

            if (state.canRetry) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isPermissionDenied) Icons.Default.CloudSync else Icons.Default.Refresh,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPermissionDenied) "Authorize Google Drive & Restore" else "Retry")
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.canUseCachedData) {
                OutlinedButton(
                    onClick = onUseCachedData,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use cached data")
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = onSkipToHome,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start as new account (Skip)")
            }
            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onSignOut) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
