package it.ciano.expensetracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import it.ciano.expensetracker.data.model.Debt
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Insert
    suspend fun insertDebt(debt: Debt): Long

    @Query("SELECT * FROM debts WHERE isSettled = 0 ORDER BY id")
    fun getAllOpenDebts(): Flow<List<Debt>>

    @Query("SELECT * FROM debts WHERE transactionId = :transactionId")
    fun getDebtsByTransaction(transactionId: Int): Flow<List<Debt>>

    @Query("SELECT amount FROM debts WHERE id = :debtId")
    suspend fun getAmountById(debtId: Int): Double?

    @Query("UPDATE debts SET isSettled = 1, settledDate = :date WHERE id = :debtId")
    suspend fun markSettled(debtId: Int, date: Long)
}