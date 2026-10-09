package com.spendwise.intelligence

import com.spendwise.intelligence.anomaly.AnomalyDetectorImpl
import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.DefaultCapabilityProvider
import com.spendwise.intelligence.categorizer.SmartCategorizerImpl
import com.spendwise.intelligence.domain.feature.AnomalySeverity
import com.spendwise.intelligence.domain.feature.RecurringFrequency
import com.spendwise.intelligence.domain.model.CategoryInfo
import com.spendwise.intelligence.domain.model.ExpenseRecord
import com.spendwise.intelligence.domain.model.PaymentSource
import com.spendwise.intelligence.forecast.ForecastEngineImpl
import com.spendwise.intelligence.nlp.NlpExpenseParserImpl
import com.spendwise.intelligence.recurring.RecurringDetectorImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class IntelligenceTestSuite {

    private lateinit var capabilityProvider: DefaultCapabilityProvider
    private lateinit var nlpParser: NlpExpenseParserImpl
    private lateinit var categorizer: SmartCategorizerImpl
    private lateinit var recurringDetector: RecurringDetectorImpl
    private lateinit var forecastEngine: ForecastEngineImpl
    private lateinit var anomalyDetector: AnomalyDetectorImpl

    private val categories = listOf(
        CategoryInfo(id = 1, name = "Food", isDefault = true),
        CategoryInfo(id = 2, name = "Rent", isDefault = true),
        CategoryInfo(id = 3, name = "Medical", isDefault = true),
        CategoryInfo(id = 4, name = "Grocery", isDefault = true),
        CategoryInfo(id = 5, name = "Savings", isDefault = true),
        CategoryInfo(id = 6, name = "Travel", isDefault = true)
    )

    @Before
    fun setup() {
        capabilityProvider = DefaultCapabilityProvider()
        nlpParser = NlpExpenseParserImpl(capabilityProvider)
        categorizer = SmartCategorizerImpl(capabilityProvider)
        recurringDetector = RecurringDetectorImpl(capabilityProvider)
        forecastEngine = ForecastEngineImpl(capabilityProvider)
        anomalyDetector = AnomalyDetectorImpl(capabilityProvider)
    }

    @Test
    fun testNlpParser_parsesAmountAndCreditCard() = runBlocking {
        val result = nlpParser.parse("Dinner 450 card")
        assertEquals(450.0, result.amount ?: 0.0, 0.01)
        assertEquals(PaymentSource.CREDIT_CARD, result.source)
        assertEquals("Food", result.categoryHint)
        assertTrue(result.confidence > 0.6f)
    }

    @Test
    fun testNlpParser_parsesMultiplierAndCurrency() = runBlocking {
        val result = nlpParser.parse("₹1.5k groceries at Blinkit yesterday")
        assertEquals(1500.0, result.amount ?: 0.0, 0.01)
        assertEquals("Grocery", result.categoryHint)
        assertNotNull(result.dateEpochMillis)
    }

    @Test
    fun testSmartCategorizer_classifiesTravelAndMedical() = runBlocking {
        val travelResult = categorizer.suggestCategory(
            merchant = "Uber",
            note = "Cab to airport",
            amount = 350.0,
            categories = categories
        )
        assertFalse(travelResult.isEmpty())
        assertEquals("Travel", travelResult.first().category.name)

        val medicalResult = categorizer.suggestCategory(
            merchant = "Apollo Pharmacy",
            note = "Cough syrup and paracetamol",
            amount = 180.0,
            categories = categories
        )
        assertFalse(medicalResult.isEmpty())
        assertEquals("Medical", medicalResult.first().category.name)
    }

    @Test
    fun testRecurringDetector_detectsMonthlyPattern() = runBlocking {
        val now = System.currentTimeMillis()
        val expenses = listOf(
            ExpenseRecord.fromDouble(id = "1", amount = 199.0, categoryId = "1", source = PaymentSource.CREDIT_CARD, dateEpochMillis = now - TimeUnit.DAYS.toMillis(60), merchant = "Netflix"),
            ExpenseRecord.fromDouble(id = "2", amount = 199.0, categoryId = "1", source = PaymentSource.CREDIT_CARD, dateEpochMillis = now - TimeUnit.DAYS.toMillis(30), merchant = "Netflix"),
            ExpenseRecord.fromDouble(id = "3", amount = 199.0, categoryId = "1", source = PaymentSource.CREDIT_CARD, dateEpochMillis = now, merchant = "Netflix")
        )

        val recurring = recurringDetector.detectRecurring(expenses)
        assertEquals(1, recurring.size)
        assertEquals(RecurringFrequency.MONTHLY, recurring.first().frequency)
        assertEquals(199.0, recurring.first().averageAmount, 1.0)
    }

    @Test
    fun testForecastEngine_computesP50AndDeficit() = runBlocking {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        val forecast = forecastEngine.forecastMonthEnd(
            year = year,
            month = month,
            currentSpend = 40000.0,
            salary = 50000.0,
            history = emptyList()
        )

        assertTrue(forecast.projectedTotalSpend >= 40000.0)
        assertNotNull(forecast.confidence)
        assertTrue(forecast.dailyProjections.isNotEmpty())
    }

    @Test
    fun testAnomalyDetector_flagsOutlierExpense() = runBlocking {
        val normalExpenses = (1..10).map { i ->
            ExpenseRecord.fromDouble(
                id = i.toString(),
                amount = 200.0 + (i % 20),
                categoryId = "1",
                source = PaymentSource.INCOME,
                dateEpochMillis = System.currentTimeMillis()
            )
        }

        val spikeExpense = ExpenseRecord.fromDouble(
            id = "999",
            amount = 3500.0, // Significant spike compared to 200 avg
            categoryId = "1",
            source = PaymentSource.INCOME,
            dateEpochMillis = System.currentTimeMillis()
        )

        val anomaly = anomalyDetector.evaluateExpense(spikeExpense, normalExpenses)
        assertNotNull(anomaly)
        assertEquals(AnomalySeverity.HIGH, anomaly?.severity)
    }
}
