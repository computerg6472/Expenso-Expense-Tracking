package com.spendwise.intelligence.nlp

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.feature.NlpExpenseParser
import com.spendwise.intelligence.domain.feature.ParsedExpense
import com.spendwise.intelligence.domain.model.PaymentSource
import java.util.Calendar
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NlpExpenseParserImpl @Inject constructor(
    private val capabilityProvider: CapabilityProvider
) : NlpExpenseParser {

    override suspend fun parse(input: String): ParsedExpense {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ParsedExpense(
                rawInput = input,
                amount = null,
                categoryHint = null,
                source = null,
                merchant = null,
                note = "",
                dateEpochMillis = null,
                confidence = 0f,
                isFallback = true
            )
        }

        val lower = trimmed.lowercase()

        // 1. Extract Amount
        val (amount, amountMatchRange) = extractAmount(trimmed)

        // 2. Extract Payment Source
        val (source, sourceMatchRange) = extractPaymentSource(lower)

        // 3. Extract Date Offset
        val (dateEpoch, dateMatchRange) = extractDate(lower)

        // 4. Extract Category Hint
        val categoryHint = extractCategoryHint(lower)

        // 5. Extract Merchant and clean Note
        val merchant = extractMerchant(trimmed)
        val cleanNote = buildCleanNote(
            raw = trimmed,
            rangesToRemove = listOfNotNull(amountMatchRange, sourceMatchRange, dateMatchRange)
        )

        val confidence = calculateConfidence(amount, categoryHint, source, merchant)

        return ParsedExpense(
            rawInput = input,
            amount = amount,
            categoryHint = categoryHint,
            source = source ?: PaymentSource.INCOME,
            merchant = merchant,
            note = cleanNote,
            dateEpochMillis = dateEpoch,
            confidence = confidence,
            isFallback = true // Rule-based NLP is the robust offline bedrock
        )
    }

    private fun extractAmount(text: String): Pair<Double?, IntRange?> {
        // Support: ₹450, Rs. 450, 450 rs, 450/-, 1.5k, 2.5K, $450, 450
        val pattern = Pattern.compile(
            """(?i)(?:₹|rs\.?|inr|\$|€|£)?\s*(\d+(?:,\d+)*(?:\.\d+)?)\s*(k|k\b|/-|rs\.?|bucks|rupees)?""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(text)

        while (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "") ?: continue
            val suffix = matcher.group(2)?.lowercase() ?: ""
            var parsed = numStr.toDoubleOrNull() ?: continue

            if (suffix.startsWith("k")) {
                parsed *= 1000.0
            }

            if (parsed > 0.0) {
                return Pair(parsed, matcher.start()..matcher.end())
            }
        }

        return Pair(null, null)
    }

    private fun extractPaymentSource(lower: String): Pair<PaymentSource?, IntRange?> {
        val ccKeywords = listOf("credit card", "credit", "cc", "card", "hdfc card", "icici card", "sbi card", "amex", "visa", "mastercard")
        for (kw in ccKeywords) {
            val idx = findWordBoundary(lower, kw)
            if (idx != -1) {
                return Pair(PaymentSource.CREDIT_CARD, idx until (idx + kw.length))
            }
        }

        val incomeKeywords = listOf("cash", "debit card", "debit", "upi", "gpay", "phonepe", "paytm", "bank", "income", "salary")
        for (kw in incomeKeywords) {
            val idx = findWordBoundary(lower, kw)
            if (idx != -1) {
                return Pair(PaymentSource.INCOME, idx until (idx + kw.length))
            }
        }

        return Pair(null, null)
    }

    private fun extractDate(lower: String): Pair<Long?, IntRange?> {
        val cal = Calendar.getInstance()

        if (lower.contains("day before yesterday")) {
            val idx = lower.indexOf("day before yesterday")
            cal.add(Calendar.DAY_OF_YEAR, -2)
            return Pair(cal.timeInMillis, idx until (idx + "day before yesterday".length))
        }

        if (lower.contains("yesterday")) {
            val idx = lower.indexOf("yesterday")
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return Pair(cal.timeInMillis, idx until (idx + "yesterday".length))
        }

        if (lower.contains("today")) {
            val idx = lower.indexOf("today")
            return Pair(cal.timeInMillis, idx until (idx + "today".length))
        }

        return Pair(null, null)
    }

    private fun extractCategoryHint(lower: String): String? {
        val foodKeywords = setOf(
            "dinner", "lunch", "breakfast", "coffee", "tea", "chai", "cafe", "restaurant",
            "pizza", "burger", "biryani", "swiggy", "zomato", "snack", "snacks", "drinks",
            "beer", "pub", "bar", "food", "eat", "dining", "starbucks", "subway", "mcdonalds", "kfc", "dosa"
        )
        val travelKeywords = setOf(
            "uber", "ola", "rapido", "auto", "cab", "taxi", "petrol", "diesel", "fuel",
            "cng", "metro", "bus", "train", "flight", "toll", "fastag", "parking", "travel",
            "travell", "trip", "ticket", "indianoil", "hp petrol", "shell"
        )
        val groceryKeywords = setOf(
            "grocery", "groceries", "blinkit", "zepto", "instamart", "bigbasket", "supermarket",
            "dmart", "ration", "vegetables", "veggies", "fruits", "milk", "bread", "eggs", "store"
        )
        val shoppingKeywords = setOf(
            "shopping", "amazon", "flipkart", "myntra", "ajio", "meesho", "clothes", "shoes",
            "dress", "shirt", "pants", "mall", "zara", "h&m", "electronics", "gadget", "watch"
        )
        val billsKeywords = setOf(
            "bill", "bills", "electricity", "bescom", "power", "water", "wifi", "internet",
            "broadband", "recharge", "mobile recharge", "jio", "airtel", "vi", "dth", "gas", "lpg", "cylinder"
        )
        val entertainmentKeywords = setOf(
            "movie", "cinema", "theatre", "bookmyshow", "pvr", "inox", "netflix", "hotstar",
            "prime", "spotify", "concert", "game", "gaming", "bowling", "fun", "outing"
        )
        val medicalKeywords = setOf(
            "medicine", "medical", "pharmacy", "doctor", "clinic", "hospital", "apollo",
            "medplus", "netmeds", "1mg", "pharmeasy", "test", "lab", "dentist", "tablets", "pills"
        )
        val rentKeywords = setOf(
            "rent", "house rent", "room rent", "flat rent", "pg", "maintenance", "landlord"
        )
        val savingsKeywords = setOf(
            "savings", "sip", "mutual fund", "stock", "stocks", "investment", "gold", "deposit", "fd", "rd"
        )
        val educationKeywords = setOf(
            "fees", "school", "college", "tuition", "course", "udemy", "coursera", "book", "books", "stationery"
        )

        return when {
            foodKeywords.any { lower.contains(it) } -> "Food"
            travelKeywords.any { lower.contains(it) } -> "Travel"
            groceryKeywords.any { lower.contains(it) } -> "Grocery"
            shoppingKeywords.any { lower.contains(it) } -> "Shopping"
            billsKeywords.any { lower.contains(it) } -> "Bills"
            entertainmentKeywords.any { lower.contains(it) } -> "Entertainment"
            medicalKeywords.any { lower.contains(it) } -> "Medical"
            rentKeywords.any { lower.contains(it) } -> "Rent"
            savingsKeywords.any { lower.contains(it) } -> "Savings"
            educationKeywords.any { lower.contains(it) } -> "Education"
            else -> null
        }
    }

    private fun extractMerchant(text: String): String? {
        val patterns = listOf(
            Pattern.compile("""(?i)\b(?:at|from|to|in)\s+([A-Za-z0-9&'\-]+(?:\s+[A-Za-z0-9&'\-]+)?)"""),
            Pattern.compile("""(?i)\b(uber|ola|rapido|blinkit|zepto|swiggy|zomato|amazon|flipkart|starbucks|mcdonalds|kfc|subway|dominos|dmart|apollo|netmeds|indianoil)\b""")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() && candidate.length > 1) {
                    val candidateLower = candidate.lowercase()
                    if (!listOf("credit", "card", "cash", "upi", "yesterday", "today").contains(candidateLower)) {
                        return candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                }
            }
        }
        return null
    }

    private fun buildCleanNote(raw: String, rangesToRemove: List<IntRange>): String {
        var result = raw
        val sortedRanges = rangesToRemove.sortedByDescending { it.first }

        for (range in sortedRanges) {
            if (range.first in 0..result.length && range.last < result.length) {
                result = result.removeRange(range.first, range.last + 1)
            }
        }

        // Clean leftover filler words like "paid", "spent", "on", "via", "with", "for"
        result = result.replace(Regex("""(?i)\b(paid|spent|on|for|with|via|by|using)\b"""), " ")
        result = result.replace(Regex("""\s+"""), " ").trim()

        if (result.isBlank()) {
            result = raw.trim()
        }

        return result.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun findWordBoundary(text: String, word: String): Int {
        val regex = Regex("""(?i)\b${Regex.escape(word)}\b""")
        return regex.find(text)?.range?.first ?: -1
    }

    private fun calculateConfidence(
        amount: Double?,
        categoryHint: String?,
        source: PaymentSource?,
        merchant: String?
    ): Float {
        var score = 0.0f
        if (amount != null) score += 0.45f
        if (categoryHint != null) score += 0.25f
        if (source != null) score += 0.15f
        if (merchant != null) score += 0.15f
        return score.coerceIn(0f, 1f)
    }
}
