package it.ciano.expensetracker.data.repository

import it.ciano.expensetracker.data.dao.TransactionDao
import it.ciano.expensetracker.data.model.Transaction
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    // Recupera tutte le transazioni (ordinate dalla più recente)
    fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions()
    }

    // Recupera le transazioni filtrate per una specifica categoria
    fun getTransactionsByCategory(categoryId: Int): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByCategory(categoryId)
    }

    // Recupera le transazioni in un intervallo di date (es. inizio e fine mese)
    fun getTransactionsByPeriod(startDate: Long, endDate: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByPeriod(startDate, endDate)
    }

    // CALCOLO: Restituisce il totale di tutte le USCITE (Expense)
    fun getTotalExpenses(): Flow<Double?> {
        return transactionDao.getTotalExpenses()
    }

    // CALCOLO: Restituisce il totale di tutte le ENTRATE (Income)
    fun getTotalIncome(): Flow<Double?> {
        return transactionDao.getTotalIncome()
    }

    // Operazioni di scrittura (suspend perché sono lente)
    suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }
	
	suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }
}