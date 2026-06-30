package it.ciano.expensetracker.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.TransactionTag

@Dao
interface TagDao {
    // --- Gestione Tag ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: Tag): Long

    @Delete
    suspend fun deleteTag(tag: Tag)

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): Tag?

    // --- Gestione Collegamenti (Transazione <-> Tag) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkTagToTransaction(transactionTag: TransactionTag)

    @Delete
    suspend fun unlinkTagFromTransaction(transactionTag: TransactionTag)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId")
    suspend fun removeAllTagsFromTransaction(transactionId: Int)

    // Questa è la query magica: recupera tutti i Tag associati a una specifica Transazione
    @Query("""
        SELECT tags.* FROM tags 
        INNER JOIN transaction_tags ON tags.tagId = transaction_tags.tagId 
        WHERE transaction_tags.transactionId = :transactionId
    """)
    fun getTagsForTransaction(transactionId: Int): Flow<List<Tag>>


    // Recupera tutte le transazioni che hanno un determinato Tag
    @Query("""
        SELECT transactions.* FROM transactions 
        INNER JOIN transaction_tags ON transactions.transactionId = transaction_tags.transactionId 
        WHERE transaction_tags.tagId = :tagId
    """)
    fun getTransactionsForTag(tagId: Int): Flow<List<Transaction>>
}
