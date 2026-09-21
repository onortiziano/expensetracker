package it.ciano.expensetracker.data.repository

import androidx.room.withTransaction
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.dao.TagDao
import it.ciano.expensetracker.data.dao.TransactionDao
import it.ciano.expensetracker.data.dao.TransactionTagDao
import it.ciano.expensetracker.data.import.ImportDeduplicator
import it.ciano.expensetracker.data.import.ImportError
import it.ciano.expensetracker.data.import.ImportOutcome
import it.ciano.expensetracker.data.import.ImportRow
import it.ciano.expensetracker.data.model.Debt
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.TransactionTag
import it.ciano.expensetracker.data.model.TransactionWithTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class TransactionRepository(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val transactionTagDao: TransactionTagDao,
    private val tagDao: TagDao
) {
    private val debtDao = database.debtDao()
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getAllTransactionsWithTags(): Flow<List<TransactionWithTags>> {
        return combine(
            transactionDao.getAllTransactions(),
            transactionTagDao.getAllTransactionTags(),
            tagDao.getAllTags()
        ) { transactions, transactionTags, tags ->
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
        saveTagsForTransaction(transactionId, tagIds)
    }

    suspend fun updateTransaction(transaction: Transaction, tagIds: Set<Int>) {
        transactionDao.updateTransaction(transaction)
        saveTagsForTransaction(transaction.id, tagIds)
    }

    suspend fun deleteTransaction(transaction: Transaction): Int {
        transactionTagDao.deleteTagsForTransaction(transaction.id)
        return transactionDao.deleteTransaction(transaction)
    }

    suspend fun saveTagsForTransaction(transactionId: Int, tagIds: Set<Int>) {
        transactionTagDao.deleteTagsForTransaction(transactionId)
        tagIds.forEach { tagId ->
            transactionTagDao.insertTransactionTag(TransactionTag(transactionId, tagId))
        }
    }

    /**
     * Modality B1: one full EXPENSE transaction + one open [Debt] per other
     * person, written in a single atomic transaction (no partial splits).
     */
    suspend fun insertSplitWithDebts(transaction: Transaction, debts: List<Debt>, tagIds: Set<Int>): Long {
        var splitId = 0L
        database.withTransaction {
            splitId = transactionDao.insertTransaction(transaction)
            val tid = splitId.toInt()
            debts.forEach { debtDao.insertDebt(it.copy(transactionId = tid)) }
            saveTagsForTransaction(tid, tagIds)
        }
        return splitId
    }

    /**
     * Settles an open debt: marks it settled and inserts an automatic INCOME
     * right-back transaction, atomically. If the debt is already settled it is
     * a no-op (no duplicate INCOME, no toggle-back). Returns the settled amount
     * (as a Long) or -1 when the debt was not found / already settled.
     */
    suspend fun settleDebt(
        debtId: Int,
        rightBackTitle: String,
        rightBackDate: Long,
        incomeCategoryId: Int
    ): Long {
        // Already settled → no-op (no duplicate INCOME, no toggle-back).
        val open = debtDao.getAllOpenDebts().first().firstOrNull { it.id == debtId } ?: return -1L
        database.withTransaction {
            debtDao.markSettled(debtId, rightBackDate)
            transactionDao.insertTransaction(
                Transaction(
                    title = rightBackTitle,
                    amount = open.amount,
                    type = "INCOME",
                    categoryId = incomeCategoryId,
                    date = rightBackDate,
                    note = ""
                )
            )
        }
        return open.amount.toLong()
    }

    /**
     * Importa in modo atomico una lista di transazioni dal file. Se
     * [skipDuplicates] è true, salta le righe che corrispondono esattamente
     * (titolo+importo+data+tipo) a una transazione già presente.
     */
    suspend fun bulkInsert(rows: List<ImportRow>, skipDuplicates: Boolean = true): ImportOutcome {
        val (toInsert, skipped) = if (skipDuplicates) {
            ImportDeduplicator.split(rows) { row ->
                transactionDao.countExactMatch(
                    title = row.transaction.title,
                    amount = row.transaction.amount,
                    date = row.transaction.date,
                    type = row.transaction.type
                ) > 0
            }
        } else {
            rows to 0
        }

        val errors = mutableListOf<ImportError>()
        database.withTransaction {
            for (row in toInsert) {
                try {
                    transactionDao.insertTransaction(
                        Transaction(
                            title = row.transaction.title,
                            amount = row.transaction.amount,
                            type = row.transaction.type,
                            categoryId = row.categoryId,
                            date = row.transaction.date,
                            note = row.transaction.note
                        )
                    )
                } catch (e: Exception) {
                    errors.add(
                        ImportError(
                            line = row.transaction.sourceLine,
                            reason = e.message ?: "Errore db"
                        )
                    )
                }
            }
        }
        return ImportOutcome(imported = toInsert.size - errors.size, skippedDuplicate = skipped, errors = errors)
    }
}