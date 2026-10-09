package com.spendwise.intelligence.receipt

import com.spendwise.intelligence.capability.AiFeature
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.domain.feature.ReceiptItem
import com.spendwise.intelligence.domain.feature.ReceiptScanResult
import com.spendwise.intelligence.domain.feature.ReceiptScanner
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptScannerImpl @Inject constructor(
    private val capabilityProvider: CapabilityProvider
) : ReceiptScanner {

    override suspend fun scanReceipt(imageBytes: ByteArray): ReceiptScanResult {
        // If imageBytes contains raw text or OCR-extracted text bytes
        val text = try {
            String(imageBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
        return parseReceiptText(text)
    }

    fun parseReceiptText(rawText: String): ReceiptScanResult {
        if (rawText.isBlank()) {
            return ReceiptScanResult(
                merchant = null,
                totalAmount = null,
                dateEpochMillis = null,
                items = emptyList(),
                rawText = rawText,
                confidence = 0f,
                isFallback = true
            )
        }

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // 1. Extract Merchant (typically in the top 3 non-empty lines)
        var merchantCandidate: String? = null
        for (i in 0 until minOf(3, lines.size)) {
            val line = lines[i]
            if (!line.matches(Regex("""(?i).*(tax invoice|receipt|bill|cash memo|welcome|store|tel:|gstin).*"""))) {
                merchantCandidate = line
                break
            }
        }

        // 2. Extract Total Amount
        var totalAmount: Double? = null
        val totalPattern = Pattern.compile(
            """(?i)\b(?:total|grand\s*total|net\s*amount|bill\s*amount|amount\s*payable|subtotal)\s*[:=]?\s*(?:₹|rs\.?|inr|\$)?\s*(\d+(?:,\d+)*(?:\.\d{1,2})?)"""
        )

        for (line in lines.reversed()) {
            val matcher = totalPattern.matcher(line)
            if (matcher.find()) {
                val num = matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (num != null && num > 0) {
                    totalAmount = num
                    break
                }
            }
        }

        // Fallback: search for highest number near the bottom
        if (totalAmount == null) {
            val numberRegex = Regex("""(?:₹|rs\.?|inr|\$)?\s*(\d+(?:\.\d{1,2})?)""")
            for (line in lines.takeLast(5)) {
                val matches = numberRegex.findAll(line).toList()
                for (m in matches) {
                    val num = m.groups[1]?.value?.toDoubleOrNull()
                    if (num != null && (totalAmount == null || num > totalAmount)) {
                        totalAmount = num
                    }
                }
            }
        }

        // 3. Extract Line Items
        val items = mutableListOf<ReceiptItem>()
        val lineItemRegex = Regex("""^([A-Za-z0-9\s\-]+?)\s+(?:₹|rs\.?|inr|\$)?\s*(\d+(?:\.\d{1,2})?)$""")
        for (line in lines) {
            val match = lineItemRegex.find(line)
            if (match != null) {
                val itemName = match.groups[1]?.value?.trim().orEmpty()
                val price = match.groups[2]?.value?.toDoubleOrNull() ?: 0.0
                if (itemName.length > 2 && price > 0.0 && !itemName.equals("total", ignoreCase = true)) {
                    items.add(ReceiptItem(name = itemName, amount = price))
                }
            }
        }

        val confidence = when {
            merchantCandidate != null && totalAmount != null -> 0.90f
            totalAmount != null -> 0.70f
            merchantCandidate != null -> 0.50f
            else -> 0.20f
        }

        return ReceiptScanResult(
            merchant = merchantCandidate,
            totalAmount = totalAmount,
            dateEpochMillis = System.currentTimeMillis(),
            items = items,
            rawText = rawText,
            confidence = confidence,
            isFallback = true
        )
    }
}
