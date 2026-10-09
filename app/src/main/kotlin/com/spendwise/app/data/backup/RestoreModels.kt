package com.spendwise.app.data.backup

sealed class RestoreStep {
    object SignedIn : RestoreStep()
    object ReachingCloud : RestoreStep()
    object CheckingBackup : RestoreStep()
    data class LoadingCurrentMonth(val monthKey: String) : RestoreStep()
    data class LoadingHistory(val done: Int, val total: Int) : RestoreStep()
    data class VerifyingChecksums(val mismatched: Int) : RestoreStep()
    object Complete : RestoreStep()
}

sealed class RestoreResult {
    data class Success(val expensesRestored: Int, val monthsRestored: Int, val fromCache: Boolean = false) : RestoreResult()
    data class NewAccount(val uid: String) : RestoreResult()
    sealed class Failure : RestoreResult() {
        data class Offline(val hasCachedData: Boolean) : Failure()
        data class PermissionDenied(val detail: String) : Failure()
        data class NotFound(val uid: String) : Failure()
        data class Timeout(val step: String) : Failure()
        data class PasswordRequired(val mode: Int) : Failure()
        data class CorruptedMonth(val monthKey: String, val error: String) : Failure()
        data class Unknown(val message: String, val cause: Throwable? = null) : Failure()
    }
}
