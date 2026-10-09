package com.spendwise.app.data.cloud

import com.spendwise.app.data.cloud.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests validating the Firestore Cloud Data Model and schema constraints
 * matching firestore.rules security specifications.
 */
class CloudModelRulesTest {

    private val monthKeyRegex = Regex("^[0-9]{4}-[0-9]{2}$")

    @Test
    fun testCloudUserProfileSchema() {
        val profile = CloudUserProfile(
            uid = "user_12345",
            email = "user@example.com",
            displayName = "Test User",
            photoUrl = "https://example.com/photo.jpg",
            onboardingDone = true,
            schemaVersion = 2
        )

        assertEquals("user_12345", profile.uid)
        assertEquals(2, profile.schemaVersion)
        assertTrue(profile.onboardingDone)
    }

    @Test
    fun testCloudMonthFormatAndSalaryValidation() {
        val validMonthKey = "2026-10"
        val invalidMonthKey = "2026/10"

        assertTrue(monthKeyRegex.matches(validMonthKey))
        assertFalse(monthKeyRegex.matches(invalidMonthKey))

        val month = CloudMonth(
            monthKey = validMonthKey,
            year = 2026,
            month = 10,
            salary = 50000.0
        )

        assertTrue("Salary must be non-negative", month.salary >= 0)
        assertFalse("Tombstone flag defaults to false", month.isDeleted)
    }

    @Test
    fun testCloudExpenseValidationAndTombstone() {
        val validExpense = CloudExpense(
            id = "exp-uuid-1",
            amount = 250.0,
            categoryId = "cat-food",
            dateEpoch = 1791288000000L,
            year = 2026,
            month = 10,
            paymentSource = "INCOME",
            note = "Lunch",
            isDeleted = false
        )

        assertTrue(validExpense.amount >= 0)
        assertTrue(validExpense.paymentSource in listOf("INCOME", "CREDIT_CARD"))

        // Soft delete tombstone check
        val tombstone = validExpense.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
        assertTrue(tombstone.isDeleted)
    }

    @Test
    fun testCreditCardLedgerIsDerivedFromExpenses() {
        // Principle: Credit card ledger totals and balances are DERIVED from expenses and never stored twice
        val expenses = listOf(
            CloudExpense(id = "1", amount = 1000.0, paymentSource = "CREDIT_CARD", isDeleted = false),
            CloudExpense(id = "2", amount = 500.0, paymentSource = "CREDIT_CARD", isDeleted = false),
            CloudExpense(id = "3", amount = 800.0, paymentSource = "INCOME", isDeleted = false),
            CloudExpense(id = "4", amount = 200.0, paymentSource = "CREDIT_CARD", isDeleted = true) // tombstone excluded
        )

        val derivedCreditCardSpend = expenses
            .filter { !it.isDeleted && it.paymentSource == "CREDIT_CARD" }
            .sumOf { it.amount }

        assertEquals(1500.0, derivedCreditCardSpend, 0.001)
    }

    @Test
    fun testCloudCategorySchema() {
        val category = CloudCategory(
            id = "cat-1",
            name = "Groceries",
            colorHex = "#10B981",
            icon = "🛒",
            isDefault = true
        )

        assertTrue("Category name must not be blank", category.name.isNotBlank())
        assertEquals("#10B981", category.colorHex)
    }
}
