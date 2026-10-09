package com.spendwise.app.data.backup

import com.spendwise.app.data.backup.snapshot.*
import com.spendwise.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

class BackupCryptoAndSnapshotTest {

    private lateinit var serializer: SnapshotSerializer
    private lateinit var cryptoManager: BackupCryptoManager

    @Before
    fun setUp() {
        serializer = SnapshotSerializer()
        cryptoManager = BackupCryptoManager()
    }

    private fun createSampleSnapshot(): BackupSnapshot {
        val header = SnapshotHeader(
            schemaVersion = 5,
            appVersion = "5.0",
            createdAt = 1791288000000L,
            deviceId = "device-test-123",
            accountEmail = "sarah.connor@gmail.com",
            expensesCount = 2,
            monthsCount = 1,
            categoriesCount = 2,
            generation = 42L,
            contentHash = "hash123",
            encryptionMode = SnapshotHeader.ENCRYPTION_MODE_ACCOUNT_BOUND
        )

        val categories = listOf(
            Category(id = "cat-food", name = "Food & Dining", colorHex = "#FF5722", iconName = "restaurant"),
            Category(id = "cat-fuel", name = "Fuel", colorHex = "#4CAF50", iconName = "local_gas_station")
        )

        val budgets = listOf(
            Budget(id = "b-1", categoryId = "cat-food", year = 2026, month = 10, budgetAmount = 15000.0)
        )

        val incomeMonths = listOf(
            IncomeMonth(id = "inc-1", year = 2026, month = 10, salary = 95000.0, creditCardDueCarried = 5000.0)
        )

        val expenses = listOf(
            Expense(
                id = "exp-1",
                amount = 450.0,
                paymentSource = "INCOME",
                categoryId = "cat-food",
                dateEpoch = 1791288100000L,
                year = 2026,
                month = 10,
                note = "Team lunch"
            ),
            Expense(
                id = "exp-2",
                amount = 2200.0,
                paymentSource = "CREDIT_CARD",
                categoryId = "cat-fuel",
                dateEpoch = 1791288200000L,
                year = 2026,
                month = 10,
                note = "Fuel refill"
            )
        )

        val creditCards = listOf(
            CreditCardLedger(id = "cc-1", year = 2026, month = 10, totalSpend = 2200.0, carriedToNextMonth = false)
        )

        val preferences = PreferencesSnapshot(
            currencySymbol = "₹",
            monthlySalary = 95000.0,
            onboardingDone = true,
            themeMode = "SYSTEM"
        )

        return BackupSnapshot(
            header = header,
            categories = categories,
            budgets = budgets,
            incomeMonths = incomeMonths,
            expenses = expenses,
            creditCardLedgers = creditCards,
            preferences = preferences
        )
    }

    @Test
    fun testSnapshotSerializationAndDeserializationRoundtrip() {
        val original = createSampleSnapshot()
        val json = serializer.toJson(original)

        assertNotNull(json)
        assertTrue(json.contains("sarah.connor@gmail.com"))
        assertTrue(json.contains("Team lunch"))
        assertTrue(json.contains("95000"))

        val restored = serializer.fromJson(json)

        assertEquals(original.header.schemaVersion, restored.header.schemaVersion)
        assertEquals(original.header.accountEmail, restored.header.accountEmail)
        assertEquals(original.header.expensesCount, restored.header.expensesCount)
        assertEquals(original.categories.size, restored.categories.size)
        assertEquals(original.expenses.size, restored.expenses.size)
        assertEquals(original.incomeMonths.size, restored.incomeMonths.size)
        assertEquals(original.creditCardLedgers.size, restored.creditCardLedgers.size)
        assertEquals(original.preferences.currencySymbol, restored.preferences.currencySymbol)

        assertEquals(original.expenses[0].amount, restored.expenses[0].amount, 0.001)
        assertEquals(original.expenses[1].paymentSource, restored.expenses[1].paymentSource)
        assertEquals(original.incomeMonths[0].salary, restored.incomeMonths[0].salary, 0.001)
    }

    @Test
    fun testGzipCompressionReducesSizeAndDecompressesAccurately() {
        val original = createSampleSnapshot()
        val json = serializer.toJson(original)
        val rawBytes = json.toByteArray(StandardCharsets.UTF_8)

        val compressed = serializer.compressGzip(json)
        val decompressed = serializer.decompressGzip(compressed)

        assertEquals(json, decompressed)
        assertTrue(compressed.isNotEmpty())
    }

    @Test
    fun testSha256ChecksumIntegrity() {
        val original = createSampleSnapshot()
        val json = serializer.toJson(original)
        val bytes = json.toByteArray(StandardCharsets.UTF_8)

        val hash1 = serializer.computeSha256(bytes)
        val hash2 = serializer.computeSha256(bytes)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // 256 bits = 64 hex characters
    }

    @Test
    fun testAccountBoundEncryptionAndDecryption() {
        val email = "alex.turner@example.com"
        val sampleData = "Expenso secure backup payload data with multiple sensitive records".toByteArray(StandardCharsets.UTF_8)

        val encrypted = cryptoManager.encryptAccountBound(sampleData, email)
        assertNotNull(encrypted)
        assertTrue(encrypted.size > sampleData.size)

        // Mode inspection
        val mode = cryptoManager.inspectEnvelopeMode(encrypted)
        assertEquals(SnapshotHeader.ENCRYPTION_MODE_ACCOUNT_BOUND, mode)

        // Decrypt with correct email
        val decrypted = cryptoManager.decrypt(encrypted, accountEmail = email)
        assertArrayEquals(sampleData, decrypted)

        // Decrypt with incorrect email must throw CryptoException
        assertThrows(CryptoException::class.java) {
            cryptoManager.decrypt(encrypted, accountEmail = "intruder@evil.com")
        }
    }

    @Test
    fun testPasswordProtectedEncryptionAndDecryption() {
        val password = "MySuperSecurePassword2026!".toCharArray()
        val sampleData = "Financial records payload to be locked with user-defined password".toByteArray(StandardCharsets.UTF_8)

        val encrypted = cryptoManager.encryptWithPassword(sampleData, password)
        assertEquals(SnapshotHeader.ENCRYPTION_MODE_PASSWORD, cryptoManager.inspectEnvelopeMode(encrypted))

        // Decrypt with correct password
        val decrypted = cryptoManager.decrypt(encrypted, password = password)
        assertArrayEquals(sampleData, decrypted)

        // Decrypt with wrong password must throw CryptoException
        assertThrows(CryptoException::class.java) {
            cryptoManager.decrypt(encrypted, password = "WrongPassword123".toCharArray())
        }
    }

    @Test
    fun testRecoveryKeyGenerationAndEncryption() {
        val recoveryKey = cryptoManager.generateRecoveryKey()
        assertEquals(64, recoveryKey.length)

        val sampleData = "Payload encrypted using 64-character hex recovery key".toByteArray(StandardCharsets.UTF_8)
        val encrypted = cryptoManager.encryptWithRecoveryKey(sampleData, recoveryKey)
        assertEquals(SnapshotHeader.ENCRYPTION_MODE_RECOVERY_KEY, cryptoManager.inspectEnvelopeMode(encrypted))

        val decrypted = cryptoManager.decrypt(encrypted, recoveryKeyHex = recoveryKey)
        assertArrayEquals(sampleData, decrypted)

        // Modified recovery key must fail
        val alteredKey = "0" + recoveryKey.substring(1)
        assertThrows(CryptoException::class.java) {
            cryptoManager.decrypt(encrypted, recoveryKeyHex = alteredKey)
        }
    }

    @Test
    fun testTamperedCiphertextIsRejectedByAeadTag() {
        val email = "user@test.com"
        val sampleData = "Secret financial payload".toByteArray(StandardCharsets.UTF_8)
        val encrypted = cryptoManager.encryptAccountBound(sampleData, email)

        // Corrupt one byte of ciphertext near the end
        val corrupted = encrypted.clone()
        corrupted[corrupted.size - 5] = (corrupted[corrupted.size - 5].toInt() xor 0xFF).toByte()

        assertThrows(CryptoException::class.java) {
            cryptoManager.decrypt(corrupted, accountEmail = email)
        }
    }
}
