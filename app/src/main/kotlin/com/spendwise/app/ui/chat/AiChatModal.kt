package com.spendwise.app.ui.chat

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.data.ai.ExpenseDraftType
import com.spendwise.app.data.ai.PendingExpenseDraft
import com.spendwise.app.data.local.entity.Category
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AiChatModal(
    onDismissRequest: () -> Unit,
    initialQuery: String? = null,
    initialVoiceMode: Boolean = false,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    // Handle back button dismissal
    BackHandler(onBack = onDismissRequest)

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.sendMessage(spokenText)
            }
        }
    }

    fun launchVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Expenso AI...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(initialVoiceMode) {
        if (initialVoiceMode && uiState.isSignedIn) {
            launchVoiceRecognition()
        }
    }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && uiState.isSignedIn) {
            viewModel.sendMessage(initialQuery)
        }
    }

    // Dynamic keyboard and navigation bar insets calculation
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isKeyboardOpen = imeBottom > 0.dp
    val bottomInsetPadding = if (isKeyboardOpen) imeBottom + 10.dp else navBottom + 12.dp

    // Full screen overlay with animated scrim background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (!uiState.isSignedIn) {
            // Signed-out State: Google Sign-in Prompt Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomInsetPadding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Text(
                        text = "Sign in to use Expenso AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Sign in with your Google account to unlock real-time financial conversational intelligence, predictive budgeting, and automated spending insights.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Surface(
                        onClick = {
                            try {
                                googleSignInLauncher.launch(viewModel.getSignInIntent(context))
                            } catch (e: Exception) {
                                activity?.let { viewModel.signInWithGoogle(it) }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        tonalElevation = 2.dp,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("G ", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF4285F4))
                            Text(
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1F2937)
                            )
                        }
                    }

                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            // Signed-in State: Fixed Header, Scrollable Messages (flex 1), Fixed Input Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .fillMaxHeight(if (isKeyboardOpen) 0.94f else 0.82f)
                    .animateContentSize(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))
                    .padding(horizontal = 12.dp)
                    .padding(bottom = bottomInsetPadding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                var inputText by remember { mutableStateOf("") }
                val listState = rememberLazyListState()
                val focusRequester = remember { FocusRequester() }

                // Auto scroll to latest message when new message arrives or keyboard opens
                LaunchedEffect(uiState.messages.size) {
                    if (uiState.messages.isNotEmpty()) {
                        listState.animateScrollToItem(uiState.messages.size - 1)
                    }
                }

                LaunchedEffect(isKeyboardOpen) {
                    if (isKeyboardOpen && uiState.messages.isNotEmpty()) {
                        delay(90) // Wait for layout resize
                        listState.animateScrollToItem(uiState.messages.size - 1)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // 1. Compact Fixed Header (Single Line Title & No-Wrap Badge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Ask Expenso AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Gemini 2.0 Flash",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(onClick = onDismissRequest, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close AI Chat", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // 2. Horizontally scrollable Suggestion Chips (Hidden when keyboard is open)
                    AnimatedVisibility(
                        visible = !isKeyboardOpen,
                        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                    ) {
                        val pills = listOf(
                            "Remaining balance? 💰",
                            "Top spend? 📈",
                            "Food spend? 🍔",
                            "Where should I reduce? 💡",
                            "Compare with last month 📊",
                            "Which day did I spend most? 📅"
                        )
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pills) { pill ->
                                Surface(
                                    onClick = {
                                        viewModel.sendMessage(pill.substringBeforeLast(" "))
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = pill,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // 3. Scrollable Message List (Flex 1)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { msg ->
                            if (msg.isUser) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp).copy(bottomEnd = androidx.compose.foundation.shape.CornerSize(4.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.widthIn(max = 300.dp)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp).copy(bottomStart = androidx.compose.foundation.shape.CornerSize(4.dp)),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.widthIn(max = 320.dp)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        )
                                    }

                                    // Confirmation Card for Drafting Expenses
                                    if (msg.pendingExpenseDrafts != null && msg.pendingExpenseDrafts.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        ExpenseConfirmationCard(
                                            drafts = msg.pendingExpenseDrafts,
                                            cardState = msg.cardState,
                                            savedSummary = msg.savedBalanceSummary,
                                            availableCategories = uiState.categories,
                                            onConfirm = { viewModel.confirmDrafts(msg.id) },
                                            onEdit = {
                                                val first = msg.pendingExpenseDrafts.first()
                                                inputText = "Add ${first.amount.toInt()} for ${first.note} ${first.categoryName}"
                                                focusRequester.requestFocus()
                                            },
                                            onCancel = { viewModel.cancelDrafts(msg.id) },
                                            onUndo = { viewModel.undoDrafts(msg.id) },
                                            onSelectCategory = { draftId, cat -> viewModel.updateDraftCategory(msg.id, draftId, cat) },
                                            onCreateCategory = { draftId, catName -> viewModel.createAndAssignCategory(msg.id, draftId, catName) }
                                        )
                                    }

                                    // Offline Indicator with Retry
                                    if (msg.isOfflineError) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Offline Mode · Ledgers synced locally",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "• Retry",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable { viewModel.retryLastMessage() }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Thinking / Typing Indicator
                        if (uiState.isLoading) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            Text(
                                                text = "Thinking...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // 4. Fixed Input Bar (Positioned 8-12px directly above keyboard with zero overlap)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("Ask about your spending...", fontSize = 14.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (inputText.isNotBlank() && !uiState.isLoading) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                        keyboardController?.hide()
                                    }
                                })
                            )

                            IconButton(
                                onClick = { launchVoiceRecognition() },
                                modifier = Modifier.semantics { contentDescription = "Voice Input" }
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Voice Input",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            val isSendValid = inputText.isNotBlank() && !uiState.isLoading
                            IconButton(
                                onClick = {
                                    if (isSendValid) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                        keyboardController?.hide()
                                    }
                                },
                                enabled = isSendValid
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (isSendValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Confirmation Card for adding one or multiple drafted expenses.
 * Prevents double-taps and allows Confirm, Edit, or Cancel.
 */
@Composable
private fun ExpenseConfirmationCard(
    drafts: List<PendingExpenseDraft>,
    cardState: DraftCardState,
    savedSummary: String?,
    availableCategories: List<Category>,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onSelectCategory: (String, Category) -> Unit,
    onCreateCategory: (String, String) -> Unit
) {
    val firstDraft = drafts.firstOrNull()
    val isDelete = firstDraft?.draftType == ExpenseDraftType.DELETE
    val isUpdate = firstDraft?.draftType == ExpenseDraftType.UPDATE
    val totalAmount = drafts.sumOf { it.amount }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDelete) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(
            1.dp,
            if (isDelete) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = when {
                        isDelete -> Icons.Default.DeleteForever
                        isUpdate -> Icons.Default.Edit
                        else -> Icons.Default.ReceiptLong
                    },
                    contentDescription = null,
                    tint = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = when {
                        isDelete -> "Delete Expense"
                        isUpdate -> "Update Expense"
                        drafts.size > 1 -> "Confirm ${drafts.size} Expenses (Total: ₹${totalAmount.toInt()})"
                        else -> "Confirm Expense"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(8.dp))

            drafts.forEach { draft ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (isUpdate && draft.originalNote != null) {
                            Text(
                                text = "Before: ${draft.originalNote} • ₹${draft.originalAmount?.toInt() ?: draft.amount.toInt()} in ${draft.originalCategoryName ?: draft.categoryName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "After: ${draft.note} • ₹${draft.amount.toInt()} in ${draft.categoryName}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = draft.note,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${draft.categoryName} • ${draft.dateFormatted} • ${if (draft.paymentSource == "CREDIT_CARD") "Credit Card" else "Income/UPI"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "₹${draft.amount.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Match reason tag & Others fallback banner
                        if (cardState == DraftCardState.PENDING && !isDelete) {
                            if (draft.isOthersFallback) {
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "⚠️ No related category found · Added to Others",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        if (!draft.suggestedCategoryName.isNullOrBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            SuggestionChip(
                                                onClick = { onCreateCategory(draft.id, draft.suggestedCategoryName) },
                                                label = {
                                                    Text(
                                                        "+ Create \"${draft.suggestedCategoryName}\"",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            } else if (draft.matchReason.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "✓ ${draft.matchReason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }

                            // Candidate categories quick-select chips (for clarification or reselection)
                            if (draft.candidateCategories.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (draft.clarificationNeeded) "Select matching category:" else "Choose category:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    items(draft.candidateCategories) { candidateName ->
                                        val matchedCat = availableCategories.find { it.name.equals(candidateName, ignoreCase = true) }
                                        FilterChip(
                                            selected = draft.categoryName.equals(candidateName, ignoreCase = true),
                                            onClick = {
                                                if (matchedCat != null) {
                                                    onSelectCategory(draft.id, matchedCat)
                                                }
                                            },
                                            label = { Text(candidateName, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when (cardState) {
                DraftCardState.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (isDelete) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                        ) {
                            Text(
                                text = when {
                                    isDelete -> "Delete"
                                    isUpdate -> "Confirm Update"
                                    drafts.size > 1 -> "Confirm all (${drafts.size} items - ₹${totalAmount.toInt()})"
                                    else -> "Confirm"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!isDelete) {
                            OutlinedButton(
                                onClick = onEdit,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Edit")
                            }
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                DraftCardState.SAVING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = if (isDelete) "Deleting from ledger..." else "Saving to ledger & cloud...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                DraftCardState.SAVED -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = when {
                                        isDelete -> "Deleted & Removed from Ledger"
                                        isUpdate -> "Updated in Ledger"
                                        else -> "Confirmed & Saved to Ledger"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = onUndo,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Undo", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (!savedSummary.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = savedSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                DraftCardState.FAILED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Action failed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        TextButton(onClick = onConfirm) {
                            Text("Retry")
                        }
                    }
                }
                DraftCardState.CANCELLED -> {
                    Text(
                        text = "Cancelled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
