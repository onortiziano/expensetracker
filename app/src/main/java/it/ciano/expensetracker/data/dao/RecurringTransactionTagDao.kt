package it.ciano.expensetracker.data.dao

import androidx.room.*
import it.ciano.expensetracker.data.model.RecurringTransactionTag
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionTagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringTag(link: RecurringTransactionTag)

    @Query("DELETE FROM recurring_transaction_tags WHERE recurringTransactionId = :recurringId")
    suspend fun deleteTagsForRecurring(recurringId: Int)

    @Query("SELECT * FROM recurring_transaction_tags")
    fun getAllRecurringTags(): Flow<List<RecurringTransactionTag>>

    @Query("SELECT tagId FROM recurring_transaction_tags WHERE recurringTransactionId = :recurringId")
    suspend fun getTagIdsForRecurring(recurringId: Int): List<Int>
}
