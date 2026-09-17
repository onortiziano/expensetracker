package it.ciano.expensetracker.data.repository

import androidx.room.withTransaction
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.dao.RecurringTransactionDao
import it.ciano.expensetracker.data.dao.RecurringTransactionTagDao
import it.ciano.expensetracker.data.dao.TagDao
import it.ciano.expensetracker.data.dao.TransactionDao
import it.ciano.expensetracker.data.dao.TransactionTagDao
import it.ciano.expensetracker.data.generation.GeneratedOccurrence
import it.ciano.expensetracker.data.model.RecurringTransaction
import it.ciano.expensetracker.data.model.RecurringTransactionTag
import it.ciano.expensetracker.data.model.RecurringTransactionWithTags
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.TransactionTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RecurringTransactionRepository(
    private val database: AppDatabase,
    private val recurringDao: RecurringTransactionDao,
    private val recurringTagDao: RecurringTransactionTagDao,
    private val transactionDao: TransactionDao,
    private val transactionTagDao: TransactionTagDao,
    private val tagDao: TagDao
) {
    fun getAllRecurringWithTags(): Flow<List<RecurringTransactionWithTags>> =
        combine(
            recurringDao.getAllRecurring(),
            recurringTagDao.getAllRecurringTags(),
            tagDao.getAllTags()
        ) { recurs, links, tags ->
            recurs.map { r ->
                val itemTags = links
                    .filter { it.recurringTransactionId == r.id }
                    .mapNotNull { l -> tags.find { it.tagId == l.tagId } }
                RecurringTransactionWithTags(r, itemTags)
            }
        }

    suspend fun getDueTemplates(today: Long): List<RecurringTransaction> =
        recurringDao.getActiveDue(today)

    suspend fun getTagIds(recurringId: Int): Set<Int> =
        recurringTagDao.getTagIdsForRecurring(recurringId).toSet()

    suspend fun insertRecurring(recurring: RecurringTransaction, tagIds: Set<Int>): Long {
        val id = recurringDao.insertRecurring(recurring)
        saveTags(id.toInt(), tagIds)
        return id
    }

    suspend fun updateRecurring(recurring: RecurringTransaction, tagIds: Set<Int>) {
        recurringDao.updateRecurring(recurring)
        saveTags(recurring.id, tagIds)
    }

    suspend fun deleteRecurring(recurring: RecurringTransaction) {
        recurringTagDao.deleteTagsForRecurring(recurring.id)
        recurringDao.deleteRecurring(recurring)
    }

    private suspend fun saveTags(recurringId: Int, tagIds: Set<Int>) {
        recurringTagDao.deleteTagsForRecurring(recurringId)
        tagIds.forEach { tagId ->
            recurringTagDao.insertRecurringTag(RecurringTransactionTag(recurringId, tagId))
        }
    }

    /**
     * Applica in modo atomico le transazioni generate e gli aggiornamenti dei template.
     * Un crash a metà non lascia il DB in uno stato di duplicazione.
     */
    suspend fun applyGeneration(
        occurrences: List<GeneratedOccurrence>,
        updated: List<RecurringTransaction>
    ) {
        database.withTransaction {
            occurrences.forEach { occ ->
                val id = transactionDao.insertTransaction(
                    Transaction(
                        title = occ.title,
                        amount = occ.amount,
                        type = occ.type,
                        categoryId = occ.categoryId,
                        date = occ.date,
                        note = occ.note
                    )
                ).toInt()
                occ.tagIds.forEach { tagId ->
                    transactionTagDao.insertTransactionTag(TransactionTag(id, tagId))
                }
            }
            updated.forEach { recurringDao.updateRecurring(it) }
        }
    }
}