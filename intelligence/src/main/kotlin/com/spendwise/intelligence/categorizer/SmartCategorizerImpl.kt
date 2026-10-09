package com.spendwise.intelligence.categorizer

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.feature.CategoryConfidence
import com.spendwise.intelligence.domain.feature.SmartCategorizer
import com.spendwise.intelligence.domain.model.CategoryInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartCategorizerImpl @Inject constructor(
    private val capabilityProvider: CapabilityProvider
) : SmartCategorizer {

    private val categoryOntology = mapOf(
        "Food" to listOf(
            "dinner", "lunch", "breakfast", "brunch", "coffee", "tea", "chai", "cafe",
            "restaurant", "pizza", "burger", "biryani", "swiggy", "zomato", "snack", "snacks",
            "drinks", "beer", "pub", "bar", "food", "eat", "dining", "starbucks", "subway",
            "mcdonalds", "kfc", "dosa", "idli", "bakery", "cake", "ice cream", "dominos",
            "haldiram", "sweets", "buffet", "treat", "sandwich"
        ),
        "Travel" to listOf(
            "uber", "ola", "rapido", "auto", "cab", "taxi", "petrol", "diesel", "fuel",
            "cng", "metro", "bus", "train", "flight", "toll", "fastag", "parking", "travel",
            "ticket", "indianoil", "hp petrol", "shell", "irctc", "makemytrip", "redbus",
            "airline", "commute", "fare"
        ),
        "Grocery" to listOf(
            "grocery", "groceries", "blinkit", "zepto", "instamart", "bigbasket", "supermarket",
            "dmart", "ration", "vegetables", "veggies", "fruits", "milk", "bread", "eggs",
            "store", "reliance fresh", "provisions", "chicken", "meat", "market", "kirana"
        ),
        "Medical" to listOf(
            "medicine", "medical", "pharmacy", "doctor", "clinic", "hospital", "apollo",
            "medplus", "netmeds", "1mg", "pharmeasy", "test", "lab", "dentist", "tablets",
            "pills", "syrup", "health", "consultation", "diagnosis", "eye", "spectacles", "dental"
        ),
        "Rent" to listOf(
            "rent", "house rent", "room rent", "flat rent", "pg", "maintenance", "landlord",
            "apartment", "society", "lease", "brokerage"
        ),
        "Savings" to listOf(
            "savings", "sip", "mutual fund", "stock", "stocks", "investment", "gold",
            "deposit", "fd", "rd", "ppf", "nps", "zerodha", "groww", "angelone", "crypto"
        ),
        "Shopping" to listOf(
            "shopping", "clothes", "shirt", "pants", "dress", "shoes", "amazon", "flipkart",
            "myntra", "zara", "h&m", "mall", "electronics", "gadget", "laptop", "mobile"
        ),
        "Entertainment" to listOf(
            "movie", "cinema", "theatre", "netflix", "prime", "hotstar", "spotify",
            "concert", "gaming", "game", "bookmyshow", "show", "amusement"
        ),
        "Bills" to listOf(
            "bill", "electricity", "water", "gas", "wifi", "broadband", "recharge",
            "airtel", "jio", "vi", "utility", "cylinder"
        ),
        "Hair Care" to listOf(
            "hair", "haircut", "salon", "barber", "shampoo", "conditioner", "hair oil",
            "hair spa", "hair color", "stylist", "parlour"
        ),
        "Skin Care" to listOf(
            "skin", "skincare", "skin care", "sunscreen", "moisturizer", "serum",
            "cleanser", "toner", "face wash", "facewash", "facial", "cream",
            "dermatologist", "derma", "lotion", "lip balm"
        ),
        "Orders" to listOf(
            "order", "orders", "amazon", "flipkart", "myntra", "meesho", "ajio",
            "nykaa", "online shopping", "package", "parcel", "delivery", "ecommerce",
            "courier", "online order"
        )
    )

    override suspend fun suggestCategory(
        merchant: String?,
        note: String?,
        amount: Double,
        categories: List<CategoryInfo>
    ): List<CategoryConfidence> {
        if (categories.isEmpty()) return emptyList()

        val textToAnalyze = "${merchant.orEmpty()} ${note.orEmpty()}".lowercase()
        val results = mutableListOf<CategoryConfidence>()

        for (category in categories) {
            val catName = category.name.trim()
            val catLower = catName.lowercase()
            var score = 0.0f
            var matchedKeyword: String? = null

            // 1. Direct name match in input text
            if (textToAnalyze.contains(catLower)) {
                score = maxOf(score, 0.95f)
                matchedKeyword = catName
            }

            // 2. Ontology match for category name
            val ontologyKeywords = findKeywordsForCategory(catLower)
            for (kw in ontologyKeywords) {
                if (containsWord(textToAnalyze, kw)) {
                    val kwScore = if (kw.length > 5) 0.90f else 0.80f
                    if (kwScore > score) {
                        score = kwScore
                        matchedKeyword = kw
                    }
                }
            }

            // 3. Amount heuristics (e.g. Rent is typically larger amount)
            if (catLower.contains("rent") && amount >= 5000) {
                if (score > 0f) score = (score + 0.05f).coerceAtMost(1f)
            }

            if (score > 0f) {
                results.add(CategoryConfidence(category, score, matchedKeyword))
            }
        }

        // Sort descending by confidence
        results.sortByDescending { it.confidence }

        // If no match found, fallback with equal low confidence for defaults or first category
        if (results.isEmpty() && categories.isNotEmpty()) {
            val defaultCat = categories.firstOrNull { it.isDefault } ?: categories.first()
            results.add(CategoryConfidence(defaultCat, 0.30f, null))
        }

        return results
    }

    private fun findKeywordsForCategory(categoryNameLower: String): List<String> {
        for ((key, words) in categoryOntology) {
            if (categoryNameLower.contains(key.lowercase()) || key.lowercase().contains(categoryNameLower)) {
                return words
            }
        }
        return emptyList()
    }

    private fun containsWord(text: String, word: String): Boolean {
        if (text.contains(word)) {
            val pattern = Regex("""(?i)\b${Regex.escape(word)}\b""")
            return pattern.containsMatchIn(text)
        }
        return false
    }
}
