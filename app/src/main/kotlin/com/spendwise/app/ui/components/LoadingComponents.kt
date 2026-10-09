package com.spendwise.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable shimmer pulse modifier for loading skeleton placeholders.
 */
fun Modifier.shimmerPulse(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    this.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .shimmerPulse()
    )
}

/**
 * Polished loading skeleton for DashboardScreen
 */
@Composable
fun DashboardLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Balance Card Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkeletonBox(modifier = Modifier.size(width = 110.dp, height = 16.dp))
                    SkeletonBox(modifier = Modifier.size(width = 75.dp, height = 18.dp), cornerRadius = 6.dp)
                }
                Spacer(Modifier.height(14.dp))
                SkeletonBox(modifier = Modifier.size(width = 160.dp, height = 28.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBox(modifier = Modifier.size(width = 90.dp, height = 14.dp))
            }
        }

        // Summary Cards Row Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SkeletonBox(modifier = Modifier.size(width = 50.dp, height = 12.dp))
                        Spacer(Modifier.height(8.dp))
                        SkeletonBox(modifier = Modifier.size(width = 65.dp, height = 18.dp))
                    }
                }
            }
        }

        // Credit Card Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SkeletonBox(modifier = Modifier.size(width = 140.dp, height = 16.dp))
                Spacer(Modifier.height(10.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(8.dp), cornerRadius = 4.dp)
                Spacer(Modifier.height(10.dp))
                SkeletonBox(modifier = Modifier.size(width = 120.dp, height = 14.dp))
            }
        }

        // Category Section Skeleton
        SkeletonBox(modifier = Modifier.size(width = 100.dp, height = 18.dp))
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonBox(modifier = Modifier.size(36.dp), cornerRadius = 18.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SkeletonBox(modifier = Modifier.size(width = 80.dp, height = 14.dp))
                            SkeletonBox(modifier = Modifier.size(width = 50.dp, height = 10.dp))
                        }
                    }
                    SkeletonBox(modifier = Modifier.size(width = 60.dp, height = 16.dp))
                }
            }
        }
    }
}

/**
 * Polished loading skeleton for AiForecastScreen
 */
@Composable
fun ForecastLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // AI Health Card Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkeletonBox(modifier = Modifier.size(width = 120.dp, height = 18.dp))
                    SkeletonBox(modifier = Modifier.size(width = 80.dp, height = 22.dp), cornerRadius = 6.dp)
                }
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(60.dp), cornerRadius = 12.dp)
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(36.dp), cornerRadius = 10.dp)
            }
        }

        // Predictive Trajectory Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBox(modifier = Modifier.size(width = 180.dp, height = 18.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(120.dp), cornerRadius = 12.dp)
            }
        }
    }
}

/**
 * Polished loading skeleton for ReportsScreen matching exact KPI, Donut Chart, and Breakdown layout.
 */
@Composable
fun ReportsLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // KPI Grid Skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(2) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SkeletonBox(modifier = Modifier.size(width = 80.dp, height = 12.dp))
                        Spacer(Modifier.height(8.dp))
                        SkeletonBox(modifier = Modifier.size(width = 110.dp, height = 24.dp))
                        Spacer(Modifier.height(6.dp))
                        SkeletonBox(modifier = Modifier.size(width = 60.dp, height = 12.dp))
                    }
                }
            }
        }

        // Title Skeleton
        SkeletonBox(modifier = Modifier.size(width = 160.dp, height = 20.dp))

        // Donut Chart Card Skeleton
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                SkeletonBox(
                    modifier = Modifier.size(175.dp),
                    cornerRadius = 90.dp
                )
            }
        }

        // Category Breakdown Title Skeleton
        SkeletonBox(modifier = Modifier.size(width = 150.dp, height = 20.dp))

        // Category Breakdown Items Skeleton
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        SkeletonBox(modifier = Modifier.size(36.dp), cornerRadius = 18.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SkeletonBox(modifier = Modifier.size(width = 90.dp, height = 14.dp))
                            SkeletonBox(modifier = Modifier.size(width = 50.dp, height = 10.dp))
                        }
                    }
                    SkeletonBox(modifier = Modifier.size(width = 70.dp, height = 16.dp))
                }
            }
        }
    }
}
