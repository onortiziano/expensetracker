package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val amount: Double,
    val type: String,          // "EXPENSE" o "INCOME"
    val categoryId: Int,
    val frequency: String,     // DAILY | WEEKLY | BIWEEKLY | MONTHLY | ANNUAL
    val startDate: Long,       // epoch millis
    val endDate: Long? = null, // null = indefinita
    val nextDueDate: Long,     // quando generare la prossima transazione
    val note: String = "",
    val isActive: Boolean = true,
    val lastGeneratedDate: Long? = null
)
