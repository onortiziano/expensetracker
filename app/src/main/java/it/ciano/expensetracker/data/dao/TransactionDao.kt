package it.ciano.expensetracker.data.dao

import androidx.room.*
import it.ciano.expensetracker.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction): Int

    // Prende tutte le transazioni ordinate dalla più recente alla più vecchia
    @Transaction
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactionsWithTags(): Flow<List<TransactionWithTags>>


    // Prende solo le transazioni di una specifica categoria
    @Query("SELECT * FROM transactions WHERE categoryId = :catId ORDER BY date DESC")
    fun getTransactionsByCategory(catId: Int): Flow<List<Transaction>>

    // CALCOLO TOTALE USCITE: Somma l'importo dove il tipo è 'EXPENSE'
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE'")
    fun getTotalExpenses(): Flow<Double?>

    // CALCOLO TOTALE ENTRATE: Somma l'importo dove il tipo è 'INCOME'
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME'")
    fun getTotalIncome(): Flow<Double?>

    // PRELIEVO PER DATA: Prende le transazioni tra due timestamp (inizio e fine mese)
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsByPeriod(startDate: Long, endDate: Long): Flow<List<Transaction>>
	
	// MODIFICA
	@Update
    suspend fun updateTransaction(transaction: Transaction)
}