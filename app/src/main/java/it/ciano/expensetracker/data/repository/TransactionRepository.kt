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
    private val transactionTagDao: TransactionTagDao,
    private val tagDao: it.ciano.expensetracker.data.dao.TagDao
) {
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getAllTransactionsWithTags(): Flow<List<TransactionWithTags>> {
        return kotlinx.coroutines.flow.combine(
            transactionDao.getAllTransactions(),
            transactionTagDao.getAllTransactionTags(),
            tagDao.getAllTags()
        ) { transactions, transactionTags, tags ->
            android.util.Log.d("REPO_DEBUG", "Combine: trans=${transactions.size}, tagLinks=${transactionTags.size}, tags=${tags.size}")
            transactions.map { transaction ->
                val tagsForThisTransaction = transactionTags
                    .filter { it.transactionId == transaction.id }
                    .mapNotNull { tt -> tags.find { it.tagId == tt.tagId } }
                TransactionWithTags(transaction, tagsForThisTransaction)
            }
        }
    }

    fun getMonthlyExpenses(startInclusive: Long, endExclusive: Long): Flow<List<Transaction>> {
        return transactionDao.getMonthlyExpenses(startInclusive, endExclusive)
    }

    fun getTotalExpenses(): Flow<Double?> = transactionDao.getTotalExpenses()
    fun getTotalIncome(): Flow<Double?> = transactionDao.getTotalIncome()

    suspend fun insertTransaction(transaction: Transaction, tagIds: Set<Int>) {
        val transactionId = transactionDao.insertTransaction(transaction).toInt()
        android.util.Log.d("REPO_TAGS", "Inserita transazione ID: $transactionId con tag: $tagIds")
        saveTagsForTransaction(transactionId, tagIds)
    }

    suspend fun updateTransaction(transaction: Transaction, tagIds: Set<Int>) {
        transactionDao.updateTransaction(transaction)
        android.util.Log.d("REPO_TAGS", "Aggiornata transazione ID: ${transaction.id} con tag: $tagIds")
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
