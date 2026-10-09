package com.spendwise.app.ui.screen.history

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.spendwise.app.domain.model.ExpenseWithCategory
import com.spendwise.app.domain.model.PaymentSource
import com.spendwise.app.ui.components.bounceClick
import com.spendwise.app.ui.navigation.Screen
import com.spendwise.app.ui.theme.IncomeGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateGroupFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            com.spendwise.app.ui.components.TopBanner(
                bottomCornerRadius = 24.dp,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
            ) {
                // Header Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Expense History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${uiState.expenses.size} entries",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val searchContainerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.90f) else Color.White
                val searchTextColor = if (isDark) Color.White else Color(0xFF0F172A)
                val searchPlaceholderColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                val searchIconTint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                val searchClearTint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

                // Search Bar with dark mode support on the blue banner
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = {
                        Text(
                            "Search by note or description...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = searchPlaceholderColor
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = searchIconTint)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = searchClearTint)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = searchContainerColor,
                        unfocusedContainerColor = searchContainerColor,
                        disabledContainerColor = searchContainerColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = searchTextColor,
                        unfocusedTextColor = searchTextColor,
                        cursorColor = searchIconTint,
                        selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
                            handleColor = searchIconTint,
                            backgroundColor = searchIconTint.copy(alpha = 0.35f)
                        )
                    )
                )

                Spacer(Modifier.height(10.dp))

                // Filters: All, Income, Credit Card, and Splits styled as pills on the blue banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryFilterPill(
                        text = "All",
                        selected = uiState.selectedSource == null && !uiState.onlySplits,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (uiState.onlySplits) viewModel.toggleSplitFilter()
                            viewModel.onSourceFilterChange(null)
                        }
                    )
                    HistoryFilterPill(
                        text = "Income",
                        selected = uiState.selectedSource == PaymentSource.INCOME && !uiState.onlySplits,
                        isDark = isDark,
                        modifier = Modifier.weight(1.1f),
                        onClick = {
                            if (uiState.onlySplits) viewModel.toggleSplitFilter()
                            viewModel.onSourceFilterChange(PaymentSource.INCOME)
                        }
                    )
                    HistoryFilterPill(
                        text = "Credit Card",
                        selected = uiState.selectedSource == PaymentSource.CREDIT_CARD && !uiState.onlySplits,
                        isDark = isDark,
                        modifier = Modifier.weight(1.3f),
                        onClick = {
                            if (uiState.onlySplits) viewModel.toggleSplitFilter()
                            viewModel.onSourceFilterChange(PaymentSource.CREDIT_CARD)
                        }
                    )
                    HistoryFilterPill(
                        text = if (uiState.totalPendingReceivable > 0) "Splits (₹${uiState.totalPendingReceivable.toInt()})" else "Splits",
                        selected = uiState.onlySplits,
                        isDark = isDark,
                        modifier = Modifier.weight(1.2f),
                        onClick = { viewModel.toggleSplitFilter() }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
        ) {

            // Expense Items List with smooth AnimatedContent transition on option switch
            AnimatedContent(
                targetState = uiState.selectedSource to uiState.expenses.isEmpty(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing))
                },
                label = "history_list_transition",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { (_, isEmpty) ->
                if (isEmpty) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No expenses found for this filter",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val groupedExpenses = remember(uiState.expenses) {
                        uiState.expenses.groupBy { item ->
                            dateGroupFormat.format(Date(item.expense.dateEpoch))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    groupedExpenses.forEach { (dateStr, itemsForDate) ->
                        val dayTotal = itemsForDate.sumOf { it.expense.amount }

                        item(key = "day_card_$dateStr") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column {
                                    // Cohesive Day Header inside the Card
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "${itemsForDate.size} ${if (itemsForDate.size == 1) "entry" else "entries"}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Day Total:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${uiState.currencySymbol}%.2f".format(dayTotal),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }


                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    // Multiple day entries joined in single card with inner dividers
                                    itemsForDate.forEachIndexed { index, item ->
                                        ExpenseHistoryRow(
                                            item = item,
                                            currencySymbol = uiState.currencySymbol,
                                            sdf = sdf,
                                            onClick = {
                                                navController.navigate(Screen.AddExpense.route(item.expense.id))
                                            },
                                            onToggleReimburse = {
                                                viewModel.markSplitReimbursed(item.expense.id, !item.expense.isReimbursed)
                                            }
                                        )
                                        if (index < itemsForDate.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}




@Composable
fun ExpenseHistoryRow(
    item: ExpenseWithCategory,
    currencySymbol: String,
    sdf: SimpleDateFormat,
    onClick: () -> Unit,
    onToggleReimburse: () -> Unit = {}
) {
    val catColor = try {
        Color(android.graphics.Color.parseColor(item.category.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category Avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(catColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.category.name.take(1).uppercase(),
                fontWeight = FontWeight.SemiBold,
                color = catColor,
                style = MaterialTheme.typography.titleSmall
            )
        }

        Spacer(Modifier.width(12.dp))

        // Left: Name, Category, and Split Status
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Name / Note (Normal text)
            Text(
                text = if (item.expense.note.isNotBlank()) item.expense.note else item.category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Category & Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.category.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = catColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = sdf.format(Date(item.expense.dateEpoch)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Split Badge & Quick Settle
            if (item.expense.isSplit) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.expense.isReimbursed) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                    ) {
                        Text(
                            text = if (item.expense.isReimbursed) "Split • Settled" else "Split • $currencySymbol%.2f Owed".format(item.expense.receivableAmount),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.expense.isReimbursed) Color(0xFF16A34A) else Color(0xFFB45309),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }

                    if (!item.expense.isReimbursed) {
                        Surface(
                            onClick = onToggleReimburse,
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "Settle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Right: Amount on top, Payment Source pill neatly aligned underneath
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "$currencySymbol%.2f".format(item.expense.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (item.expense.isSplit) {
                Text(
                    text = "My share: $currencySymbol%.2f".format(item.expense.netUserExpense),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val isCredit = item.expense.paymentSource == "CREDIT_CARD"
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isCredit) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                } else {
                    IncomeGreen.copy(alpha = 0.12f)
                }
            ) {
                Text(
                    text = if (isCredit) "Credit Card" else "Income",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isCredit) MaterialTheme.colorScheme.secondary else IncomeGreen,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryFilterPill(
    text: String,
    selected: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val targetBg = when {
        selected && isDark -> Color(0xFF1E293B)
        selected -> Color.White
        isDark -> Color(0xFF0F172A).copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.2f)
    }
    val targetTextColor = when {
        selected && isDark -> Color(0xFF93C5FD)
        selected -> Color(0xFF2563EB)
        isDark -> Color(0xFFE2E8F0)
        else -> Color.White
    }
    val targetBorder = when {
        selected && isDark -> BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.6f))
        selected -> null
        isDark -> BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.4f))
        else -> null
    }

    val animatedBgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "pill_bg"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "pill_text"
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (selected && !isDark) 2.dp else 0.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "pill_elev"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = animatedBgColor,
        border = targetBorder,
        shadowElevation = animatedElevation,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = animatedTextColor
            )
        }
    }
}

