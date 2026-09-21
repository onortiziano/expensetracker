package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.model.Debt
import it.ciano.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreditsViewModel(private val repository: TransactionRepository) : ViewModel() {
    val openCredits: StateFlow<List<Debt>> = repository
        .getAllOpenDebts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun settle(debt: Debt, rightBackTitle: String, incomeCategoryId: Int) {
        viewModelScope.launch {
            repository.settleDebt(
                debtId = debt.id,
                rightBackTitle = rightBackTitle,
                rightBackDate = System.currentTimeMillis(),
                incomeCategoryId = incomeCategoryId
            )
        }
    }
}