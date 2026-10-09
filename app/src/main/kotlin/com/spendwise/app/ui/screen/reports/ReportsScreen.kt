package com.spendwise.app.ui.screen.reports

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.domain.model.CategorySpend
import com.spendwise.app.ui.components.ReportsLoadingSkeleton
import com.spendwise.app.ui.components.bounceClick
import com.spendwise.app.ui.components.staggeredEntrance
import com.spendwise.app.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    navController: NavController,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val totalSpent = remember(uiState.categorySpends) {
        uiState.categorySpends.sumOf { it.totalSpend }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            com.spendwise.app.ui.components.TopBanner(
                bottomCornerRadius = 24.dp,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Financial Intelligence",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Reports & Outflow Analytics",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = { viewModel.exportCsv(context) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 250),
            label = "reports_crossfade"
        ) { loading ->
            if (loading) {
                ReportsLoadingSkeleton(
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .imePadding()
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    // KPI Grid
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .staggeredEntrance(0),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Total Outflow", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${uiState.currencySymbol}%.2f".format(totalSpent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(2.dp))
                                    Text("Active Month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Daily Run-Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${uiState.currencySymbol}%.2f".format(totalSpent / 31.0), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(2.dp))
                                    Text("● On Track", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Category Distribution",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.staggeredEntrance(1)
                        )
                    }

                    if (uiState.categorySpends.isNotEmpty()) {
                        item {
                            DonutChartCard(
                                spends = uiState.categorySpends,
                                selectedCategory = uiState.selectedCategory,
                                currencySymbol = uiState.currencySymbol,
                                totalSpent = totalSpent,
                                onSelectCategory = { spend ->
                                    viewModel.selectCategory(if (uiState.selectedCategory?.categoryId == spend.categoryId) null else spend)
                                },
                                onClearSelection = { viewModel.selectCategory(null) },
                                modifier = Modifier.staggeredEntrance(2)
                            )
                        }

                        // Interactive Category Drill-Down Detailed Report Card
                        item {
                            AnimatedVisibility(
                                visible = uiState.selectedCategory != null,
                                enter = fadeIn(tween(300)) + expandVertically(tween(350)),
                                exit = fadeOut(tween(250)) + shrinkVertically(tween(300))
                            ) {
                                uiState.selectedCategory?.let { selCat ->
                                    CategoryDetailedReportCard(
                                        category = selCat,
                                        expenses = uiState.categoryExpenses,
                                        currencySymbol = uiState.currencySymbol,
                                        onClose = { viewModel.selectCategory(null) },
                                        onExpenseClick = { expenseId ->
                                            navController.navigate(Screen.AddExpense.route(expenseId))
                                        }
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Category Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        items(uiState.categorySpends) { spend ->
                            CategoryPercentRow(
                                spend = spend,
                                currencySymbol = uiState.currencySymbol,
                                isSelected = uiState.selectedCategory?.categoryId == spend.categoryId,
                                onClick = {
                                    viewModel.selectCategory(
                                        if (uiState.selectedCategory?.categoryId == spend.categoryId) null else spend
                                    )
                                }
                            )
                        }
                    } else {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("No expenses logged for this month yet.")
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Monthly Spending Trends (Last 12 Months)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        MonthlyTrendsCard(
                            incomeTotals = uiState.monthlyIncomeSpendTotals,
                            creditTotals = uiState.monthlyCreditSpendTotals,
                            currencySymbol = uiState.currencySymbol
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DonutChartCard(
    spends: List<CategorySpend>,
    selectedCategory: CategorySpend?,
    currencySymbol: String,
    totalSpent: Double,
    onSelectCategory: (CategorySpend) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(175.dp)
                        .clickable(onClick = onClearSelection)
                ) {
                    var startAngle = -90f
                    spends.forEach { spend ->
                        val isThisSelected = selectedCategory?.categoryId == spend.categoryId
                        val sweepAngle = (spend.percentage.toFloat() / 100f) * 360f * animProgress.value
                        val baseColor = try {
                            Color(android.graphics.Color.parseColor(spend.colorHex))
                        } catch (e: Exception) {
                            Color(0xFF2563EB)
                        }
                        val sliceColor = if (selectedCategory == null || isThisSelected) {
                            baseColor
                        } else {
                            baseColor.copy(alpha = 0.35f)
                        }
                        val strokeWidth = if (isThisSelected) 38f else 28f

                        drawArc(
                            color = sliceColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth)
                        )
                        startAngle += (spend.percentage.toFloat() / 100f) * 360f
                    }
                }

                // Inner hole content with Active Total & animated selection
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClearSelection)
                        .padding(4.dp)
                ) {
                    Text(
                        text = selectedCategory?.categoryName ?: "Active Total",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$currencySymbol%.0f".format(selectedCategory?.totalSpend ?: totalSpent),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = selectedCategory?.let { "%.1f%% of total".format(it.percentage) }
                            ?: "${spends.size} categories",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedCategory != null) MaterialTheme.colorScheme.primary else Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryDetailedReportCard(
    category: CategorySpend,
    expenses: List<Expense>,
    currencySymbol: String,
    onClose: () -> Unit,
    onExpenseClick: (String) -> Unit
) {
    val categoryColor = try {
        Color(android.graphics.Color.parseColor(category.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val avgPerEntry = if (expenses.isNotEmpty()) category.totalSpend / expenses.size else 0.0
    val dateTimeFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, categoryColor.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Category Name & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(categoryColor)
                    )
                    Column {
                        Text(
                            text = category.categoryName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Detailed Category Report",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Report",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 4-Tile Mini Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol%.0f".format(category.totalSpend), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("Share", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.1f%%".format(category.percentage), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = categoryColor)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("Entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${expenses.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("Avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol%.0f".format(avgPerEntry), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // History Log Section
            Text(
                text = "Transaction History (${expenses.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (expenses.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No transactions found for ${category.categoryName} this month.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    expenses.forEach { expense ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onExpenseClick(expense.id) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (expense.note.isNotBlank()) expense.note else category.categoryName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = dateTimeFormat.format(Date(expense.dateEpoch)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = expense.paymentSource.replace("_", " "),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (expense.paymentSource == "INCOME") Color(0xFF10B981) else Color(0xFF38BDF8),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "$currencySymbol%.2f".format(expense.amount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPercentRow(
    spend: CategorySpend,
    currencySymbol: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = try {
        Color(android.graphics.Color.parseColor(spend.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "cat_percent_bg"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "cat_percent_border"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, animatedBorderColor),
        colors = CardDefaults.cardColors(containerColor = animatedBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
                Text(
                    text = spend.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$currencySymbol%.2f".format(spend.totalSpend),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "%.0f%%".format(spend.percentage),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    imageVector = if (isSelected) Icons.Default.ExpandLess else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MonthlyTrendsCard(
    incomeTotals: List<com.spendwise.app.data.local.dao.MonthlyTotal>,
    creditTotals: List<com.spendwise.app.data.local.dao.MonthlyTotal>,
    currencySymbol: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val allTotals = (incomeTotals + creditTotals).map { it.total }
            val maxAmount = (allTotals.maxOrNull() ?: 1.0).coerceAtLeast(1.0)

            if (incomeTotals.isEmpty() && creditTotals.isEmpty()) {
                Text("No past monthly data available yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                incomeTotals.take(6).forEach { total ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Month ${total.month}/${total.year}", style = MaterialTheme.typography.bodyMedium)
                            Text("$currencySymbol%.0f".format(total.total), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (total.total / maxAmount).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}


