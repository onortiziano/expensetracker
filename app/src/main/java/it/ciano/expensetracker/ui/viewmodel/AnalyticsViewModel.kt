package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.repository.*
import it.ciano.expensetracker.data.model.*
import java.util.*

data class BudgetComparison(
    val monthLabel: String,
    val plannedBudget: Double,
    val actualSpending: Double,
    val month: Int,
    val year: Int
)

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val globalBudgetRepo = GlobalBudgetRepository(application)
    private val categoryBudgetRepo = CategoryBudgetRepository(application)
    private val transactionRepo = TransactionRepository(
        db.transactionDao(),
        db.transactionTagDao(),
        db.tagDao()
    )

    // Stato del periodo di analisi
    private val _startDate = MutableStateFlow(Calendar.getInstance().apply { 
        set(Calendar.DAY_OF_MONTH, 1) 
    }.timeInMillis)
    val startDate: StateFlow<Long> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow(Calendar.getInstance().timeInMillis)
    val endDate: StateFlow<Long> = _endDate.asStateFlow()

    fun updateStartDate(date: Long) { _startDate.value = date }
    fun updateEndDate(date: Long) { _endDate.value = date }

    // Flusso principale per il grafico: confronta budget e spesa per ogni mese nell'intervallo
    val monthlyComparison: Flow<List<BudgetComparison>> = combine(_startDate, _endDate) { start, end ->
        start to end
    }.flatMapLatest { (start, end) ->
        val months = generateMonthRange(start, end)
        combine(
            *months.map { monthData ->
                combine(
                    globalBudgetRepo.getBudgetForMonth(monthData.month, monthData.year),
                    transactionRepo.getTransactionsByPeriod(start, end) // In realtà dovremmo filtrare per mese specifico
                ) { budget, transactions ->
                    val totalBudget = budget?.amount ?: 0.0
                    val spending = transactions
                        .filter { 
                            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
                            cal.get(Calendar.MONTH) + 1 == monthData.month && cal.get(Calendar.YEAR) == monthData.year 
                        }
                        .filter { it.type == "EXPENSE" }
                        .sumOf { it.amount }
                    
                    BudgetComparison(
                        monthLabel = "${monthData.month}/${monthData.year}",
                        plannedBudget = totalBudget,
                        actualSpending = spending,
                        month = monthData.month,
                        year = monthData.year
                    )
                }
            }.toTypedArray()
        ) { it.toList() }
    }

    private fun generateMonthRange(start: Long, end: Long): List<MonthYear> {
        val result = mutableListOf<MonthYear>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        cal.set(Calendar.DAY_OF_MONTH, 1)

        while (cal.timeInMillis <= end) {
            result.add(MonthYear(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)))
            cal.add(Calendar.MONTH, 1)
        }
        return result
    }

    private data class MonthYear(val month: Int, val year: Int)
}