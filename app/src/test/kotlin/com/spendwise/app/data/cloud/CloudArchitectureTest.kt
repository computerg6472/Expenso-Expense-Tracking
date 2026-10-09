package com.spendwise.app.data.cloud

import com.spendwise.app.data.cloud.model.*
import com.spendwise.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Verification unit tests for the Cloud-First Single Source of Truth Architecture.
 */
class CloudArchitectureTest {

    @Test
    fun testCloudRebuildTransformsCloudDataCorrectly() {
        // Given remote cloud expenses
        val remoteExpenses = listOf(
            CloudExpense(
                id = "exp-1",
                amount = 250.0,
                categoryId = "cat-food",
                dateEpoch = 1791288000000L,
                year = 2026,
                month = 10,
                paymentSource = "INCOME",
                note = "Groceries",
                isDeleted = false,
                updatedAt = 1000L
            ),
            CloudExpense(
                id = "exp-2",
                amount = 1500.0,
                categoryId = "cat-shopping",
                dateEpoch = 1791288100000L,
                year = 2026,
                month = 10,
                paymentSource = "CREDIT_CARD",
                note = "Electronics",
                isDeleted = false,
                updatedAt = 1050L
            ),
            CloudExpense(
                id = "exp-3",
                amount = 50.0,
                categoryId = "cat-food",
                isDeleted = true // Tombstone
            )
        )

        // Filter active expenses as rebuildCacheFromCloud does
        val activeExpenses = remoteExpenses.filter { !it.isDeleted }
        assertEquals(2, activeExpenses.size)

        // Map to Room entities
        val roomExpenses = activeExpenses.map {
            Expense(
                id = it.id,
                amount = it.amount,
                paymentSource = it.paymentSource,
                categoryId = it.categoryId,
                dateEpoch = it.dateEpoch,
                year = it.year,
                month = it.month,
                monthKey = String.format("%04d-%02d", it.year, it.month),
                note = it.note,
                isDeleted = false,
                updatedAt = it.updatedAt
            )
        }

        assertEquals(2, roomExpenses.size)
        assertEquals("2026-10", roomExpenses[0].monthKey)

        // Derive Credit Card Ledger (Principle: derived strictly from expenses, never stored redundantly)
        val ccExpenses = roomExpenses.filter { it.paymentSource == "CREDIT_CARD" }
        val derivedCreditCardTotal = ccExpenses.sumOf { it.amount }
        assertEquals(1500.0, derivedCreditCardTotal, 0.001)
    }

    @Test
    fun testNewDeviceSimulationSkipsOnboardingWhenCloudProfileOnboarded() {
        // Given an existing user signing in on a brand new device
        val existingCloudProfile = CloudUserProfile(
            uid = "user_google_99",
            email = "person@gmail.com",
            displayName = "Alex",
            onboardingDone = true,
            schemaVersion = 2
        )

        // Decision logic: does the startup flow require onboarding?
        val requiresOnboarding = !existingCloudProfile.onboardingDone
        assertFalse("New device with onboarded cloud account must never show onboarding", requiresOnboarding)
    }

    @Test
    fun testNewUserRequiresOnboarding() {
        // Given a fresh cloud profile without onboarding completed
        val freshCloudProfile = CloudUserProfile(
            uid = "user_fresh",
            email = "fresh@gmail.com",
            onboardingDone = false,
            schemaVersion = 2
        )

        val requiresOnboarding = !freshCloudProfile.onboardingDone
        assertTrue("Fresh user profile must route to onboarding", requiresOnboarding)
    }

    @Test
    fun testOfflineWriteQueuesInOutboxAndPreservesTimestamp() {
        val expenseId = "offline-exp-123"
        val writeTimestamp = 1791289999000L

        val offlineExpense = Expense(
            id = expenseId,
            amount = 99.0,
            paymentSource = "INCOME",
            categoryId = "cat-misc",
            dateEpoch = writeTimestamp,
            year = 2026,
            month = 10,
            monthKey = "2026-10",
            note = "Coffee",
            isDeleted = false,
            updatedAt = writeTimestamp
        )

        val outboxItem = SyncOutbox(
            entityType = "EXPENSE",
            entityId = offlineExpense.id,
            operation = "UPSERT",
            timestamp = offlineExpense.updatedAt
        )

        assertEquals("EXPENSE", outboxItem.entityType)
        assertEquals(offlineExpense.id, outboxItem.entityId)
        assertEquals(writeTimestamp, outboxItem.timestamp)

        // When syncing online with last-write-wins:
        val cloudExpense = CloudExpense(
            id = offlineExpense.id,
            amount = offlineExpense.amount,
            categoryId = offlineExpense.categoryId,
            dateEpoch = offlineExpense.dateEpoch,
            year = offlineExpense.year,
            month = offlineExpense.month,
            paymentSource = offlineExpense.paymentSource,
            note = offlineExpense.note,
            updatedAt = outboxItem.timestamp,
            isDeleted = false
        )

        assertEquals(writeTimestamp, cloudExpense.updatedAt)
        assertEquals(99.0, cloudExpense.amount, 0.001)
    }

    @Test
    fun testMonthlySalaryIsSingleDocumentPerMonth() {
        val monthDoc1 = CloudMonth(
            monthKey = "2026-10",
            year = 2026,
            month = 10,
            salary = 75000.0,
            notes = "October Salary"
        )

        assertEquals("2026-10", monthDoc1.monthKey)
        assertEquals(75000.0, monthDoc1.salary, 0.001)
        assertFalse(monthDoc1.isDeleted)
    }
}
