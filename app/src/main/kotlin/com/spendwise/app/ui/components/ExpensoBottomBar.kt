package com.spendwise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.app.ui.theme.ExpensoMotionDefaults

data class ExpensoNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String,
    val contentDescription: String = label
)

@Composable
fun ExpensoBottomBar(
    items: List<ExpensoNavItem>,
    currentRoute: String?,
    onItemClick: (String) -> Unit,
    onReselect: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val backgroundColor = MaterialTheme.colorScheme.surface
    val hairlineColor = if (isDark) Color(0xFF27272A) else ExpensoMotionDefaults.HairlineLight
    val capsuleColor = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else ExpensoMotionDefaults.CapsuleLightTint
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = if (isDark) Color(0xFF71717A) else ExpensoMotionDefaults.InactiveGray

    val barShape = RoundedCornerShape(
        topStart = ExpensoMotionDefaults.BarCornerRadius,
        topEnd = ExpensoMotionDefaults.BarCornerRadius
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 0.dp else 8.dp,
                shape = barShape,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.06f)
            )
            .border(
                width = 1.dp,
                color = hairlineColor,
                shape = barShape
            ),
        shape = barShape,
        color = backgroundColor,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ExpensoMotionDefaults.BarHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    // GPU-friendly press feedback scale
                    val pressScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isPressed) 0.92f else 1.0f,
                        animationSpec = androidx.compose.animation.core.tween(90, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        label = "tab_press_scale"
                    )

                    // Smooth GPU-friendly active indicator alpha & scale
                    val indicatorAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isSelected) 1.0f else 0.0f,
                        animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        label = "indicator_alpha"
                    )
                    val indicatorScaleX by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isSelected) 1.0f else 0.75f,
                        animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        label = "indicator_scale"
                    )

                    val animatedIconTint by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isSelected) activeColor else inactiveColor,
                        animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        label = "icon_tint"
                    )
                    val animatedLabelColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isSelected) activeColor else inactiveColor,
                        animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        label = "label_color"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                this.selected = isSelected
                                this.contentDescription = "${item.label} tab"
                            }
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        onReselect(item.route)
                                    } else {
                                        onItemClick(item.route)
                                    }
                                },
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = null // clean touch without heavy rectangular ripple
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            // Icon container with GPU-transformed capsule indicator (zero layout invalidation)
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = ExpensoMotionDefaults.ActiveCapsuleWidth,
                                        height = ExpensoMotionDefaults.ActiveCapsuleHeight
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background capsule animated on GPU transform and opacity only
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha = indicatorAlpha
                                            scaleX = indicatorScaleX
                                        }
                                        .clip(RoundedCornerShape(ExpensoMotionDefaults.ActiveCapsuleRadius))
                                        .background(capsuleColor)
                                )

                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = animatedIconTint
                                )
                            }

                            // Always visible, crisp label with smooth color transition
                            Text(
                                text = item.label,
                                fontSize = ExpensoMotionDefaults.LabelFontSize,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = animatedLabelColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
