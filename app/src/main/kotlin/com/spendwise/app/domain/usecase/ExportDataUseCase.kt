package com.spendwise.app.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.spendwise.app.data.local.entity.Expense
import com.spendwise.app.domain.repository.CategoryRepository
import com.spendwise.app.domain.repository.ExpenseRepository
import com.opencsv.CSVWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class ExportDataUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) {
    /**
     * Export all expenses to CSV and return a shareable URI.
     */
    suspend fun exportCsv(context: Context, year: Int, month: Int): Uri = withContext(Dispatchers.IO) {
        val expenses = expenseRepository.observeByYearMonth(year, month).first()
        val categories = categoryRepository.getAll().associateBy { it.id }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val fileName = "spendwise_${year}_${String.format("%02d", month)}.csv"
        val file = File(context.cacheDir, fileName)

        CSVWriter(FileWriter(file)).use { writer ->
            writer.writeNext(arrayOf("Date", "Amount", "Category", "Payment Source", "Note"))
            expenses.forEach { expense ->
                writer.writeNext(arrayOf(
                    sdf.format(Date(expense.dateEpoch)),
                    expense.amount.toString(),
                    categories[expense.categoryId]?.name ?: "Unknown",
                    expense.paymentSource,
                    expense.note
                ))
            }
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
