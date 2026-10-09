package com.spendwise.app.ui.screen.addexpense

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.spendwise.app.domain.model.PaymentSource
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    navController: NavController,
    expenseId: String? = null,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(expenseId) {
        expenseId?.let { viewModel.loadExpense(it) }
    }

    LaunchedEffect(Unit) {
        com.spendwise.app.service.UpiMonitorService.cancelPaymentDoneNotification(context)
    }

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            com.spendwise.app.service.UpiMonitorService.cancelPaymentDoneNotification(context)
            navController.popBackStack()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCategoryPickerSheet by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            com.spendwise.app.ui.components.TopBanner(
                bottomCornerRadius = 24.dp,
                contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text(
                            text = if (uiState.isEditMode) "Edit Expense" else "Add Expense",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    if (uiState.isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Smart Quick-Fill Bar (Only in new expense mode)
            if (!uiState.isEditMode) {
                var smartPrompt by remember { mutableStateOf("") }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("✨", fontSize = 16.sp)
                                Text(
                                    "AI Smart Quick-Fill",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "On-Device AI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = smartPrompt,
                                onValueChange = { smartPrompt = it },
                                placeholder = { Text("e.g. Dinner 450 card or Uber 220", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (smartPrompt.isNotBlank()) {
                                            viewModel.parseSmartPrompt(smartPrompt)
                                            smartPrompt = ""
                                        }
                                    }
                                )
                            )

                            Button(
                                onClick = {
                                    if (smartPrompt.isNotBlank()) {
                                        viewModel.parseSmartPrompt(smartPrompt)
                                        smartPrompt = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text("Fill", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }


            // Amount
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = viewModel::setAmount,
                label = { Text("Amount") },
                prefix = { Text("\u20B9") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = uiState.amountError != null,
                supportingText = uiState.amountError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Payment Source
            Text("Payment Source", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentSource.entries.forEach { source ->
                    val isSelected = uiState.paymentSource == source
                    val animatedBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "ps_bg"
                    )
                    val animatedContentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "ps_text"
                    )
                    val animatedBorderColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "ps_border"
                    )

                    Surface(
                        onClick = { viewModel.setPaymentSource(source) },
                        shape = RoundedCornerShape(10.dp),
                        color = animatedBg,
                        border = BorderStroke(1.dp, animatedBorderColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = source.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = animatedContentColor
                            )
                        }
                    }
                }
            }

            // Category Selector
            Text("Category", style = MaterialTheme.typography.labelLarge)
            val selectedCat = uiState.selectedCategory
            val selectedCatColor = selectedCat?.let {
                try {
                    Color(android.graphics.Color.parseColor(it.colorHex))
                } catch (_: Exception) {
                    MaterialTheme.colorScheme.primary
                }
            } ?: MaterialTheme.colorScheme.primary

            OutlinedCard(
                onClick = { showCategoryPickerSheet = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(selectedCatColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedCat?.name?.take(1)?.uppercase() ?: "?",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text(
                                text = selectedCat?.name ?: "Select Category",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selectedCat != null) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedCat != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedCat != null && selectedCat.aliases.isNotBlank()) {
                                Text(
                                    text = selectedCat.aliases.split(",").take(2).joinToString(", "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Choose Category",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Audited Date & Time
            val auditDateTimeFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
            var showDatePicker by remember { mutableStateOf(false) }
            val cal = remember(uiState.selectedDate) {
                Calendar.getInstance().apply { timeInMillis = uiState.selectedDate }
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audited Timestamp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = auditDateTimeFormat.format(Date(uiState.selectedDate)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                    }

                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Time/Date", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (showDatePicker) {
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        cal.set(Calendar.YEAR, y)
                        cal.set(Calendar.MONTH, m)
                        cal.set(Calendar.DAY_OF_MONTH, d)
                        // Chain TimePickerDialog immediately to audit exact hour/minute
                        android.app.TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                cal.set(Calendar.MINUTE, minute)
                                viewModel.setDate(cal.timeInMillis)
                                showDatePicker = false
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }

            // Note
            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::setNote,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            // Split Expense Section
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (uiState.isSplit) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surface
                )
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Filled.CallSplit,
                                contentDescription = null,
                                tint = if (uiState.isSplit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = "Split Expense",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Share cost with friends & track payback",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isSplit,
                            onCheckedChange = { viewModel.setSplit(it) }
                        )
                    }

                    AnimatedVisibility(
                        visible = uiState.isSplit,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Split people counter
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Split equally between",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { viewModel.setSplitPeopleCount(uiState.splitPeopleCount - 1) },
                                        enabled = uiState.splitPeopleCount > 2,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "${uiState.splitPeopleCount} people",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    FilledTonalIconButton(
                                        onClick = { viewModel.setSplitPeopleCount(uiState.splitPeopleCount + 1) },
                                        enabled = uiState.splitPeopleCount < 20,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Dual editable inputs: Your Net Share & To Receive Back
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = uiState.myNetShare,
                                    onValueChange = viewModel::setMyNetShare,
                                    label = { Text("Your Net Share") },
                                    prefix = { Text("₹") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    isError = uiState.splitValidationError != null,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = uiState.receivableAmount,
                                    onValueChange = viewModel::setReceivableAmount,
                                    label = { Text("To Receive Back") },
                                    prefix = { Text("₹") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    isError = uiState.splitValidationError != null,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Dynamic Real-time Split Validation Error
                            uiState.splitValidationError?.let { errorMsg ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = errorMsg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Summary of shares
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Your Expense", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "₹${uiState.myNetShare.ifBlank { "0.00" }}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .width(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Friends Owe You", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "₹${uiState.receivableAmount.ifBlank { "0.00" }}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD97706)
                                        )
                                    }
                                }
                            }

                            // Reimbursement status toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Already Reimbursed / Settled?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (uiState.isReimbursed) "Marked as fully settled" else "Pending payback from friends",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (uiState.isReimbursed) Color(0xFF16A34A) else Color(0xFFD97706)
                                    )
                                }
                                Switch(
                                    checked = uiState.isReimbursed,
                                    onCheckedChange = { viewModel.setReimbursed(it) }
                                )
                            }
                        }
                    }
                }
            }

            // Error
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            // Save button
            Button(
                onClick = viewModel::saveExpense,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (uiState.isEditMode) "Update Expense" else "Save Expense")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Expense?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense()
                    showDeleteDialog = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCategoryPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryPickerSheet = false },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showCategoryPickerSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                var catSearchQuery by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = catSearchQuery,
                    onValueChange = { catSearchQuery = it },
                    placeholder = { Text("Search categories...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                val filteredCats = remember(uiState.categories, catSearchQuery) {
                    if (catSearchQuery.isBlank()) uiState.categories
                    else uiState.categories.filter { it.name.contains(catSearchQuery, ignoreCase = true) }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCats, key = { it.id }) { cat ->
                        val isSelected = cat.id == uiState.selectedCategory?.id
                        val itemColor = try {
                            Color(android.graphics.Color.parseColor(cat.colorHex))
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        Surface(
                            onClick = {
                                viewModel.setCategory(cat)
                                showCategoryPickerSheet = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(itemColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cat.name.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (cat.aliases.isNotBlank()) {
                                            Text(
                                                text = cat.aliases.split(",").take(2).joinToString(", "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
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
