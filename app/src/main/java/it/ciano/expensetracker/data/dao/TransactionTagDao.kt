package it.ciano.expensetracker.data.dao

import androidx.room.*
import it.ciano.expensetracker.data.model.TransactionTag
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionTag(transactionTag: TransactionTag)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId")
    suspend fun deleteTagsForTransaction(transactionId: Int)

    @Query("SELECT * FROM transaction_tags WHERE transactionId = :transactionId")
    suspend fun getTagsForTransaction(transactionId: Int): List<it.ciano.expensetracker.data.model.Tag> 
    // Attenzione: questa query di base non funziona, useremo la relazione in TransactionDao
}
