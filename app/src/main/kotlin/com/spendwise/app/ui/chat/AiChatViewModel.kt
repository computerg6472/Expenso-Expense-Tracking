package com.spendwise.app.ui.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.ai.AiIntelligenceService
import com.spendwise.app.data.ai.PendingExpenseDraft
import com.spendwise.app.data.auth.GoogleAuthManager
import com.spendwise.app.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.spendwise.app.data.local.entity.Category
import com.spendwise.app.domain.repository.CategoryRepository
import javax.inject.Inject

enum class DraftCardState {
    PENDING,
    SAVING,
    SAVED,
    FAILED,
    CANCELLED
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pendingExpenseDrafts: List<PendingExpenseDraft>? = null,
    val cardState: DraftCardState = DraftCardState.PENDING,
    val savedBalanceSummary: String? = null,
    val savedExpenseIds: List<String> = emptyList(),
    val isOfflineError: Boolean = false
) {
    val isConfirmed: Boolean get() = cardState == DraftCardState.SAVED
    val isCancelled: Boolean get() = cardState == DraftCardState.CANCELLED
}

data class AiChatUiState(
    val isSignedIn: Boolean = false,
    val userDisplayName: String = "",
    val userEmail: String = "",
    val categories: List<Category> = emptyList(),
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            isUser = false,
            text = "Hello! I am your Expenso AI Assistant. Ask me anything about your spending, available balance, daily budget ceiling, or say 'add an expense 300 for dinner today'."
        )
    ),
    val isLoading: Boolean = false,
    val lastQuery: String = ""
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val aiIntelligenceService: AiIntelligenceService,
    private val categoryRepository: CategoryRepository,
    private val googleAuthManager: GoogleAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                appPreferences.userEmail,
                appPreferences.userDisplayName
            ) { email, name ->
                Pair(email, name)
            }.collect { (email, name) ->
                _uiState.update {
                    it.copy(
                        isSignedIn = email.isNotBlank(),
                        userEmail = email,
                        userDisplayName = name
                    )
                }
            }
        }

        viewModelScope.launch {
            categoryRepository.observeAll().collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }
    }

    fun sendMessage(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        val userMsg = ChatMessage(isUser = true, text = trimmed)
        val currentHistory = _uiState.value.messages.map { it.isUser to it.text }

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isLoading = true,
                lastQuery = trimmed
            )
        }

        viewModelScope.launch {
            val response = aiIntelligenceService.askAi(trimmed, currentHistory)

            val aiMsg = ChatMessage(
                isUser = false,
                text = response.text,
                pendingExpenseDrafts = response.pendingDrafts,
                cardState = if (response.pendingDrafts.isNullOrEmpty()) DraftCardState.SAVED else DraftCardState.PENDING,
                isOfflineError = response.isOffline
            )

            _uiState.update {
                it.copy(
                    messages = it.messages + aiMsg,
                    isLoading = false
                )
            }
        }
    }

    fun confirmDrafts(messageId: String) {
        val state = _uiState.value
        val msg = state.messages.find { it.id == messageId } ?: return
        if (msg.cardState != DraftCardState.PENDING && msg.cardState != DraftCardState.FAILED) return

        val drafts = msg.pendingExpenseDrafts ?: return

        // 1. Immediately transition to SAVING
        _uiState.update { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.id == messageId) it.copy(cardState = DraftCardState.SAVING) else it
                }
            )
        }

        viewModelScope.launch {
            try {
                val result = aiIntelligenceService.confirmAndSaveDrafts(drafts)
                // 2. Transition to SAVED: clean single-state display
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.map {
                            if (it.id == messageId) {
                                it.copy(
                                    cardState = DraftCardState.SAVED,
                                    text = "Expense confirmed & saved to ledger.",
                                    savedBalanceSummary = result.summary,
                                    savedExpenseIds = result.savedExpenseIds
                                )
                            } else it
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.map {
                            if (it.id == messageId) it.copy(cardState = DraftCardState.FAILED) else it
                        }
                    )
                }
            }
        }
    }

    fun cancelDrafts(messageId: String) {
        val state = _uiState.value
        val msg = state.messages.find { it.id == messageId } ?: return
        if (msg.cardState != DraftCardState.PENDING && msg.cardState != DraftCardState.FAILED) return

        _uiState.update { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.id == messageId) {
                        it.copy(
                            cardState = DraftCardState.CANCELLED,
                            text = "Draft cancelled."
                        )
                    } else it
                }
            )
        }
    }

    fun undoDrafts(messageId: String) {
        val state = _uiState.value
        val msg = state.messages.find { it.id == messageId } ?: return
        if (msg.cardState != DraftCardState.SAVED || msg.savedExpenseIds.isEmpty()) return

        viewModelScope.launch {
            try {
                aiIntelligenceService.undoSaveDrafts(msg.savedExpenseIds)
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.map {
                            if (it.id == messageId) {
                                it.copy(
                                    cardState = DraftCardState.CANCELLED,
                                    text = "Expense undone and removed from ledger."
                                )
                            } else it
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateDraftCategory(messageId: String, draftId: String, category: Category) {
        _uiState.update { current ->
            current.copy(
                messages = current.messages.map { msg ->
                    if (msg.id == messageId && msg.pendingExpenseDrafts != null) {
                        val updated = msg.pendingExpenseDrafts.map { draft ->
                            if (draft.id == draftId) {
                                draft.copy(
                                    categoryId = category.id,
                                    categoryName = category.name,
                                    isOthersFallback = false,
                                    matchReason = "Changed to: ${category.name}"
                                )
                            } else draft
                        }
                        msg.copy(pendingExpenseDrafts = updated)
                    } else msg
                }
            )
        }
    }

    fun createAndAssignCategory(messageId: String, draftId: String, categoryName: String) {
        viewModelScope.launch {
            val generatedAliases = aiIntelligenceService.generateAliasesForCategory(categoryName.trim())
            val aliasesStr = generatedAliases.joinToString(", ")
            val newCat = Category(
                name = categoryName.trim(),
                isDefault = false,
                aliases = if (aliasesStr.isNotBlank()) aliasesStr else categoryName.trim().lowercase()
            )
            categoryRepository.addCategory(newCat)
            updateDraftCategory(messageId, draftId, newCat)
        }
    }

    fun retryLastMessage() {
        val last = _uiState.value.lastQuery
        if (last.isNotBlank()) {
            sendMessage(last)
        }
    }

    fun getSignInIntent(context: Context): Intent = googleAuthManager.getSignInIntent(context)

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            googleAuthManager.signIn(activity)
        }
    }

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            googleAuthManager.handleSignInResult(data)
        }
    }
}
