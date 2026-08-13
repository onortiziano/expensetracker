package it.ciano.expensetracker.data.ingest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.repository.CategoryRepository
import it.ciano.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object ExpenseIngest {

    suspend fun process(context: Context, params: Map<String, String?>): Boolean {
        val appContext = context.applicationContext
        return when (val result = IntentExpenseParser.parse(params)) {
            is IngestResult.Success -> {
                try {
                    val db = AppDatabase.getDatabase(appContext)
                    val repository = ExpenseIngestRepository(
                        CategoryRepository(db.categoryDao()),
                        TransactionRepository(db.transactionDao(), db.transactionTagDao(), db.tagDao())
                    )
                    repository.insertExpense(result.expense)
                    val formattedAmount = DecimalFormat(
                        "0.00",
                        DecimalFormatSymbols(Locale.getDefault())
                    ).format(result.expense.amount)
                    showToast(appContext, appContext.getString(R.string.ingest_success, formattedAmount))
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ExpenseIngest", "Inserimento spesa fallito", e)
                    showToast(appContext, appContext.getString(R.string.ingest_error_db))
                    false
                }
            }
            is IngestResult.Error -> {
                val messageRes = when (result.error) {
                    IngestError.MISSING_AMOUNT -> R.string.ingest_error_missing_amount
                    IngestError.INVALID_AMOUNT -> R.string.ingest_error_invalid_amount
                }
                showToast(appContext, appContext.getString(messageRes))
                false
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
