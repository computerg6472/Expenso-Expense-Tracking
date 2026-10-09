package com.spendwise.app.ui.screen.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.spendwise.app.domain.model.DashboardData
import com.spendwise.app.domain.model.CategorySpend
import com.spendwise.app.ui.navigation.Screen
import com.spendwise.app.ui.theme.CreditBlue
import com.spendwise.app.ui.theme.DueRose
import com.spendwise.app.ui.theme.IncomeGreen

@Composable
fun DashboardScreen(
    navController: NavController,
    onOpenAiChat: (isVoice: Boolean, query: String?) -> Unit = { _, _ -> },
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isAiBannerDismissedForSession by remember { mutableStateOf(false) }
    var showAddCreditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.quickLogSuccessMessage) {
        uiState.quickLogSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearQuickLogMessage()
        }
    }

    if (!uiState.isLoading && uiState.salaryNotSet) {
        SalarySetupPrompt(
            currencySymbol = uiState.currencySymbol,
            onSaveSalary = { amount -> viewModel.setSalary(amount) },
            onGoToSettings = { navController.navigate(Screen.Settings.route) }
        )
    }

    if (showAddCreditDialog) {
        AddExtraCreditBottomSheet(
            currencySymbol = uiState.currencySymbol,
            currentExtraCredit = uiState.dashboard?.extraCredit ?: 0.0,
            onDismiss = { showAddCreditDialog = false },
            onConfirm = { amount ->
                viewModel.addExtraCredit(amount)
                showAddCreditDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.spendwise.app.R.drawable.app_logo),
                            contentDescription = "Expenso Logo",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column {
                            Text(
                                text = "Expenso",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "${uiState.dashboard?.let { monthName(it.month) } ?: ""} ${uiState.dashboard?.year ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4ADE80))
                            )
                            Text(
                                text = "Live Ledger",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "dashboard_loading_crossfade"
        ) { loading ->
            if (loading) {
                com.spendwise.app.ui.components.DashboardLoadingSkeleton(
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                ) {
                    item {
                        com.spendwise.app.ui.components.AiHomeBanner(
                            visible = !uiState.aiFloatingButtonEnabled && !isAiBannerDismissedForSession,
                            onClick = { onOpenAiChat(false, null) },
                            onVoiceClick = { onOpenAiChat(true, null) },
                            onDismiss = { isAiBannerDismissedForSession = true }
                        )
                    }

                    uiState.dashboard?.let { data ->
                        item {
                            AvailableBalanceCard(
                                data = data,
                                currency = uiState.currencySymbol,
                                onAddCreditClick = { showAddCreditDialog = true }
                            )
                        }
                        if (data.pendingReceivableTotal > 0.0) {
                            item {
                                PendingReceivablesCard(
                                    pendingAmount = data.pendingReceivableTotal,
                                    currency = uiState.currencySymbol,
                                    onClick = {
                                        navController.navigate(Screen.History.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                        item { SummaryCardsRow(data, uiState.currencySymbol) }
                        item { CreditCardTrackingCard(data, uiState.currencySymbol) }
                    }

                    if (uiState.categorySpends.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Category",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${uiState.categorySpends.size} active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(uiState.categorySpends) { spend ->
                            CategorySpendRow(spend, uiState.currencySymbol)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AvailableBalanceCard(
    data: DashboardData,
    currency: String,
    onAddCreditClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Status indicator and badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(IncomeGreen)
                    )
                    Text(
                        text = "Available Balance",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = IncomeGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Safe to Spend",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = IncomeGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Main Balance Number & Add Money Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$currency%.2f".format(data.availableBalance),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onAddCreditClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Add Money",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Financial breakdown bar with clean 2-column layout
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Base Salary",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$currency%.2f".format(data.salary),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (data.extraCredit > 0.0) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Extra Added",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "+$currency%.2f".format(data.extraCredit),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PendingReceivablesCard(
    pendingAmount: Double,
    currency: String,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF2E2211) else Color(0xFFFEF3C7)
    val textPrimary = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
    val textSecondary = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val buttonBg = Color(0xFFD97706)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CallSplit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Friend Splits Pending",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "You are owed $currency%.2f from shared bills".format(pendingAmount),
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = buttonBg
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExtraCreditBottomSheet(
    currencySymbol: String,
    currentExtraCredit: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Add Extra Money / Credit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Text(
                text = "Add freelance earnings, gifts, bonuses, or cashback to this month's spending pool.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (currentExtraCredit > 0.0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Currently added this month: $currencySymbol%.2f".format(currentExtraCredit),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            OutlinedTextField(
                value = amountInput,
                onValueChange = {
                    amountInput = it.filter { ch -> ch.isDigit() || ch == '.' }
                    errorText = null
                },
                label = { Text("Amount to Add") },
                prefix = { Text(currencySymbol) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText?.let { { Text(it) } },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(500, 1000, 2000, 5000).forEach { quickVal ->
                    OutlinedButton(
                        onClick = { amountInput = quickVal.toString() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+$quickVal", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val parsed = amountInput.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        errorText = "Enter a valid positive amount"
                    } else {
                        onConfirm(parsed)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Add to Available Balance", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SummaryCardsRow(data: DashboardData, currency: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard(
            modifier = Modifier.weight(1f),
            title = "Income Spent",
            amount = data.spentViaIncome,
            currency = currency,
            color = IncomeGreen
        )
        SummaryCard(
            modifier = Modifier.weight(1f),
            title = "CC Spend",
            amount = data.creditCardSpendThisMonth,
            currency = currency,
            color = CreditBlue
        )
        if (data.creditCardDueCarried > 0) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "CC Due",
                amount = data.creditCardDueCarried,
                currency = currency,
                color = DueRose
            )
        }
    }
}

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: Double,
    currency: String,
    color: Color
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardBg = if (isDark) Color(0xFF18181B) else Color(0xFFF1F5F9)
    val cardBorder = if (isDark) Color(0xFF27272A) else Color(0xFFE2E8F0)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$currency%.0f".format(amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


fun categoryEmoji(name: String): String = when {
    name.contains("Food", ignoreCase = true) || name.contains("Dinner", ignoreCase = true) || name.contains("Eat", ignoreCase = true) || name.contains("Lunch", ignoreCase = true) -> "🍔"
    name.contains("Rent", ignoreCase = true) || name.contains("Home", ignoreCase = true) || name.contains("House", ignoreCase = true) -> "🏠"
    name.contains("Medical", ignoreCase = true) || name.contains("Health", ignoreCase = true) || name.contains("Medicine", ignoreCase = true) -> "💊"
    name.contains("Grocer", ignoreCase = true) || name.contains("Supermarket", ignoreCase = true) -> "🛒"
    name.contains("Saving", ignoreCase = true) || name.contains("Invest", ignoreCase = true) -> "💰"
    name.contains("Travel", ignoreCase = true) || name.contains("Flight", ignoreCase = true) || name.contains("Trip", ignoreCase = true) -> "✈️"
    name.contains("Transport", ignoreCase = true) || name.contains("Fuel", ignoreCase = true) || name.contains("Cab", ignoreCase = true) || name.contains("Metro", ignoreCase = true) -> "🚗"
    name.contains("Shopping", ignoreCase = true) || name.contains("Cloth", ignoreCase = true) -> "🛍️"
    name.contains("Bill", ignoreCase = true) || name.contains("Utilit", ignoreCase = true) || name.contains("Recharge", ignoreCase = true) -> "⚡"
    name.contains("Entertain", ignoreCase = true) || name.contains("Movie", ignoreCase = true) || name.contains("Game", ignoreCase = true) -> "🎬"
    else -> "🏷️"
}

@Composable
fun CategorySpendRow(spend: CategorySpend, currency: String) {
    val color = try { Color(android.graphics.Color.parseColor(spend.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circle Avatar with Emoji
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = categoryEmoji(spend.categoryName),
                        fontSize = 22.sp
                    )
                }

                Spacer(Modifier.width(14.dp))

                // Name & Total
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = spend.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Total:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$currency%.0f".format(spend.totalSpend),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Stats: Share % Pill & Remaining Budget / Total Stat
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = color.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "%.1f%% share".format(spend.percentage),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (spend.budget != null && spend.budget > 0) {
                        val remaining = (spend.budget - spend.totalSpend).coerceAtLeast(0.0)
                        Text(
                            text = "$currency%.0f left".format(remaining),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (spend.totalSpend > spend.budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "$currency%.2f".format(spend.totalSpend),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Slim visual progress bar
            LinearProgressIndicator(
                progress = { (spend.percentage.toFloat() / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
fun SalarySetupPrompt(
    currencySymbol: String,
    onSaveSalary: (Double) -> Unit,
    onGoToSettings: () -> Unit
) {
    var salaryText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val quickPills = listOf(25000.0, 50000.0, 75000.0, 100000.0)

    Dialog(
        onDismissRequest = { /* First time setup required */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Set Monthly Salary",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Enter your monthly income to start tracking available balance and safe daily spending.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Quick preset pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        quickPills.forEach { pill ->
                            val pillStr = if (pill % 1.0 == 0.0) pill.toLong().toString() else pill.toString()
                            val isSelected = salaryText == pillStr
                            Surface(
                                onClick = { salaryText = pillStr },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 1.dp
                            ) {
                                Text(
                                    text = "$currencySymbol${(pill / 1000).toInt()}k",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = salaryText,
                        onValueChange = { input ->
                            salaryText = input.filter { it.isDigit() || it == '.' }
                        },
                        label = { Text("Monthly Salary") },
                        prefix = { Text(currencySymbol) },
                        placeholder = { Text("e.g. 50000") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                val amount = salaryText.toDoubleOrNull() ?: 0.0
                                if (amount > 0) {
                                    onSaveSalary(amount)
                                }
                            }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            val amount = salaryText.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                onSaveSalary(amount)
                            }
                        },
                        enabled = (salaryText.toDoubleOrNull() ?: 0.0) > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Save & Start Tracking", fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = onGoToSettings,
                        modifier = Modifier.padding(top = 0.dp)
                    ) {
                        Text("More Settings", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

fun monthName(month: Int): String = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)[month - 1]

@Composable
fun CreditCardTrackingCard(data: DashboardData, currency: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = "Credit Card",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Credit Card",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Total Limit: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$currency%.0f".format(data.creditLimit),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Available Limit",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$currency%.2f".format(data.availableCreditLimit),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (data.availableCreditLimit > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Added Expense",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "-$currency%.2f".format(data.creditCardSpendThisMonth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }


            LinearProgressIndicator(
                progress = { (data.creditUtilizationPercentage / 100f).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (data.creditUtilizationPercentage >= 80.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }
    }
}




