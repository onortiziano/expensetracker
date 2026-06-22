package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.model.Budget
import it.ciano.expensetracker.data.repository.BudgetRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class BudgetViewModel(private val repository: BudgetRepository) : ViewModel() {

    // Funzione di utilità per ottenere il mese e l'anno correnti
    private fun getCurrentDate(): Pair<Int, Int> {
        val calendar = Calendar.getInstance()
        return Pair(calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR))
    }

    // 1. Il budget del mese corrente (aggiornato in tempo reale)
    val currentBudget: StateFlow<Budget?> = 
        repository.getBudgetForMonth(getCurrentDate().first, getCurrentDate().second)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    // 2. Imposta o aggiorna il budget per il mese corrente
    fun setBudget(amount: Double) {
        val (month, year) = getCurrentDate()
        val budget = Budget(month = month, year = year, limitAmount = amount)
        
        viewModelScope.launch {
            repository.insertBudget(budget)
        }
    }

    // 3. Elimina il budget del mese corrente
    fun clearBudget() {
        val (month, year) = getCurrentDate()
        // Per eliminare, dobbiamo prima recuperare l'ID del budget esistente
        viewModelScope.launch {
            repository.getBudgetForMonth(month, year).firstOrNull()?.let { budget ->
                repository.deleteBudget(budget)
            }
        }
    }
}