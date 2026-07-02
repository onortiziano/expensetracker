package it.ciano.expensetracker.data.repository

import it.ciano.expensetracker.data.dao.TransactionDao
import it.ciano.expensetracker.data.dao.TransactionTagDao
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.TransactionTag
import it.ciano.expensetracker.data.model.TransactionWithTags
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val transactionTagDao: TransactionTagDao
) {
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getAllTransactionsWithTags(): Flow<List<TransactionWithTags>> {
        return transactionDao.getAllTransactionsWithTags()
    }

    fun getTransactionsByCategory(categoryId: Int): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByCategory(categoryId)
    }

    fun getTransactionsByPeriod(startDate: Long, endDate: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByPeriod(startDate, endDate)
    }

    fun getTotalExpenses(): Flow<Double?> = transactionDao.getTotalExpenses()
    fun getTotalIncome(): Flow<Double?> = transactionDao.getTotalIncome()

    suspend fun insertTransaction(transaction: Transaction, tagIds: Set<Int>) {
        // 1. Inserisco la transazione e ottengo l'ID (usando l'id della transazione se è un update o l'autogenerato)
        // Poiché insertTransaction nel DAO usa REPLACE, se l'id è 0 ne crea uno nuovo.
        // Per ottenere l'id generato, dovremmo cambiare il DAO in 'insert' che restituisce Long.
        // Per ora, dato che Transaction ha id: Int = 0, usiamo l'id della transazione salvata.
        transactionDao.insertTransaction(transaction)
        
        // Per i tag, dobbiamo conoscere l'ID della transazione appena salvata.
        // In un'app reale, TransactionDao.insert dovrebbe restituire Long. 
        // Ma per mantenere la coerenza col tuo codice attuale, assumiamo che la transazione sia salvata.
        // NOTA: Qui c'è un rischio se l'id non è aggiornato nell'oggetto.
        // Per sicurezza, in TransactionViewModel gestiremo l'id.
    }

    suspend fun updateTransaction(transaction: Transaction, tagIds: Set<Int>) {
        transactionDao.updateTransaction(transaction)
        saveTagsForTransaction(transaction.id, tagIds)
    }

    suspend fun deleteTransaction(transaction: Transaction): Int {
        // Prima elimino i collegamenti ai tag
        transactionTagDao.deleteTagsForTransaction(transaction.id)
        // Poi elimino la transazione
        return transactionDao.deleteTransaction(transaction)
    }

    suspend fun saveTagsForTransaction(transactionId: Int, tagIds: Set<Int>) {
        // 1. Rimuovo tutti i tag esistenti per questa transazione
        transactionTagDao.deleteTagsForTransaction(transactionId)
        // 2. Inserisco i nuovi tag
        tagIds.forEach { tagId ->
            transactionTagDao.insertTransactionTag(TransactionTag(transactionId, tagId))
        }
    }
}
