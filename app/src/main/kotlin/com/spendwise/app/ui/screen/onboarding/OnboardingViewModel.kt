package com.spendwise.app.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.preferences.AppPreferences
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

data class OnboardingUiState(
    val salaryInput: String = "50000",
    val selectedCurrency: String = "₹",
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val backupTriggerManager: com.spendwise.app.data.backup.BackupTriggerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onSalaryChanged(input: String) {
        val clean = input.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(salaryInput = clean, errorMessage = null) }
    }

    fun onCurrencySelected(symbol: String) {
        _uiState.update { it.copy(selectedCurrency = symbol) }
    }

    fun completeOnboarding() {
        val salary = _uiState.value.salaryInput.toDoubleOrNull() ?: 0.0
        val currency = _uiState.value.selectedCurrency

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            withContext(Dispatchers.IO) {
                try {
                    val cal = Calendar.getInstance()
                    val year = cal.get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH) + 1

                    // 1. Populate local Room database
                    appPreferences.setCurrencySymbol(currency)
                    appPreferences.setOnboardingDone(true)
                    incomeRepository.setSalary(year, month, salary)
                    categoryRepository.insertDefaults()
                    categoryRepository.ensureOthersCategory()

                    // 2. Schedule initial snapshot backup to Google Drive
                    backupTriggerManager.markDataChanged()

                    _uiState.update { it.copy(isLoading = false, isComplete = true) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    appPreferences.setCurrencySymbol(currency)
                    appPreferences.setOnboardingDone(true)
                    _uiState.update { it.copy(isLoading = false, isComplete = true) }
                }
            }
        }
    }
}
