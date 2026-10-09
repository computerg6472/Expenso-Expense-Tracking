package com.spendwise.app.ui.screen.forecast

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.spendwise.app.ui.theme.DueRose
import com.spendwise.app.ui.theme.IncomeGreen
import com.spendwise.app.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiForecastScreen(
    navController: NavController,
    viewModel: AiForecastViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            com.spendwise.app.ui.components.TopBanner(
                bottomCornerRadius = 24.dp,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("✨", fontSize = 20.sp)
                            Text(
                                text = "AI Forecast",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Real-Time Velocity & Predictive Trajectory",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Day ${uiState.currentDay}/${uiState.daysInMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "forecast_loading_crossfade"
        ) { loading ->
            if (loading) {
                com.spendwise.app.ui.components.ForecastLoadingSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                ) {
                    // Section 1: AI Financial Intelligence & Velocity Card
                    item {
                        AiHealthVelocityCard(uiState = uiState)
                    }

                    // Section 2: Predictive Month-End Trajectory Forecast
                    item {
                        PredictiveTrajectoryCard(uiState = uiState)
                    }

                    // Section 3: Interactive What-If Scenario Simulator
                    item {
                        WhatIfSimulatorCard(
                            uiState = uiState,
                            onSelectReduction = viewModel::updateWhatIfReduction
                        )
                    }

                    // Section 4: Category Burn Velocity Radar
                    if (uiState.categoryVelocities.isNotEmpty()) {
                        item {
                            Text(
                                text = "Category Burn Radar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        items(uiState.categoryVelocities) { cat ->
                            CategoryVelocityItem(cat = cat, currency = uiState.currencySymbol)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiHealthVelocityCard(uiState: AiForecastUiState) {
    val currency = uiState.currencySymbol
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row with Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text("✨", fontSize = 16.sp)
                    Text(
                        text = "AI Financial Intelligence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        uiState.healthScore >= 80 -> IncomeGreen.copy(alpha = 0.15f)
                        uiState.healthScore >= 60 -> MaterialTheme.colorScheme.primaryContainer
                        else -> DueRose.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = "${uiState.healthScore}/100 • ${uiState.healthStatus}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        softWrap = false,
                        maxLines = 1,
                        color = when {
                            uiState.healthScore >= 80 -> IncomeGreen
                            uiState.healthScore >= 60 -> MaterialTheme.colorScheme.primary
                            else -> DueRose
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Real-time burn rate & runway forecast",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )


            // 4-Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Daily Burn Rate
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Daily Burn Rate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$currency%,.0f / day".format(uiState.dailyBurnRate),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Projected Spend
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Projected Spend",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$currency%,.0f".format(uiState.projectedMonthEndSpend),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.projectedSurplus >= 0) MaterialTheme.colorScheme.primary else DueRose
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Salary Runway
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Salary Runway",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${uiState.remainingRunwayDays} Days Left",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Expected Surplus / Deficit
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (uiState.projectedSurplus >= 0) "Expected Surplus" else "Expected Deficit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (uiState.projectedSurplus >= 0) "+$currency%,.0f".format(uiState.projectedSurplus) else "-$currency%,.0f".format(-uiState.projectedSurplus),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.projectedSurplus >= 0) IncomeGreen else DueRose
                        )
                    }
                }
            }

            // Proactive AI Nudge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("💡", fontSize = 18.sp)
                    Text(
                        text = uiState.aiInsightNudge,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PredictiveTrajectoryCard(uiState: AiForecastUiState) {
    val currency = uiState.currencySymbol
    val totalSpent = (uiState.dashboard?.spentViaIncome ?: 0.0) + (uiState.dashboard?.creditCardSpendThisMonth ?: 0.0)
    val salary = uiState.dashboard?.salary ?: 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text("🔮", fontSize = 16.sp)
                    Text(
                        text = "Month-End Trajectory",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Predictive",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Pacing bar
            val progress = if (uiState.projectedMonthEndSpend > 0) {
                (totalSpent / uiState.projectedMonthEndSpend).toFloat().coerceIn(0f, 1f)
            } else 0f

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current: $currency%,.0f".format(totalSpent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "Projected: $currency%,.0f".format(uiState.projectedMonthEndSpend),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = if (uiState.projectedSurplus >= 0) MaterialTheme.colorScheme.primary else DueRose
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (uiState.projectedSurplus >= 0) MaterialTheme.colorScheme.primary else DueRose,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Pacing metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Safe Daily Spend Ceiling",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$currency%,.0f / day".format(uiState.safeDailyCeiling),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = IncomeGreen
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Month Time Elapsed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(uiState.currentDay.toFloat() / uiState.daysInMonth * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun WhatIfSimulatorCard(
    uiState: AiForecastUiState,
    onSelectReduction: (Double) -> Unit
) {
    val currency = uiState.currencySymbol
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🎛️", fontSize = 16.sp)
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "What-If Savings Simulator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Simulate daily spend adjustments for the next ${uiState.remainingRunwayDays} days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(100.0, 200.0, 500.0, 1000.0).forEach { reduction ->
                    val isSelected = uiState.whatIfDailyReduction == reduction
                    val animatedBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "whatif_bg"
                    )
                    val animatedContentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "whatif_text"
                    )
                    val animatedBorderColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "whatif_border"
                    )

                    OutlinedButton(
                        onClick = { onSelectReduction(reduction) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = animatedBg,
                            contentColor = animatedContentColor
                        ),
                        border = BorderStroke(1.dp, animatedBorderColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "-$currency${reduction.toInt()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = IncomeGreen.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Additional Month-End Surplus",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AnimatedContent(
                            targetState = uiState.whatIfProjectedSavings,
                            transitionSpec = {
                                (slideInVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) { height -> height / 2 } + fadeIn(tween(180))) togetherWith
                                (slideOutVertically(animationSpec = tween(160, easing = FastOutSlowInEasing)) { height -> -height / 2 } + fadeOut(tween(140)))
                            },
                            label = "whatif_savings_anim"
                        ) { savings ->
                            Text(
                                text = "+$currency%,.0f".format(savings),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = IncomeGreen
                            )
                        }
                    }
                    Text(
                        text = "Over ${uiState.remainingRunwayDays} Days",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryVelocityItem(
    cat: CategoryVelocityRisk,
    currency: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = when (cat.categoryName.lowercase()) {
                                "food" -> "🍔"
                                "transport" -> "🚗"
                                "shopping" -> "🛍️"
                                "bills" -> "🧾"
                                "entertainment" -> "🎬"
                                "health" -> "💊"
                                else -> "🏷️"
                            },
                            fontSize = 18.sp
                        )
                    }
                }

                Column {
                    Text(
                        text = cat.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Total: $currency%,.0f".format(cat.totalSpend),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currency%,.0f / day".format(cat.dailyBurn),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (cat.riskLevel) {
                        "High Burn" -> DueRose.copy(alpha = 0.15f)
                        "Moderate" -> WarningAmber.copy(alpha = 0.15f)
                        else -> IncomeGreen.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = cat.riskLevel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (cat.riskLevel) {
                            "High Burn" -> DueRose
                            "Moderate" -> WarningAmber
                            else -> IncomeGreen
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
